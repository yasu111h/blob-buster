package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** ボスの状態 */
enum class BossState { ENTERING, FIGHTING, DYING, GONE }

/**
 * レベル50のラスボス（ストーリーモード専用）。
 *
 * - 入場: 画面上部から定位置（screenHeight * 0.22f）まで降下。入場中は無敵・攻撃なし
 * - フェーズ制（HPで切替）:
 *   フェーズ1 (HP > 66%): 同方向5連射＋5-way扇状弾
 *   フェーズ2 (33%〜66%): 同方向5連射＋リング弾12発＋衝撃波
 *   フェーズ3 (< 33%):   同方向5連射＋5-way扇＋リング弾16発＋衝撃波、移動速度UP
 * - フェーズ移行直後は短時間無敵（白フラッシュ演出）
 * - 撃破: DYING状態で点滅しながら縮小・消滅（爆発演出はGameView側のBossExplosion）
 */
class Boss(
    private val screenWidth: Int,
    private val screenHeight: Int,
    bossMaxHp: Int = GameConfig.BOSS_MAX_HP,        // ステージ別の最大HP
    private val maxPhase: Int = 3,                  // 到達できる最大フェーズ（1〜3）
    private val attackIntervalMult: Float = 1f,     // 攻撃間隔倍率（大きいほど弾幕が薄い）
    private val useAltBitmap: Boolean = false,       // trueなら boss2.png を使う（Stage3〜5）
    private val phases: List<BossPhasePattern> = DEFAULT_PHASES  // 3フェーズ分の攻撃構成
) {
    val width: Float = screenWidth * GameConfig.BOSS_WIDTH_RATIO
    /** 当たり判定半径（見た目よりやや小さめ） */
    val radius: Float = width * 0.38f

    var x: Float = screenWidth / 2f
    var y: Float = -width * 0.6f          // 画面外上から登場
    private var prevX: Float = x          // 補間用: 前回更新時の位置
    private var prevY: Float = y
    private val targetY: Float = screenHeight * 0.22f  // 定位置（画面上部1/4あたり）
    private val enterSpeed: Float = screenHeight * 0.004f

    val maxHp: Int = bossMaxHp
    var hp: Int = maxHp
        private set

    var state: BossState = BossState.ENTERING
        private set

    /** 現在フェーズ（1〜maxPhase）。HPで決まるが、ステージ上限maxPhaseでクランプ */
    val phase: Int
        get() = when {
            hp > maxHp * 2 / 3 -> 1
            hp > maxHp / 3     -> 2
            else               -> 3
        }.coerceAtMost(maxPhase)

    private var prevPhase: Int = 1
    private var phaseInvincibleTimer: Int = 0   // フェーズ移行直後の無敵
    private var phaseFlashTimer: Int = 0        // フェーズ移行フラッシュ演出
    private var hitFlashTimer: Int = 0          // 被弾フラッシュ
    private var dyingTimer: Int = 0
    private var frameCount: Int = 0

    /** 入場中・フェーズ移行直後・撃破演出中はダメージを受けない */
    val isInvincible: Boolean
        get() = state != BossState.FIGHTING || phaseInvincibleTimer > 0

    val isDying: Boolean get() = state == BossState.DYING
    val isGone: Boolean get() = state == BossState.GONE

    /** 撃破演出の進行度（0.0〜1.0） */
    val dyingProgress: Float
        get() = (dyingTimer.toFloat() / GameConfig.BOSS_DYING_FRAMES).coerceIn(0f, 1f)

    // スイング移動
    private var swayAngle: Float = 0f
    private val swayAmplitude: Float = screenWidth * 0.16f

    // 攻撃タイマー（初期値をずらして弾と衝撃波の同時発射が重ならない位相にする）
    private var atkTimer1: Int = 0    // 同方向5連射
    private var atkTimer2: Int = -24  // 5方向扇弾（狭い狙い扇）
    private var atkTimer3: Int = -48  // リング弾
    private var atkTimer4: Int = -72  // 衝撃波
    private var atkTimer5: Int = -60  // 扇弾（広角扇）

    companion object {
        /** ステージ設定が無い場合（エンドレス用）のデフォルト3フェーズ攻撃構成 */
        private val DEFAULT_PHASES = listOf(
            BossPhasePattern(spread = true),
            BossPhasePattern(ring = 12, shockwave = true),
            BossPhasePattern(spread = true, ring = 16, shockwave = true),
        )

        private var bossBitmap: Bitmap? = null       // Stage1・2用（boss.png）
        private var bossBitmapAlt: Bitmap? = null    // Stage3〜5用（boss2.png）

        // ── プレースホルダー描画用Paint（画像差し替え後は未使用） ──
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5A0A78") }
        private val bodyDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#37054A") }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(70, 200, 60, 255)
            style = Paint.Style.STROKE
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") }
        private val eyeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD740") }
        private val spikePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#7B00CC") }
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 255, 255, 255) }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        /**
         * ボス画像をロードする。
         * res/drawable/boss.png（Stage1・2用）と boss2.png（Stage3〜5用）を読み込む。
         * 存在しなければそれぞれnullのまま（drawPlaceholder()によるCanvas描画にフォールバック）。
         */
        fun initBitmap(context: Context, bossWidth: Float) {
            bossBitmap = loadScaled(context, "boss", bossWidth)
            bossBitmapAlt = loadScaled(context, "boss2", bossWidth)
        }

        /** drawable名から指定サイズに縮小したBitmapを生成（無ければnull）。 */
        private fun loadScaled(context: Context, name: String, bossWidth: Float): Bitmap? {
            val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (resId == 0) return null
            val size = bossWidth.toInt().coerceAtLeast(4)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, opts)
            opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, size, size)
            opts.inJustDecodeBounds = false
            opts.inScaled = false
            val raw = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return null
            val scaled = Bitmap.createScaledBitmap(raw, size, size, true)
            if (raw !== scaled) raw.recycle()
            return scaled
        }

        private fun calcSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
            var sample = 1
            if (rawW > reqW || rawH > reqH) {
                while (rawW / (sample * 2) >= reqW && rawH / (sample * 2) >= reqH) {
                    sample *= 2
                }
            }
            return sample
        }
    }

    fun update() {
        prevX = x; prevY = y
        frameCount++
        if (hitFlashTimer > 0) hitFlashTimer--
        if (phaseFlashTimer > 0) phaseFlashTimer--
        if (phaseInvincibleTimer > 0) phaseInvincibleTimer--

        when (state) {
            BossState.ENTERING -> {
                y += enterSpeed
                if (y >= targetY) {
                    y = targetY
                    state = BossState.FIGHTING
                }
            }
            BossState.FIGHTING -> {
                // フェーズが進むほど速くスイング
                val swaySpeed = when (phase) {
                    1 -> 0.012f
                    2 -> 0.018f
                    else -> 0.026f
                }
                swayAngle += swaySpeed
                x = screenWidth / 2f + sin(swayAngle) * swayAmplitude

                // フェーズ移行検知（フラッシュ＋短時間無敵）
                if (phase != prevPhase) {
                    prevPhase = phase
                    phaseInvincibleTimer = GameConfig.BOSS_PHASE_INVINCIBLE_FRAMES
                    phaseFlashTimer = GameConfig.BOSS_PHASE_INVINCIBLE_FRAMES
                    // 攻撃タイマーをずらしてリセット（複数攻撃の同時発射を防ぐ）
                    atkTimer1 = 0; atkTimer2 = -24; atkTimer3 = -48; atkTimer4 = -72; atkTimer5 = -60
                }
            }
            BossState.DYING -> {
                dyingTimer++
                if (dyingTimer >= GameConfig.BOSS_DYING_FRAMES) {
                    state = BossState.GONE
                }
            }
            BossState.GONE -> {}
        }
    }

    /**
     * フェーズに応じた攻撃。既存EnemyBulletを再利用する。
     * 衝撃波はBlob.tryShootと同様、引数のリストへ直接addする。
     * @param bulletCount 現在画面上の敵弾数
     * @param maxBullets  敵弾の上限
     * @param shockwaves  衝撃波リスト
     */
    fun tryShoot(
        playerX: Float, playerY: Float,
        bulletCount: Int, maxBullets: Int,
        shockwaves: MutableList<Shockwave>
    ): List<EnemyBullet> {
        if (state != BossState.FIGHTING || phaseInvincibleTimer > 0) return emptyList()
        if (bulletCount >= maxBullets) return emptyList()

        val result = mutableListOf<EnemyBullet>()
        // 現在フェーズの攻撃構成（HPで決まるphaseでインデックス）
        val p = phases.getOrElse(phase - 1) { phases.last() }

        // 同方向5連射
        if (p.burst) {
            atkTimer1++
            if (atkTimer1 >= ivp(GameConfig.BOSS_ATK_BURST_BASE, p.freqMult)) { atkTimer1 = 0
                burstShot(playerX, playerY, result)
            }
        }
        // 5方向扇弾（狭い狙い扇・5発）
        if (p.spread) {
            atkTimer2++
            if (atkTimer2 >= ivp(GameConfig.BOSS_ATK_SPREAD_BASE, p.freqMult)) { atkTimer2 = 0
                result.addAll(spreadShot(playerX, playerY, count = 5, spread = 0.42f, tint = 2, speedMult = 1.2f))
            }
        }
        // 扇弾（広角扇・7発）
        if (p.wideSpread) {
            atkTimer5++
            if (atkTimer5 >= ivp(GameConfig.BOSS_ATK_WIDE_SPREAD_BASE, p.freqMult)) { atkTimer5 = 0
                result.addAll(spreadShot(playerX, playerY, count = 7, spread = 0.78f, tint = 3, speedMult = 1.05f))
            }
        }
        // 全方位リング弾（12発 or 16発）
        if (p.ring > 0) {
            atkTimer3++
            if (atkTimer3 >= ivp(GameConfig.BOSS_ATK_RING_BASE, p.freqMult)) { atkTimer3 = 0
                result.addAll(ringShot(count = p.ring, speedMult = if (p.ring >= 16) 1.0f else 0.9f))
            }
        }
        // 衝撃波
        if (p.shockwave) {
            atkTimer4++
            if (atkTimer4 >= ivp(GameConfig.BOSS_ATK_SHOCKWAVE_BASE, p.freqMult)) { atkTimer4 = 0
                fireShockwave(playerX, playerY, shockwaves)
            }
        }
        return result
    }

    /** 攻撃間隔 = 基準値 × フェーズの攻撃頻度倍率 × ステージのボス攻撃間隔倍率（最低1フレーム） */
    private fun ivp(base: Int, freqMult: Float): Int =
        (base * freqMult * attackIntervalMult).toInt().coerceAtLeast(1)

    /** 同方向5連射: 速度差をつけた照準弾×5（ENEMY8と同方式・ボス用に少し高速） */
    private fun burstShot(playerX: Float, playerY: Float, result: MutableList<EnemyBullet>) {
        listOf(2.2f, 1.9f, 1.6f, 1.3f, 1.0f).forEach { s ->
            aimShot(playerX, playerY, tint = 1, speedMult = s)?.let { result.add(it) }
        }
    }

    /** プレイヤー方向への扇形衝撃波（sweepAngle=14fで通常敵より強化） */
    private fun fireShockwave(playerX: Float, playerY: Float, shockwaves: MutableList<Shockwave>) {
        if (shockwaves.size >= GameConfig.BOSS_MAX_SHOCKWAVES) return
        val angleDeg = Math.toDegrees(atan2((playerY - y).toDouble(), (playerX - x).toDouble())).toFloat()
        shockwaves.add(Shockwave(x, y, screenWidth, screenHeight, angleDeg,
            sweepAngle = GameConfig.BOSS_SHOCKWAVE_SWEEP_ANGLE))
    }

    private fun aimShot(playerX: Float, playerY: Float, tint: Int, speedMult: Float = 1f): EnemyBullet? {
        val dx = playerX - x; val dy = playerY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return null
        val speed = screenHeight * 0.009f * speedMult * GameConfig.ENEMY_BULLET_SPEED_MULT
        return EnemyBullet(x, y, (dx / dist) * speed, (dy / dist) * speed, screenWidth, screenHeight, tint)
    }

    private fun spreadShot(playerX: Float, playerY: Float, count: Int, spread: Float, tint: Int, speedMult: Float = 1f): List<EnemyBullet> {
        val dx = playerX - x; val dy = playerY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return emptyList()
        val base = atan2(dy, dx)
        val speed = screenHeight * 0.009f * speedMult * GameConfig.ENEMY_BULLET_SPEED_MULT
        val step = if (count > 1) spread * 2f / (count - 1) else 0f
        return (0 until count).map { i ->
            val a = base - spread + i * step
            EnemyBullet(x, y, cos(a) * speed, sin(a) * speed, screenWidth, screenHeight, tint)
        }
    }

    /** ボス中心から全方位に弾をばらまく */
    private fun ringShot(count: Int, speedMult: Float = 1f): List<EnemyBullet> {
        val speed = screenHeight * 0.009f * speedMult * GameConfig.ENEMY_BULLET_SPEED_MULT
        val offset = (frameCount % 360) * 0.0174f  // 毎回少し回転させて弾幕に変化をつける
        return (0 until count).map { i ->
            val a = offset + i * (2f * Math.PI.toFloat() / count)
            EnemyBullet(x, y, cos(a) * speed, sin(a) * speed, screenWidth, screenHeight, tint = 0)
        }
    }

    /**
     * プレイヤー弾の被弾処理。
     * @return 撃破した（DYINGへ移行した）瞬間のみtrue
     */
    fun takeDamage(damage: Int = 1): Boolean {
        if (isInvincible) return false
        hp -= damage
        hitFlashTimer = 4
        if (hp <= 0) {
            hp = 0
            state = BossState.DYING
            dyingTimer = 0
            return true
        }
        return false
    }

    fun draw(canvas: Canvas, alpha: Float = 0f) {
        if (state == BossState.GONE) return

        // 撃破演出中は点滅しながら縮小・フェードアウト
        var scale = 1f
        if (state == BossState.DYING) {
            if (frameCount % 6 < 3) return
            bitmapPaint.alpha = ((1f - dyingProgress) * 255).toInt().coerceIn(0, 255)
            scale = 1f - dyingProgress * 0.65f
        } else if (hitFlashTimer > 0) {
            // 被弾中：白い円ではなくボスの絵自体を軽く明滅させる
            val k = 0.5f + 0.5f * sin(frameCount * 0.7f)        // 0〜1
            bitmapPaint.alpha = (255 - 95 * k).toInt().coerceIn(0, 255)  // 約160〜255
        } else {
            bitmapPaint.alpha = 255
        }

        // 前回位置と現在位置の中間へずらして描く（カクつき防止の補間）
        val iox = (prevX - x) * (1f - alpha)
        val ioy = (prevY - y) * (1f - alpha)
        canvas.save()
        canvas.translate(iox, ioy)

        if (scale != 1f) {
            canvas.save()
            canvas.scale(scale, scale, x, y)
            drawBody(canvas)
            canvas.restore()
        } else {
            drawBody(canvas)
        }

        // 被弾フラッシュ／フェーズ移行フラッシュの白い円は廃止
        // （連射で当たり続けるとボスが白く潰れて見えるため。無敵判定など挙動はそのまま）

        canvas.restore()
        bitmapPaint.alpha = 255
    }

    /**
     * ボス本体の描画。
     * Stage3〜5は boss2.png（useAltBitmap=true）、Stage1・2は boss.png を使う。
     * 該当画像が無ければもう一方→プレースホルダーへフォールバック。
     */
    private fun drawBody(canvas: Canvas) {
        val bmp = if (useAltBitmap) (bossBitmapAlt ?: bossBitmap) else bossBitmap
        if (bmp != null) {
            val half = width / 2f
            canvas.drawBitmap(bmp, null, RectF(x - half, y - half, x + half, y + half), bitmapPaint)
            return
        }
        drawPlaceholder(canvas)
    }

    /** 仮グラフィック（boss.png が用意されるまでのプレースホルダー） */
    private fun drawPlaceholder(canvas: Canvas) {
        val r = radius
        val pulse = 1f + sin(frameCount * 0.08f) * 0.04f

        // 外周グロー
        glowPaint.strokeWidth = r * 0.18f
        canvas.drawCircle(x, y, r * 1.08f * pulse, glowPaint)

        // 周囲のトゲ（8方向の三角形的な装甲）
        for (i in 0 until 8) {
            val a = i * (Math.PI.toFloat() / 4f) + frameCount * 0.005f
            val sx = x + cos(a) * r * 0.95f
            val sy = y + sin(a) * r * 0.95f
            canvas.drawCircle(sx, sy, r * 0.22f, spikePaint)
        }

        // 本体（二重円）
        canvas.drawCircle(x, y, r * pulse, bodyPaint)
        canvas.drawCircle(x, y, r * 0.72f, bodyDarkPaint)

        // 中央の目（フェーズが進むと目が大きく赤くなる）
        val eyeScale = 0.20f + (phase - 1) * 0.04f
        canvas.drawCircle(x, y, r * (eyeScale + 0.10f), eyePaint)
        canvas.drawCircle(x, y, r * eyeScale * 0.5f, eyeCorePaint)

        // 左右のサブの目
        canvas.drawCircle(x - r * 0.45f, y - r * 0.15f, r * 0.10f, eyePaint)
        canvas.drawCircle(x + r * 0.45f, y - r * 0.15f, r * 0.10f, eyePaint)
    }
}
