package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
        /**
         * デバッグモード：デバッグボタン・パネルの有効/無効。
         * BuildConfig.DEBUG に連動させ、debugビルド(=手元の実機テスト)ではtrue、
         * リリースビルド(=Google Play配信版)では自動的にfalseになる。
         * これにより公開版で一般ユーザーがデバッグパネル(無敵・レベル操作等)を開けなくなる。
         */
        val DEBUG_MODE = BuildConfig.DEBUG
    }

    private var gameThread: GameThread? = null

    // リトライ要求フラグ。GAME_OVER画面のRetryはUIスレッドで押されるが、
    // リセット処理(initGame)を直接呼ぶと、描画中のGameThreadと弾リスト等の操作が競合して
    // ConcurrentModificationExceptionで落ちる。そこでUIスレッドはこのフラグを立てるだけにし、
    // 実際のリセットはGameThreadのupdate()冒頭で行う（リスト操作を1スレッドに統一）。
    @Volatile private var pendingReset = false

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
    private var bossHealDelayTimer: Int = 0        // 残存敵を全滅させてからボス前回復までのディレイ(約0.3秒)
    private var bossWarningDelayTimer: Int = 0     // ボス前回復からWARNING演出開始までのディレイ(約0.3秒)
    private var bossWarningTimer: Int = 0          // WARNING演出の残りフレーム
    private var bossHpDisplayRatio: Float = 1f     // HPバーの減少アニメーション用
    private var clearTapDelayTimer: Int = 0        // CLEAR直後の誤タップ防止
    private var gameOverTapDelayTimer: Int = 0     // GAME OVER直後の誤タップ防止
    private var clearAnimFrame: Int = 0            // CLEAR画面のアニメーション用カウンタ
    private var bossMinionTimer: Int = 0           // ボス戦中の雑魚出現タイマー
    private var bossExplosion: BossExplosion? = null      // ボス撃破爆発エフェクト
    private var clearCelebration: ClearCelebration? = null // クリア画面の紙吹雪演出

    // ボスHPバーは毒々しいトキシックグリーン（ボスらしく汚めだが派手で目立つ色）
    private val bossHpBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 10, 20, 4)
    }
    private val bossHpBarTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 225, 255, 140)
    }
    private val bossHpBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#379B0C")  // 濃いめのトキシックグリーン
    }
    private val bossHpBarBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 180, 255, 100)
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val bossLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A6F03C"); isFakeBoldText = true
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
    // オーバーレイ画面（一時停止/クリア/失敗）で、その画面表示後にDOWNを受けたボタン。
    // 同じボタン上でUPしたときのみ押下成立とする（ドラッグ流入での誤タップ防止）。
    // ※矩形オブジェクトは毎フレーム作り直されるため、識別子(enum)で保持すること（同一比較===は使わない）。
    private enum class OverlayBtn { RESUME, PAUSE_HOME, CLEAR_RETURN, GAMEOVER_RETRY, GAMEOVER_HOME }
    private var armedBtn: OverlayBtn? = null
    var onGoHome: (() -> Unit)? = null   // 直前の画面へ戻る（CLEAR時のステージ選択へのReturn等）
    var onGoTitle: (() -> Unit)? = null  // タイトル(ホーム)画面へ戻るコールバック

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
    private val pauseOverlayPaint = Paint().apply { color = Color.argb(190, 4, 8, 18) }
    // "PAUSED" タイトル（メタリックシアン＋発光。フォント・シェーダはsurfaceCreatedで設定）
    private val pauseLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pauseGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pauseSubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    // ── SFボタン様式（ホーム画面と同じ角カット＋四隅ブラケット＋発光）用のPaint ──
    private val sfBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sfBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val sfBtnBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.6f; strokeCap = Paint.Cap.SQUARE
    }
    private val sfBtnGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val sfBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true; letterSpacing = 0.12f
    }
    private val sfBtnIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    // ── アイテム取得エフェクト ────────────────────────────
    private var powerUpFlashTimer = 0
    private val powerUpAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
        style = Paint.Style.STROKE
    }
    // ────────────────────────────────────────────────────

    // ── ティアアップエフェクト ────────────────────────────
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
        color = Color.parseColor("#FF2EA6")  // 鮮やかなピンク（「HP」文字とハート共通）
    }
    // HUD文字列のキャッシュ（毎フレームの String.format / "♥".repeat / Rect生成を避けてGCゴミを減らす）
    private var cachedScoreValue: Int = Int.MIN_VALUE
    private var cachedScoreText: String = ""
    private var cachedHp: Int = Int.MIN_VALUE
    private var cachedHeartText: String = ""
    private val heartBoundsReuse = Rect()
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
    // GAME OVER / CLEAR 用フォント（他画面と統一：Saira）
    private val goTitleTypeface = UiKit.loadSaira(context, 800)
    private val goUiTypeface = UiKit.loadSaira(context, 600)
    private val gameOverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3B4E"); typeface = goTitleTypeface; letterSpacing = 0.05f
    }
    private val gameOverGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 40, 60); typeface = goTitleTypeface; letterSpacing = 0.05f
    }
    private val retryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = goUiTypeface
    }
    private val gameOverScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); typeface = goUiTypeface; letterSpacing = 0.06f  // プレイ中HUDと同じ水色
    }
    // 情報パネル（SCORE/LEVEL/RANKを囲むHUD枠）
    private val goPanelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(130, 8, 18, 38) }
    private val goPanelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 70, 200, 255); style = Paint.Style.STROKE; strokeWidth = 1.8f
    }
    private val rankInBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 180, 120, 0)
    }
    private val rankInBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
        style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val rankInTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740"); typeface = goTitleTypeface; letterSpacing = 0.06f
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        // プレイ中HUD・各種ボタンのフォントも他画面と統一（Saira）。
        // ここで設定するのは、各Paintの初期化子より後（宣言順）で typeface を確定させるため。
        for (p in listOf(
            scorePaint, heartPaint, roundPaint, levelPaint, bossLabelPaint,
            // 各種ボタン: 一時停止(II) / RESUME・Retry・Return / Home / SFボタン共通ラベル
            pauseBtnTextPaint, resumeBtnTextPaint, homeBtnTextPaint, sfBtnTextPaint,
            // デバッグパネル（開発用）
            dbgBtnTextPaint, dbgLabelPaint, dbgOnPaint, dbgOffPaint, dbgInfoTextPaint,
            // クリア画面の小見出し（STAGE CLEAR案内）
            clearBonusPaint
        )) {
            p.typeface = goUiTypeface
        }
        // クリア画面の大見出しは太字(800)で統一
        congratsPaint.typeface = goTitleTypeface
        clearTextPaint.typeface = goTitleTypeface
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        screenWidth = width
        screenHeight = height

        // 共有PaintとBulletPoolをscreenWidth確定後に1回だけ初期化
        Blob.initSharedPaints(screenWidth, context)
        Player.initBitmap(context, screenWidth * 0.08f)
        Bullet.initSharedPaints(screenWidth)
        EnemyBullet.initSharedPaints(screenWidth)
        PowerUpItem.initPaints(screenWidth, goUiTypeface)
        Boss.initBitmap(context, screenWidth * GameConfig.BOSS_WIDTH_RATIO)
        bulletPool = BulletPool(screenWidth, screenHeight, initialSize = 60)

        val textSize = screenWidth * 0.05f
        scorePaint.textSize = textSize
        roundPaint.textSize = textSize
        gameOverPaint.textSize = screenWidth * 0.12f
        gameOverGlowPaint.textSize = screenWidth * 0.12f
        gameOverGlowPaint.maskFilter = BlurMaskFilter(screenWidth * 0.02f, BlurMaskFilter.Blur.NORMAL)
        retryPaint.textSize = screenWidth * 0.06f
        gameOverScorePaint.textSize = screenWidth * 0.07f

        // PAUSED タイトル（メタリックシアン＋発光）とサブテキスト
        pauseLabelPaint.textSize = screenWidth * 0.12f
        pauseLabelPaint.typeface = goTitleTypeface
        pauseLabelPaint.letterSpacing = 0.06f
        pauseLabelPaint.shader = LinearGradient(
            0f, screenHeight * 0.375f, 0f, screenHeight * 0.425f,
            intArrayOf(Color.parseColor("#EAF6FF"), Color.WHITE, Color.parseColor("#CDEBFF"),
                Color.parseColor("#6FB6E6"), Color.parseColor("#2C74A8")),
            floatArrayOf(0f, 0.40f, 0.52f, 0.74f, 1f), Shader.TileMode.CLAMP
        )
        pauseGlowPaint.textSize = screenWidth * 0.12f
        pauseGlowPaint.typeface = goTitleTypeface
        pauseGlowPaint.letterSpacing = 0.06f
        pauseGlowPaint.color = Color.argb(150, 60, 200, 255)
        pauseGlowPaint.maskFilter = BlurMaskFilter(screenWidth * 0.02f, BlurMaskFilter.Blur.NORMAL)
        pauseSubPaint.textSize = screenWidth * 0.032f
        pauseSubPaint.typeface = goUiTypeface
        pauseSubPaint.color = Color.argb(160, 130, 195, 235)
        pauseSubPaint.letterSpacing = 0.22f

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
        val area = screenHeight * 0.92f
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
        val dbgBtnY = screenHeight * 0.94f
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
            screenHeight * 0.94f,
            screenWidth * 0.02f + screenWidth * 0.13f,
            screenHeight * 0.94f + screenWidth * 0.07f
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

        if (!::player.isInitialized) {
            // 初回のSurface生成時のみ新規ゲーム開始。
            initGame()
        } else if (gameState == GameState.PLAYING) {
            // バックグラウンド復帰などでSurfaceが作り直された場合は、
            // ゲームをリセットせず自動的に一時停止する。
            gameState = GameState.PAUSED
            soundManager.pauseBgmByUser()
        }
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
        gameOverTapDelayTimer = 0   // 遅延なし（ボタンを即表示）
        soundManager.pauseBgmByUser()
        rankAchieved = HighScoreManager.saveScore(context, scoreManager.score)
    }

    /** ボス撃破演出完了後に呼ばれる。CLEAR状態へ（撃破ボーナスは撃破の瞬間に加算済み） */
    private fun triggerClear() {
        gameState = GameState.CLEAR
        clearTapDelayTimer = 0   // 遅延なし（Returnボタンを即表示）
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
        bossHealDelayTimer = 0
        bossWarningDelayTimer = 0
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
        armedBtn = null
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
            val tx = event.getX(event.actionIndex); val ty = event.getY(event.actionIndex)
            when (event.actionMasked) {
                // 再開/Homeボタンは、この画面上でDOWNを受けたときだけ「押下候補」として武装する
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    armedBtn = when {
                        resumeBtnRect.contains(tx, ty) -> OverlayBtn.RESUME
                        homeBtnRect.contains(tx, ty)   -> OverlayBtn.PAUSE_HOME
                        else -> null
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    when {
                        // デバッグパネルが開いている場合の操作（開発用・従来通りUPで判定）
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
                        // 再開ボタン（同じボタン上でDOWN→UPしたときのみ）
                        armedBtn == OverlayBtn.RESUME && resumeBtnRect.contains(tx, ty) -> {
                            gameState = GameState.PLAYING
                            soundManager.resumeBgmByUser()
                        }
                        // Homeボタン（タイトル画面へ戻る。同じボタン上でDOWN→UPしたときのみ）
                        armedBtn == OverlayBtn.PAUSE_HOME && homeBtnRect.contains(tx, ty) -> onGoTitle?.invoke()
                    }
                    armedBtn = null
                }
                MotionEvent.ACTION_CANCEL -> armedBtn = null
            }
            return true
        }

        // CLEAR中: Returnボタンを押した時だけステージ選択へ戻る（onGoHome=GameActivity.finish）
        if (gameState == GameState.CLEAR) {
            val tx = event.getX(event.actionIndex); val ty = event.getY(event.actionIndex)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                    armedBtn = if (clearReturnBtnRect.contains(tx, ty)) OverlayBtn.CLEAR_RETURN else null
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (clearTapDelayTimer <= 0 && armedBtn == OverlayBtn.CLEAR_RETURN &&
                        clearReturnBtnRect.contains(tx, ty)) {
                        onGoHome?.invoke()
                    }
                    armedBtn = null
                }
                MotionEvent.ACTION_CANCEL -> armedBtn = null
            }
            return true
        }

        if (gameState == GameState.GAME_OVER) {
            // Retry/Homeは、この画面上でDOWN→同じボタン上でUPしたときだけ反応する
            // （ドラッグ中の失敗遷移で指を離しても誤爆しないように）。表示遅延中は無反応。
            val tx = event.getX(event.actionIndex); val ty = event.getY(event.actionIndex)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    armedBtn = when {
                        gameOverRetryBtnRect.contains(tx, ty) -> OverlayBtn.GAMEOVER_RETRY
                        gameOverHomeBtnRect.contains(tx, ty)  -> OverlayBtn.GAMEOVER_HOME
                        else -> null
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    if (gameOverTapDelayTimer <= 0) {
                        when {
                            armedBtn == OverlayBtn.GAMEOVER_RETRY && gameOverRetryBtnRect.contains(tx, ty) -> pendingReset = true
                            armedBtn == OverlayBtn.GAMEOVER_HOME && gameOverHomeBtnRect.contains(tx, ty)  -> onGoTitle?.invoke()
                        }
                    }
                    armedBtn = null
                }
                MotionEvent.ACTION_CANCEL -> armedBtn = null
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
                        player.y = event.getY(idx).coerceIn(screenHeight * 0.35f, screenHeight * 0.92f)
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
        // リトライ要求の処理（GameThread上で実行＝リスト操作の競合を回避）
        if (pendingReset) {
            pendingReset = false
            initGame()
            return
        }
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
                    <= blob.collisionRadius + player.width / 2f) { dashHit = true; break }
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

        // ティアアップ検知（表示は出さず、HP+1回復のみ。演出はボス前回復と統一）
        if (blobManager.tierUpEvent) {
            blobManager.tierUpEvent = false
            applyHeal(1)
        }
        if (healMessageTimer > 0) healMessageTimer--

        // ── 道中回復（ストーリーのステージ設定で指定レベル到達時に1回） ──
        stageConfig?.let { cfg ->
            if (!midHealDone && cfg.midHealLevel > 0 && blobManager.level >= cfg.midHealLevel) {
                midHealDone = true
                applyHeal(cfg.midHealAmount)
            }
        }

        // ── ボス出現シーケンス（ストーリーモードのみ） ──────────
        // 流れ: レベル到達 → 新規出現停止 → 表示中の敵を全滅 → 約0.3秒後にボス前回復 → WARNING → ボス登場
        if (isStoryMode && !bossSpawned && blobManager.level >= bossTriggerLevel) {
            // 通常敵の新規出現を停止
            blobManager.spawningEnabled = false
            if (bossWaitTimer < GameConfig.BOSS_WAIT_MAX_FRAMES) bossWaitTimer++
            // 表示中の敵を全滅させたら（または待機上限を超えたら）回復ディレイを開始
            val enemiesCleared = blobManager.blobs.isEmpty() || bossWaitTimer >= GameConfig.BOSS_WAIT_MAX_FRAMES
            if (enemiesCleared && !preBossHealDone && bossHealDelayTimer == 0) {
                bossHealDelayTimer = GameConfig.BOSS_PRE_HEAL_DELAY_FRAMES
            }
        }
        // 全滅から約0.3秒後にボス前回復
        if (bossHealDelayTimer > 0) {
            bossHealDelayTimer--
            if (bossHealDelayTimer == 0) {
                preBossHealDone = true
                stageConfig?.let { if (it.preBossHealAmount > 0) applyHeal(it.preBossHealAmount) }
                // 回復のさらに約0.3秒後にWARNING演出を開始
                bossWarningDelayTimer = GameConfig.BOSS_PRE_HEAL_DELAY_FRAMES
            }
        }
        // 回復から約0.3秒後にWARNING演出を開始
        if (bossWarningDelayTimer > 0) {
            bossWarningDelayTimer--
            if (bossWarningDelayTimer == 0) {
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
        // 削除ライン(0.92f=地面ライン)との間にバッファを設けることで、
        // 最速弾でも削除前に地面を越えることが数学上ありえない構造にする。
        // プレイヤーも地面ライン(0.92f)より下には行けないため、下領域は純粋なUIエリア。
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
                val r  = bullet.radius + blob.collisionRadius
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
                val r  = player.hitRadius + blob.collisionRadius
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
        } catch (e: Exception) {
            // 描画中の例外でゲームスレッド(=アプリ)を巻き込んで落とさない
        } finally {
            // surface破棄と描画が競合するとunlockが例外を投げることがある。
            // ここで握り潰さないとGameThread上のuncaught例外でアプリごとクラッシュする。
            try {
                holder.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                // 次フレームで再取得するので無視
            }
        }
    }

    private fun drawInternal(canvas: Canvas, alpha: Float = 0f) {
        val area = screenHeight * 0.92f

        // まずキャンバス全体を背景色で塗る（保険）。
        // 画面サイズが後から変わっても screenHeight は surfaceChanged が空で更新されないため、
        // bgBitmap（screenHeight高）より実キャンバスが高いと下端に塗り残し＝未初期化バッファの
        // ゴミ（色付きの四角＝紙吹雪が溜まって見える現象）が残る。全面塗りで物理的に防ぐ。
        canvas.drawColor(bgPaint.color)

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
            canvas.clipRect(0f, 0f, screenWidth.toFloat(), screenHeight * 0.92f)
            canvas.drawCircle(player.x, player.y, auraR, powerUpAuraPaint)
            canvas.restore()
        }

        // プレイヤー描画（無敵中は点滅）
        player.draw(canvas, invincibleTimer > 0, frameCount)

        // デバッグ: 当たり判定範囲を可視化（自機=赤 / 敵・ボス=シアン）
        if (debugShowHitbox) {
            // 敵（通常敵）の判定円
            for (blob in blobManager.blobs) {
                canvas.drawCircle(blob.cx, blob.cy, blob.collisionRadius, enemyHitboxFillPaint)
                canvas.drawCircle(blob.cx, blob.cy, blob.collisionRadius, enemyHitboxStrokePaint)
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
        //   段1(上): SCORE ＋ LEVEL（水色）／ HP（右・ピンク）
        //   段2(下・小さめ・緑): モード名。ボスは「BOSS MODE  STAGE ○」（すべて緑）
        val marginX = screenWidth * 0.03f
        val row1Y = screenHeight * 0.048f
        val row2Y = screenHeight * 0.078f
        val baseSize = screenWidth * 0.05f
        val subSize = screenWidth * 0.040f

        // 段1左（水色）: SCORE ＋ LEVEL
        scorePaint.textSize = baseSize
        if (scoreManager.score != cachedScoreValue) {
            cachedScoreValue = scoreManager.score
            cachedScoreText = "SCORE  ${"%,d".format(cachedScoreValue)}"
        }
        val scoreText = cachedScoreText
        canvas.drawText(scoreText, marginX, row1Y, scorePaint)
        val lvLabel = "Lv ${blobManager.level}"
        val lvX = marginX + scorePaint.measureText(scoreText) + screenWidth * 0.05f
        canvas.drawText(lvLabel, lvX, row1Y, scorePaint)

        // 段1右: HP（ピンクのハート・右寄せ）
        val hpForDraw = hp.coerceAtLeast(0)
        if (hpForDraw != cachedHp) {
            cachedHp = hpForDraw
            cachedHeartText = "HP  " + "♥".repeat(hpForDraw)
        }
        val heartText = cachedHeartText
        heartPaint.textSize = baseSize
        heartPaint.getTextBounds(heartText, 0, heartText.length, heartBoundsReuse)
        canvas.drawText(heartText, screenWidth - heartBoundsReuse.width() - marginX, row1Y, heartPaint)

        // 段2（下・小さめ）: モード名。ホーム画面のボタン色に合わせる（BOSS=赤 / ENDLESS=紫）
        levelPaint.textSize = subSize
        if (isStoryMode) {
            levelPaint.color = Color.parseColor("#FF4557")                 // 赤（ホームのBOSS MODEと同じ）
            val modeText = "BOSS MODE"
            canvas.drawText(modeText, marginX, row2Y, levelPaint)
            val stageText = StageConfig.titleOf(stage)                     // STAGE 1〜5 / STAGE FINAL
            val stX = marginX + levelPaint.measureText(modeText) + screenWidth * 0.045f
            canvas.drawText(stageText, stX, row2Y, levelPaint)
        } else {
            levelPaint.color = Color.parseColor("#B06BFF")                 // 紫（ホームのENDLESSと同じ）
            canvas.drawText("ENDLESS MODE", marginX, row2Y, levelPaint)
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
                val barTop = screenHeight * 0.115f  // 上部HUD(2段目)と被らないよう下げる
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

        // ティアアップ表示は廃止（「TIER N」等は出さない）。回復のみ applyHeal で演出する。

        // PAUSED オーバーレイ
        if (gameState == GameState.PAUSED) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), pauseOverlayPaint)
            // "PAUSED"（発光＋メタリックシアン）
            val pausedText = "PAUSED"
            val pausedBounds = Rect()
            pauseLabelPaint.getTextBounds(pausedText, 0, pausedText.length, pausedBounds)
            val pausedX = (screenWidth - pausedBounds.width()) / 2f
            val pausedY = screenHeight * 0.41f
            canvas.drawText(pausedText, pausedX, pausedY, pauseGlowPaint)
            canvas.drawText(pausedText, pausedX, pausedY, pauseLabelPaint)
            // サブテキスト
            val pSub = "GAME PAUSED"
            canvas.drawText(pSub, (screenWidth - pauseSubPaint.measureText(pSub)) / 2f,
                pausedY + screenHeight * 0.035f, pauseSubPaint)
            // 中央再開ボタン / Homeボタン（SFボタン様式）
            drawSfButton(canvas, resumeBtnRect, "▶", "Play", Color.parseColor("#4DFF9E"))
            drawSfButton(canvas, homeBtnRect, "⌂", "Home", Color.parseColor("#FFC93C"))
        }

        // GAME OVER オーバーレイ
        if (gameState == GameState.GAME_OVER) {
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), overlayPaint)

            val sh = screenHeight.toFloat()

            // GAME OVER テキスト（発光＋メタリック赤）。少し上へ
            val goText = "GAME OVER"
            val goBounds = Rect()
            gameOverPaint.getTextBounds(goText, 0, goText.length, goBounds)
            val goX = (screenWidth - goBounds.width()) / 2f
            val goY = sh * 0.335f
            canvas.drawText(goText, goX, goY, gameOverGlowPaint)
            canvas.drawText(goText, goX, goY, gameOverPaint)

            // ── 情報パネル：NEW RECORDの有無で高さが変わっても全体が釣り合う動的レイアウト ──
            val hasRank = rankAchieved in 1..3
            val panelTop = sh * 0.40f
            val scoreY = panelTop + sh * 0.060f
            val lvY = scoreY + sh * 0.052f
            val rankCenterY = lvY + sh * 0.078f
            val panelBottom = if (hasRank) rankCenterY + sh * 0.070f else lvY + sh * 0.032f
            val panel = RectF(screenWidth * 0.15f, panelTop, screenWidth * 0.85f, panelBottom)
            val panelPath = UiKit.cutRectPath(panel, panel.height() * 0.12f)
            canvas.drawPath(panelPath, goPanelBgPaint)
            canvas.drawPath(panelPath, goPanelBorderPaint)

            // SCORE / Lv（画面中央付近・プレイ中HUDと同じ水色）
            gameOverScorePaint.color = Color.parseColor("#40C4FF")
            val scoreText = "SCORE  ${"%,d".format(scoreManager.score)}"
            val scoreBounds = Rect()
            gameOverScorePaint.getTextBounds(scoreText, 0, scoreText.length, scoreBounds)
            canvas.drawText(scoreText, (screenWidth - scoreBounds.width()) / 2f, scoreY, gameOverScorePaint)
            val levelText = "Lv ${GameConfig.levelForScore(scoreManager.score)}"
            val levelBounds = Rect()
            gameOverScorePaint.getTextBounds(levelText, 0, levelText.length, levelBounds)
            canvas.drawText(levelText, (screenWidth - levelBounds.width()) / 2f, lvY, gameOverScorePaint)

            // NEW RECORD / RANK（あるときだけ・大きめ）
            if (hasRank) drawRankBadge(canvas, rankCenterY)

            // Retry / Home はパネル下端から一定間隔で配置（枠が伸縮しても間隔一定＝バランス維持）
            val bw = screenWidth * 0.48f
            val bh = sh * 0.078f
            val bx = (screenWidth - bw) / 2f
            val retryTop = panelBottom + sh * 0.034f
            gameOverRetryBtnRect = RectF(bx, retryTop, bx + bw, retryTop + bh)
            val homeTop = retryTop + bh + sh * 0.028f
            gameOverHomeBtnRect = RectF(bx, homeTop, bx + bw, homeTop + bh)
            drawSfButton(canvas, gameOverRetryBtnRect, "↻", "Retry", Color.parseColor("#4DFF9E"))
            drawSfButton(canvas, gameOverHomeBtnRect, "⌂", "Home", Color.parseColor("#FFC93C"))
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
            canvas.drawText(line2, (screenWidth - b2.width()) / 2f, screenHeight * 0.358f, clearTextPaint)  // 行間を詰める

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

            // 最終スコア（1行目）とレベル（2行目）を別行で表示（プレイ画面と同じ表記）
            val scoreLine = "SCORE  ${"%,d".format(scoreManager.score)}"
            val sBounds = Rect()
            gameOverScorePaint.getTextBounds(scoreLine, 0, scoreLine.length, sBounds)
            canvas.drawText(scoreLine, (screenWidth - sBounds.width()) / 2f, screenHeight * 0.535f, gameOverScorePaint)
            val levelLine = "Lv ${GameConfig.levelForScore(scoreManager.score)}"
            val lBounds = Rect()
            gameOverScorePaint.getTextBounds(levelLine, 0, levelLine.length, lBounds)
            canvas.drawText(levelLine, (screenWidth - lBounds.width()) / 2f, screenHeight * 0.585f, gameOverScorePaint)

            // ランクイン表示（ボタンではなくお祝いラベル）
            if (rankAchieved in 1..3) {
                drawRankBadge(canvas, screenHeight * 0.645f)
            }

            // Returnボタン（ステージ選択へ戻る）。誤タップ防止の待機が明けてから表示（SFボタン様式）
            if (clearTapDelayTimer <= 0) {
                drawSfButton(canvas, clearReturnBtnRect, "↩", "Return", Color.parseColor("#4DFF9E"))
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
    /**
     * ホーム画面と同じSFボタン様式で描く：角カットの枠＋暗い半透明背景＋外周グロー＋四隅ブラケット、
     * アイコン（システム字形）＋ラベル（Saira）をアクセント色で中央寄せ。
     */
    private fun drawSfButton(canvas: Canvas, rect: RectF, icon: String?, label: String, accent: Int) {
        val path = UiKit.cutRectPath(rect, rect.height() * 0.30f)
        // 外周グロー
        sfBtnGlowPaint.color = Color.argb(75, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, sfBtnGlowPaint)
        // 背景（暗い半透明）
        sfBtnBgPaint.color = Color.argb(180, 8, 18, 36)
        canvas.drawPath(path, sfBtnBgPaint)
        // 枠線
        sfBtnBorderPaint.color = accent
        canvas.drawPath(path, sfBtnBorderPaint)
        // ラベル（アイコン＝システム字形 ＋ 単語＝Saira）
        val ts = rect.height() * 0.40f
        sfBtnTextPaint.textSize = ts; sfBtnTextPaint.color = accent
        sfBtnIconPaint.textSize = ts; sfBtnIconPaint.color = accent
        val wordW = sfBtnTextPaint.measureText(label)
        val gap = if (icon != null) rect.width() * 0.03f else 0f
        val iconW = if (icon != null) sfBtnIconPaint.measureText(icon) else 0f
        var cx = rect.centerX() - (iconW + gap + wordW) / 2f
        val fm = sfBtnTextPaint.fontMetrics
        val cy = rect.centerY() - (fm.ascent + fm.descent) / 2f
        if (icon != null) { canvas.drawText(icon, cx, cy, sfBtnIconPaint); cx += iconW + gap }
        canvas.drawText(label, cx, cy, sfBtnTextPaint)
    }

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
        // 2行構成（★なし・全て大文字）: 1行目 NEW RECORD / 2行目 RANK #n
        rankInTextPaint.textAlign = Paint.Align.CENTER
        rankInTextPaint.textSize = screenWidth * 0.072f   // 大きめに（NEW RECORDを目立たせる）
        val cx = screenWidth / 2f
        val fm = rankInTextPaint.fontMetrics
        val vOff = -(fm.ascent + fm.descent) / 2f   // 視覚中心をターゲットYに合わせる補正
        val half = screenWidth * 0.044f             // 行間の半分（文字が大きくなった分ひろげる）

        canvas.drawText("NEW RECORD", cx, centerY - half + vOff, rankInTextPaint)
        canvas.drawText("RANK #$rankAchieved", cx, centerY + half + vOff, rankInTextPaint)

        rankInTextPaint.textAlign = Paint.Align.LEFT
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
