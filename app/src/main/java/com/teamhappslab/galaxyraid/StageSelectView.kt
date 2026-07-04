package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

/**
 * ボスモード（ストーリー）のステージ選択画面。
 * 解放済みステージのみ選択可能（AppPrefsのクリア状況に連動）。
 */
class StageSelectView(context: Context) : View(context) {

    /** 解放済みステージがタップされたときに呼ばれる（引数=ステージ番号） */
    var onStageSelected: ((Int) -> Unit)? = null
    /** 戻るボタンがタップされたとき */
    var onBackTapped: (() -> Unit)? = null

    private var clearedStage = 0
    private var loadingStage = 0   // ローディング中のステージ番号（0=なし）
    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() { animTick++; invalidate(); handler.postDelayed(this, 16L) }
    }

    private var screenW = 0f
    private var screenH = 0f
    private val stageRects = arrayOfNulls<RectF>(StageConfig.MAX_STAGE)
    private var backRect = RectF()

    private val space = SpaceBackground()
    private val titleTypeface = UiKit.loadSaira(context, 800)
    private val uiTypeface = UiKit.loadSaira(context, 600)

    // ── タイトル ──
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface; letterSpacing = 0.04f }
    private val titleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface; letterSpacing = 0.04f }
    private val titleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; letterSpacing = 0.04f; color = Color.argb(170, 40, 6, 10)
    }
    private val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(225, 255, 150, 70); letterSpacing = 0.18f
    }

    // ── カード ──
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 10, 26, 52) }
    private val cardLockedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 18, 20, 28) }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val cardClearedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34E29B"); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val cardLockedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 90, 100, 120); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val stageTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; color = Color.WHITE; letterSpacing = 0.04f
    }
    private val stageSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(190, 150, 210, 255); letterSpacing = 0.10f
    }
    private val lockedTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; color = Color.argb(120, 150, 160, 180); letterSpacing = 0.04f
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiTypeface; letterSpacing = 0.06f }
    private val backBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 120, 160, 200); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val backBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(210, 150, 200, 240); letterSpacing = 0.12f
    }

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation() { handler.removeCallbacks(updateRunnable) }

    /** 表示更新（解放状況を読み直す） */
    fun refresh() {
        clearedStage = AppPrefs.getStoryClearedStage(context)
        loadingStage = 0
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat(); screenH = h.toFloat()
        space.configure(w, h, 53L)

        titlePaint.textSize = w * 0.095f
        titleGlowPaint.textSize = w * 0.095f
        titleShadowPaint.textSize = w * 0.095f
        titleGlowPaint.maskFilter = BlurMaskFilter(w * 0.022f, BlurMaskFilter.Blur.NORMAL)
        taglinePaint.textSize = w * 0.032f
        stageTitlePaint.textSize = w * 0.056f
        lockedTitlePaint.textSize = w * 0.056f
        stageSubPaint.textSize = w * 0.032f
        statusPaint.textSize = w * 0.040f
        backBtnTextPaint.textSize = w * 0.044f

        // タイトル「BOSS MODE」のメタリック塗り（赤み寄り＝危険な雰囲気）
        titlePaint.shader = LinearGradient(
            0f, h * 0.055f, 0f, h * 0.105f,
            intArrayOf(Color.parseColor("#FFE6EC"), Color.WHITE, Color.parseColor("#FFD6DE"),
                Color.parseColor("#FF7A98"), Color.parseColor("#C42A45")),
            floatArrayOf(0f, 0.40f, 0.52f, 0.74f, 1f), Shader.TileMode.CLAMP
        )

        val cardW = w * 0.84f
        val cardH = h * 0.092f
        val gap = h * 0.016f
        val firstTop = h * 0.195f
        for (i in 0 until StageConfig.MAX_STAGE) {
            val top = firstTop + (cardH + gap) * i
            stageRects[i] = RectF((w - cardW) / 2f, top, (w + cardW) / 2f, top + cardH)
        }

        val bw = w * 0.40f
        val bh = h * 0.062f
        backRect = RectF((w - bw) / 2f, h * 0.90f, (w + bw) / 2f, h * 0.90f + bh)
    }

    private fun isUnlocked(stage: Int) = stage <= maxOf(2, clearedStage + 1)
    private fun isCleared(stage: Int) = stage <= clearedStage

    /** FINAL STAGE（最終ステージ）は直前のSTAGE5をクリアするまで表示しない。 */
    private val finalStageUnlocked get() = clearedStage >= StageConfig.MAX_STAGE - 1
    /** 画面に表示するステージ数（FINAL STAGEは解放後にのみ出現させる）。 */
    private val visibleStageCount get() = if (finalStageUnlocked) StageConfig.MAX_STAGE else StageConfig.MAX_STAGE - 1

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        space.draw(canvas, animTick)

        // ── タイトル「BOSS MODE」＋ミッションタグライン ──
        drawTitle(canvas, "BOSS MODE", screenH * 0.095f)
        taglinePaint.alpha = 255   // 明滅させず常に明るく固定
        val tag = "⚠  ENGAGE THE GUARDIANS  ⚠"
        canvas.drawText(tag, (screenW - taglinePaint.measureText(tag)) / 2f, screenH * 0.140f, taglinePaint)

        // ステージカード（FINAL STAGEはSTAGE5クリアまで非表示）
        for (i in 0 until visibleStageCount) {
            val stage = i + 1
            val rect = stageRects[i] ?: continue
            val unlocked = isUnlocked(stage)
            val cleared = isCleared(stage)

            canvas.drawRoundRect(rect, 18f, 18f, if (unlocked) cardBgPaint else cardLockedBgPaint)
            val border = when {
                cleared -> cardClearedBorderPaint
                unlocked -> cardBorderPaint
                else -> cardLockedBorderPaint
            }
            if (unlocked && !cleared) {
                val pulse = sin(animTick * 0.06f + i) * 0.3f + 0.7f
                border.alpha = (150 * pulse + 80).toInt().coerceIn(0, 255)
            } else border.alpha = 255
            canvas.drawRoundRect(rect, 18f, 18f, border)

            val titleText = StageConfig.titleOf(stage)
            val subText = StageConfig.subtitleOf(stage)
            val tx = rect.left + screenW * 0.06f
            if (unlocked && loadingStage == stage) {
                statusPaint.color = Color.parseColor("#FFD740")
                val status = "NOW LOADING..."
                val stb = Rect(); statusPaint.getTextBounds(status, 0, status.length, stb)
                canvas.drawText(status, rect.centerX() - stb.width() / 2f, rect.centerY() + stb.height() / 2f, statusPaint)
            } else if (unlocked) {
                canvas.drawText(titleText, tx, rect.centerY() - screenH * 0.005f, stageTitlePaint)
                canvas.drawText(subText, tx, rect.centerY() + screenH * 0.03f, stageSubPaint)
                // ステータス（右側）: CLEAR✓ / ENGAGE▶
                statusPaint.color = if (cleared) Color.parseColor("#34E29B") else Color.parseColor("#FFD740")
                val status = if (cleared) "CLEAR ✓" else "ENGAGE ▶"
                val stb = Rect(); statusPaint.getTextBounds(status, 0, status.length, stb)
                canvas.drawText(status, rect.right - stb.width() - screenW * 0.05f, rect.centerY() + stb.height() / 2f, statusPaint)
            } else {
                canvas.drawText(titleText, tx, rect.centerY() + screenH * 0.012f, lockedTitlePaint)
                statusPaint.color = Color.argb(150, 150, 160, 180)
                val status = "🔒 LOCKED"
                val stb = Rect(); statusPaint.getTextBounds(status, 0, status.length, stb)
                canvas.drawText(status, rect.right - stb.width() - screenW * 0.05f, rect.centerY() + stb.height() / 2f, statusPaint)
            }
        }

        // 戻るボタン
        canvas.drawRoundRect(backRect, 16f, 16f, cardLockedBgPaint)
        canvas.drawRoundRect(backRect, 16f, 16f, backBtnBorderPaint)
        val backText = "◀  BACK"
        val bb = Rect(); backBtnTextPaint.getTextBounds(backText, 0, backText.length, bb)
        canvas.drawText(backText, backRect.centerX() - bb.width() / 2f, backRect.centerY() + bb.height() / 2f, backBtnTextPaint)
    }

    private fun drawTitle(canvas: Canvas, text: String, baseY: Float) {
        val x = (screenW - titlePaint.measureText(text)) / 2f
        val glowA = (sin(animTick * 0.05f) * 35 + 90).toInt().coerceIn(0, 255)
        titleGlowPaint.color = Color.argb(glowA, 255, 70, 110)
        canvas.drawText(text, x, baseY, titleGlowPaint)
        canvas.drawText(text, x, baseY + titlePaint.textSize * 0.025f, titleShadowPaint)
        canvas.drawText(text, x, baseY, titlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (loadingStage != 0) return true
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (backRect.contains(event.x, event.y)) {
                onBackTapped?.invoke()
                return true
            }
            for (i in 0 until visibleStageCount) {
                val rect = stageRects[i] ?: continue
                if (rect.contains(event.x, event.y)) {
                    val stage = i + 1
                    if (isUnlocked(stage)) {
                        loadingStage = stage
                        invalidate()
                        handler.postDelayed({ onStageSelected?.invoke(stage) }, 500L)
                    }
                    return true
                }
            }
        }
        return true
    }
}
