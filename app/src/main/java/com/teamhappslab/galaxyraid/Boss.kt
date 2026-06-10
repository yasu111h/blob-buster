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
 *   フェーズ1 (HP > 66%): 狙い撃ち＋3-way扇状弾
 *   フェーズ2 (33%〜66%): 全方位リング弾＋狙い撃ち高速化
 *   フェーズ3 (< 33%):   5-way扇＋リング弾の複合、移動速度UP
 * - フェーズ移行直後は短時間無敵（白フラッシュ演出）
 * - 撃破: DYING状態で点滅しながら消滅（爆発演出はGameView側でShockwaveを生成）
 */
class Boss(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val width: Float = screenWidth * GameConfig.BOSS_WIDTH_RATIO
    /** 当たり判定半径（見た目よりやや小さめ） */
    val radius: Float = width * 0.38f

    var x: Float = screenWidth / 2f
    var y: Float = -width * 0.6f          // 画面外上から登場
    private val targetY: Float = screenHeight * 0.22f  // 定位置（画面上部1/4あたり）
    private val enterSpeed: Float = screenHeight * 0.004f

    val maxHp: Int = GameConfig.BOSS_MAX_HP
    var hp: Int = maxHp
        private set

    var state: BossState = BossState.ENTERING
        private set

    /** 現在フェーズ（1〜3） */
    val phase: Int
        get() = when {
            hp > maxHp * 2 / 3 -> 1
            hp > maxHp / 3     -> 2
            else               -> 3
        }

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

    // 攻撃タイマー
    private var atkTimer1: Int = 0  // 狙い撃ち
    private var atkTimer2: Int = 0  // 扇状弾
    private var atkTimer3: Int = 0  // リング弾

    companion object {
        private var bossBitmap: Bitmap? = null

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
         * res/drawable/boss.png（等）が存在すればそれを使用、無ければnullのまま
         * （drawPlaceholder()によるCanvas描画にフォールバック）。
         */
        fun initBitmap(context: Context, bossWidth: Float) {
            bossBitmap = null
            val resId = context.resources.getIdentifier("boss", "drawable", context.packageName)
            if (resId == 0) return
            val size = bossWidth.toInt().coerceAtLeast(4)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, opts)
            opts.inSampleSize = calcSampleSize(opts.outWidth, opts.outHeight, size, size)
            opts.inJustDecodeBounds = false
            opts.inScaled = false
            val raw = BitmapFactory.decodeResource(context.resources, resId, opts) ?: return
            bossBitmap = Bitmap.createScaledBitmap(raw, size, size, true)
            if (raw !== bossBitmap) raw.recycle()
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
     * @param bulletCount 現在画面上の敵弾数
     * @param maxBullets  敵弾の上限
     */
    fun tryShoot(playerX: Float, playerY: Float, bulletCount: Int, maxBullets: Int): List<EnemyBullet> {
        if (state != BossState.FIGHTING || phaseInvincibleTimer > 0) return emptyList()
        if (bulletCount >= maxBullets) return emptyList()

        val result = mutableListOf<EnemyBullet>()

        when (phase) {
            1 -> {
                // 狙い撃ち（約1.5秒間隔）
                atkTimer1++
                if (atkTimer1 >= 90) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 1, speedMult = 1.3f)?.let { result.add(it) }
                }
                // 3-way扇状弾（約2秒間隔）
                atkTimer2++
                if (atkTimer2 >= 120) { atkTimer2 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 3, spread = 0.35f, tint = 2, speedMult = 1.1f))
                }
            }
            2 -> {
                // 狙い撃ち高速化（約0.9秒間隔）
                atkTimer1++
                if (atkTimer1 >= 55) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 1, speedMult = 1.5f)?.let { result.add(it) }
                }
                // 全方位リング弾（約2.5秒間隔・12発）
                atkTimer3++
                if (atkTimer3 >= 150) { atkTimer3 = 0
                    result.addAll(ringShot(count = 12, speedMult = 0.9f))
                }
            }
            else -> {
                // 狙い撃ち（約0.8秒間隔）
                atkTimer1++
                if (atkTimer1 >= 50) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 1, speedMult = 1.6f)?.let { result.add(it) }
                }
                // 5-way扇状弾（約1.3秒間隔）
                atkTimer2++
                if (atkTimer2 >= 80) { atkTimer2 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 5, spread = 0.45f, tint = 2, speedMult = 1.2f))
                }
                // 全方位リング弾（約2.2秒間隔・16発）
                atkTimer3++
                if (atkTimer3 >= 130) { atkTimer3 = 0
                    result.addAll(ringShot(count = 16, speedMult = 1.0f))
                }
            }
        }
        return result
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

    fun draw(canvas: Canvas) {
        if (state == BossState.GONE) return

        // 撃破演出中は点滅しながらフェードアウト
        if (state == BossState.DYING) {
            if (frameCount % 6 < 3) return
            bitmapPaint.alpha = ((1f - dyingProgress) * 255).toInt().coerceIn(0, 255)
        } else {
            bitmapPaint.alpha = 255
        }

        drawBody(canvas)

        // 被弾フラッシュ（白点滅）
        if (hitFlashTimer > 0) {
            canvas.drawCircle(x, y, radius, flashPaint)
        }
        // フェーズ移行フラッシュ
        if (phaseFlashTimer > 0 && frameCount % 4 < 2) {
            canvas.drawCircle(x, y, radius * 1.1f, flashPaint)
        }
        bitmapPaint.alpha = 255
    }

    /**
     * ボス本体の描画。
     * 画像差し替え手順: res/drawable/boss.png を追加するだけでよい
     * （initBitmap()が実行時に "boss" drawableを検出して自動的に使用する）。
     */
    private fun drawBody(canvas: Canvas) {
        val bmp = bossBitmap
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
