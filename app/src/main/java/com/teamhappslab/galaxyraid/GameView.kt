package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState {
    PLAYING, PAUSED, GAME_OVER, CLEAR
}

class GameView(
    context: Context,
    private val soundManager: SoundManager,
    private val gameMode: String = "endless",
    private val stage: Int = 0   // ストーリーモードのステージ番号（1〜5）。エンドレスは0
) : SurfaceView(context), SurfaceHolder.Callback {

    companion object {
        /** デバッグモード：false にするとデバッグボタン・パネルが完全無効化される（リリース用） */
        const val DEBUG_MODE = true
    }

    private var gameThread: GameThread? = null

    // マルチタッチ管理
    private var dragPointerId: Int = -1   // プレイヤー移動用の指
    private var lastDragX: Float = 0f
    private val pendingBullets = mutableListOf<Bullet>() // UIスレッドから追加する弾

    // 敵弾の上限
    private val maxEnemyBullets = 35

    // 衝撃波リスト
    private val shockwaves = mutableListOf<Shockwave>()

    // ── ボス（ストーリーモード） ────────────────────────
    private val isStoryMode: Boolean get() = gameMode == "story"
    /** ストーリーのステージ設定（エンドレスはnull） */
    private val stageConfig: StageConfig? = if (gameMode == "story") StageConfig.forStage(stage) else null
    /** このステージでボスが出現する内部レベル */
    private val bossTriggerLevel: Int get() = stageConfig?.bossTriggerLevel ?: GameConfig.BOSS_TRIGGER_LEVEL
    private var midHealDone: Boolean = false       // 道中回復を済ませたか
    private var preBossHealDone: Boolean = false   // ボス前回復を済ませたか
    private var healMessageTimer: Int = 0          // 「HP回復」表示の残りフレーム
    private var boss: Boss? = null
    private var bossSpawned: Boolean = false       // ボスを一度出したか
    private var bossWaitTimer: Int = 0             // ボス出現レベル到達後、残存敵待ちのタイマー
    private var bossWarningTimer: Int = 0          // WARNING演出の残りフレーム
    private var bossHpDisplayRatio: Float = 1f     // HPバーの減少アニメーション用
    private var clearTapDelayTimer: Int = 0        // CLEAR直後の誤タップ防止
    private var gameOverTapDelayTimer: Int = 0     // GAME OVER直後の誤タップ防止
    private var clearAnimFrame: Int = 0            // CLEAR画面のアニメーション用カウンタ
    private var bossMinionTimer: Int = 0           // ボス戦中の雑魚出現タイマー
    private var bossExplosion: BossExplosion? = null      // ボス撃破爆発エフェクト
    private var clearCelebration: ClearCelebration? = null // クリア画面の紙吹雪演出

    private val bossHpBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 30, 0, 0)
    }
    private val bossHpBarTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 200, 80)
    }
    private val bossHpBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
    }
    private val bossHpBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 80, 80)
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val bossLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5252"); isFakeBoldText = true
    }
    private val warningTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744"); isFakeBoldText = true
    }
    private val warningBgPaint = Paint().apply { color = Color.argb(0, 120, 0, 0) }
    private val clearTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740"); isFakeBoldText = true
    }
    private val clearBonusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88"); isFakeBoldText = true
    }
    /** CONGRATULATIONS! 用（虹色グラデーション・脈動。シェーダーはsurfaceCreatedで設定） */
    private val congratsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    /** ボス撃破演出の最後の白フラッシュ用 */
    private val bossFlashPaint = Paint()
    // ────────────────────────────────────────────────────

    // ダッシュダメージ用：前フレームのプレイヤー位置
    private var prevPlayerX: Float = 0f
    private var prevPlayerY: Float = 0f

    // ── デバッグ ─────────────────────────────────────────
    var debugShowEnemies: Boolean = true      // 敵の描画ON/OFF
    var debugEnemyCanShoot: Boolean = true    // 敵の攻撃ON/OFF
    var debugShowInfo: Boolean = false        // デバッグ情報表示
    var debugShowHitbox: Boolean = false      // 自機の当たり判定範囲を表示

    private var debugPanelOpen: Boolean = false
    private var debugBtnRect   = RectF()
    private var debugPanelRect = RectF()
    private var dbgToggle1Rect = RectF()  // 敵の表示
    private var dbgToggle2Rect = RectF()  // 敵の攻撃
    private var dbgToggle3Rect = RectF()  // デバッグ表示
    private var dbgCloseRect   = RectF()
    // レベル操作ボタン
    private var dbgLvlMinusRect = RectF()
    private var dbgLvlPlusRect  = RectF()
    // 弾段数ボタン
    private var dbgBullet1Rect  = RectF()
    private var dbgBullet3Rect  = RectF()
    private var dbgBullet5Rect  = RectF()
    // HPボタン
    private var dbgHp1Rect = RectF()
    private var dbgHp2Rect = RectF()
    private var dbgHp3Rect = RectF()
    // 無敵モードトグル
    var debugInvincible: Boolean = false
    private var dbgInvincibleRect = RectF()
    // 弾減らないモードトグル（状態はPlayer.bulletLevelNoDecayが保持）
    private var dbgNoDecayRect = RectF()
    // 判定範囲表示トグル
    private var dbgHitboxRect = RectF()
    // レベル±10ボタン
    private var dbgLvlMinus10Rect = RectF()
    private var dbgLvlPlus10Rect  = RectF()

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
    // 自機の当たり判定範囲デバッグ描画（半透明の赤で塗り＋輪郭）
    private val hitboxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 40, 40)
    }
    private val hitboxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 80, 80); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    // 敵の当たり判定範囲デバッグ描画（半透明のシアンで塗り＋輪郭）
    private val enemyHitboxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 210, 255)
    }
    private val enemyHitboxStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 220, 255); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val dbgInfoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 8, 20)
    }
    private val dbgInfoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 100, 220, 255)
    }
    // ────────────────────────────────────────────────────

    // ── 一時停止ボタン ─────────────────────────────────────
    private var pauseBtnRect  = RectF()
    private var resumeBtnRect = RectF()  // PAUSEDオーバーレイ中央の再開ボタン
    private var homeBtnRect         = RectF()  // PAUSEDオーバーレイのHomeボタン
    private var gameOverHomeBtnRect = RectF()  // GAME_OVERオーバーレイのHomeボタン
    private var gameOverRetryBtnRect = RectF() // GAME_OVERオーバーレイのRetryボタン
    private var clearReturnBtnRect = RectF()   // CLEARオーバーレイのReturnボタン（ステージ選択へ）
    var onGoHome: (() -> Unit)? = null   // ホーム画面へ戻るコールバック

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
    private val resumeBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 30, 140, 60)
    }
    private val resumeBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 64, 255, 128)
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val resumeBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; isFakeBoldText = true
    }
    private val homeBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 50, 30, 10)
    }
    private val homeBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 180, 0)
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val homeBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 210, 60); isFakeBoldText = true
    }
    // ── アイテム取得エフェクト ────────────────────────────
    private var powerUpFlashTimer = 0
    private val powerUpAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
        style = Paint.Style.STROKE
    }
    // ────────────────────────────────────────────────────

    // ── ティアアップエフェクト ────────────────────────────
    private var tierUpTimer: Int = 0  // ティアアップエフェクトの残りフレーム
    private var tierUpNumber: Int = 0 // 何ティアになったか
    private val tierUpBgPaint = Paint().apply { color = Color.argb(0, 0, 0, 0) }  // 動的に変更
    private val tierUpTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740"); isFakeBoldText = true
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

    // 星空データ（多層パララックス）。baseYに bgScrollY*speedMul を足して描画位置を出す。
    private class Star(
        val x: Float,
        val baseY: Float,
        val r: Float,
        val alphaBase: Int,
        val color: Int,        // rgb（描画時にalphaを上書き）
        val speedMul: Float,   // 層ごとのスクロール速度（奥=遅い/手前=速い）
        val glow: Boolean,     // 明るい星は周囲を淡く光らせる
        val twinkle: Boolean,  // 一部の星だけ明滅
        val twinklePhase: Float
    )
    private val stars = mutableListOf<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 近景スターストリーク（高速前進感を出す縦の光線）
    private class Streak(val x: Float, val baseY: Float, val len: Float, val speedMul: Float, val alpha: Int)
    private val streaks = mutableListOf<Streak>()
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(150, 195, 245)   // 弾(シアン/マゼンタ)と紛れない控えめな青白
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // 背景（背景色＋ネオン星雲）を1枚の不透明Bitmapに焼いておき、毎フレーム等倍で貼るだけ＝高速。
    // ※毎フレームのグラデーション生成・拡大・半透明合成を避けるのが目的（SurfaceViewはCPU描画）。
    private var bgBitmap: Bitmap? = null

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
    private val levelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#69F0AE")  // ネオングリーン（レベル表記用）
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
    private val rankInBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 180, 120, 0)
    }
    private val rankInBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val rankInTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740"); isFakeBoldText = true
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height

        // 共有PaintとBulletPoolをscreenWidth確定後に1回だけ初期化
        Blob.initSharedPaints(screenWidth, context)
        Player.initBitmap(context, screenWidth * 0.08f)
        Bullet.initSharedPaints(screenWidth)
        EnemyBullet.initSharedPaints(screenWidth)
        PowerUpItem.initPaints(screenWidth)
        Boss.initBitmap(context, screenWidth * GameConfig.BOSS_WIDTH_RATIO)
        bulletPool = BulletPool(screenWidth, screenHeight, initialSize = 60)

        val textSize = screenWidth * 0.05f
        scorePaint.textSize = textSize
        roundPaint.textSize = textSize
        gameOverPaint.textSize = screenWidth * 0.12f
        retryPaint.textSize = screenWidth * 0.06f
        gameOverScorePaint.textSize = screenWidth * 0.07f
        bossLabelPaint.textSize = screenWidth * 0.038f
        warningTextPaint.textSize = screenWidth * 0.13f
        clearTextPaint.textSize = screenWidth * 0.10f
        clearBonusPaint.textSize = screenWidth * 0.055f

        // CONGRATULATIONS! の虹色グラデーション
        congratsPaint.textSize = screenWidth * 0.088f
        congratsPaint.shader = LinearGradient(
            0f, 0f, screenWidth.toFloat(), 0f,
            intArrayOf(
                Color.parseColor("#FF5252"), Color.parseColor("#FFD740"),
                Color.parseColor("#69F0AE"), Color.parseColor("#40C4FF"),
                Color.parseColor("#E040FB"), Color.parseColor("#FFD740")
            ),
            null, Shader.TileMode.CLAMP
        )

        // 星空を生成（多層パララックス：奥=小さく暗く遅い／手前=大きく明るく速い）
        val rng = Random(42)
        val area = screenHeight * 0.88f
        stars.clear()
        // 1ピクセルあたりのスケール（画面幅基準で星サイズを端末非依存に）
        val px = screenWidth / 1080f

        // 遠景：小さく暗い青白・ゆっくり
        repeat(70) {
            stars.add(makeStar(rng, area,
                rMin = 0.6f * px, rMax = 1.2f * px,
                speedMin = 0.20f, speedMax = 0.38f,
                alphaMin = 35, alphaMax = 85,
                color = Color.rgb(180, 200, 230), glow = false, twinkleChance = 0.15f))
        }
        // 中景：青白・少しシアン寄り
        repeat(38) {
            stars.add(makeStar(rng, area,
                rMin = 1.0f * px, rMax = 1.9f * px,
                speedMin = 0.5f, speedMax = 0.85f,
                alphaMin = 80, alphaMax = 150,
                color = Color.rgb(200, 222, 255), glow = false, twinkleChance = 0.3f))
        }
        // 近景：大きく明るい・速い・グローあり
        repeat(14) {
            stars.add(makeStar(rng, area,
                rMin = 1.9f * px, rMax = 3.1f * px,
                speedMin = 1.2f, speedMax = 2.1f,
                alphaMin = 150, alphaMax = 220,
                color = Color.rgb(232, 242, 255), glow = true, twinkleChance = 0.35f))
        }
        // アクセント：ネオン色（シアン/マゼンタ/ゴールド）を少量
        val accentColors = intArrayOf(
            Color.rgb(64, 196, 255), Color.rgb(255, 80, 140), Color.rgb(255, 215, 96))
        repeat(6) {
            stars.add(makeStar(rng, area,
                rMin = 2.0f * px, rMax = 3.4f * px,
                speedMin = 0.5f, speedMax = 1.2f,
                alphaMin = 130, alphaMax = 200,
                color = accentColors[rng.nextInt(accentColors.size)], glow = true, twinkleChance = 0.5f))
        }

        // 近景スターストリーク（縦の光線）
        streaks.clear()
        repeat(10) {
            streaks.add(Streak(
                x = rng.nextFloat() * screenWidth,
                baseY = rng.nextFloat() * area,
                len = area * (0.025f + rng.nextFloat() * 0.04f),
                speedMul = 2.6f + rng.nextFloat() * 2.2f,
                alpha = 45 + rng.nextInt(70)
            ))
        }

        // 背景（背景色＋星雲）を全画面の不透明Bitmapへ1回だけ焼く
        bgBitmap?.recycle()
        bgBitmap = buildBackground(screenWidth, screenHeight, area)

        // デバッグボタン（右下）
        val dbgBtnW = screenWidth * 0.13f
        val dbgBtnH = screenWidth * 0.07f
        val dbgBtnX = screenWidth - dbgBtnW - screenWidth * 0.02f
        val dbgBtnY = screenHeight * 0.895f
        debugBtnRect = RectF(dbgBtnX, dbgBtnY, dbgBtnX + dbgBtnW, dbgBtnY + dbgBtnH)
        dbgBtnTextPaint.textSize = screenWidth * 0.035f

        // デバッグパネル（右寄り）
        val panelW = screenWidth * 0.64f
        val panelH = screenHeight * 0.66f
        val panelX = screenWidth - panelW - screenWidth * 0.03f
        val panelY = screenHeight * 0.24f
        debugPanelRect = RectF(panelX, panelY, panelX + panelW, panelY + panelH)
        dbgLabelPaint.textSize = screenWidth * 0.034f
        dbgOnPaint.textSize = screenWidth * 0.034f
        dbgOffPaint.textSize = screenWidth * 0.034f

        // 閉じるボタン（パネル上端・右寄り・十分な余白）
        dbgCloseRect = RectF(panelX + panelW * 0.74f, panelY + panelH * 0.01f, panelX + panelW * 0.98f, panelY + panelH * 0.07f)

        // トグル行の配置（6行）: 敵の表示 / 敵の攻撃 / デバッグ表示 / 無敵モード / 弾減らない / 判定範囲
        val rowH = panelH * 0.070f
        val rowSpacing = panelH * 0.072f
        val rowY1 = panelY + panelH * 0.14f
        val rowY2 = rowY1 + rowSpacing
        val rowY3 = rowY2 + rowSpacing
        val rowY4 = rowY3 + rowSpacing
        val rowY5 = rowY4 + rowSpacing
        val rowY6 = rowY5 + rowSpacing
        dbgToggle1Rect    = RectF(panelX + panelW * 0.05f, rowY1 - rowH * 0.8f, panelX + panelW * 0.95f, rowY1 + rowH * 0.2f)
        dbgToggle2Rect    = RectF(panelX + panelW * 0.05f, rowY2 - rowH * 0.8f, panelX + panelW * 0.95f, rowY2 + rowH * 0.2f)
        dbgToggle3Rect    = RectF(panelX + panelW * 0.05f, rowY3 - rowH * 0.8f, panelX + panelW * 0.95f, rowY3 + rowH * 0.2f)
        dbgInvincibleRect = RectF(panelX + panelW * 0.05f, rowY4 - rowH * 0.8f, panelX + panelW * 0.95f, rowY4 + rowH * 0.2f)
        dbgNoDecayRect    = RectF(panelX + panelW * 0.05f, rowY5 - rowH * 0.8f, panelX + panelW * 0.95f, rowY5 + rowH * 0.2f)
        dbgHitboxRect     = RectF(panelX + panelW * 0.05f, rowY6 - rowH * 0.8f, panelX + panelW * 0.95f, rowY6 + rowH * 0.2f)

        // レベル操作ボタン（LVL −10 / − / + / +10）
        val btnH  = panelH * 0.085f
        val btnW  = panelW * 0.14f
        val lvlRowY = rowY6 + panelH * 0.105f
        dbgLvlMinus10Rect = RectF(panelX + panelW * 0.34f, lvlRowY, panelX + panelW * 0.34f + btnW, lvlRowY + btnH)
        dbgLvlMinusRect   = RectF(panelX + panelW * 0.50f, lvlRowY, panelX + panelW * 0.50f + btnW, lvlRowY + btnH)
        dbgLvlPlusRect    = RectF(panelX + panelW * 0.65f, lvlRowY, panelX + panelW * 0.65f + btnW, lvlRowY + btnH)
        dbgLvlPlus10Rect  = RectF(panelX + panelW * 0.80f, lvlRowY, panelX + panelW * 0.80f + btnW, lvlRowY + btnH)

        // 弾段数ボタン（1 / 3 / 5）
        val bltRowY = lvlRowY + panelH * 0.115f
        val bltBtnW = panelW * 0.16f
        dbgBullet1Rect = RectF(panelX + panelW * 0.38f, bltRowY, panelX + panelW * 0.38f + bltBtnW, bltRowY + btnH)
        dbgBullet3Rect = RectF(panelX + panelW * 0.57f, bltRowY, panelX + panelW * 0.57f + bltBtnW, bltRowY + btnH)
        dbgBullet5Rect = RectF(panelX + panelW * 0.76f, bltRowY, panelX + panelW * 0.76f + bltBtnW, bltRowY + btnH)

        // HP操作ボタン（1 / 2 / 3）
        val hpRowY = bltRowY + panelH * 0.105f
        dbgHp1Rect = RectF(panelX + panelW * 0.38f, hpRowY, panelX + panelW * 0.38f + bltBtnW, hpRowY + btnH)
        dbgHp2Rect = RectF(panelX + panelW * 0.57f, hpRowY, panelX + panelW * 0.57f + bltBtnW, hpRowY + btnH)
        dbgHp3Rect = RectF(panelX + panelW * 0.76f, hpRowY, panelX + panelW * 0.76f + bltBtnW, hpRowY + btnH)

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

        // 中央再開ボタン（PAUSEDオーバーレイ用）
        val rBtnW = screenWidth * 0.48f
        val rBtnH = screenHeight * 0.078f
        resumeBtnRect = RectF(
            (screenWidth - rBtnW) / 2f, screenHeight * 0.50f,
            (screenWidth + rBtnW) / 2f, screenHeight * 0.50f + rBtnH
        )
        resumeBtnTextPaint.textSize = screenWidth * 0.07f

        // Homeボタン（PAUSEDオーバーレイ用）
        homeBtnRect = RectF(
            (screenWidth - rBtnW) / 2f, screenHeight * 0.62f,
            (screenWidth + rBtnW) / 2f, screenHeight * 0.62f + rBtnH
        )
        homeBtnTextPaint.textSize = screenWidth * 0.065f

        // Retryボタン（GAME_OVERオーバーレイ用・Homeボタンの上）
        gameOverRetryBtnRect = RectF(
            (screenWidth - rBtnW) / 2f, screenHeight * 0.67f,
            (screenWidth + rBtnW) / 2f, screenHeight * 0.67f + rBtnH
        )
        // Homeボタン（GAME_OVERオーバーレイ用）
        gameOverHomeBtnRect = RectF(
            (screenWidth - rBtnW) / 2f, screenHeight * 0.79f,
            (screenWidth + rBtnW) / 2f, screenHeight * 0.79f + rBtnH
        )

        // Returnボタン（CLEARオーバーレイ用・ステージ選択へ戻る）
        clearReturnBtnRect = RectF(
            (screenWidth - rBtnW) / 2f, screenHeight * 0.76f,
            (screenWidth + rBtnW) / 2f, screenHeight * 0.76f + rBtnH
        )

        // アイテム取得オーラ
        powerUpAuraPaint.strokeWidth = screenWidth * 0.018f

        initGame()
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopThread()
    }

    /** 1個の星を生成（各層のパラメータ範囲から乱数で決定）。 */
    private fun makeStar(
        rng: Random, area: Float,
        rMin: Float, rMax: Float,
        speedMin: Float, speedMax: Float,
        alphaMin: Int, alphaMax: Int,
        color: Int, glow: Boolean, twinkleChance: Float
    ): Star = Star(
        x = rng.nextFloat() * screenWidth,
        baseY = rng.nextFloat() * area,
        r = rMin + rng.nextFloat() * (rMax - rMin),
        alphaBase = alphaMin + rng.nextInt((alphaMax - alphaMin).coerceAtLeast(1)),
        color = color,
        speedMul = speedMin + rng.nextFloat() * (speedMax - speedMin),
        glow = glow,
        twinkle = rng.nextFloat() < twinkleChance,
        twinklePhase = rng.nextFloat() * 6.28f
    )

    /**
     * 背景（背景色＋薄いネオン星雲）を全画面の不透明Bitmapへ1回だけ焼く。
     * 毎フレームはこれを等倍で貼るだけなので、グラデ生成・拡大・半透明合成のコストがゼロになる。
     * @param area 星雲を載せる上側プレイエリアの高さ（下端のUIエリアは背景色のみ）
     */
    private fun buildBackground(w: Int, h: Int, area: Float): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // 背景色で全面を不透明に塗る
        c.drawColor(Color.parseColor("#080E1A"))
        val fw = w.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 左上：薄いシアン
        p.shader = RadialGradient(
            fw * 0.24f, area * 0.20f, fw * 0.75f,
            intArrayOf(Color.argb(40, 64, 196, 255), Color.argb(14, 40, 120, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, area, p)
        // 右下：薄いマゼンタ
        p.shader = RadialGradient(
            fw * 0.80f, area * 0.80f, fw * 0.78f,
            intArrayOf(Color.argb(32, 255, 64, 129), Color.argb(12, 150, 40, 90), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, area, p)
        // 中央上やや：ごく淡い青紫で奥行き
        p.shader = RadialGradient(
            fw * 0.5f, area * 0.42f, fw * 0.55f,
            intArrayOf(Color.argb(20, 90, 80, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, area, p)
        p.shader = null
        // 全面不透明なので合成ではなくコピー扱いにして貼付を高速化
        bmp.setHasAlpha(false)
        return bmp
    }

    private var rankAchieved: Int = 0  // 0=ランクインなし、1〜3=ランク順位

    private fun triggerGameOver() {
        gameState = GameState.GAME_OVER
        gameOverTapDelayTimer = 30  // 約0.5秒はボタンを出さず誤タップを防ぐ
        soundManager.pauseBgmByUser()
        rankAchieved = HighScoreManager.saveScore(context, scoreManager.score)
    }

    /** ボス撃破演出完了後に呼ばれる。CLEAR状態へ（撃破ボーナスは撃破の瞬間に加算済み） */
    private fun triggerClear() {
        gameState = GameState.CLEAR
        clearTapDelayTimer = 60  // 1秒間は誤タップでホームに戻らないように
        soundManager.pauseBgmByUser()       // 戦闘BGMを停止
        soundManager.playClearJingle(context)  // 勝利ジングル（bgm_clearがあれば再生・無ければ代用ファンファーレ）
        clearCelebration = ClearCelebration(screenWidth, screenHeight)
        bossExplosion = null
        // ストーリーモード: このステージのクリアを記録 → 次ステージ解放
        if (isStoryMode && stage in 1..StageConfig.MAX_STAGE) {
            AppPrefs.setStoryStageCleared(context, stage)
        }
        rankAchieved = HighScoreManager.saveScore(context, scoreManager.score)
    }

    /** HP回復（amount>=99で全回復）。実際に回復したときだけ演出を出す。 */
    private fun applyHeal(amount: Int) {
        if (amount <= 0 || hp >= maxHp) return
        hp = if (amount >= 99) maxHp else minOf(hp + amount, maxHp)
        healMessageTimer = 90   // 1.5秒間「HP回復」を表示
        powerUpFlashTimer = 30
        soundManager.playItemPickup()
    }

    private fun initGame() {
        soundManager.restartBgm(context)  // 初回は起動、リトライ時は先頭から再生
        player = Player(screenWidth, screenHeight)
        bullets.clear()
        blobManager = BlobManager(screenWidth, screenHeight, stageConfig)
        scoreManager.reset()
        hp = maxHp
        invincibleTimer = 0
        gameState = GameState.PLAYING
        rankAchieved = 0
        frameCount = 0
        bgScrollY = 0f
        enemyBullets.clear()
        items.clear()
        shockwaves.clear()
        boss = null
        bossSpawned = false
        bossWaitTimer = 0
        bossWarningTimer = 0
        bossHpDisplayRatio = 1f
        clearTapDelayTimer = 0
        clearAnimFrame = 0
        bossMinionTimer = 0
        bossExplosion = null
        clearCelebration = null
        midHealDone = false
        preBossHealDone = false
        healMessageTimer = 0
        dragPointerId = -1
        synchronized(pendingBullets) { pendingBullets.clear() }
        prevPlayerX = screenWidth / 2f
        prevPlayerY = screenHeight * 0.85f
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

        // PAUSED中: 再開・DBGボタン・デバッグパネル操作を受け付ける
        if (gameState == GameState.PAUSED) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val tx = event.x; val ty = event.y
                when {
                    // デバッグパネルが開いている場合の操作
                    debugPanelOpen && dbgCloseRect.contains(tx, ty)    -> debugPanelOpen = false
                    debugPanelOpen && dbgToggle1Rect.contains(tx, ty)  -> debugShowEnemies = !debugShowEnemies
                    debugPanelOpen && dbgToggle2Rect.contains(tx, ty)  -> debugEnemyCanShoot = !debugEnemyCanShoot
                    debugPanelOpen && dbgToggle3Rect.contains(tx, ty)  -> debugShowInfo = !debugShowInfo
                    debugPanelOpen && dbgInvincibleRect.contains(tx, ty)  -> debugInvincible = !debugInvincible
                    debugPanelOpen && dbgNoDecayRect.contains(tx, ty)    -> player.bulletLevelNoDecay = !player.bulletLevelNoDecay
                    debugPanelOpen && dbgHitboxRect.contains(tx, ty)     -> debugShowHitbox = !debugShowHitbox
                    debugPanelOpen && dbgLvlMinus10Rect.contains(tx, ty) -> { blobManager.setLevel(blobManager.level - 10); scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                    debugPanelOpen && dbgLvlMinusRect.contains(tx, ty)   -> { blobManager.setLevel(blobManager.level - 1);  scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                    debugPanelOpen && dbgLvlPlusRect.contains(tx, ty)    -> { blobManager.setLevel(blobManager.level + 1);  scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                    debugPanelOpen && dbgLvlPlus10Rect.contains(tx, ty)  -> { blobManager.setLevel(blobManager.level + 10); scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                    debugPanelOpen && dbgBullet1Rect.contains(tx, ty)    -> player.setBulletLevel(1)
                    debugPanelOpen && dbgBullet3Rect.contains(tx, ty)    -> player.setBulletLevel(3)
                    debugPanelOpen && dbgBullet5Rect.contains(tx, ty)    -> player.setBulletLevel(5)
                    debugPanelOpen && dbgHp1Rect.contains(tx, ty)        -> hp = 1
                    debugPanelOpen && dbgHp2Rect.contains(tx, ty)        -> hp = 2
                    debugPanelOpen && dbgHp3Rect.contains(tx, ty)        -> hp = 3
                    debugPanelOpen && !debugPanelRect.contains(tx, ty)   -> debugPanelOpen = false
                    // DBGボタン
                    DEBUG_MODE && debugBtnRect.contains(tx, ty) -> debugPanelOpen = !debugPanelOpen
                    // 再開ボタン
                    resumeBtnRect.contains(tx, ty) -> {
                        gameState = GameState.PLAYING
                        soundManager.resumeBgmByUser()
                    }
                    // Homeボタン（即帰還）
                    homeBtnRect.contains(tx, ty) -> onGoHome?.invoke()
                }
            }
            return true
        }

        // CLEAR中: Returnボタンを押した時だけステージ選択へ戻る（onGoHome=GameActivity.finish）
        if (gameState == GameState.CLEAR) {
            if (event.actionMasked == MotionEvent.ACTION_UP && clearTapDelayTimer <= 0 &&
                clearReturnBtnRect.contains(event.x, event.y)) {
                onGoHome?.invoke()
            }
            return true
        }

        if (gameState == GameState.GAME_OVER) {
            // Retry/Homeはそれぞれのボタンを押した時だけ反応する（画面どこでもリトライは廃止）
            // 誤タップ防止のためボタン表示遅延中は無反応
            if (event.actionMasked == MotionEvent.ACTION_UP && gameOverTapDelayTimer <= 0) {
                when {
                    gameOverRetryBtnRect.contains(event.x, event.y) -> initGame()
                    gameOverHomeBtnRect.contains(event.x, event.y)  -> onGoHome?.invoke()
                }
            }
            return true
        }

        // デバッグパネルが開いている場合はパネルのタッチを優先
        if (debugPanelOpen && event.actionMasked == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            when {
                dbgCloseRect.contains(tx, ty)    -> debugPanelOpen = false
                dbgToggle1Rect.contains(tx, ty)  -> debugShowEnemies = !debugShowEnemies
                dbgToggle2Rect.contains(tx, ty)  -> debugEnemyCanShoot = !debugEnemyCanShoot
                dbgToggle3Rect.contains(tx, ty)  -> debugShowInfo = !debugShowInfo
                dbgInvincibleRect.contains(tx, ty)  -> debugInvincible = !debugInvincible
                dbgNoDecayRect.contains(tx, ty)    -> player.bulletLevelNoDecay = !player.bulletLevelNoDecay
                dbgHitboxRect.contains(tx, ty)     -> debugShowHitbox = !debugShowHitbox
                dbgLvlMinus10Rect.contains(tx, ty) -> { blobManager.setLevel(blobManager.level - 10); scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                dbgLvlMinusRect.contains(tx, ty)   -> { blobManager.setLevel(blobManager.level - 1);  scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                dbgLvlPlusRect.contains(tx, ty)    -> { blobManager.setLevel(blobManager.level + 1);  scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                dbgLvlPlus10Rect.contains(tx, ty)  -> { blobManager.setLevel(blobManager.level + 10); scoreManager.setScore(blobManager.levelThreshold(blobManager.level)) }
                dbgBullet1Rect.contains(tx, ty)    -> player.setBulletLevel(1)
                dbgBullet3Rect.contains(tx, ty)    -> player.setBulletLevel(3)
                dbgBullet5Rect.contains(tx, ty)    -> player.setBulletLevel(5)
                dbgHp1Rect.contains(tx, ty)        -> hp = 1
                dbgHp2Rect.contains(tx, ty)        -> hp = 2
                dbgHp3Rect.contains(tx, ty)        -> hp = 3
                !debugPanelRect.contains(tx, ty)   -> debugPanelOpen = false
            }
            return true
        }

        // デバッグボタンタップ（UP時）
        if (DEBUG_MODE && event.actionMasked == MotionEvent.ACTION_UP &&
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
                        player.y = event.getY(idx).coerceIn(screenHeight * 0.35f, screenHeight * 0.88f)
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
        if (gameState == GameState.CLEAR) {
            clearAnimFrame++
            if (clearTapDelayTimer > 0) clearTapDelayTimer--
            clearCelebration?.update()
        }
        if (gameState == GameState.GAME_OVER) {
            if (gameOverTapDelayTimer > 0) gameOverTapDelayTimer--
        }
        if (gameState != GameState.PLAYING) return

        frameCount++
        if (powerUpFlashTimer > 0) powerUpFlashTimer--

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
                if (!debugInvincible) hp--
                invincibleTimer = invincibleDuration
                soundManager.playPlayerDamaged()
                if (!debugInvincible && hp <= 0) triggerGameOver()
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

        // ティアアップ検知
        if (blobManager.tierUpEvent) {
            blobManager.tierUpEvent = false
            tierUpTimer = 180  // 3秒間エフェクト
            tierUpNumber = blobManager.globalTier
            // ティアアップ時にHP+1回復（最大HPを超えない）
            hp = minOf(hp + 1, maxHp)
        }
        if (tierUpTimer > 0) tierUpTimer--
        if (healMessageTimer > 0) healMessageTimer--

        // ── 道中回復（ストーリーのステージ設定で指定レベル到達時に1回） ──
        stageConfig?.let { cfg ->
            if (!midHealDone && cfg.midHealLevel > 0 && blobManager.level >= cfg.midHealLevel) {
                midHealDone = true
                applyHeal(cfg.midHealAmount)
            }
        }

        // ── ボス出現シーケンス（ストーリーモードのみ） ──────────
        if (isStoryMode && !bossSpawned && blobManager.level >= bossTriggerLevel) {
            // 通常敵の新規出現を停止
            blobManager.spawningEnabled = false
            // ボス前回復（1回だけ・残存敵を待っている間に発動）
            if (!preBossHealDone) {
                preBossHealDone = true
                stageConfig?.let { if (it.preBossHealAmount > 0) applyHeal(it.preBossHealAmount) }
            }
            if (bossWaitTimer < GameConfig.BOSS_WAIT_MAX_FRAMES) bossWaitTimer++
            // 残存敵が掃けたら（または待機上限を超えたら）WARNING演出開始
            if (bossWarningTimer == 0 &&
                (blobManager.blobs.isEmpty() || bossWaitTimer >= GameConfig.BOSS_WAIT_MAX_FRAMES)) {
                bossWarningTimer = GameConfig.BOSS_WARNING_FRAMES
            }
        }
        if (bossWarningTimer > 0) {
            bossWarningTimer--
            if (bossWarningTimer == 0) {
                boss = if (stageConfig != null) {
                    Boss(screenWidth, screenHeight,
                        stageConfig.bossMaxHp, stageConfig.bossMaxPhase, stageConfig.bossAttackIntervalMult,
                        stageConfig.useAltBoss, StageConfig.bossPhasesFor(stage))
                } else {
                    Boss(screenWidth, screenHeight)
                }
                bossSpawned = true
                bossHpDisplayRatio = 1f
            }
        }

        // ── ボス更新 ─────────────────────────────────────
        boss?.let { b ->
            b.update()

            // HPバーの減少アニメーション（表示値が実値へゆっくり追従）
            val actualRatio = b.hp.toFloat() / b.maxHp
            if (bossHpDisplayRatio > actualRatio) {
                bossHpDisplayRatio = (bossHpDisplayRatio - 0.004f).coerceAtLeast(actualRatio)
            }

            if (b.isGone) {
                // 撃破演出完了 → ゲームクリア
                boss = null
                triggerClear()
                return
            }
            if (b.isDying) {
                // 撃破演出: 専用爆発エフェクト（オレンジ→黄→白の拡大円＋飛散パーティクル）
                val exp = bossExplosion ?: BossExplosion(screenWidth, screenHeight).also { bossExplosion = it }
                if (frameCount % GameConfig.BOSS_EXPLOSION_BURST_INTERVAL == 0) {
                    val ex = b.x + (Random.nextFloat() - 0.5f) * b.radius * 1.8f
                    val ey = b.y + (Random.nextFloat() - 0.5f) * b.radius * 1.4f
                    exp.burst(ex, ey)
                    soundManager.playEnemyKilled()  // 連続爆発音
                }
            } else {
                // ボス戦闘中: 約11秒ごとに救済用雑魚を出現（画面上の雑魚は最大2体まで）
                if (b.state == BossState.FIGHTING) {
                    bossMinionTimer++
                    if (bossMinionTimer >= GameConfig.BOSS_MINION_INTERVAL) {
                        bossMinionTimer = 0
                        if (blobManager.blobs.size < GameConfig.BOSS_MINION_MAX) {
                            blobManager.spawnBossMinion()
                        }
                    }
                }
                if (debugEnemyCanShoot) {
                    // ボスの攻撃（既存EnemyBulletを再利用・衝撃波はリストへ直接add）
                    enemyBullets.addAll(b.tryShoot(player.x, player.y,
                        enemyBullets.size, GameConfig.BOSS_MAX_ENEMY_BULLETS, shockwaves))
                }
            }
        }
        bossExplosion?.update()

        // 敵弾発射（上限チェック・混雑度による間隔制御込み）
        // 発射禁止ライン(0.80f)より下にいる敵は撃たせない。
        // 削除ライン(0.88f=地面ライン)との間にバッファを設けることで、
        // 最速弾でも削除前に地面を越えることが数学上ありえない構造にする。
        // プレイヤーも地面ライン(0.88f)より下には行けないため、下領域は純粋なUIエリア。
        if (debugEnemyCanShoot) {
            val noFireLine = screenHeight * 0.80f
            for (blob in blobManager.blobs) {
                if (blob.cy > noFireLine) continue
                enemyBullets.addAll(blob.tryShoot(player.x, player.y, enemyBullets.size, maxEnemyBullets, shockwaves))
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
                powerUpFlashTimer = 30
                soundManager.playItemPickup()
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
                // 画面上部（まだほぼ見えていない敵）は当たり判定をスキップ
                if (blob.cy < blob.radius) continue
                val dy = bullet.y - blob.cy
                val r  = bullet.radius + blob.radius
                if (dy > r || dy < -r) continue
                val dx = bullet.x - blob.cx
                if (dx * dx + dy * dy <= r * r) {
                    bullet.isDead = true
                    bulletPool.recycle(bullet)
                    if (blob.takeDamage()) {    // blob.isDead = true もここでセット
                        scoreManager.addScore((blob.size.score() * blobManager.scoreMultiplier).toInt())
                        blobManager.onKill()
                        deadBlobsForDrop.add(blob)
                        soundManager.playEnemyKilled()
                    }
                    break
                }
            }
        }

        // 弾×ボス当たり判定（無敵中は弾が素通り）
        boss?.let { b ->
            if (!b.isInvincible) {
                var bossKilled = false
                for (bullet in bullets) {
                    if (bullet.isDead) continue
                    val dy = bullet.y - b.y
                    val r  = bullet.radius + b.radius
                    if (dy > r || dy < -r) continue
                    val dx = bullet.x - b.x
                    if (dx * dx + dy * dy <= r * r) {
                        bullet.isDead = true
                        bulletPool.recycle(bullet)
                        if (b.takeDamage()) bossKilled = true
                    }
                }
                if (bossKilled) {
                    soundManager.playEnemyKilled()
                    // 撃破の瞬間（死亡エフェクト開始時）にボーナスを加算。
                    // まだPLAYING中なのでblobManager.levelもスコアに追従し、
                    // 上部HUDとCLEAR画面のスコア・レベルがズレない。
                    scoreManager.addScore(GameConfig.bossDefeatBonus(stage))
                    // 撃破の瞬間: 残っている敵弾・衝撃波を一掃して演出に集中
                    enemyBullets.clear()
                    shockwaves.clear()
                }
            }
        }

        // イテレータで一括削除（removeAll + 線形検索を排除）
        val bIter2 = bullets.iterator()
        while (bIter2.hasNext()) { if (bIter2.next().isDead) bIter2.remove() }
        blobManager.blobs.removeAll { it.isDead }

        // 倒した敵からアイテムドロップ（画面上2個まで。ボス戦の救済雑魚は確定ドロップ）
        for (deadBlob in deadBlobsForDrop) {
            if (deadBlob.guaranteedDrop ||
                (items.size < 2 && Random.nextFloat() < deadBlob.size.itemDropChance())) {
                items.add(PowerUpItem(deadBlob.cx, deadBlob.cy, screenWidth, screenHeight))
            }
        }

        // Player×Blob当たり判定（toList()コピーなし）
        if (invincibleTimer <= 0) {
            for (blob in blobManager.blobs) {
                val dy = player.y - blob.cy
                val r  = player.hitRadius + blob.radius
                if (dy > r || dy < -r) continue
                val dx = player.x - blob.cx
                if (dx * dx + dy * dy <= r * r) {
                    if (!debugInvincible) hp--
                    invincibleTimer = invincibleDuration
                    soundManager.playPlayerDamaged()
                    if (!debugInvincible && hp <= 0) triggerGameOver()
                    break
                }
            }
        } else {
            invincibleTimer--
        }

        // Player×ボス本体当たり判定（戦闘中のみ）
        boss?.let { b ->
            if (!b.isDying && invincibleTimer <= 0) {
                val dy = player.y - b.y
                val r  = player.hitRadius + b.radius * 0.85f
                if (dy <= r && dy >= -r) {
                    val dx = player.x - b.x
                    if (dx * dx + dy * dy <= r * r) {
                        if (!debugInvincible) hp--
                        invincibleTimer = invincibleDuration
                        soundManager.playPlayerDamaged()
                        if (!debugInvincible && hp <= 0) triggerGameOver()
                    }
                }
            }
        }

        // 衝撃波 更新・プレイヤー被弾判定（ボス撃破演出中の爆発は当たらない）
        val bossDyingNow = boss?.isDying == true
        val swIter = shockwaves.iterator()
        while (swIter.hasNext()) {
            val sw = swIter.next()
            sw.update()
            if (sw.isDead) { swIter.remove(); continue }
            if (!bossDyingNow && invincibleTimer <= 0 && sw.hitsPlayer(player.x, player.y, player.hitRadius)) {
                if (!debugInvincible) hp--
                invincibleTimer = invincibleDuration
                soundManager.playPlayerDamaged()
                if (!debugInvincible && hp <= 0) triggerGameOver()
            }
        }

        // 敵弾×Player当たり判定
        if (invincibleTimer <= 0) {
            val ebHitIter = enemyBullets.iterator()
            while (ebHitIter.hasNext()) {
                val eb = ebHitIter.next()
                val dy = player.y - eb.y
                val r  = player.hitRadius + eb.radius
                if (dy > r || dy < -r) continue
                val dx = player.x - eb.x
                if (dx * dx + dy * dy <= r * r) {
                    ebHitIter.remove()
                    if (!debugInvincible) hp--
                    invincibleTimer = invincibleDuration
                    soundManager.playPlayerDamaged()
                    if (!debugInvincible && hp <= 0) triggerGameOver()
                    break
                }
            }
        }

        // GAME_OVER判定（念の為）
        if (hp <= 0 && !debugInvincible && gameState == GameState.PLAYING) {
            triggerGameOver()
        }
    }

    /** ゲームが実際に進行中か（PLAYINGのみ）。停止中は補間を切って静止させるために使う。 */
    fun isSimulating(): Boolean = gameState == GameState.PLAYING

    fun draw(alpha: Float = 0f) {
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            drawInternal(canvas, alpha)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawInternal(canvas: Canvas, alpha: Float = 0f) {
        val area = screenHeight * 0.88f

        // 背景（背景色＋星雲を焼いた不透明Bitmapを等倍で貼るだけ）。無ければ従来の単色塗り。
        val bg = bgBitmap
        if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null)
        else canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // グリッドライン（下側ほど濃く・上に行くほど消える＝航路感）
        val gridSpacing = screenWidth * 0.12f
        // 縦線は控えめ（一定の薄さ）
        var gx = 0f
        while (gx <= screenWidth) {
            gridPaint.alpha = 10
            canvas.drawLine(gx, area * 0.25f, gx, area, gridPaint)
            gx += gridSpacing
        }
        // 横線はスクロール＋高さで濃淡（上=透明→下=やや濃い）
        val scrollOffset = bgScrollY % gridSpacing
        var gy = scrollOffset - gridSpacing
        while (gy <= area) {
            if (gy >= 0f) {
                val t = (gy / area).coerceIn(0f, 1f)   // 0=上 1=下
                gridPaint.alpha = (4 + t * t * 22f).toInt()
                canvas.drawLine(0f, gy, screenWidth.toFloat(), gy, gridPaint)
            }
            gy += gridSpacing
        }

        // 近景スターストリーク（高速前進感）
        for (s in streaks) {
            val sy = ((s.baseY + bgScrollY * s.speedMul) % area).let { if (it < 0f) it + area else it }
            streakPaint.strokeWidth = 1.6f
            streakPaint.alpha = s.alpha
            canvas.drawLine(s.x, sy, s.x, (sy + s.len).coerceAtMost(area), streakPaint)
        }

        // 星空（多層パララックス・一部明滅・明るい星はグロー）
        for (star in stars) {
            val sy = ((star.baseY + bgScrollY * star.speedMul) % area).let { if (it < 0f) it + area else it }
            val a = if (star.twinkle) {
                (star.alphaBase * (0.7f + 0.3f * sin(frameCount * 0.08f + star.twinklePhase))).toInt().coerceIn(0, 255)
            } else star.alphaBase
            if (star.glow) {
                starGlowPaint.color = star.color
                starGlowPaint.alpha = (a * 0.28f).toInt().coerceIn(0, 255)
                canvas.drawCircle(star.x, sy, star.r * 2.4f, starGlowPaint)
            }
            starPaint.color = star.color
            starPaint.alpha = a
            canvas.drawCircle(star.x, sy, star.r, starPaint)
        }

        // 地面ライン（プレイエリア境界）
        canvas.drawLine(0f, area, screenWidth.toFloat(), area, groundLinePaint)

        // Blob描画（デバッグ: 非表示トグル）。alphaで前回位置との中間を描き補間
        if (debugShowEnemies) blobManager.draw(canvas, alpha)

        // ボス描画（敵と同レイヤー・弾より後ろ）
        boss?.draw(canvas, alpha)

        // ボス撃破爆発エフェクト
        bossExplosion?.draw(canvas)

        // 衝撃波描画（敵の後ろ・弾の前）
        shockwaves.forEach { it.draw(canvas) }

        // 弾描画
        bullets.forEach { it.draw(canvas, alpha) }

        // 敵弾描画
        enemyBullets.forEach { it.draw(canvas, alpha) }

        // アイテム描画
        items.forEach { it.draw(canvas, alpha) }

        // アイテム取得オーラ（プレイヤーの周囲に黄金リング、地面ラインでクリップ）
        if (powerUpFlashTimer > 0) {
            val progress = powerUpFlashTimer.toFloat() / 30f
            powerUpAuraPaint.alpha = (progress * 200).toInt()
            val auraR = player.width * (0.8f + (1f - progress) * 1.2f)
            canvas.save()
            canvas.clipRect(0f, 0f, screenWidth.toFloat(), screenHeight * 0.88f)
            canvas.drawCircle(player.x, player.y, auraR, powerUpAuraPaint)
            canvas.restore()
        }

        // プレイヤー描画（無敵中は点滅）
        player.draw(canvas, invincibleTimer > 0, frameCount)

        // デバッグ: 当たり判定範囲を可視化（自機=赤 / 敵・ボス=シアン）
        if (debugShowHitbox) {
            // 敵（通常敵）の判定円
            for (blob in blobManager.blobs) {
                canvas.drawCircle(blob.cx, blob.cy, blob.radius, enemyHitboxFillPaint)
                canvas.drawCircle(blob.cx, blob.cy, blob.radius, enemyHitboxStrokePaint)
            }
            // ボスの判定円
            boss?.let { b ->
                if (!b.isGone) {
                    canvas.drawCircle(b.x, b.y, b.radius, enemyHitboxFillPaint)
                    canvas.drawCircle(b.x, b.y, b.radius, enemyHitboxStrokePaint)
                }
            }
            // 自機の判定円（半径 = width×0.35×0.6）
            val hr = player.hitRadius
            canvas.drawCircle(player.x, player.y, hr, hitboxFillPaint)
            canvas.drawCircle(player.x, player.y, hr, hitboxStrokePaint)
        }

        // ── 上部ステータス（2段構成）──
        // 桁数が増えても重ならないよう、重要度で段を分ける：
        //   段1: SCORE（左）／ HP（右）
        //   段2: STAGE（金）＋ Lv（緑）  ※エンドレスは LEVEL のみ
        val marginX = screenWidth * 0.03f
        val row1Y = screenHeight * 0.048f
        val row2Y = screenHeight * 0.088f
        val baseSize = screenWidth * 0.05f
        val subSize = screenWidth * 0.042f

        // 段1左: SCORE（3桁区切りで読みやすく）
        scorePaint.textSize = baseSize
        canvas.drawText("SCORE  ${"%,d".format(scoreManager.score)}", marginX, row1Y, scorePaint)

        // 段1右: HP（赤ハート・右寄せ）
        val heartText = "HP  " + "♥".repeat(hp.coerceAtLeast(0))
        heartPaint.textSize = baseSize
        val heartBounds = Rect()
        heartPaint.getTextBounds(heartText, 0, heartText.length, heartBounds)
        canvas.drawText(heartText, screenWidth - heartBounds.width() - marginX, row1Y, heartPaint)

        // 段2左: ステージ（金）＋ レベル（緑）
        roundPaint.textSize = subSize
        levelPaint.textSize = subSize
        if (isStoryMode) {
            val stageText = "STAGE $stage"
            canvas.drawText(stageText, marginX, row2Y, roundPaint)
            val lvX = marginX + roundPaint.measureText(stageText) + screenWidth * 0.05f
            canvas.drawText("Lv ${blobManager.level}", lvX, row2Y, levelPaint)
        } else {
            canvas.drawText("LEVEL ${blobManager.level}", marginX, row2Y, levelPaint)
        }

        // 回復メッセージ（道中・ボス前の回復時に一定時間表示）
        if (healMessageTimer > 0) {
            val healText = "✚ HP RECOVERED"
            clearBonusPaint.textSize = screenWidth * 0.06f
            clearBonusPaint.alpha = (healMessageTimer.toFloat() / 90f * 255f).toInt().coerceIn(0, 255)
            val hb = Rect(); clearBonusPaint.getTextBounds(healText, 0, healText.length, hb)
            canvas.drawText(healText, (screenWidth - hb.width()) / 2f, screenHeight * 0.30f, clearBonusPaint)
            clearBonusPaint.alpha = 255
        }

        // ── ボスUI: 画面上端のHPバー＋BOSSラベル ─────────────
        boss?.let { b ->
            if (!b.isGone) {
                val barH = screenHeight * 0.018f
                val barMargin = screenWidth * 0.04f
                val barTop = screenHeight * 0.065f
                val barRect = RectF(barMargin, barTop, screenWidth - barMargin, barTop + barH)
                canvas.drawRoundRect(barRect, barH / 2, barH / 2, bossHpBarBgPaint)
                val actualRatio = b.hp.toFloat() / b.maxHp
                // 減少アニメーション: 直近のダメージ分を明るい色のトレイルで表示
                if (bossHpDisplayRatio > actualRatio) {
                    val trailRect = RectF(barRect.left, barRect.top,
                        barRect.left + barRect.width() * bossHpDisplayRatio, barRect.bottom)
                    canvas.drawRoundRect(trailRect, barH / 2, barH / 2, bossHpBarTrailPaint)
                }
                if (actualRatio > 0f) {
                    val hpRect = RectF(barRect.left, barRect.top,
                        barRect.left + barRect.width() * actualRatio, barRect.bottom)
                    canvas.drawRoundRect(hpRect, barH / 2, barH / 2, bossHpBarPaint)
                }
                canvas.drawRoundRect(barRect, barH / 2, barH / 2, bossHpBarBorderPaint)
                canvas.drawText("BOSS", barMargin, barTop + barH + bossLabelPaint.textSize * 1.1f, bossLabelPaint)
            }
        }

        // ── ボス撃破演出の最後: 画面全体の白フラッシュ（約0.3秒） ──
        boss?.let { b ->
            if (b.isDying) {
                val flashStart = 1f - GameConfig.BOSS_FLASH_FRAMES.toFloat() / GameConfig.BOSS_DYING_FRAMES
                if (b.dyingProgress >= flashStart) {
                    val t = (b.dyingProgress - flashStart) / (1f - flashStart)
                    bossFlashPaint.color = Color.argb((t * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                    canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bossFlashPaint)
                }
            }
        }

        // ── WARNING演出（ボス登場前・点滅） ────────────────
        if (bossWarningTimer > 0) {
            // 赤いフラッシュ背景
            val flashA = (sin(bossWarningTimer * 0.25f) * 40f + 40f).toInt().coerceIn(0, 90)
            warningBgPaint.color = Color.argb(flashA, 150, 0, 0)
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), warningBgPaint)
            // 点滅テキスト
            if (bossWarningTimer % 30 < 20) {
                val wText = "WARNING"
                val wBounds = Rect()
                warningTextPaint.getTextBounds(wText, 0, wText.length, wBounds)
                canvas.drawText(wText, (screenWidth - wBounds.width()) / 2f, screenHeight * 0.42f, warningTextPaint)
            }
        }

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

        // DBGボタン（非表示・タッチ判定のみ有効）

        // デバッグパネルは PAUSEオーバーレイの後に描画（後で移動）
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

        // ティアアップエフェクト
        if (tierUpTimer > 0) {
            val progress = tierUpTimer.toFloat() / 180f
            // フラッシュ効果（最初の30フレームは白くフラッシュ）
            if (tierUpTimer > 150) {
                val flashA = ((tierUpTimer - 150).toFloat() / 30f * 120).toInt()
                tierUpBgPaint.color = Color.argb(flashA, 255, 215, 0)
                canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), tierUpBgPaint)
            }
            val alpha = (progress * 255).toInt().coerceIn(0, 255)
            val hasEnemyPowerUp = tierUpNumber >= 3  // Lv100〜から敵強化

            // TIER N
            tierUpTextPaint.textSize = screenWidth * 0.10f
            tierUpTextPaint.color = Color.argb(alpha, 255, 215, 0)
            val line1 = "TIER $tierUpNumber"
            val b1 = android.graphics.Rect(); tierUpTextPaint.getTextBounds(line1, 0, line1.length, b1)
            val baseY = if (hasEnemyPowerUp) screenHeight * 0.30f else screenHeight * 0.35f
            canvas.drawText(line1, (screenWidth - b1.width()) / 2f, baseY, tierUpTextPaint)

            // ENEMY POWER UP（Lv100〜のみ）
            if (hasEnemyPowerUp) {
                tierUpTextPaint.textSize = screenWidth * 0.08f
                tierUpTextPaint.color = Color.argb(alpha, 255, 100, 100)
                val line2 = "ENEMY POWER UP"
                val b2 = android.graphics.Rect(); tierUpTextPaint.getTextBounds(line2, 0, line2.length, b2)
                canvas.drawText(line2, (screenWidth - b2.width()) / 2f, baseY + screenHeight * 0.10f, tierUpTextPaint)
            }

            // HP +1 回復（毎回表示）
            tierUpTextPaint.textSize = screenWidth * 0.075f
            tierUpTextPaint.color = Color.argb(alpha, 100, 255, 150)
            val lineHp = "♥  HP +1"
            val bHp = android.graphics.Rect(); tierUpTextPaint.getTextBounds(lineHp, 0, lineHp.length, bHp)
            val hpY = if (hasEnemyPowerUp) baseY + screenHeight * 0.20f else baseY + screenHeight * 0.12f
            canvas.drawText(lineHp, (screenWidth - bHp.width()) / 2f, hpY, tierUpTextPaint)

            // 色をリセット
            tierUpTextPaint.color = Color.parseColor("#FFD740")
        }

        // PAUSED オーバーレイ
        if (gameState == GameState.PAUSED) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), pauseOverlayPaint)
            // "PAUSED" テキスト
            pauseLabelPaint.textSize = screenWidth * 0.12f
            val pausedText = "PAUSED"
            val pausedBounds = Rect()
            pauseLabelPaint.getTextBounds(pausedText, 0, pausedText.length, pausedBounds)
            canvas.drawText(pausedText, (screenWidth - pausedBounds.width()) / 2f, screenHeight * 0.42f, pauseLabelPaint)
            // 中央再開ボタン
            canvas.drawRoundRect(resumeBtnRect, 24f, 24f, resumeBtnBgPaint)
            canvas.drawRoundRect(resumeBtnRect, 24f, 24f, resumeBtnBorderPaint)
            resumeBtnTextPaint.textSize = screenWidth * 0.07f
            val rLabel = "▶  Play"
            val rBounds = Rect()
            resumeBtnTextPaint.getTextBounds(rLabel, 0, rLabel.length, rBounds)
            canvas.drawText(rLabel,
                resumeBtnRect.centerX() - rBounds.width() / 2f,
                resumeBtnRect.centerY() + rBounds.height() / 2f,
                resumeBtnTextPaint)

            // Homeボタン
            canvas.drawRoundRect(homeBtnRect, 24f, 24f, homeBtnBgPaint)
            canvas.drawRoundRect(homeBtnRect, 24f, 24f, homeBtnBorderPaint)
            val hLabel = "⌂  Home"
            val hBounds = Rect()
            homeBtnTextPaint.getTextBounds(hLabel, 0, hLabel.length, hBounds)
            canvas.drawText(hLabel,
                homeBtnRect.centerX() - hBounds.width() / 2f,
                homeBtnRect.centerY() + hBounds.height() / 2f,
                homeBtnTextPaint)
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

            // スコア（1行目）とレベル（2行目）を別行で表示
            val scoreText = "SCORE: ${scoreManager.score}"
            val scoreBounds = Rect()
            gameOverScorePaint.getTextBounds(scoreText, 0, scoreText.length, scoreBounds)
            canvas.drawText(
                scoreText,
                (screenWidth - scoreBounds.width()) / 2f,
                screenHeight * 0.48f,
                gameOverScorePaint
            )
            val levelText = "Lv.${GameConfig.levelForScore(scoreManager.score)}"
            val levelBounds = Rect()
            gameOverScorePaint.getTextBounds(levelText, 0, levelText.length, levelBounds)
            canvas.drawText(
                levelText,
                (screenWidth - levelBounds.width()) / 2f,
                screenHeight * 0.535f,
                gameOverScorePaint
            )

            // ランクイン表示（ボタンではなくお祝いラベル）
            if (rankAchieved in 1..3) {
                drawRankBadge(canvas, screenHeight * 0.595f)
            }

            // Retry/Homeボタンは誤タップ防止のため約0.5秒遅れて表示する
            if (gameOverTapDelayTimer <= 0) {
                // Retryボタン（緑・Homeボタンの上）。ここを押した時だけリトライする
                canvas.drawRoundRect(gameOverRetryBtnRect, 24f, 24f, resumeBtnBgPaint)
                canvas.drawRoundRect(gameOverRetryBtnRect, 24f, 24f, resumeBtnBorderPaint)
                resumeBtnTextPaint.textSize = screenWidth * 0.058f
                drawCenteredLabel(canvas, "↻  Retry", gameOverRetryBtnRect, resumeBtnTextPaint)

                // Homeボタン
                canvas.drawRoundRect(gameOverHomeBtnRect, 24f, 24f, homeBtnBgPaint)
                canvas.drawRoundRect(gameOverHomeBtnRect, 24f, 24f, homeBtnBorderPaint)
                homeBtnTextPaint.textSize = screenWidth * 0.058f
                drawCenteredLabel(canvas, "⌂  Home", gameOverHomeBtnRect, homeBtnTextPaint)
            }
        }

        // ── MISSION COMPLETE オーバーレイ（ボス撃破・ゲームクリア） ──
        if (gameState == GameState.CLEAR) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)

            // 紙吹雪・花火パーティクル（オーバーレイの上・テキストの下）
            clearCelebration?.draw(canvas)

            // CONGRATULATIONS!（虹色グラデーション・ゆっくり脈動）
            val pulse = 1f + sin(clearAnimFrame * 0.06f) * 0.045f
            congratsPaint.textSize = screenWidth * 0.088f * pulse
            congratsPaint.alpha = (215 + sin(clearAnimFrame * 0.06f) * 40f).toInt().coerceIn(0, 255)
            val congratsText = "CONGRATULATIONS!"
            val cBounds = Rect()
            congratsPaint.getTextBounds(congratsText, 0, congratsText.length, cBounds)
            canvas.drawText(congratsText, (screenWidth - cBounds.width()) / 2f, screenHeight * 0.20f, congratsPaint)

            // MISSION COMPLETE（2行）
            val line1 = "MISSION"
            val line2 = "COMPLETE"
            val b1 = Rect(); clearTextPaint.getTextBounds(line1, 0, line1.length, b1)
            val b2 = Rect(); clearTextPaint.getTextBounds(line2, 0, line2.length, b2)
            canvas.drawText(line1, (screenWidth - b1.width()) / 2f, screenHeight * 0.30f, clearTextPaint)
            canvas.drawText(line2, (screenWidth - b2.width()) / 2f, screenHeight * 0.38f, clearTextPaint)

            // 撃破ボーナス（textSizeを明示的に戻す：他描画で変更されている場合に備える）
            clearBonusPaint.textSize = screenWidth * 0.055f
            // ストーリー: ステージクリア表記（次ステージ解放の案内）
            if (isStoryMode && stage in 1..StageConfig.MAX_STAGE) {
                val stageClearText = if (stage < StageConfig.MAX_STAGE)
                    "STAGE $stage CLEAR ▶ STAGE ${stage + 1} UNLOCKED"
                else "ALL STAGES CLEAR!"
                val sb = Rect(); clearBonusPaint.getTextBounds(stageClearText, 0, stageClearText.length, sb)
                canvas.drawText(stageClearText, (screenWidth - sb.width()) / 2f, screenHeight * 0.435f, clearBonusPaint)
            }

            // 撃破ボーナスは撃破の瞬間にスコア加算済みのため、ここでは表示しない

            // 最終スコア（1行目）とレベル（2行目）を別行で表示
            val scoreLine = "SCORE: ${scoreManager.score}"
            val sBounds = Rect()
            gameOverScorePaint.getTextBounds(scoreLine, 0, scoreLine.length, sBounds)
            canvas.drawText(scoreLine, (screenWidth - sBounds.width()) / 2f, screenHeight * 0.535f, gameOverScorePaint)
            val levelLine = "Lv.${GameConfig.levelForScore(scoreManager.score)}"
            val lBounds = Rect()
            gameOverScorePaint.getTextBounds(levelLine, 0, levelLine.length, lBounds)
            canvas.drawText(levelLine, (screenWidth - lBounds.width()) / 2f, screenHeight * 0.585f, gameOverScorePaint)

            // ランクイン表示（ボタンではなくお祝いラベル）
            if (rankAchieved in 1..3) {
                drawRankBadge(canvas, screenHeight * 0.645f)
            }

            // Returnボタン（ステージ選択へ戻る）。誤タップ防止の待機が明けてから表示
            if (clearTapDelayTimer <= 0) {
                canvas.drawRoundRect(clearReturnBtnRect, 24f, 24f, resumeBtnBgPaint)
                canvas.drawRoundRect(clearReturnBtnRect, 24f, 24f, resumeBtnBorderPaint)
                resumeBtnTextPaint.textSize = screenWidth * 0.065f
                val returnLabel = "↩  Return"
                val returnBounds = Rect()
                resumeBtnTextPaint.getTextBounds(returnLabel, 0, returnLabel.length, returnBounds)
                canvas.drawText(returnLabel,
                    clearReturnBtnRect.centerX() - returnBounds.width() / 2f,
                    clearReturnBtnRect.centerY() + returnBounds.height() / 2f,
                    resumeBtnTextPaint)
            }
        }

        // ── デバッグパネル（全ステートで最前面に描画）──────────────
        if (debugPanelOpen) {
            canvas.drawRoundRect(debugPanelRect, 12f, 12f, dbgPanelBgPaint)
            canvas.drawRoundRect(debugPanelRect, 12f, 12f, dbgPanelBorderPaint)

            canvas.drawText("DEBUG PANEL", debugPanelRect.left + screenWidth * 0.03f,
                debugPanelRect.top + dbgLabelPaint.textSize * 1.2f, dbgLabelPaint)
            canvas.drawText("[X]", dbgCloseRect.left, dbgCloseRect.bottom, dbgOffPaint)

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
            drawToggleRow(dbgInvincibleRect, "無敵モード", debugInvincible)
            drawToggleRow(dbgNoDecayRect, "弾減らない", player.bulletLevelNoDecay)
            drawToggleRow(dbgHitboxRect, "判定範囲", debugShowHitbox)

            // LVL操作（−10 / − / + / +10）
            val lvlLabelY = dbgLvlMinusRect.centerY() + dbgLabelPaint.textSize * 0.4f
            canvas.drawText("LVL: ${blobManager.level}", debugPanelRect.left + screenWidth * 0.03f, lvlLabelY, dbgLabelPaint)
            listOf(
                dbgLvlMinus10Rect to "−10",
                dbgLvlMinusRect   to "−",
                dbgLvlPlusRect    to "+",
                dbgLvlPlus10Rect  to "+10"
            ).forEach { (rect, label) ->
                canvas.drawRoundRect(rect, 6f, 6f, dbgBtnPaint)
                val isPlus = label.startsWith("+")
                val lbounds = android.graphics.Rect()
                (if (isPlus) dbgOnPaint else dbgOffPaint).getTextBounds(label, 0, label.length, lbounds)
                canvas.drawText(label, rect.centerX() - lbounds.width() / 2f, lvlLabelY, if (isPlus) dbgOnPaint else dbgOffPaint)
            }

            // 弾段数操作
            val bltLabelY = dbgBullet1Rect.centerY() + dbgLabelPaint.textSize * 0.4f
            canvas.drawText("BLT:", debugPanelRect.left + screenWidth * 0.03f, bltLabelY, dbgLabelPaint)
            listOf(dbgBullet1Rect to "1", dbgBullet3Rect to "3", dbgBullet5Rect to "5").forEach { (rect, label) ->
                val active = (label.toInt() == player.bulletLevel)
                canvas.drawRoundRect(rect, 6f, 6f, dbgBtnPaint)
                canvas.drawText(label, rect.centerX() - screenWidth * 0.012f, bltLabelY, if (active) dbgOnPaint else dbgOffPaint)
            }

            // HP操作
            val hpLabelY = dbgHp1Rect.centerY() + dbgLabelPaint.textSize * 0.4f
            canvas.drawText("HP:", debugPanelRect.left + screenWidth * 0.03f, hpLabelY, dbgLabelPaint)
            listOf(dbgHp1Rect to "1", dbgHp2Rect to "2", dbgHp3Rect to "3").forEach { (rect, label) ->
                val active = (label.toInt() == hp)
                canvas.drawRoundRect(rect, 6f, 6f, dbgBtnPaint)
                canvas.drawText(label, rect.centerX() - screenWidth * 0.012f, hpLabelY, if (active) dbgOnPaint else dbgOffPaint)
            }
        }
        // ────────────────────────────────────────────────────────
    }

    /** ボタン矩形の中にラベルを縦横中央で描く（フォントメトリクス基準で正確に中央化） */
    private fun drawCenteredLabel(canvas: Canvas, label: String, rect: RectF, paint: Paint) {
        val prevAlign = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, rect.centerX(), baseline, paint)
        paint.textAlign = prevAlign
    }

    /**
     * ランクイン表示（ボタンではなく「お祝いラベル」として描く）。
     * 塗りつぶし枠を使わず、金色テキスト＋下の細い飾り線で、Retry/Homeボタンと明確に区別する。
     */
    private fun drawRankBadge(canvas: Canvas, centerY: Float) {
        val place = when (rankAchieved) { 1 -> "1位"; 2 -> "2位"; else -> "3位" }
        val medal = "★  ランキング $place  ★"
        rankInTextPaint.textSize = screenWidth * 0.058f
        rankInTextPaint.textAlign = Paint.Align.CENTER
        val fm = rankInTextPaint.fontMetrics
        val baseline = centerY - (fm.ascent + fm.descent) / 2f
        canvas.drawText(medal, screenWidth / 2f, baseline, rankInTextPaint)
        rankInTextPaint.textAlign = Paint.Align.LEFT
        // 下の飾り線（ボタンの塗り枠と差をつけるための細いアクセント）
        val lineHalf = screenWidth * 0.14f
        val lineY = baseline + screenWidth * 0.030f
        canvas.drawLine(screenWidth / 2f - lineHalf, lineY, screenWidth / 2f + lineHalf, lineY, rankInBorderPaint)
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
