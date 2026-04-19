package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

class Blob(
    var cx: Float,
    var cy: Float,
    var vx: Float,
    var vy: Float,
    val size: BlobSize,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    val radius: Float get() = size.radius(screenWidth)

    private val groundY: Float = screenHeight * 0.88f
    private val gravity: Float = screenHeight * 0.0005f

    fun update() {
        vy += gravity
        cx += vx
        cy += vy

        // 地面バウンス
        if (cy + radius >= groundY) {
            cy = groundY - radius
            vy = -abs(vy)
        }
        // 天井バウンス
        if (cy - radius <= 0f) {
            cy = radius
            vy = abs(vy)
        }
        // 左壁バウンス
        if (cx - radius <= 0f) {
            cx = radius
            vx = abs(vx)
        }
        // 右壁バウンス
        if (cx + radius >= screenWidth) {
            cx = screenWidth - radius
            vx = -abs(vx)
        }
    }

    fun split(): List<Blob> {
        val smallerSize = size.smaller() ?: return emptyList()
        val leftVx = when (smallerSize) {
            BlobSize.MEDIUM -> -screenWidth * 0.007f
            BlobSize.SMALL -> -screenWidth * 0.009f
            BlobSize.LARGE -> -screenWidth * 0.005f
        }
        val rightVx = -leftVx
        val splitVy = -screenHeight * 0.018f

        return listOf(
            Blob(cx, cy, leftVx, splitVy, smallerSize, screenWidth, screenHeight),
            Blob(cx, cy, rightVx, splitVy, smallerSize, screenWidth, screenHeight)
        )
    }

    fun draw(canvas: Canvas) {
        paint.color = size.color()
        canvas.drawCircle(cx, cy, radius, paint)

        // 目の描画（白目）
        val eyeOffsetX = radius * 0.3f
        val eyeOffsetY = radius * 0.2f
        val eyeRadius = radius * 0.22f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeRadius, eyePaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeRadius, eyePaint)

        // 瞳
        val pupilRadius = eyeRadius * 0.55f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, pupilRadius, pupilPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, pupilRadius, pupilPaint)
    }
}
