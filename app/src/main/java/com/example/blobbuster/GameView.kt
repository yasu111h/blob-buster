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

enum class GameState {
    PLAYING, GAME_OVER
}

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    // マルチタッチ管理
    private var dragPointerId: Int = -1   // プレイヤー移動用の指
    private var lastDragX: Float = 0f
    private val pendingBullets = mutableListOf<Bullet>() // UIスレッドから追加する弾

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

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

    private val bgPaint = Paint().apply { color = Color.parseColor("#0D1B2A") }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 0f
        isFakeBoldText = true
    }
    private val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
    }
    private val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
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

        val textSize = screenWidth * 0.05f
        scorePaint.textSize = textSize
        roundPaint.textSize = textSize
        gameOverPaint.textSize = screenWidth * 0.12f
        retryPaint.textSize = screenWidth * 0.06f
        gameOverScorePaint.textSize = screenWidth * 0.07f

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
        dragPointerId = -1
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
                    // 上ゾーン: 射撃（UIスレッドから安全に追加）
                    val bullet = player.shoot(tapX, tapY)
                    if (bullet != null) {
                        synchronized(pendingBullets) { pendingBullets.add(bullet) }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragPointerId != -1) {
                    val idx = event.findPointerIndex(dragPointerId)
                    if (idx != -1) {
                        val currentX = event.getX(idx)
                        player.move(currentX - lastDragX)
                        lastDragX = currentX
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (pointerId == dragPointerId) dragPointerId = -1
            }
        }
        return true
    }

    fun update() {
        if (gameState != GameState.PLAYING) return

        frameCount++

        // プレイヤー更新
        player.update()

        // UIスレッドで追加された弾をメインリストへ
        synchronized(pendingBullets) {
            bullets.addAll(pendingBullets)
            pendingBullets.clear()
        }

        // 弾の更新
        bullets.forEach { it.update() }
        bullets.removeAll { it.isDead }

        // Blob更新
        blobManager.update()

        // 弾×Blob当たり判定
        val bulletsToRemove = mutableListOf<Bullet>()
        val blobsToRemove = mutableListOf<Blob>()
        val blobsToAdd = mutableListOf<Blob>()

        for (bullet in bullets.toList()) {
            if (bullet.isDead) continue
            for (blob in blobManager.blobs.toList()) {
                if (blobsToRemove.contains(blob)) continue
                val dx = bullet.x - blob.cx
                val dy = bullet.y - blob.cy
                val r = bullet.radius + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    bulletsToRemove.add(bullet)
                    blobsToRemove.add(blob)
                    scoreManager.addScore(blob.size.score())
                    blobsToAdd.addAll(blob.split())
                    break
                }
            }
        }

        bullets.removeAll(bulletsToRemove)
        blobManager.blobs.removeAll(blobsToRemove)
        blobManager.blobs.addAll(blobsToAdd)

        // Player×Blob当たり判定
        if (invincibleTimer <= 0) {
            for (blob in blobManager.blobs.toList()) {
                val dx = player.x - blob.cx
                val dy = player.y - blob.cy
                val r = player.width / 2f + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    hp--
                    invincibleTimer = invincibleDuration
                    if (hp <= 0) {
                        gameState = GameState.GAME_OVER
                    }
                    break
                }
            }
        } else {
            invincibleTimer--
        }

        // クリア判定
        if (blobManager.isEmpty()) {
            blobManager.nextRound()
            scoreManager.round = blobManager.round
            bullets.clear()
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

        // UI: ラウンド（中央上）
        val roundText = "ROUND ${scoreManager.round}"
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
