package com.example.blobbuster

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class TitleView(context: Context) : View(context) {

    var onStartTapped: (() -> Unit)? = null

    private var isLoading = false  // STARTタップ後のローディング状態
    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            animTick++
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    private data class Star(val x: Float, val y: Float, val r: Float, val baseAlpha: Int, val phase: Float)
    private val stars = mutableListOf<Star>()

    private data class DecoBlob(
        var x: Float, var y: Float,
        val radius: Float,
        val color: Int,
        val vx: Float,
        val vy: Float,
        val phase: Float
    )
    private val decoBlobs = mutableListOf<DecoBlob>()

    private var startButtonRect = RectF()
    private var screenW = 0f
    private var screenH = 0f

    // Paints
    private val bgPaint = Paint().apply { color = Color.parseColor("#080E1A") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(18, 64, 160, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val titleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); isFakeBoldText = true
    }
    private val title2GlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val title2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4081"); isFakeBoldText = true
    }
    private val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 100, 200, 255)
    }
    private val btnGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 8, 24, 50)
    }
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); isFakeBoldText = true
    }
    private val blobGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val versionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 100, 150, 200)
    }
    private val spinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        style = Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation() { handler.removeCallbacks(updateRunnable) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()

        titlePaint.textSize = w * 0.17f
        titleGlowPaint.textSize = w * 0.17f
        title2Paint.textSize = w * 0.15f
        title2GlowPaint.textSize = w * 0.15f
        taglinePaint.textSize = w * 0.036f
        btnTextPaint.textSize = w * 0.07f
        versionPaint.textSize = w * 0.028f

        val bw = w * 0.58f
        val bh = h * 0.078f
        startButtonRect = RectF((w - bw) / 2f, h * 0.67f, (w + bw) / 2f, h * 0.67f + bh)

        val rng = Random(99)
        stars.clear()
        repeat(80) {
            stars.add(Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                r = rng.nextFloat() * 2.2f + 0.3f,
                baseAlpha = rng.nextInt(110) + 50,
                phase = rng.nextFloat() * 6.28f
            ))
        }

        val blobColors = listOf(
            Color.parseColor("#FFB3C1"), Color.parseColor("#00F5FF"),
            Color.parseColor("#FF6600"), Color.parseColor("#FF2D78"),
            Color.parseColor("#39FF14"), Color.parseColor("#7B00CC"),
            Color.parseColor("#CC0000")
        )
        decoBlobs.clear()
        val rng2 = Random(77)
        repeat(9) { i ->
            decoBlobs.add(DecoBlob(
                x = rng2.nextFloat() * w,
                y = rng2.nextFloat() * h,
                radius = w * (0.04f + rng2.nextFloat() * 0.065f),
                color = blobColors[i % blobColors.size],
                vx = (rng2.nextFloat() - 0.5f) * w * 0.0018f,
                vy = (rng2.nextFloat() - 0.5f) * h * 0.0010f,
                phase = rng2.nextFloat() * 6.28f
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        // Move decorative blobs
        for (b in decoBlobs) {
            b.x = (b.x + b.vx).let { if (it < -b.radius * 2) screenW + b.radius else if (it > screenW + b.radius * 2) -b.radius else it }
            b.y = (b.y + b.vy).let { if (it < -b.radius * 2) screenH + b.radius else if (it > screenH + b.radius * 2) -b.radius else it }
        }

        // Background
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        // Grid
        val gs = screenW * 0.12f
        var gx = 0f; while (gx <= screenW) { canvas.drawLine(gx, 0f, gx, screenH, gridPaint); gx += gs }
        var gy = 0f; while (gy <= screenH) { canvas.drawLine(0f, gy, screenW, gy, gridPaint); gy += gs }

        // Stars
        for (s in stars) {
            val a = (sin(animTick * 0.04f + s.phase) * 40 + s.baseAlpha).toInt().coerceIn(20, 255)
            starPaint.color = Color.argb(a, 200, 220, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }

        // Decorative blobs
        for (b in decoBlobs) {
            val r = (b.color shr 16) and 0xFF
            val g = (b.color shr 8) and 0xFF
            val bl = b.color and 0xFF
            val pulse = sin(animTick * 0.03f + b.phase) * 0.12f + 0.88f
            blobGlowPaint.color = Color.argb(20, r, g, bl)
            blobBodyPaint.color = Color.argb(40, r, g, bl)
            canvas.drawCircle(b.x, b.y, b.radius * 1.7f * pulse, blobGlowPaint)
            canvas.drawCircle(b.x, b.y, b.radius * pulse, blobBodyPaint)
        }

        // Title "BLOB"
        val glowA = (sin(animTick * 0.05f) * 35 + 65).toInt()
        val blobText = "BLOB"
        val blobBounds = Rect(); titlePaint.getTextBounds(blobText, 0, blobText.length, blobBounds)
        val blobX = (screenW - blobBounds.width()) / 2f
        val blobY = screenH * 0.33f
        titleGlowPaint.color = Color.argb(glowA, 64, 196, 255)
        for (ox in listOf(-5f, 0f, 5f)) for (oy in listOf(-5f, 0f, 5f)) canvas.drawText(blobText, blobX + ox, blobY + oy, titleGlowPaint)
        canvas.drawText(blobText, blobX, blobY, titlePaint)

        // Title "BUSTER"
        val busterText = "BUSTER"
        val busterBounds = Rect(); title2Paint.getTextBounds(busterText, 0, busterText.length, busterBounds)
        val busterX = (screenW - busterBounds.width()) / 2f + screenW * 0.05f
        val busterY = blobY + screenH * 0.105f
        title2GlowPaint.color = Color.argb(glowA, 255, 64, 128)
        for (ox in listOf(-4f, 0f, 4f)) for (oy in listOf(-4f, 0f, 4f)) canvas.drawText(busterText, busterX + ox, busterY + oy, title2GlowPaint)
        canvas.drawText(busterText, busterX, busterY, title2Paint)

        // Tagline
        val tag = "— SURVIVE THE BLOB INVASION —"
        val tagBounds = Rect(); taglinePaint.getTextBounds(tag, 0, tag.length, tagBounds)
        canvas.drawText(tag, (screenW - tagBounds.width()) / 2f, busterY + screenH * 0.062f, taglinePaint)

        // START button
        val pulse = sin(animTick * 0.06f).toFloat() * 0.35f + 0.65f
        val expand = screenW * 0.025f * pulse
        btnGlowPaint.color = Color.argb((70 * pulse).toInt(), 64, 196, 255)
        canvas.drawRoundRect(
            RectF(startButtonRect.left - expand, startButtonRect.top - expand,
                startButtonRect.right + expand, startButtonRect.bottom + expand),
            28f, 28f, btnGlowPaint
        )
        canvas.drawRoundRect(startButtonRect, 20f, 20f, btnBgPaint)
        btnBorderPaint.alpha = (180 * pulse + 75).toInt().coerceIn(0, 255)
        canvas.drawRoundRect(startButtonRect, 20f, 20f, btnBorderPaint)

        val startText = if (isLoading) "Now Loading..." else "▶  START"
        val startBounds = Rect(); btnTextPaint.getTextBounds(startText, 0, startText.length, startBounds)
        btnTextPaint.alpha = if (isLoading) {
            // ローディング中はゆっくり点滅
            (sin(animTick * 0.08f) * 60 + 160).toInt().coerceIn(80, 220)
        } else {
            (190 * pulse + 65).toInt().coerceIn(0, 255)
        }
        canvas.drawText(
            startText,
            (screenW - startBounds.width()) / 2f,
            startButtonRect.centerY() + startBounds.height() / 2f,
            btnTextPaint
        )

        // ローディングスピナー
        if (isLoading) {
            val spinR = screenW * 0.06f
            val spinCX = screenW / 2f
            val spinCY = startButtonRect.bottom + spinR * 2.0f
            spinPaint.strokeWidth = screenW * 0.008f
            val spinStart = (animTick * 6f) % 360f
            canvas.drawArc(
                android.graphics.RectF(spinCX - spinR, spinCY - spinR, spinCX + spinR, spinCY + spinR),
                spinStart, 270f, false, spinPaint
            )
        }

        // Version
        canvas.drawText("v0.1.0", screenW * 0.04f, screenH * 0.97f, versionPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !isLoading) {
            if (startButtonRect.contains(event.x, event.y)) {
                isLoading = true
                invalidate()
                onStartTapped?.invoke()
            }
        }
        return true
    }
}
