package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/** 設定画面。タイトル画面と同じSF/HUD調で統一。 */
class SettingsView(context: Context) : View(context) {

    var onBack: (() -> Unit)? = null
    var onResetScores: (() -> Unit)? = null

    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { animTick++; invalidate(); handler.postDelayed(this, 16L) }
    }

    private var screenW = 0f
    private var screenH = 0f

    private var bgmBtnRect   = RectF()
    private var sfxBtnRect   = RectF()
    private var resetBtnRect = RectF()
    private var backBtnRect  = RectF()

    private var bgmOn = true
    private var sfxOn = true

    private val space = SpaceBackground()
    private val titleTypeface = UiKit.loadSaira(context, 800)
    private val uiTypeface = UiKit.loadSaira(context, 600)

    // ── 色 ──
    private val cCyan  = Color.parseColor("#3FC4FF")
    private val cGreen = Color.parseColor("#34E29B")
    private val cRed   = Color.parseColor("#FF5C7A")
    private val cDim   = Color.parseColor("#88A6C4")

    // ── Paints ──
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface }
    private val titleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface }
    private val titleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; color = Color.argb(170, 6, 26, 52)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(150, 130, 175, 215); letterSpacing = 0.24f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(225, 200, 225, 255); letterSpacing = 0.10f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface; letterSpacing = 0.08f }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiTypeface; letterSpacing = 0.14f }

    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.6f; strokeCap = Paint.Cap.SQUARE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.argb(45, 64, 196, 255)
    }

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation()  { handler.removeCallbacks(updateRunnable) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat(); screenH = h.toFloat()
        space.configure(w, h, 31L)

        titlePaint.textSize = w * 0.105f
        titleGlowPaint.textSize = w * 0.105f
        titleShadowPaint.textSize = w * 0.105f
        titleGlowPaint.maskFilter = BlurMaskFilter(w * 0.020f, BlurMaskFilter.Blur.NORMAL)
        subPaint.textSize = w * 0.030f
        labelPaint.textSize = w * 0.046f
        statusPaint.textSize = w * 0.046f
        btnTextPaint.textSize = w * 0.050f

        // メタリックなタイトル塗り
        titlePaint.shader = LinearGradient(
            0f, h * 0.12f, 0f, h * 0.20f,
            intArrayOf(Color.parseColor("#D9ECFF"), Color.WHITE, Color.parseColor("#CFEBFF"),
                Color.parseColor("#74B8E8"), Color.parseColor("#235F96")),
            floatArrayOf(0f, 0.40f, 0.52f, 0.74f, 1f), Shader.TileMode.CLAMP
        )

        bgmOn = AppPrefs.isBgmEnabled(context)
        sfxOn = AppPrefs.isSfxEnabled(context)

        val bw = w * 0.74f
        val bh = h * 0.078f
        val x = (w - bw) / 2f
        bgmBtnRect   = RectF(x, h * 0.345f, x + bw, h * 0.345f + bh)
        sfxBtnRect   = RectF(x, h * 0.450f, x + bw, h * 0.450f + bh)
        resetBtnRect = RectF(x, h * 0.620f, x + bw, h * 0.620f + bh)
        backBtnRect  = RectF(x, h * 0.840f, x + bw, h * 0.840f + bh)
    }

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        space.draw(canvas, animTick)

        // タイトル
        drawGlowTitle(canvas, "SETTINGS", screenH * 0.175f)
        val sub = "SYSTEM CONFIG"
        canvas.drawText(sub, (screenW - subPaint.measureText(sub)) / 2f, screenH * 0.215f, subPaint)

        // セクション見出し的な区切り
        canvas.drawLine(screenW * 0.13f, screenH * 0.295f, screenW * 0.87f, screenH * 0.295f, dividerPaint)

        // トグル
        drawToggle(canvas, bgmBtnRect, "BGM", bgmOn, 0f)
        drawToggle(canvas, sfxBtnRect, "SOUND EFFECTS", sfxOn, 0.6f)

        canvas.drawLine(screenW * 0.13f, screenH * 0.560f, screenW * 0.87f, screenH * 0.560f, dividerPaint)

        // RESET（危険）・BACK
        drawActionButton(canvas, resetBtnRect, "RESET HIGH SCORES", cRed, 1.2f)
        drawActionButton(canvas, backBtnRect, "◀  BACK", cCyan, 1.8f)
    }

    private fun drawGlowTitle(canvas: Canvas, text: String, baseY: Float) {
        titlePaint.letterSpacing = 0.06f
        titleGlowPaint.letterSpacing = 0.06f
        titleShadowPaint.letterSpacing = 0.06f
        val x = (screenW - titlePaint.measureText(text)) / 2f
        val glowA = (sin(animTick * 0.04f) * 30 + 80).toInt().coerceIn(0, 255)
        titleGlowPaint.color = Color.argb(glowA, 70, 190, 255)
        canvas.drawText(text, x, baseY, titleGlowPaint)
        canvas.drawText(text, x, baseY + titlePaint.textSize * 0.025f, titleShadowPaint)
        canvas.drawText(text, x, baseY, titlePaint)
    }

    /** SFパネルの枠（グロー＋背景＋枠線＋コーナーブラケット）を描く。 */
    private fun drawFrame(canvas: Canvas, rect: RectF, accent: Int, pulse: Float) {
        val cut = rect.height() * 0.30f
        val path = UiKit.cutRectPath(rect, cut)
        glowPaint.color = Color.argb((85 * pulse).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, glowPaint)
        panelBgPaint.color = Color.argb(155, 10, 20, 40)
        canvas.drawPath(path, panelBgPaint)
        borderPaint.color = Color.argb((150 * pulse + 80).toInt().coerceIn(0, 255),
            Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, borderPaint)
        // コーナーブラケット（左上・右下）
        bracketPaint.color = accent
        val bl = rect.height() * 0.30f
        val ins = rect.height() * 0.18f
        canvas.drawLine(rect.left + ins, rect.top + ins, rect.left + ins + bl, rect.top + ins, bracketPaint)
        canvas.drawLine(rect.left + ins, rect.top + ins, rect.left + ins, rect.top + ins + bl, bracketPaint)
        canvas.drawLine(rect.right - ins, rect.bottom - ins, rect.right - ins - bl, rect.bottom - ins, bracketPaint)
        canvas.drawLine(rect.right - ins, rect.bottom - ins, rect.right - ins, rect.bottom - ins - bl, bracketPaint)
    }

    private fun drawToggle(canvas: Canvas, rect: RectF, label: String, isOn: Boolean, phase: Float) {
        val accent = if (isOn) cGreen else cDim
        val pulse = sin(animTick * 0.05f + phase) * 0.3f + 0.7f
        drawFrame(canvas, rect, accent, pulse)
        labelPaint.color = Color.argb(230, 200, 225, 255)
        val cy = rect.centerY() + labelPaint.textSize * 0.34f
        canvas.drawText(label, rect.left + rect.width() * 0.09f, cy, labelPaint)
        val status = if (isOn) "ON" else "OFF"
        statusPaint.color = if (isOn) cGreen else cRed
        canvas.drawText(status, rect.right - statusPaint.measureText(status) - rect.width() * 0.09f,
            rect.centerY() + statusPaint.textSize * 0.34f, statusPaint)
    }

    private fun drawActionButton(canvas: Canvas, rect: RectF, label: String, accent: Int, phase: Float) {
        val pulse = sin(animTick * 0.05f + phase) * 0.35f + 0.65f
        drawFrame(canvas, rect, accent, pulse)
        val textAlpha = (180 * pulse + 75).toInt().coerceIn(0, 255)
        btnTextPaint.color = Color.argb(textAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawText(label, (screenW - btnTextPaint.measureText(label)) / 2f,
            rect.centerY() + btnTextPaint.textSize * 0.34f, btnTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            when {
                bgmBtnRect.contains(tx, ty) -> { bgmOn = !bgmOn; AppPrefs.setBgmEnabled(context, bgmOn); invalidate() }
                sfxBtnRect.contains(tx, ty) -> { sfxOn = !sfxOn; AppPrefs.setSfxEnabled(context, sfxOn); invalidate() }
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
