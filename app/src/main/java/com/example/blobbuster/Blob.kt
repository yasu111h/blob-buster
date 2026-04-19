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
    private val baseColor = size.color()
    private val r = (baseColor shr 16) and 0xFF
    private val g = (baseColor shr 8) and 0xFF
    private val b = baseColor and 0xFF

    // 外側グロー（大）
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, r, g, b)
    }
    // 内側グロー（中）
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(75, r, g, b)
    }
    // メインボディ
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = baseColor
    }
    // リムハイライト
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    // 下半分の影
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0)
    }
    // 目のグロー
    private val eyeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(85, 255, 40, 40)
    }
    // 白目
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF9E7")
    }
    // 瞳（赤く光る）
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF1744")
    }
    // 瞳ハイライト
    private val pupilHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    // 眉毛
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A0000")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    val radius: Float get() = size.radius(screenWidth)

    private val groundY: Float = screenHeight * 0.88f
    private val gravity: Float = screenHeight * 0.0005f

    fun update() {
        vy += gravity
        cx += vx
        cy += vy

        if (cy + radius >= groundY) {
            cy = groundY - radius
            vy = -abs(vy)
        }
        if (cy - radius <= 0f) {
            cy = radius
            vy = abs(vy)
        }
        if (cx - radius <= 0f) {
            cx = radius
            vx = abs(vx)
        }
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
        rimPaint.strokeWidth = radius * 0.055f
        eyebrowPaint.strokeWidth = radius * 0.09f

        // --- グロー ---
        canvas.drawCircle(cx, cy, radius * 1.45f, outerGlowPaint)
        canvas.drawCircle(cx, cy, radius * 1.18f, innerGlowPaint)

        // --- メインボディ ---
        canvas.drawCircle(cx, cy, radius, bodyPaint)

        // --- リムハイライト（上部が明るい）---
        canvas.drawCircle(cx, cy, radius * 0.91f, rimPaint)

        // --- 下半分を暗くして立体感を出す ---
        canvas.save()
        canvas.clipRect(cx - radius, cy, cx + radius, cy + radius)
        canvas.drawCircle(cx, cy + radius * 0.12f, radius * 0.82f, shadowPaint)
        canvas.restore()

        // --- 目 ---
        val eyeOffsetX = radius * 0.33f
        val eyeOffsetY = radius * 0.12f
        val eyeRadius = radius * 0.25f

        // 目グロー
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeRadius * 1.6f, eyeGlowPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeRadius * 1.6f, eyeGlowPaint)

        // 白目
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, eyeRadius, eyeWhitePaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, eyeRadius, eyeWhitePaint)

        // 赤い瞳
        val pupilRadius = eyeRadius * 0.62f
        canvas.drawCircle(cx - eyeOffsetX, cy - eyeOffsetY, pupilRadius, pupilPaint)
        canvas.drawCircle(cx + eyeOffsetX, cy - eyeOffsetY, pupilRadius, pupilPaint)

        // 瞳ハイライト（小さな白点）
        val hlr = pupilRadius * 0.28f
        canvas.drawCircle(cx - eyeOffsetX - pupilRadius * 0.28f, cy - eyeOffsetY - pupilRadius * 0.28f, hlr, pupilHighlightPaint)
        canvas.drawCircle(cx + eyeOffsetX - pupilRadius * 0.28f, cy - eyeOffsetY - pupilRadius * 0.28f, hlr, pupilHighlightPaint)

        // --- 怒り眉毛（内側が上がるV字） ---
        // 左眉: 外（低）→ 内（高）
        canvas.drawLine(
            cx - eyeOffsetX - eyeRadius * 1.0f, cy - eyeOffsetY - eyeRadius * 0.7f,
            cx - eyeOffsetX + eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f,
            eyebrowPaint
        )
        // 右眉: 内（高）→ 外（低）
        canvas.drawLine(
            cx + eyeOffsetX - eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f,
            cx + eyeOffsetX + eyeRadius * 1.0f, cy - eyeOffsetY - eyeRadius * 0.7f,
            eyebrowPaint
        )
    }
}
