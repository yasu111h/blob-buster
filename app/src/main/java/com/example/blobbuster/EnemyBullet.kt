package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class EnemyBullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val radius: Float = screenWidth * 0.018f
    var isDead: Boolean = false

    companion object {
        private var sharedPaints: EnemyBulletPaints? = null

        fun initSharedPaints(screenWidth: Int) {
            sharedPaints = EnemyBulletPaints.create(screenWidth)
        }
    }

    fun update() {
        x += vx
        y += vy
        if (y > screenHeight + radius || y < -radius ||
            x < -radius || x > screenWidth + radius) {
            isDead = true
        }
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        val p = sharedPaints ?: return

        // 進行方向に伸びる緑のミサイル形状
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

private class EnemyBulletPaints(
    val glow: Paint,
    val core: Paint,
    val tip: Paint
) {
    companion object {
        fun create(screenWidth: Int): EnemyBulletPaints {
            val r = screenWidth * 0.018f
            return EnemyBulletPaints(
                glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(80, 50, 255, 80)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = r * 2.8f
                },
                core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 150, 255, 100)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = r * 1.2f
                },
                tip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#AAFFAA")
                }
            )
        }
    }
}
