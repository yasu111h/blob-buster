package com.example.blobbuster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState {
    PLAYING, PAUSED, GAME_OVER
}

class GameView(context: Context, private val soundManager: SoundManager) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameThread: GameThread? = null

    // マルチタッチ管理
    private var dragPointerId: Int = -1   // プレイヤー移動用の指
    private var lastDragX: Float = 0f
    private val pendingBullets = mutableListOf<Bullet>() // UIスレッドから追加する弾

    // 敵弾の上限
    private val maxEnemyBullets = 35

    // ダッシュダメージ用：前フレームのプレイヤー位置
    private var prevPlayerX: Float = 0f
    private var prevPlayerY: Float = 0f

    // ── デバッグ ─────────────────────────────────────────
    var debugShowEnemies: Boolean = true      // 敵の描画ON/OFF
    var debugEnemyCanShoot: Boolean = true    // 敵の攻撃ON/OFF
    var debugShowInfo: Boolean = false        // デバッグ情報表示

    private var debugPanelOpen: Boolean = false
    private var debugBtnRect   = RectF()
    private var debugPanelRect = RectF()
    private var dbgToggle1Rect = RectF()  // 敵の表示
    private var dbgToggle2Rect = RectF()  // 敵の攻撃
    private var dbgToggle3Rect = RectF()  // デバッグ表示
    private var dbgCloseRect   = RectF()

    // FPS計測
    private var lastFrameNs: Long = 0L
    @Volatile var currentFps: Float = 0f
    @Volatile var currentFrameMs: Float = 0f

    // デバッグUI用Paint（使い回し）
    private val dbgBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 20, 40, 80)
    }
    private val dbgBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 64, 196, 255)
        style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val dbgBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 64, 196, 255); isFakeBoldText = true
    }
    private val dbgPanelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 5, 15, 35)
    }
    private val dbgPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 64, 196, 255)
        style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val dbgLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 180, 220, 255)
    }
    private val dbgOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88"); isFakeBoldText = true
    }
    private val dbgOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444"); isFakeBoldText = true
    }
    private val dbgInfoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 8, 20)
    }
    private val dbgInfoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 100, 220, 255)
    }
    // ────────────────────────────────────────────────────

    // ── 一時停止ボタン ─────────────────────────────────────
    private var pauseBtnRect = RectF()
    private val pauseBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 20, 80, 40)
    }
    private val pauseBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 64, 255, 128)
        style = Paint.Style.STROKE; strokeWidth = 1.5f
    }
    private val pauseBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 64, 255, 128); isFakeBoldText = true
    }
    private val pauseOverlayPaint = Paint().apply { color = Color.argb(180, 0, 0, 0) }
    private val pauseLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; isFakeBoldText = true
    }
    // ────────────────────────────────────────────────────

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    /** Bulletをプールして使い回す。毎回newしないのでGCを抑制 */
    private lateinit var bulletPool: BulletPool

    private lateinit var player: Player
    private val bullets: MutableList<Bullet> = mutableListOf()
    private val enemyBullets: MutableList<EnemyBullet> = mutableListOf()
    private val items: MutableList<PowerUpItem> = mutableListOf()
    private lateinit var blobManager: BlobManager
    private val scoreManager = ScoreManager()

    private var hp: Int = 3
    private val maxHp: Int = 3
    private var invincibleTimer: Int = 0
    private val invincibleDuration: Int = 90  // 1.5秒 @ 60fps

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
        EnemyBullet.initSharedPaints(screenWidth)
        PowerUpItem.initPaints(screenWidth)
        bulletPool = BulletPool(screenWidth, screenHeight, initialSize = 60)

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

        // デバッグボタン（右下）
        val dbgBtnW = screenWidth * 0.13f
        val dbgBtnH = screenWidth * 0.07f
        val dbgBtnX = screenWidth - dbgBtnW - screenWidth * 0.02f
        val dbgBtnY = screenHeight * 0.895f
        debugBtnRect = RectF(dbgBtnX, dbgBtnY, dbgBtnX + dbgBtnW, dbgBtnY + dbgBtnH)
        dbgBtnTextPaint.textSize = screenWidth * 0.035f

        // デバッグパネル（右寄り）
        val panelW = screenWidth * 0.55f
        val panelH = screenHeight * 0.28f
        val panelX = screenWidth - panelW - screenWidth * 0.03f
        val panelY = screenHeight * 0.60f
        debugPanelRect = RectF(panelX, panelY, panelX + panelW, panelY + panelH)
        dbgLabelPaint.textSize = screenWidth * 0.034f
        dbgOnPaint.textSize = screenWidth * 0.034f
        dbgOffPaint.textSize = screenWidth * 0.034f

        // トグル行の配置
        val rowH = panelH * 0.22f
        val rowY1 = panelY + panelH * 0.22f
        val rowY2 = rowY1 + panelH * 0.25f
        val rowY3 = rowY2 + panelH * 0.25f
        dbgToggle1Rect = RectF(panelX + panelW * 0.05f, rowY1 - rowH * 0.8f, panelX + panelW * 0.95f, rowY1 + rowH * 0.2f)
        dbgToggle2Rect = RectF(panelX + panelW * 0.05f, rowY2 - rowH * 0.8f, panelX + panelW * 0.95f, rowY2 + rowH * 0.2f)
        dbgToggle3Rect = RectF(panelX + panelW * 0.05f, rowY3 - rowH * 0.8f, panelX + panelW * 0.95f, rowY3 + rowH * 0.2f)
        dbgCloseRect = RectF(panelX + panelW * 0.75f, panelY + panelH * 0.02f, panelX + panelW * 0.98f, panelY + panelH * 0.16f)

        // デバッグ情報テキスト
        dbgInfoTextPaint.textSize = screenWidth * 0.030f

        // 一時停止ボタン（左下）
        pauseBtnRect = RectF(
            screenWidth * 0.02f,
            screenHeight * 0.895f,
            screenWidth * 0.02f + screenWidth * 0.13f,
            screenHeight * 0.895f + screenWidth * 0.07f
        )
        pauseBtnTextPaint.textSize = screenWidth * 0.040f

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
        enemyBullets.clear()
        items.clear()
        dragPointerId = -1
        synchronized(pendingBullets) { pendingBullets.clear() }
        prevPlayerX = screenWidth / 2f
        prevPlayerY = screenHeight * 0.90f
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

    private val dragZoneTop get() = screenHeight * 0.75f  // 後方互換のため残す

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 一時停止ボタン（GAME_OVER以外で有効）
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            pauseBtnRect.contains(event.x, event.y)) {
            when (gameState) {
                GameState.PLAYING -> { gameState = GameState.PAUSED; soundManager.pauseBgmByUser() }
                GameState.PAUSED  -> { gameState = GameState.PLAYING; soundManager.resumeBgmByUser() }
                else -> {}
            }
            return true
        }

        // PAUSED中は他のタッチを無視
        if (gameState == GameState.PAUSED) return true

        if (gameState == GameState.GAME_OVER) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) initGame()
            return true
        }

        // デバッグパネルが開いている場合はパネルのタッチを優先
        if (debugPanelOpen && event.actionMasked == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            when {
                dbgCloseRect.contains(tx, ty)   -> debugPanelOpen = false
                dbgToggle1Rect.contains(tx, ty) -> debugShowEnemies = !debugShowEnemies
                dbgToggle2Rect.contains(tx, ty) -> debugEnemyCanShoot = !debugEnemyCanShoot
                dbgToggle3Rect.contains(tx, ty) -> debugShowInfo = !debugShowInfo
                !debugPanelRect.contains(tx, ty) -> debugPanelOpen = false
            }
            return true
        }

        // デバッグボタンタップ（UP時）
        if (event.actionMasked == MotionEvent.ACTION_UP &&
            debugBtnRect.contains(event.x, event.y)) {
            debugPanelOpen = !debugPanelOpen
            return true
        }

        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val tapX = event.getX(actionIndex)
                val tapY = event.getY(actionIndex)

                // プレイヤー近辺かどうか判定
                val dx = tapX - player.x
                val dy = tapY - player.y
                val distToPlayer = sqrt(dx * dx + dy * dy)

                if (distToPlayer <= player.playerRadius && dragPointerId == -1) {
                    // プレイヤー近辺 → ドラッグ開始
                    dragPointerId = pointerId
                }
                // それ以外のタップは無視（発射方向は常に真上固定）
            }

            MotionEvent.ACTION_MOVE -> {
                // プレイヤー移動（X・Y両方追従）
                if (dragPointerId != -1) {
                    val idx = event.findPointerIndex(dragPointerId)
                    if (idx != -1) {
                        player.x = event.getX(idx).coerceIn(player.width / 2f, screenWidth - player.width / 2f)
                        player.y = event.getY(idx).coerceIn(screenHeight * 0.35f, screenHeight * 0.93f)
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

        // FPS計測
        val nowNs = System.nanoTime()
        if (lastFrameNs != 0L) {
            val elapsedNs = nowNs - lastFrameNs
            currentFrameMs = elapsedNs / 1_000_000f
            currentFps = if (elapsedNs > 0) 1_000_000_000f / elapsedNs else 0f
        }
        lastFrameNs = nowNs

        // 背景スクロール
        bgScrollY += 1.5f

        // プレイヤー更新
        player.update()

        // ダッシュダメージ判定（高速移動で敵・敵弾と衝突したら1ダメージ）
        val dashDx = player.x - prevPlayerX
        val dashDy = player.y - prevPlayerY
        val dashDist = sqrt(dashDx * dashDx + dashDy * dashDy)
        val dashThreshold = screenWidth * 0.11f  // 画面幅の11%以上移動でダッシュ扱い
        if (dashDist > dashThreshold && invincibleTimer <= 0) {
            var dashHit = false
            for (blob in blobManager.blobs) {
                if (segmentCircleDist(prevPlayerX, prevPlayerY, player.x, player.y, blob.cx, blob.cy)
                    <= blob.radius + player.width / 2f) { dashHit = true; break }
            }
            if (!dashHit) {
                for (eb in enemyBullets) {
                    if (segmentCircleDist(prevPlayerX, prevPlayerY, player.x, player.y, eb.x, eb.y)
                        <= eb.radius + player.width / 2f) { dashHit = true; break }
                }
            }
            if (dashHit) {
                hp--
                invincibleTimer = invincibleDuration
                soundManager.playPlayerDamaged()
                if (hp <= 0) gameState = GameState.GAME_OVER
            }
        }
        prevPlayerX = player.x
        prevPlayerY = player.y

        // 常時連射（常に真上固定）
        bullets.addAll(player.shootSpread(player.x, 0f, bulletPool))

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

        // Blob更新（プレイヤー座標とスコアを渡す）
        blobManager.update(player.x, player.y, scoreManager.score)

        // 敵弾発射（上限チェック・混雑度による間隔制御込み）
        // 発射禁止ライン(0.80f)より下にいる敵は撃たせない。
        // 削除ライン(0.88f)との間にバッファを設けることで、
        // 最速弾でも削除前に地面を越えることが数学上ありえない構造にする。
        if (debugEnemyCanShoot) {
            val noFireLine = screenHeight * 0.80f
            for (blob in blobManager.blobs) {
                if (blob.cy > noFireLine) continue
                enemyBullets.addAll(blob.tryShoot(player.x, player.y, enemyBullets.size, maxEnemyBullets))
            }
        }

        // 敵弾更新
        val ebIter = enemyBullets.iterator()
        while (ebIter.hasNext()) {
            val eb = ebIter.next()
            eb.update()
            if (eb.isDead) ebIter.remove()
        }

        // アイテム更新・回収
        val itemIter = items.iterator()
        while (itemIter.hasNext()) {
            val item = itemIter.next()
            item.update(player.x, player.y)
            if (item.checkCollect(player.x, player.y, player.width)) {
                player.increaseBulletLevel()
                item.isDead = true
                itemIter.remove()
            } else if (item.isDead) {
                itemIter.remove()
            }
        }

        // 弾×Blob当たり判定（最適化: 中間リスト廃止・O(1)dead判定）
        val deadBlobsForDrop = mutableListOf<Blob>()
        for (bullet in bullets) {
            if (bullet.isDead) continue
            for (blob in blobManager.blobs) {
                if (blob.isDead) continue   // O(1) フラグチェック（旧: O(n) contains）
                val dx = bullet.x - blob.cx
                val dy = bullet.y - blob.cy
                val r  = bullet.radius + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    bullet.isDead = true
                    bulletPool.recycle(bullet)
                    if (blob.takeDamage()) {    // blob.isDead = true もここでセット
                        scoreManager.addScore(blob.size.score())
                        blobManager.onKill()
                        deadBlobsForDrop.add(blob)
                        soundManager.playEnemyKilled()
                    }
                    break
                }
            }
        }

        // イテレータで一括削除（removeAll + 線形検索を排除）
        val bIter2 = bullets.iterator()
        while (bIter2.hasNext()) { if (bIter2.next().isDead) bIter2.remove() }
        blobManager.blobs.removeAll { it.isDead }

        // 倒した敵からアイテムドロップ（画面上2個まで）
        for (deadBlob in deadBlobsForDrop) {
            if (items.size < 2 && Random.nextFloat() < deadBlob.size.itemDropChance()) {
                items.add(PowerUpItem(deadBlob.cx, deadBlob.cy, screenWidth, screenHeight))
            }
        }

        // Player×Blob当たり判定（toList()コピーなし）
        if (invincibleTimer <= 0) {
            for (blob in blobManager.blobs) {
                val dx = player.x - blob.cx
                val dy = player.y - blob.cy
                val r  = player.width / 2f + blob.radius
                if (dx * dx + dy * dy <= r * r) {
                    hp--
                    invincibleTimer = invincibleDuration
                    soundManager.playPlayerDamaged()
                    if (hp <= 0) gameState = GameState.GAME_OVER
                    break
                }
            }
        } else {
            invincibleTimer--
        }

        // 敵弾×Player当たり判定
        if (invincibleTimer <= 0) {
            val ebHitIter = enemyBullets.iterator()
            while (ebHitIter.hasNext()) {
                val eb = ebHitIter.next()
                val dx = player.x - eb.x
                val dy = player.y - eb.y
                val r  = player.width / 2f + eb.radius
                if (dx * dx + dy * dy <= r * r) {
                    ebHitIter.remove()
                    hp--
                    invincibleTimer = invincibleDuration
                    soundManager.playPlayerDamaged()
                    if (hp <= 0) gameState = GameState.GAME_OVER
                    break
                }
            }
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

        // Blob描画（デバッグ: 非表示トグル）
        if (debugShowEnemies) blobManager.draw(canvas)

        // 弾描画
        bullets.forEach { it.draw(canvas) }

        // 敵弾描画
        enemyBullets.forEach { it.draw(canvas) }

        // アイテム描画
        items.forEach { it.draw(canvas) }

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

        // UI: LEVEL（中央上）
        val roundText = "LEVEL ${blobManager.level}"
        roundPaint.textSize = scorePaint.textSize
        val roundBounds = Rect()
        roundPaint.getTextBounds(roundText, 0, roundText.length, roundBounds)
        canvas.drawText(roundText, (screenWidth - roundBounds.width()) / 2f, screenHeight * 0.05f, roundPaint)

        // ── デバッグUI ─────────────────────────────────────
        // デバッグ情報オーバーレイ（左上）
        if (debugShowInfo) {
            val totalObjs = blobManager.blobs.size + enemyBullets.size + bullets.size + items.size
            val lines = listOf(
                "ENEMIES:   ${blobManager.blobs.size}",
                "ENEMY BLT: ${enemyBullets.size}",
                "PLY BLT:   ${bullets.size}",
                "ITEMS:     ${items.size}",
                "TOTAL OBJ: $totalObjs",
                "FPS:       ${"%.1f".format(currentFps)}",
                "FRAME:     ${"%.1f".format(currentFrameMs)}ms"
            )
            val lh = dbgInfoTextPaint.textSize * 1.5f
            val boxW = screenWidth * 0.38f
            val boxH = lh * lines.size + lh * 0.5f
            val boxX = screenWidth * 0.02f
            val boxY = screenHeight * 0.07f
            canvas.drawRoundRect(RectF(boxX, boxY, boxX + boxW, boxY + boxH), 8f, 8f, dbgInfoBgPaint)
            lines.forEachIndexed { i, text ->
                canvas.drawText(text, boxX + screenWidth * 0.02f, boxY + lh * (i + 1), dbgInfoTextPaint)
            }
        }

        // DBGボタン
        canvas.drawRoundRect(debugBtnRect, 8f, 8f, dbgBtnPaint)
        canvas.drawRoundRect(debugBtnRect, 8f, 8f, dbgBtnBorderPaint)
        val dbgLabel = "DBG"
        val dbgBounds = Rect(); dbgBtnTextPaint.getTextBounds(dbgLabel, 0, dbgLabel.length, dbgBounds)
        canvas.drawText(dbgLabel,
            debugBtnRect.centerX() - dbgBounds.width() / 2f,
            debugBtnRect.centerY() + dbgBounds.height() / 2f,
            dbgBtnTextPaint)

        // デバッグパネル
        if (debugPanelOpen) {
            canvas.drawRoundRect(debugPanelRect, 12f, 12f, dbgPanelBgPaint)
            canvas.drawRoundRect(debugPanelRect, 12f, 12f, dbgPanelBorderPaint)

            // タイトル
            canvas.drawText("DEBUG PANEL", debugPanelRect.left + screenWidth * 0.03f,
                debugPanelRect.top + dbgLabelPaint.textSize * 1.2f, dbgLabelPaint)

            // CLOSEボタン
            canvas.drawText("[X]", dbgCloseRect.left, dbgCloseRect.bottom, dbgOffPaint)

            // トグル行ヘルパー
            fun drawToggleRow(rect: RectF, label: String, enabled: Boolean) {
                val statusPaint = if (enabled) dbgOnPaint else dbgOffPaint
                val statusText = if (enabled) "ON" else "OFF"
                canvas.drawText(label, rect.left, rect.bottom, dbgLabelPaint)
                val sw = Rect(); statusPaint.getTextBounds(statusText, 0, statusText.length, sw)
                canvas.drawText(statusText, debugPanelRect.right - sw.width() - screenWidth * 0.04f, rect.bottom, statusPaint)
            }

            drawToggleRow(dbgToggle1Rect, "敵の表示", debugShowEnemies)
            drawToggleRow(dbgToggle2Rect, "敵の攻撃", debugEnemyCanShoot)
            drawToggleRow(dbgToggle3Rect, "デバッグ表示", debugShowInfo)
        }
        // ────────────────────────────────────────────────────

        // 一時停止ボタン（左下）
        canvas.drawRoundRect(pauseBtnRect, 8f, 8f, pauseBtnPaint)
        canvas.drawRoundRect(pauseBtnRect, 8f, 8f, pauseBtnBorderPaint)
        val plabel = if (gameState == GameState.PAUSED) "▶" else "||"
        val plBounds = Rect(); pauseBtnTextPaint.getTextBounds(plabel, 0, plabel.length, plBounds)
        canvas.drawText(plabel,
            pauseBtnRect.centerX() - plBounds.width() / 2f,
            pauseBtnRect.centerY() + plBounds.height() / 2f,
            pauseBtnTextPaint)

        // PAUSED オーバーレイ
        if (gameState == GameState.PAUSED) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), pauseOverlayPaint)
            pauseLabelPaint.textSize = screenWidth * 0.12f
            val pausedText = "PAUSED"
            val pausedBounds = Rect()
            pauseLabelPaint.getTextBounds(pausedText, 0, pausedText.length, pausedBounds)
            canvas.drawText(pausedText, (screenWidth - pausedBounds.width()) / 2f, screenHeight * 0.44f, pauseLabelPaint)
            pauseLabelPaint.textSize = screenWidth * 0.05f
            val resumeText = "▶ ボタンで再開"
            val resumeBounds = Rect()
            pauseLabelPaint.getTextBounds(resumeText, 0, resumeText.length, resumeBounds)
            canvas.drawText(resumeText, (screenWidth - resumeBounds.width()) / 2f, screenHeight * 0.57f, pauseLabelPaint)
        }

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

    /**
     * 線分 (ax,ay)-(bx,by) と点 (px,py) の最短距離
     * ダッシュ経路上に敵・弾がいるか判定するために使用
     */
    private fun segmentCircleDist(
        ax: Float, ay: Float, bx: Float, by: Float,
        px: Float, py: Float
    ): Float {
        val dx = bx - ax; val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val clampedT = t.coerceIn(0f, 1f)
        val projX = ax + clampedT * dx
        val projY = ay + clampedT * dy
        return sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }
}
