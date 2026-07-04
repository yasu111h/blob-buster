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
    var onResetBossProgress: (() -> Unit)? = null

    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { animTick++; invalidate(); handler.postDelayed(this, 16L) }
    }

    private var screenW = 0f
    private var screenH = 0f

    private var bgmBtnRect       = RectF()
    private var sfxBtnRect       = RectF()
    private var resetBtnRect     = RectF()
    private var resetBossBtnRect = RectF()
    private var backBtnRect      = RectF()

    private var bgmOn = true
    private var sfxOn = true

    // ── アプリ内確認モーダル（OSのAlertDialogを使わずここで描画。バーの出入り＝画面ずれを防ぐ） ──
    private var confirmVisible = false
    private var confirmDone = false            // YES実行後の「完了」表示中か
    private var confirmMessage = ""
    private var confirmAction: (() -> Unit)? = null
    private var confirmYesRect = RectF()
    private var confirmCancelRect = RectF()
    private var confirmOkRect = RectF()        // 完了表示中のOKボタン
    private val dimPaint = Paint()

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
        bgmBtnRect       = RectF(x, h * 0.345f, x + bw, h * 0.345f + bh)
        sfxBtnRect       = RectF(x, h * 0.450f, x + bw, h * 0.450f + bh)
        resetBtnRect     = RectF(x, h * 0.600f, x + bw, h * 0.600f + bh)
        resetBossBtnRect = RectF(x, h * 0.705f, x + bw, h * 0.705f + bh)
        backBtnRect      = RectF(x, h * 0.840f, x + bw, h * 0.840f + bh)
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

        canvas.drawLine(screenW * 0.13f, screenH * 0.555f, screenW * 0.87f, screenH * 0.555f, dividerPaint)

        // RESET（危険）・BACK
        drawActionButton(canvas, resetBtnRect, "RESET HIGH SCORES", cRed, 1.2f)
        drawActionButton(canvas, resetBossBtnRect, "RESET BOSS PROGRESS", cRed, 1.5f)
        drawActionButton(canvas, backBtnRect, "◀  BACK", cCyan, 1.8f)

        // 確認モーダル（最前面）
        if (confirmVisible) drawConfirmModal(canvas)
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

    /** アプリ内確認モーダルを開く。 */
    private fun showConfirm(message: String, action: () -> Unit) {
        confirmMessage = message
        confirmAction = action
        confirmVisible = true
        confirmDone = false
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // モーダル表示中はモーダルのタップだけを処理し、下のボタンには触れさせない
        if (confirmVisible) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val tx = event.x; val ty = event.y
                if (confirmDone) {
                    // 「完了」表示中：OKで閉じる
                    if (confirmOkRect.contains(tx, ty)) {
                        confirmVisible = false; confirmDone = false; invalidate()
                    }
                } else when {
                    confirmYesRect.contains(tx, ty) -> {
                        // 実行してから「完了」表示に切り替える（モーダルは閉じない）
                        confirmAction?.invoke()
                        confirmAction = null
                        confirmDone = true
                        invalidate()
                    }
                    confirmCancelRect.contains(tx, ty) -> {
                        confirmVisible = false; confirmAction = null; invalidate()
                    }
                }
            }
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val tx = event.x; val ty = event.y
            when {
                bgmBtnRect.contains(tx, ty) -> { bgmOn = !bgmOn; AppPrefs.setBgmEnabled(context, bgmOn); invalidate() }
                sfxBtnRect.contains(tx, ty) -> { sfxOn = !sfxOn; AppPrefs.setSfxEnabled(context, sfxOn); invalidate() }
                resetBtnRect.contains(tx, ty) -> showConfirm("Reset all high scores?") {
                    HighScoreManager.resetScores(context)
                    onResetScores?.invoke()
                }
                resetBossBtnRect.contains(tx, ty) -> showConfirm("Reset boss mode progress?") {
                    AppPrefs.resetStoryProgress(context)
                    onResetBossProgress?.invoke()
                }
                backBtnRect.contains(tx, ty) -> onBack?.invoke()
            }
        }
        return true
    }

    /** 確認モーダルの描画（暗幕＋SFパネル＋メッセージ＋YES/CANCEL）。 */
    private fun drawConfirmModal(canvas: Canvas) {
        // 暗幕
        dimPaint.color = Color.argb(205, 2, 6, 14)
        canvas.drawRect(0f, 0f, screenW, screenH, dimPaint)

        // パネル
        val pw = screenW * 0.82f
        val ph = screenH * 0.26f
        val px = (screenW - pw) / 2f
        val py = (screenH - ph) / 2f
        val panel = RectF(px, py, px + pw, py + ph)
        drawFrame(canvas, panel, if (confirmDone) cGreen else cCyan, 1f)

        // メッセージ（labelPaintを一時的に小さくして中央1行で表示）
        val msg = if (confirmDone) "RESET COMPLETE" else confirmMessage
        val prevSize = labelPaint.textSize
        labelPaint.textSize = screenW * 0.040f
        labelPaint.color = if (confirmDone) cGreen else Color.argb(235, 210, 230, 255)
        canvas.drawText(msg, (screenW - labelPaint.measureText(msg)) / 2f, py + ph * 0.34f, labelPaint)
        labelPaint.textSize = prevSize

        val bh = ph * 0.30f
        val by = py + ph * 0.58f
        if (confirmDone) {
            // 完了：中央にOKボタン1つ
            val okw = pw * 0.42f
            confirmOkRect = RectF(px + (pw - okw) / 2f, by, px + (pw + okw) / 2f, by + bh)
            drawModalButton(canvas, confirmOkRect, "OK", cGreen)
        } else {
            // YES / CANCEL ボタン
            val bw = pw * 0.38f
            val gapHalf = pw * 0.04f
            confirmCancelRect = RectF(px + pw * 0.5f - gapHalf - bw, by, px + pw * 0.5f - gapHalf, by + bh)
            confirmYesRect = RectF(px + pw * 0.5f + gapHalf, by, px + pw * 0.5f + gapHalf + bw, by + bh)
            drawModalButton(canvas, confirmCancelRect, "CANCEL", cCyan)
            drawModalButton(canvas, confirmYesRect, "YES", cRed)
        }
    }

    /** モーダル内ボタン（枠内中央にラベル）。 */
    private fun drawModalButton(canvas: Canvas, rect: RectF, label: String, accent: Int) {
        drawFrame(canvas, rect, accent, 1f)
        btnTextPaint.color = accent
        canvas.drawText(label,
            rect.centerX() - btnTextPaint.measureText(label) / 2f,
            rect.centerY() + btnTextPaint.textSize * 0.34f, btnTextPaint)
    }
}
