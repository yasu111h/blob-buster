package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.sqrt
import kotlin.random.Random

private class BlobPaints(
    val outerGlow: Paint, val innerGlow: Paint, val body: Paint,
    val rim: Paint, val shadow: Paint,
    val eyeGlow: Paint, val eyeWhite: Paint, val pupil: Paint,
    val pupilHighlight: Paint, val eyebrow: Paint
) {
    companion object {
        fun create(size: BlobSize, screenWidth: Int): BlobPaints {
            val baseColor = size.color()
            val r = (baseColor shr 16) and 0xFF
            val g = (baseColor shr 8) and 0xFF
            val b = baseColor and 0xFF
            val radius = size.radius(screenWidth)
            return BlobPaints(
                outerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, r, g, b) },
                innerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(75, r, g, b) },
                body      = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor },
                rim       = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(110, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = radius * 0.055f
                },
                shadow    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 0, 0, 0) },
                eyeGlow   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 255, 40, 40) },
                eyeWhite  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF9E7") },
                pupil     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") },
                pupilHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
                eyebrow   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A0000"); style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; strokeWidth = radius * 0.09f
                }
            )
        }
    }
}

class Blob(
    var cx: Float,
    var cy: Float,
    val size: BlobSize,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val radius: Float = size.radius(screenWidth)
    var hp: Int = size.maxHp()
    private val maxHp: Int = size.maxHp()
    var isDead: Boolean = false
    private var flashTimer: Int = 0

    private val moveSpeed: Float = size.speed(screenHeight)

    // 敵弾発射（BlobSizeから取得）
    private val canShoot: Boolean = size.canShoot()
    private val shootInterval: Int = size.shootInterval()
    private var shootTimer: Int = if (canShoot) Random.nextInt(shootInterval.coerceAtMost(60)) else 0

    companion object {
        private val cache = HashMap<BlobSize, BlobPaints>(4)
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }
        private val hpBarBgPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        private val hpBarFgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }

        fun initSharedPaints(screenWidth: Int) {
            cache.clear()
            for (size in BlobSize.values()) cache[size] = BlobPaints.create(size, screenWidth)
        }
    }

    fun update(playerX: Float, playerY: Float) {
        val dx = playerX - cx
        val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 1f) {
            cx += (dx / dist) * moveSpeed
            cy += (dy / dist) * moveSpeed
        }
        if (flashTimer > 0) flashTimer--
    }

    /** プレイヤーに向けて弾を発射。発射タイミングでなければnullを返す */
    fun tryShoot(playerX: Float, playerY: Float): EnemyBullet? {
        if (!canShoot) return null
        shootTimer++
        if (shootTimer < shootInterval) return null
        shootTimer = 0

        val dx = playerX - cx
        val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return null
        val speed = screenHeight * 0.009f
        return EnemyBullet(cx, cy, (dx / dist) * speed, (dy / dist) * speed, screenWidth, screenHeight)
    }

    /** 弾が当たった時に呼ぶ。trueなら死亡 */
    fun takeDamage(): Boolean {
        hp--
        flashTimer = 8
        if (hp <= 0) { isDead = true; return true }
        return false
    }

    fun draw(canvas: Canvas) {
        val p = cache[size] ?: return

        canvas.drawCircle(cx, cy, radius * 1.45f, p.outerGlow)
        canvas.drawCircle(cx, cy, radius * 1.18f, p.innerGlow)
        canvas.drawCircle(cx, cy, radius, p.body)
        canvas.drawCircle(cx, cy, radius * 0.91f, p.rim)
        canvas.drawCircle(cx, cy + radius * 0.5f, radius * 0.72f, p.shadow)

        // 被弾フラッシュ
        if (flashTimer > 0) canvas.drawCircle(cx, cy, radius, flashPaint)

        // 目
        val eyeOffsetX = radius * 0.33f
        val eyeOffsetY = radius * 0.12f
        val eyeRadius  = radius * 0.25f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeRadius * 1.6f, p.eyeGlow)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeRadius * 1.6f, p.eyeGlow)
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeRadius, p.eyeWhite)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeRadius, p.eyeWhite)
        val pupilRadius = eyeRadius * 0.62f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, pupilRadius, p.pupil)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, pupilRadius, p.pupil)
        val hlr = pupilRadius * 0.28f
        canvas.drawCircle(cx - eyeOffsetX - pupilRadius * 0.28f, cy - eyeOffsetY - pupilRadius * 0.28f, hlr, p.pupilHighlight)
        canvas.drawCircle(cx + eyeOffsetX - pupilRadius * 0.28f, cy - eyeOffsetY - pupilRadius * 0.28f, hlr, p.pupilHighlight)
        canvas.drawLine(cx - eyeOffsetX - eyeRadius, cy - eyeOffsetY - eyeRadius * 0.7f,
            cx - eyeOffsetX + eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f, p.eyebrow)
        canvas.drawLine(cx + eyeOffsetX - eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f,
            cx + eyeOffsetX + eyeRadius, cy - eyeOffsetY - eyeRadius * 0.7f, p.eyebrow)

        // HPバー（2HP以上の敵のみ）
        if (maxHp > 1) {
            val bw = radius * 1.8f
            val bh = radius * 0.22f
            val bx = cx - bw / 2f
            val by = cy + radius * 1.25f
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, hpBarBgPaint)
            val ratio = hp.toFloat() / maxHp
            canvas.drawRoundRect(RectF(bx, by, bx + bw * ratio, by + bh), bh / 2, bh / 2, hpBarFgPaint)
        }

        // HUGE専用: 追加の威圧リング
        if (size == BlobSize.HUGE) {
            val hugePaint = cache[size]!!
            canvas.drawCircle(cx, cy, radius * 1.7f, hugePaint.outerGlow)
            canvas.drawCircle(cx, cy, radius * 1.9f, hugePaint.innerGlow)
        }
    }
}
