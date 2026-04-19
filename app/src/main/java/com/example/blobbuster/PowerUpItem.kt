package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.math.sqrt

class PowerUpItem(
    var x: Float,
    var y: Float,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val radius: Float = screenWidth * 0.032f
    var isDead: Boolean = false
    private var animTick: Int = 0

    private val speed = screenHeight * 0.007f

    companion object {
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 220, 0)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
        }
        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A0000")
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        fun initPaints(screenWidth: Int) {
            textPaint.textSize = screenWidth * 0.028f
        }
    }

    fun update(playerX: Float, playerY: Float) {
        val dx = playerX - x
        val dy = playerY - y
        val dist = sqrt(dx * dx + dy * dy)
        if (dist > 1f) {
            x += (dx / dist) * speed
            y += (dy / dist) * speed
        }
        animTick++
    }

    fun checkCollect(playerX: Float, playerY: Float, playerWidth: Float): Boolean {
        val dx = playerX - x
        val dy = playerY - y
        return dx * dx + dy * dy <= (playerWidth + radius) * (playerWidth + radius)
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        val pulse = 1f + 0.15f * sin(animTick * 0.15f).toFloat()

        canvas.drawCircle(x, y, radius * 1.8f * pulse, glowPaint)
        canvas.drawCircle(x, y, radius * pulse, bodyPaint)
        canvas.drawCircle(x, y, radius * 0.45f * pulse, corePaint)
        canvas.drawText("UP", x, y + textPaint.textSize * 0.35f, textPaint)
    }
}
