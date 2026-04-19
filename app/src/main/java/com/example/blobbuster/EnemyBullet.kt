package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * tint: 0=green(通常弾), 1=orange(高速弾), 2=red(DRAGON強攻撃弾)
 */
class EnemyBullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    private val screenWidth: Int,
    private val screenHeight: Int,
    val tint: Int = 0
) {
    val radius: Float = screenWidth * 0.018f
    var isDead: Boolean = false

    companion object {
        private val paintsMap = HashMap<Int, EnemyBulletPaints>(3)

        fun initSharedPaints(screenWidth: Int) {
            paintsMap[0] = EnemyBulletPaints.create(screenWidth,
                glowColor = Color.argb(80, 50, 255, 80),
                coreColor = Color.argb(220, 150, 255, 100),
                tipColor  = Color.parseColor("#AAFFAA"))
            paintsMap[1] = EnemyBulletPaints.create(screenWidth,
                glowColor = Color.argb(80, 255, 140, 0),
                coreColor = Color.argb(220, 255, 200, 50),
                tipColor  = Color.parseColor("#FFDD88"))
            paintsMap[2] = EnemyBulletPaints.create(screenWidth,
                glowColor = Color.argb(110, 255, 30, 30),
                coreColor = Color.argb(220, 255, 80, 80),
                tipColor  = Color.parseColor("#FF5555"))
        }
    }

    fun update() {
        x += vx
        y += vy
        if (y > screenHeight * 0.97f || y < -radius ||
            x < -radius || x > screenWidth + radius) {
            isDead = true
        }
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        val p = paintsMap[tint] ?: paintsMap[0] ?: return

        val speed = screenWidth * 0.01f
        val nx = if (speed > 0f) vx / speed else 0f
        val ny = if (speed > 0f) vy / speed else 1f

        val headLen = radius * 5f
        val tailLen = radius * 2.5f
        val hx = x + nx * headLen
        val hy = y + ny * headLen
        val tx = x - nx * tailLen
        val ty = y - ny * tailLen

        canvas.drawLine(tx, ty, hx, hy, p.glow)
        canvas.drawLine(tx, ty, hx, hy, p.core)
        canvas.drawCircle(hx, hy, radius * 0.6f, p.tip)
    }
}

private class EnemyBulletPaints(val glow: Paint, val core: Paint, val tip: Paint) {
    companion object {
        fun create(screenWidth: Int, glowColor: Int, coreColor: Int, tipColor: Int): EnemyBulletPaints {
            val r = screenWidth * 0.018f
            return EnemyBulletPaints(
                glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = glowColor; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; strokeWidth = r * 2.8f
                },
                core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = coreColor; style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND; strokeWidth = r * 1.2f
                },
                tip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tipColor }
            )
        }
    }
}
