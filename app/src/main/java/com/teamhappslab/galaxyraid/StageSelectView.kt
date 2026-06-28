package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * ストーリーモードのステージ選択画面。
 * 解放済みステージのみ選択可能（AppPrefsのクリア状況に連動）。
 */
class StageSelectView(context: Context) : View(context) {

    /** 解放済みステージがタップされたときに呼ばれる（引数=ステージ番号） */
    var onStageSelected: ((Int) -> Unit)? = null
    /** 戻るボタンがタップされたとき */
    var onBackTapped: (() -> Unit)? = null

    private var clearedStage = 0
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
    private val stageRects = arrayOfNulls<RectF>(StageConfig.MAX_STAGE)
    private var backRect = RectF()

    private data class Star(val x: Float, val y: Float, val r: Float, val baseAlpha: Int, val phase: Float)
    private val stars = mutableListOf<Star>()

    // ── Paints ──
    private val bgPaint = Paint().apply { color = Color.parseColor("#080E1A") }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(18, 64, 160, 255); style = Paint.Style.STROKE; strokeWidth = 1f
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); isFakeBoldText = true
    }
    private val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 120, 200, 255)
    }
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(210, 10, 26, 52) }
    private val cardLockedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 18, 20, 28) }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF"); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val cardClearedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88"); style = Paint.Style.STROKE; strokeWidth = 2.5f
    }
    private val cardLockedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 90, 100, 120); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val stageTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; isFakeBoldText = true
    }
    private val stageSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 150, 210, 255)
    }
    private val lockedTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 150, 160, 180); isFakeBoldText = true
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    private val backBtnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 120, 160, 200); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val backBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 150, 200, 240); isFakeBoldText = true
    }

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation() { handler.removeCallbacks(updateRunnable) }

    /** 表示更新（解放状況を読み直す） */
    fun refresh() {
        clearedStage = AppPrefs.getStoryClearedStage(context)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat(); screenH = h.toFloat()

        headerPaint.textSize = w * 0.075f
        headerSubPaint.textSize = w * 0.034f
        stageTitlePaint.textSize = w * 0.058f
        lockedTitlePaint.textSize = w * 0.058f
        stageSubPaint.textSize = w * 0.034f
        statusPaint.textSize = w * 0.040f
        backBtnTextPaint.textSize = w * 0.044f

        // ステージカードを縦に5枚
        val cardW = w * 0.84f
        val cardH = h * 0.105f
        val gap = h * 0.022f
        val firstTop = h * 0.20f
        for (i in 0 until StageConfig.MAX_STAGE) {
            val top = firstTop + (cardH + gap) * i
            stageRects[i] = RectF((w - cardW) / 2f, top, (w + cardW) / 2f, top + cardH)
        }

        val bw = w * 0.40f
        val bh = h * 0.062f
        backRect = RectF((w - bw) / 2f, h * 0.90f, (w + bw) / 2f, h * 0.90f + bh)

        val rng = Random(123)
        stars.clear()
        repeat(70) {
            stars.add(Star(
                x = rng.nextFloat() * w, y = rng.nextFloat() * h,
                r = rng.nextFloat() * 2.0f + 0.3f,
                baseAlpha = rng.nextInt(110) + 50, phase = rng.nextFloat() * 6.28f
            ))
        }
    }

    private fun isUnlocked(stage: Int) = stage <= maxOf(2, clearedStage + 1)
    private fun isCleared(stage: Int) = stage <= clearedStage

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        val gs = screenW * 0.12f
        var gx = 0f; while (gx <= screenW) { canvas.drawLine(gx, 0f, gx, screenH, gridPaint); gx += gs }
        var gy = 0f; while (gy <= screenH) { canvas.drawLine(0f, gy, screenW, gy, gridPaint); gy += gs }

        for (s in stars) {
            val a = (sin(animTick * 0.04f + s.phase) * 40 + s.baseAlpha).toInt().coerceIn(20, 255)
            starPaint.color = Color.argb(a, 200, 220, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }

        // ヘッダー
        val header = "STORY MODE"
        val hb = Rect(); headerPaint.getTextBounds(header, 0, header.length, hb)
        canvas.drawText(header, (screenW - hb.width()) / 2f, screenH * 0.11f, headerPaint)
        val sub = "— SELECT STAGE —"
        val sbnd = Rect(); headerSubPaint.getTextBounds(sub, 0, sub.length, sbnd)
        canvas.drawText(sub, (screenW - sbnd.width()) / 2f, screenH * 0.15f, headerSubPaint)

        // ステージカード
        for (i in 0 until StageConfig.MAX_STAGE) {
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
            if (unlocked) {
                canvas.drawText(titleText, tx, rect.centerY() - screenH * 0.005f, stageTitlePaint)
                canvas.drawText(subText, tx, rect.centerY() + screenH * 0.03f, stageSubPaint)
                // ステータス（右側）: CLEAR✓ または PLAY▶
                statusPaint.color = if (cleared) Color.parseColor("#00FF88") else Color.parseColor("#FFD740")
                val status = if (cleared) "CLEAR ✓" else "PLAY ▶"
                val stb = Rect(); statusPaint.getTextBounds(status, 0, status.length, stb)
                canvas.drawText(status, rect.right - stb.width() - screenW * 0.05f, rect.centerY() + stb.height() / 2f, statusPaint)
            } else {
                canvas.drawText(titleText, tx, rect.centerY() + screenH * 0.012f, lockedTitlePaint)
                // 🔒 LOCKED
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            if (backRect.contains(event.x, event.y)) {
                onBackTapped?.invoke()
                return true
            }
            for (i in 0 until StageConfig.MAX_STAGE) {
                val rect = stageRects[i] ?: continue
                if (rect.contains(event.x, event.y)) {
                    val stage = i + 1
                    if (isUnlocked(stage)) onStageSelected?.invoke(stage)
                    return true
                }
            }
        }
        return true
    }
}
