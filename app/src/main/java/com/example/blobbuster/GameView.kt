package com.example.blobbuster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState {
    PLAYING, GAME_OVER
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    // マルチタッチ管理
    private var dragPointerId: Int = -1   // プレイヤー移動用の指
    private var lastDragX: Float = 0f
    private var shootPointerId: Int = -1  // 射撃用の指
    @Volatile private var shootTargetX: Float = 0f
    @Volatile private var shootTargetY: Float = 0f
    @Volatile private var isShooting: Boolean = false
    private val pendingBullets = mutableListOf<Bullet>() // UIスレッドから追加する弾

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    /** Bulletをプールして使い回す。毎回newしないのでGCを抑制 */
    private lateinit var bulletPool: BulletPool

    private lateinit var player: Player
    private val bullets: MutableList<Bullet> = mutableListOf()
    private lateinit var blobManager: BlobManager
    private val scoreManager = ScoreManager()

    private var hp: Int = 3
    private val maxHp: Int = 3
    private var invincibleTimer: Int = 0
    private val invincibleDuration: Int = 60

    private var gameState: GameState = GameState.PLAYING
    private var frameCount: Int = 0
    private var bgScrollY: Float = 0f

    // 星空データ: [x, y, radius, alpha]
    private data class Star(val x: Float, val y: Float, val r: Float, val alpha: Int)
    private val stars = mutableListOf<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // グリッドライン（薄い）
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(18, 64, 160, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val bgPaint = Paint().apply { color = Color.parseColor("#080E1A") }
    private val groundLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 64, 196, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")  // ネオンシアン
        textSize = 0f
        isFakeBoldText = true
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")  // ネオンレッド
    }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")  // ネオンゴールド
        isFakeBoldText = true
    }
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#AA000000")
    }
    private val gameOverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        isFakeBoldText = true
    }
    private val retryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val gameOverScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height

        // 共有PaintとBulletPoolをscreenWidth確定後に1回だけ初期化
        Blob.initSharedPaints(screenWidth)
        Bullet.initSharedPaints(screenWidth)
        bulletPool = BulletPool(screenWidth, screenHeight)

        val textSize = screenWidth * 0.05f
        scorePaint.textSize = textSize
        roundPaint.textSize = textSize
        gameOverPaint.textSize = screenWidth * 0.12f
        retryPaint.textSize = screenWidth * 0.06f
        gameOverScorePaint.textSize = screenWidth * 0.07f

        // 星空を生成
        val rng = Random(42)
        stars.clear()
        repeat(90) {
            val alpha = rng.nextInt(120) + 60
            val radius = rng.nextFloat() * 2.2f + 0.4f
            stars.add(Star(
                x = rng.nextFloat() * screenWidth,
                y = rng.nextFloat() * (screenHeight * 0.88f),
                r = radius,
                alpha = alpha
            ))
        }

        initGame()
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    private fun initGame() {
        player = Player(screenWidth, screenHeight)
        bullets.clear()
        blobManager = BlobManager(screenWidth, screenHeight)
        scoreManager.reset()
        hp = maxHp
        invincibleTimer = 0
        gameState = GameState.PLAYING
        frameCount = 0
        bgScrollY = 0f
        dragPointerId = -1
        shootPointerId = -1
        isShooting = false
        synchronized(pendingBullets) { pendingBullets.clear() }
    }

    private fun startThread() {
        gameThread = GameThread(this).also {
            it.isRunning = true
            it.start()
        }
    }

    private fun stopThread() {
        gameThread?.let {
            it.isRunning = false
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                // 無視
            }
        }
        gameThread = null
    }

    fun pause() {
        stopThread()
    }

    fun resume() {
        if (gameThread == null && holder.surface.isValid) {
            startThread()
        }
    }

    // 下ゾーン境界: この y 以下はドラッグ操作、以上は射撃操作
    private val dragZoneTop get() = screenHeight * 0.75f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameState == GameState.GAME_OVER) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) initGame()
            return true
        }

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val tapX = event.getX(actionIndex)
                val tapY = event.getY(actionIndex)
                if (tapY >= dragZoneTop) {
                    // 下ゾーン: ドラッグ開始
                    if (dragPointerId == -1) {
                        dragPointerId = pointerId
                        lastDragX = tapX
                    }
                } else {
                    // 上ゾーン: 射撃開始（ホールド中は連続発射）
                    if (shootPointerId == -1) {
                        shootPointerId = pointerId
                        shootTargetX = tapX
                        shootTargetY = tapY
                        isShooting = true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // ドラッグ移動
                if (dragPointerId != -1) {
                    val idx = event.findPointerIndex(dragPointerId)
                    if (idx != -1) {
                        val currentX = event.getX(idx)
                        player.x = currentX.coerceIn(player.width / 2f, screenWidth - player.width / 2f)
                    }
                }
                // 射撃位置更新（指を動かすと向きが変わる）
                if (shootPointerId != -1) {
                    val idx = event.findPointerIndex(shootPointerId)
                    if (idx != -1) {
                        shootTargetX = event.getX(idx)
                        shootTargetY = event.getY(idx)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == dragPointerId) dragPointerId = -1
                if (pointerId == shootPointerId) {
                    shootPointerId = -1
                    isShooting = false
                }
            }
        }
        return true
    }

    fun update() {
        if (gameState != GameState.PLAYING) return

        frameCount++

        // 背景スクロール
        bgScrollY += 1.5f

        // プレイヤー更新
        player.update()

        // 常時連射（タップ中はその方向、未タップ時は真上）
        val aimX = if (isShooting) shootTargetX else player.x
        val aimY = if (isShooting) shootTargetY else 0f
        player.shoot(aimX, aimY, bulletPool)?.let { bullets.add(it) }

        // UIスレッドで追加された弾をメインリストへ
        synchronized(pendingBullets) {
            bullets.addAll(pendingBullets)
            pendingBullets.clear()
        }

        // 弾の更新（死んだ弾はプールに返却）
        val bulletIter = bullets.iterator()
        while (bulletIter.hasNext()) {
            val b = bulletIter.next()
            b.update()
            if (b.isDead) {
                bulletIter.remove()
                bulletPool.recycle(b)
            }
        }

        // Blob更新（プレイヤー座標を渡す）
        blobManager.update(player.x, player.y)

        // 弾×Blob当たり判定（toList()コピーなし）
        val bulletsToRemove = mutableListOf<Bullet>()
        val blobsToRemove   = mutableListOf<Blob>()

        for (bullet in bullets) {
            if (bullet.isDead) continue
            for (blob in blobManager.blobs) {
                if (blobsToRemove.contains(blob)) continue
                val dx = bullet.x - blob.cx
                val dy = bullet.y - blob.cy
                val r  = bullet.radius + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    bulletsToRemove.add(bullet)
                    if (blob.takeDamage()) {
                        blobsToRemove.add(blob)
                        scoreManager.addScore(blob.size.score())
                        blobManager.onKill()
                    }
                    break
                }
            }
        }

        bulletsToRemove.forEach { it.isDead = true; bulletPool.recycle(it) }
        bullets.removeAll(bulletsToRemove)
        blobManager.blobs.removeAll(blobsToRemove)

        // Player×Blob当たり判定（toList()コピーなし）
        if (invincibleTimer <= 0) {
            for (blob in blobManager.blobs) {
                val dx = player.x - blob.cx
                val dy = player.y - blob.cy
                val r  = player.width / 2f + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    hp--
                    invincibleTimer = invincibleDuration
                    if (hp <= 0) gameState = GameState.GAME_OVER
                    break
                }
            }
        } else {
            invincibleTimer--
        }

        // GAME_OVER判定（念の為）
        if (hp <= 0 && gameState == GameState.PLAYING) {
            gameState = GameState.GAME_OVER
        }
    }

    fun draw() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            drawInternal(canvas)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawInternal(canvas: Canvas) {
        // 背景
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // グリッドライン（スクロール）
        val gridSpacing = screenWidth * 0.12f
        var gx = 0f
        while (gx <= screenWidth) {
            canvas.drawLine(gx, 0f, gx, screenHeight * 0.88f, gridPaint)
            gx += gridSpacing
        }
        val scrollOffset = bgScrollY % gridSpacing
        var gy = scrollOffset - gridSpacing
        while (gy <= screenHeight * 0.88f) {
            canvas.drawLine(0f, gy, screenWidth.toFloat(), gy, gridPaint)
            gy += gridSpacing
        }

        // 星空（スクロール）
        for (star in stars) {
            val sy = ((star.y + bgScrollY * 0.4f) % (screenHeight * 0.88f)).let {
                if (it < 0f) it + screenHeight * 0.88f else it
            }
            starPaint.color = Color.argb(star.alpha, 200, 220, 255)
            canvas.drawCircle(star.x, sy, star.r, starPaint)
        }

        // 地面ライン（プレイエリア境界）
        canvas.drawLine(0f, screenHeight * 0.88f, screenWidth.toFloat(), screenHeight * 0.88f, groundLinePaint)

        // Blob描画
        blobManager.draw(canvas)

        // 弾描画
        bullets.forEach { it.draw(canvas) }

        // プレイヤー描画（無敵中は点滅）
        player.draw(canvas, invincibleTimer > 0, frameCount)

        // UI: スコア（左上）
        canvas.drawText("SCORE: ${scoreManager.score}", screenWidth * 0.03f, screenHeight * 0.05f, scorePaint)

        // UI: HP（右上・赤ハート）
        val heartText = "HP: " + "♥".repeat(hp.coerceAtLeast(0))
        heartPaint.textSize = scorePaint.textSize
        val heartBounds = Rect()
        heartPaint.getTextBounds(heartText, 0, heartText.length, heartBounds)
        canvas.drawText(heartText, screenWidth - heartBounds.width() - screenWidth * 0.03f, screenHeight * 0.05f, heartPaint)

        // UI: WAVE（中央上）
        val roundText = "WAVE ${blobManager.wave}"
        roundPaint.textSize = scorePaint.textSize
        val roundBounds = Rect()
        roundPaint.getTextBounds(roundText, 0, roundText.length, roundBounds)
        canvas.drawText(roundText, (screenWidth - roundBounds.width()) / 2f, screenHeight * 0.05f, roundPaint)

        // GAME OVER オーバーレイ
        if (gameState == GameState.GAME_OVER) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)

            // GAME OVER テキスト
            val goText = "GAME OVER"
            val goBounds = Rect()
            gameOverPaint.getTextBounds(goText, 0, goText.length, goBounds)
            canvas.drawText(
                goText,
                (screenWidth - goBounds.width()) / 2f,
                screenHeight * 0.38f,
                gameOverPaint
            )

            // スコア表示
            val scoreText = "SCORE: ${scoreManager.score}"
            val scoreBounds = Rect()
            gameOverScorePaint.getTextBounds(scoreText, 0, scoreText.length, scoreBounds)
            canvas.drawText(
                scoreText,
                (screenWidth - scoreBounds.width()) / 2f,
                screenHeight * 0.52f,
                gameOverScorePaint
            )

            // タップリトライ
            val retryText = "TAP TO RETRY"
            val retryBounds = Rect()
            retryPaint.getTextBounds(retryText, 0, retryText.length, retryBounds)
            canvas.drawText(
                retryText,
                (screenWidth - retryBounds.width()) / 2f,
                screenHeight * 0.65f,
                retryPaint
            )
        }
    }
}
