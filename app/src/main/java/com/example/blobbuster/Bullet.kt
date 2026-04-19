package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class Bullet(
    var x: Float,
    var y: Float,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val vx: Float = 0f,
    private val vy: Float = -screenHeight * 0.025f
) {
    val radius: Float = screenWidth * 0.012f
    var isDead: Boolean = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD740")
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
        if (!isDead) {
            canvas.drawCircle(x, y, radius, paint)
        }
    }
}
