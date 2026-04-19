package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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
                    color = Color.argb(110, 255, 255, 255)
                    style = Paint.Style.STROKE; strokeWidth = radius * 0.055f
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
    private val moveType: MoveType = size.moveType()

    // ZIGZAGアニメーション用
    private var zigzagAngle: Float = Random.nextFloat() * 6.28f

    // RANDOM移動用
    private var randomDirX: Float = (Random.nextFloat() * 2f - 1f)
    private var randomDirY: Float = 0.5f + Random.nextFloat() * 0.5f
    private var randomTimer: Int = Random.nextInt(60)

    // 攻撃タイマー（全敵対応）
    private var atkTimer1: Int = Random.nextInt(120)
    private var atkTimer2: Int = if (size == BlobSize.HUGE || size == BlobSize.DRAGON) Random.nextInt(60) else 0
    private var atkTimer3: Int = if (size == BlobSize.DRAGON) Random.nextInt(120) else 0

    companion object {
        private val cache = HashMap<BlobSize, BlobPaints>(8)
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }
        private val hpBarBgPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        private val hpBarGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }
        private val hpBarYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD740") }
        private val hpBarRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF3030") }

        fun initSharedPaints(screenWidth: Int) {
            cache.clear()
            for (size in BlobSize.values()) cache[size] = BlobPaints.create(size, screenWidth)
        }
    }

    fun update(playerX: Float, playerY: Float) {
        when (moveType) {
            MoveType.STRAIGHT_DOWN -> {
                cy += moveSpeed
            }
            MoveType.ZIGZAG -> {
                zigzagAngle += 0.07f
                cx += sin(zigzagAngle) * moveSpeed * 3.0f
                cy += moveSpeed * 0.8f
                cx = cx.coerceIn(radius, screenWidth - radius)
            }
            MoveType.RANDOM -> {
                randomTimer++
                if (randomTimer >= 70) {
                    randomTimer = 0
                    val angle = (Random.nextFloat() * 2f - 0.5f) * Math.PI.toFloat()  // 下方向バイアス
                    randomDirX = cos(angle)
                    randomDirY = sin(angle).coerceAtLeast(-0.3f)  // 上方向を制限
                }
                cx += randomDirX * moveSpeed * 1.5f
                cy += randomDirY * moveSpeed * 1.5f
                cx = cx.coerceIn(radius, screenWidth - radius)
                // 上に行きすぎたら補正
                if (cy < -radius * 2f) cy += moveSpeed
            }
            MoveType.CHASE -> {
                val dx = playerX - cx
                val dy = playerY - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 1f) {
                    cx += (dx / dist) * moveSpeed
                    cy += (dy / dist) * moveSpeed
                }
            }
        }
        if (flashTimer > 0) flashTimer--
    }

    fun tryShoot(playerX: Float, playerY: Float): List<EnemyBullet> {
        val result = mutableListOf<EnemyBullet>()

        when (size) {
            BlobSize.TINY -> {
                atkTimer1++
                if (atkTimer1 >= 300) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.SMALL -> {
                atkTimer1++
                if (atkTimer1 >= 240) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.SPEEDY -> {
                atkTimer1++
                if (atkTimer1 >= 200) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.MEDIUM -> {
                atkTimer1++
                if (atkTimer1 >= 120) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.LARGE -> {
                atkTimer1++
                if (atkTimer1 >= 100) { atkTimer1 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 3, spread = 0.35f, tint = 0))
                }
            }
            BlobSize.HUGE -> {
                atkTimer1++
                if (atkTimer1 >= 85) { atkTimer1 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 5, spread = 0.45f, tint = 1))
                }
                atkTimer2++
                if (atkTimer2 >= 55) { atkTimer2 = 0
                    aimShot(playerX, playerY, tint = 1, speedMult = 1.6f)?.let { result.add(it) }
                }
            }
            BlobSize.DRAGON -> {
                atkTimer1++
                if (atkTimer1 >= 180) { atkTimer1 = 0
                    result.addAll(ringBurst(count = 8, tint = 2))
                }
                atkTimer2++
                if (atkTimer2 >= 48) { atkTimer2 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 3, spread = 0.18f, tint = 2, speedMult = 1.9f))
                }
                atkTimer3++
                if (atkTimer3 >= 280) { atkTimer3 = 0
                    result.addAll(spiralBurst(count = 12, tint = 2))
                }
            }
        }
        return result
    }

    private fun aimShot(playerX: Float, playerY: Float, tint: Int, speedMult: Float = 1f): EnemyBullet? {
        val dx = playerX - cx; val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return null
        val speed = screenHeight * 0.009f * speedMult
        return EnemyBullet(cx, cy, (dx / dist) * speed, (dy / dist) * speed, screenWidth, screenHeight, tint)
    }

    private fun spreadShot(playerX: Float, playerY: Float, count: Int, spread: Float, tint: Int, speedMult: Float = 1f): List<EnemyBullet> {
        val dx = playerX - cx; val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return emptyList()
        val base = atan2(dy, dx)
        val speed = screenHeight * 0.009f * speedMult
        val step = if (count > 1) spread * 2f / (count - 1) else 0f
        return (0 until count).map { i ->
            val a = base - spread + i * step
            EnemyBullet(cx, cy, cos(a) * speed, sin(a) * speed, screenWidth, screenHeight, tint)
        }
    }

    private fun ringBurst(count: Int, tint: Int): List<EnemyBullet> {
        val speed = screenHeight * 0.007f
        val step = (2.0 * Math.PI / count).toFloat()
        return (0 until count).map { i ->
            val a = step * i
            EnemyBullet(cx, cy, cos(a) * speed, sin(a) * speed, screenWidth, screenHeight, tint)
        }
    }

    private fun spiralBurst(count: Int, tint: Int): List<EnemyBullet> {
        val baseSpeed = screenHeight * 0.006f
        val step = (2.0 * Math.PI / count).toFloat()
        return (0 until count).map { i ->
            val a = step * i
            val spd = baseSpeed * (0.7f + 0.8f * i.toFloat() / count)
            EnemyBullet(cx, cy, cos(a) * spd, sin(a) * spd, screenWidth, screenHeight, tint)
        }
    }

    fun takeDamage(): Boolean {
        hp--; flashTimer = 8
        if (hp <= 0) { isDead = true; return true }
        return false
    }

    fun draw(canvas: Canvas) {
        val p = cache[size] ?: return

        if (size == BlobSize.DRAGON) {
            canvas.drawCircle(cx, cy, radius * 2.2f, p.outerGlow)
            canvas.drawCircle(cx, cy, radius * 1.85f, p.innerGlow)
            canvas.drawCircle(cx, cy, radius * 1.55f, p.outerGlow)
        }

        canvas.drawCircle(cx, cy, radius * 1.45f, p.outerGlow)
        canvas.drawCircle(cx, cy, radius * 1.18f, p.innerGlow)
        canvas.drawCircle(cx, cy, radius, p.body)
        canvas.drawCircle(cx, cy, radius * 0.91f, p.rim)
        canvas.drawCircle(cx, cy + radius * 0.5f, radius * 0.72f, p.shadow)

        if (flashTimer > 0) canvas.drawCircle(cx, cy, radius, flashPaint)

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

        if (maxHp > 1) {
            val bw = radius * 1.8f; val bh = radius * 0.22f
            val bx = cx - bw / 2f; val by = cy + radius * 1.25f
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, hpBarBgPaint)
            val ratio = hp.toFloat() / maxHp
            val fgPaint = when (size) {
                BlobSize.DRAGON -> hpBarRedPaint
                BlobSize.HUGE   -> hpBarYellowPaint
                else            -> hpBarGreenPaint
            }
            canvas.drawRoundRect(RectF(bx, by, bx + bw * ratio, by + bh), bh / 2, bh / 2, fgPaint)
        }

        if (size == BlobSize.HUGE) {
            canvas.drawCircle(cx, cy, radius * 1.7f, p.outerGlow)
            canvas.drawCircle(cx, cy, radius * 1.9f, p.innerGlow)
        }
    }
}
