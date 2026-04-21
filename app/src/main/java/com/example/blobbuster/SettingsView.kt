package com.example.blobbuster

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

class SettingsView(context: Context) : View(context) {

    var onBack: (() -> Unit)? = null
    var onResetScores: (() -> Unit)? = null

    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            animTick++
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    private var screenW = 0f
    private var screenH = 0f

    private var bgmBtnRect   = RectF()
    private var sfxBtnRect   = RectF()
    private var resetBtnRect = RectF()
    private var backBtnRect  = RectF()

    private var bgmOn: Boolean = true
    private var sfxOn: Boolean = true

    // Paints
    private val bgPaint = Paint().apply { color = Color.parseColor("#080E1A") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(18, 64, 160, 255)
        style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); isFakeBoldText = true
    }
    private val titleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 180, 220, 255)
    }
    private val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88"); isFakeBoldText = true
    }
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444"); isFakeBoldText = true
    }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 8, 24, 50)
    }
    private val btnBorderCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val btnBorderGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88")
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val btnBorderRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444")
        style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val btnTextCyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); isFakeBoldText = true
    }
    private val btnTextRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4444"); isFakeBoldText = true
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 64, 196, 255)
        style = Paint.Style.STROKE; strokeWidth = 1f
    }

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation()  { handler.removeCallbacks(updateRunnable) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat(); screenH = h.toFloat()

        titlePaint.textSize     = w * 0.11f
        titleGlowPaint.textSize = w * 0.11f
        labelPaint.textSize     = w * 0.052f
        onPaint.textSize        = w * 0.052f
        offPaint.textSize       = w * 0.052f
        btnTextCyanPaint.textSize = w * 0.052f
        btnTextRedPaint.textSize  = w * 0.052f

        bgmOn = AppPrefs.isBgmEnabled(context)
        sfxOn = AppPrefs.isSfxEnabled(context)

        val btnW = w * 0.72f
        val btnH = h * 0.082f
        val btnX = (w - btnW) / 2f

        bgmBtnRect   = RectF(btnX, h * 0.42f, btnX + btnW, h * 0.42f + btnH)
        sfxBtnRect   = RectF(btnX, h * 0.53f, btnX + btnW, h * 0.53f + btnH)
        resetBtnRect = RectF(btnX, h * 0.66f, btnX + btnW, h * 0.66f + btnH)
        backBtnRect  = RectF(btnX, h * 0.79f, btnX + btnW, h * 0.79f + btnH)
    }

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        // Background
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        // Grid
        val gs = screenW * 0.12f
        var gx = 0f; while (gx <= screenW) { canvas.drawLine(gx, 0f, gx, screenH, gridPaint); gx += gs }
        var gy = 0f; while (gy <= screenH) { canvas.drawLine(0f, gy, screenW, gy, gridPaint); gy += gs }

        // Title "SETTINGS"
        val glowA = (sin(animTick * 0.05f) * 35 + 65).toInt()
        val titleText = "SETTINGS"
        val titleBounds = Rect(); titlePaint.getTextBounds(titleText, 0, titleText.length, titleBounds)
        val titleX = (screenW - titleBounds.width()) / 2f
        val titleY = screenH * 0.22f
        titleGlowPaint.color = Color.argb(glowA, 64, 196, 255)
        for (ox in listOf(-4f, 0f, 4f)) for (oy in listOf(-4f, 0f, 4f))
            canvas.drawText(titleText, titleX + ox, titleY + oy, titleGlowPaint)
        canvas.drawText(titleText, titleX, titleY, titlePaint)

        // 区切り線
        canvas.drawLine(screenW * 0.1f, screenH * 0.30f, screenW * 0.9f, screenH * 0.30f, dividerPaint)

        // BGMトグル
        drawToggleBtn(canvas, bgmBtnRect, "BGM", bgmOn)
        // SEトグル
        drawToggleBtn(canvas, sfxBtnRect, "SOUND EFFECTS", sfxOn)

        // 区切り線
        canvas.drawLine(screenW * 0.1f, screenH * 0.62f, screenW * 0.9f, screenH * 0.62f, dividerPaint)

        // RESET HIGH SCORESボタン
        canvas.drawRoundRect(resetBtnRect, 20f, 20f, btnBgPaint)
        canvas.drawRoundRect(resetBtnRect, 20f, 20f, btnBorderRedPaint)
        val resetText = "RESET HIGH SCORES"
        val resetBounds = Rect(); btnTextRedPaint.getTextBounds(resetText, 0, resetText.length, resetBounds)
        canvas.drawText(resetText, (screenW - resetBounds.width()) / 2f,
            resetBtnRect.centerY() + resetBounds.height() / 2f, btnTextRedPaint)

        // BACKボタン
        canvas.drawRoundRect(backBtnRect, 20f, 20f, btnBgPaint)
        canvas.drawRoundRect(backBtnRect, 20f, 20f, btnBorderCyanPaint)
        val backText = "◀  BACK"
        val backBounds = Rect(); btnTextCyanPaint.getTextBounds(backText, 0, backText.length, backBounds)
        canvas.drawText(backText, (screenW - backBounds.width()) / 2f,
            backBtnRect.centerY() + backBounds.height() / 2f, btnTextCyanPaint)
    }

    private fun drawToggleBtn(canvas: Canvas, rect: RectF, label: String, isOn: Boolean) {
        canvas.drawRoundRect(rect, 20f, 20f, btnBgPaint)
        canvas.drawRoundRect(rect, 20f, 20f, if (isOn) btnBorderGreenPaint else btnBorderRedPaint)

        val lBounds = Rect(); labelPaint.getTextBounds(label, 0, label.length, lBounds)
        canvas.drawText(label, rect.left + rect.width() * 0.08f,
            rect.centerY() + lBounds.height() / 2f, labelPaint)

        val statusText = if (isOn) "ON" else "OFF"
        val statusPaint = if (isOn) onPaint else offPaint
        val sBounds = Rect(); statusPaint.getTextBounds(statusText, 0, statusText.length, sBounds)
        canvas.drawText(statusText, rect.right - sBounds.width() - rect.width() * 0.08f,
            rect.centerY() + sBounds.height() / 2f, statusPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            when {
                bgmBtnRect.contains(tx, ty) -> {
                    bgmOn = !bgmOn
                    AppPrefs.setBgmEnabled(context, bgmOn)
                    invalidate()
                }
                sfxBtnRect.contains(tx, ty) -> {
                    sfxOn = !sfxOn
                    AppPrefs.setSfxEnabled(context, sfxOn)
                    invalidate()
                }
                resetBtnRect.contains(tx, ty) -> {
                    android.app.AlertDialog.Builder(context)
                        .setMessage("Reset all high scores?")
                        .setPositiveButton("Yes") { _, _ ->
                            HighScoreManager.resetScores(context)
                            onResetScores?.invoke()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                backBtnRect.contains(tx, ty) -> onBack?.invoke()
            }
        }
        return true
    }
}
