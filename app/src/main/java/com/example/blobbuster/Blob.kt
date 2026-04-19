package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs

/** BlobSize別に共有するPaintセット。インスタンスごとに持たず3セットだけ存在する */
private class BlobPaints(
    val outerGlow: Paint,
    val innerGlow: Paint,
    val body: Paint,
    val rim: Paint,
    val shadow: Paint,
    val eyeGlow: Paint,
    val eyeWhite: Paint,
    val pupil: Paint,
    val pupilHighlight: Paint,
    val eyebrow: Paint
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
                    style = Paint.Style.STROKE
                    strokeWidth = radius * 0.055f
                },
                shadow    = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 0, 0, 0) },
                eyeGlow   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 255, 40, 40) },
                eyeWhite  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFF9E7") },
                pupil     = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF1744") },
                pupilHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE },
                eyebrow   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1A0000")
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = radius * 0.09f
                }
            )
        }
    }
}

class Blob(
    var cx: Float,
    var cy: Float,
    var vx: Float,
    var vy: Float,
    val size: BlobSize,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    // radiusはsizeとscreenWidthが変わらないのでvalで1回だけ計算
    val radius: Float = size.radius(screenWidth)

    private val groundY: Float = screenHeight * 0.88f
    private val gravity: Float = screenHeight * 0.0005f

    companion object {
        // BlobSize 3種 × 1セットだけ存在する共有Paint
        private val cache = HashMap<BlobSize, BlobPaints>(4)

        /** surfaceCreated時に1回だけ呼ぶ */
        fun initSharedPaints(screenWidth: Int) {
            cache.clear()
            for (size in BlobSize.values()) {
                cache[size] = BlobPaints.create(size, screenWidth)
            }
        }
    }

    fun update() {
        vy += gravity
        cx += vx
        cy += vy

        if (cy + radius >= groundY) { cy = groundY - radius; vy = -abs(vy) }
        if (cy - radius <= 0f)      { cy = radius;           vy =  abs(vy) }
        if (cx - radius <= 0f)      { cx = radius;           vx =  abs(vx) }
        if (cx + radius >= screenWidth) { cx = screenWidth - radius; vx = -abs(vx) }
    }

    fun split(): List<Blob> {
        val smallerSize = size.smaller() ?: return emptyList()
        val leftVx = when (smallerSize) {
            BlobSize.MEDIUM -> -screenWidth * 0.007f
            BlobSize.SMALL  -> -screenWidth * 0.009f
            BlobSize.LARGE  -> -screenWidth * 0.005f
        }
        val splitVy = -screenHeight * 0.018f
        return listOf(
            Blob(cx, cy,  leftVx, splitVy, smallerSize, screenWidth, screenHeight),
            Blob(cx, cy, -leftVx, splitVy, smallerSize, screenWidth, screenHeight)
        )
    }

    fun draw(canvas: Canvas) {
        val p = cache[size] ?: return  // initSharedPaints未呼出しなら描画スキップ

        // グロー
        canvas.drawCircle(cx, cy, radius * 1.45f, p.outerGlow)
        canvas.drawCircle(cx, cy, radius * 1.18f, p.innerGlow)

        // メインボディ
        canvas.drawCircle(cx, cy, radius, p.body)

        // リムハイライト
        canvas.drawCircle(cx, cy, radius * 0.91f, p.rim)

        // 下影（clipRect不使用で軽量化）
        canvas.drawCircle(cx, cy + radius * 0.5f, radius * 0.72f, p.shadow)

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

        // 怒り眉毛
        canvas.drawLine(
            cx - eyeOffsetX - eyeRadius * 1.0f, cy - eyeOffsetY - eyeRadius * 0.7f,
            cx - eyeOffsetX + eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f,
            p.eyebrow
        )
        canvas.drawLine(
            cx + eyeOffsetX - eyeRadius * 0.65f, cy - eyeOffsetY - eyeRadius * 1.4f,
            cx + eyeOffsetX + eyeRadius * 1.0f,  cy - eyeOffsetY - eyeRadius * 0.7f,
            p.eyebrow
        )
    }
}
