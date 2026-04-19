package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class Bullet(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var isDead: Boolean = true  // プールから取り出す前はdead扱い

    val radius: Float = screenWidth * 0.012f

    /** プールから再利用するときに呼ぶ */
    fun reset(newX: Float, newY: Float, newVx: Float, newVy: Float) {
        x = newX; y = newY; vx = newVx; vy = newVy
        isDead = false
    }

    fun update() {
        x += vx
        y += vy
        if (y + radius < 0f || y - radius > screenHeight.toFloat() ||
            x + radius < 0f || x - radius > screenWidth.toFloat()) {
            isDead = true
        }
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        val p = sharedPaints ?: return

        val tailEndX = x - vx / screenHeight * radius * 200f
        val tailEndY = y - vy / screenHeight * radius * 200f

        canvas.drawLine(x, y, tailEndX, tailEndY, p.trail)
        canvas.drawLine(x, y, tailEndX, tailEndY, p.midGlow)
        canvas.drawCircle(x, y, radius * 2.8f, p.outerBloom)
        canvas.drawCircle(x, y, radius * 1.6f, p.innerGlow)
        canvas.drawCircle(x, y, radius * 0.7f, p.core)
    }

    companion object {
        private var sharedPaints: BulletPaints? = null

        /** surfaceCreated時に1回だけ呼ぶ */
        fun initSharedPaints(screenWidth: Int) {
            sharedPaints = BulletPaints.create(screenWidth)
        }
    }
}

private class BulletPaints(
    val trail: Paint,
    val midGlow: Paint,
    val outerBloom: Paint,
    val innerGlow: Paint,
    val core: Paint
) {
    companion object {
        fun create(screenWidth: Int): BulletPaints {
            val radius = screenWidth * 0.012f
            return BulletPaints(
                trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(60, 255, 160, 30)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = radius * 2.2f
                },
                midGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(90, 255, 200, 50)
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = radius * 1.4f
                },
                outerBloom = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(55, 255, 215, 0)
                },
                innerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(160, 255, 215, 80)
                },
                core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#FFFFFF")
                }
            )
        }
    }
}
