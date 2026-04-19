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

    // 彗星状の軌跡（外側グロー）
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 160, 30)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // 中間グロー
    private val midGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 200, 50)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // 外側ブルーム
    private val outerBloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 215, 0)
    }
    // 内側グロー
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 215, 80)
    }
    // コア（白く輝く）
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
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

        // 軌跡（現在位置から3フレーム分後ろへ）
        val tailLength = radius * 5f
        val trailEndX = x - vx / screenHeight * tailLength * 40f
        val trailEndY = y - vy / screenHeight * tailLength * 40f

        trailPaint.strokeWidth = radius * 2.2f
        canvas.drawLine(x, y, trailEndX, trailEndY, trailPaint)

        midGlowPaint.strokeWidth = radius * 1.4f
        canvas.drawLine(x, y, trailEndX, trailEndY, midGlowPaint)

        // 外側ブルーム
        canvas.drawCircle(x, y, radius * 2.8f, outerBloomPaint)

        // 内側グロー
        canvas.drawCircle(x, y, radius * 1.6f, innerGlowPaint)

        // 白いコア
        canvas.drawCircle(x, y, radius * 0.7f, corePaint)
    }
}
