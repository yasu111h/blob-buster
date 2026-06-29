package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TitleView(context: Context) : View(context) {

    var onStoryModeTapped: (() -> Unit)? = null
    var onEndlessModeTapped: (() -> Unit)? = null
    var onSettingsTapped: (() -> Unit)? = null

    private var storyLoading = false
    private var endlessLoading = false
    private var animTick = 0
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            animTick++
            invalidate()
            handler.postDelayed(this, 16L)
        }
    }

    private var highScores: List<Int> = listOf(0, 0, 0)

    fun updateHighScores(scores: List<Int>) {
        highScores = scores
        invalidate()
    }

    fun resetLoading() {
        storyLoading = false
        endlessLoading = false
        invalidate()
    }

    // ── フォント（Saira 可変フォント。太字＝800 / UI＝600） ──────────────
    private val titleTypeface: Typeface = loadSaira(800)
    private val uiTypeface: Typeface = loadSaira(600)
    private fun loadSaira(weight: Int): Typeface = try {
        Typeface.Builder(context.assets, "fonts/saira_var.ttf")
            .setFontVariationSettings("'wght' $weight")
            .build() ?: Typeface.createFromAsset(context.assets, "fonts/saira_var.ttf")
    } catch (e: Exception) {
        try { Typeface.createFromAsset(context.assets, "fonts/saira_var.ttf") }
        catch (e2: Exception) { Typeface.DEFAULT_BOLD }
    }

    private data class Star(val x: Float, val y: Float, val r: Float, val baseAlpha: Int, val phase: Float)
    private val stars = mutableListOf<Star>()

    private var storyButtonRect    = RectF()
    private var endlessButtonRect  = RectF()
    private var settingsButtonRect = RectF()
    private var screenW = 0f
    private var screenH = 0f

    // タイトル幾何（onSizeChangedで確定）
    private var galaxySize = 0f
    private var raidSize = 0f
    private var galaxyBaseY = 0f
    private var raidBaseY = 0f
    private var titleTopY = 0f
    private var titleBotY = 0f
    private var ringCx = 0f
    private var ringCy = 0f
    private var ringR = 0f

    // Paints
    private val bgPaint = Paint()
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = Color.argb(40, 120, 200, 255)
        pathEffect = DashPathEffect(floatArrayOf(6f, 14f), 0f)
    }
    private val ringTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(110, 140, 215, 255)
    }
    private val heroStarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // タイトル
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface }
    private val titleGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = titleTypeface }
    private val titleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; color = Color.argb(170, 6, 26, 52)
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(150, 130, 175, 215)
    }

    // ハイスコア
    private val scoreFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.6f; color = Color.argb(110, 70, 200, 255)
    }
    private val scoreFrameBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 8, 22, 44)
    }
    private val scoreHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(170, 120, 205, 255)
    }
    private val scoreRankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = titleTypeface; color = Color.argb(220, 90, 200, 255)
    }
    private val scoreValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(230, 215, 240, 255)
    }
    private val scoreLevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(210, 150, 230, 170)
    }
    private val scoreEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(90, 110, 160, 205)
    }

    // ボタン
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val btnBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.6f; strokeCap = Paint.Cap.SQUARE
    }
    private val btnGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f
        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
    }
    private val btnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = uiTypeface }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    private val versionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = uiTypeface; color = Color.argb(70, 100, 150, 200)
    }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun startAnimation() { handler.post(updateRunnable) }
    fun stopAnimation() { handler.removeCallbacks(updateRunnable) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenW = w.toFloat()
        screenH = h.toFloat()

        galaxySize = w * 0.150f
        raidSize   = w * 0.170f
        titlePaint.textSize = galaxySize
        titleGlowPaint.textSize = galaxySize
        titleShadowPaint.textSize = galaxySize
        taglinePaint.textSize = w * 0.030f

        scoreHeaderPaint.textSize = w * 0.030f
        scoreRankPaint.textSize   = w * 0.040f
        scoreValuePaint.textSize  = w * 0.044f
        scoreLevelPaint.textSize  = w * 0.032f
        scoreEmptyPaint.textSize  = w * 0.034f

        btnTextPaint.textSize = w * 0.052f
        iconPaint.textSize    = w * 0.052f
        versionPaint.textSize = w * 0.026f

        galaxyBaseY = h * 0.285f
        raidBaseY   = h * 0.392f
        titleTopY   = galaxyBaseY - galaxySize * 0.80f
        titleBotY   = raidBaseY + raidSize * 0.06f

        ringCx = w / 2f
        ringCy = (titleTopY + titleBotY) / 2f
        ringR  = w * 0.46f

        // 背景の縦グラデ
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, screenH,
            intArrayOf(Color.parseColor("#03040A"), Color.parseColor("#070A16"), Color.parseColor("#0B0A18")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )

        // 中央上から差す光の柱
        beamPaint.shader = LinearGradient(
            0f, 0f, 0f, screenH * 0.50f,
            intArrayOf(Color.argb(75, 130, 205, 255), Color.argb(22, 110, 180, 240), Color.argb(0, 100, 170, 230)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )

        // メタリックなタイトル塗り（上＝明るい→中央ハイライト→下＝濃いブルー）
        titlePaint.shader = LinearGradient(
            0f, titleTopY, 0f, titleBotY,
            intArrayOf(
                Color.parseColor("#D9ECFF"),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#CFEBFF"),
                Color.parseColor("#74B8E8"),
                Color.parseColor("#235F96")
            ),
            floatArrayOf(0f, 0.40f, 0.52f, 0.74f, 1f), Shader.TileMode.CLAMP
        )
        titleGlowPaint.maskFilter = BlurMaskFilter(raidSize * 0.16f, BlurMaskFilter.Blur.NORMAL)
        heroGlowPaint.maskFilter = BlurMaskFilter(w * 0.03f, BlurMaskFilter.Blur.NORMAL)

        // ビネット
        vignettePaint.shader = RadialGradient(
            w / 2f, h * 0.46f, maxOf(w, h) * 0.72f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 0)),
            floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )

        val bw = w * 0.62f
        val bh = h * 0.062f
        val gap = h * 0.016f
        val storyTop = h * 0.690f
        storyButtonRect    = RectF((w - bw) / 2f, storyTop, (w + bw) / 2f, storyTop + bh)
        endlessButtonRect  = RectF((w - bw) / 2f, storyButtonRect.bottom + gap, (w + bw) / 2f, storyButtonRect.bottom + gap + bh)
        settingsButtonRect = RectF((w - bw) / 2f, endlessButtonRect.bottom + gap, (w + bw) / 2f, endlessButtonRect.bottom + gap + bh)

        val rng = Random(99)
        stars.clear()
        repeat(70) {
            stars.add(Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                r = rng.nextFloat() * 1.8f + 0.3f,
                baseAlpha = rng.nextInt(110) + 40,
                phase = rng.nextFloat() * 6.28f
            ))
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        // 背景
        canvas.drawRect(0f, 0f, screenW, screenH, bgPaint)

        // 光の柱（台形）
        val beam = Path().apply {
            moveTo(screenW / 2f - screenW * 0.055f, 0f)
            lineTo(screenW / 2f + screenW * 0.055f, 0f)
            lineTo(screenW / 2f + screenW * 0.17f, screenH * 0.50f)
            lineTo(screenW / 2f - screenW * 0.17f, screenH * 0.50f)
            close()
        }
        canvas.drawPath(beam, beamPaint)

        // 星
        for (s in stars) {
            val a = (sin(animTick * 0.04f + s.phase) * 40 + s.baseAlpha).toInt().coerceIn(15, 255)
            starPaint.color = Color.argb(a, 205, 225, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }

        // HUDリング
        canvas.drawCircle(ringCx, ringCy, ringR, ringPaint)
        // 左右のブラケット目盛り
        for (side in listOf(-1f, 1f)) {
            val bx = ringCx + side * ringR
            canvas.drawLine(bx, ringCy - ringR * 0.10f, bx, ringCy + ringR * 0.10f, ringTickPaint)
            canvas.drawLine(bx - side * ringR * 0.05f, ringCy - ringR * 0.10f, bx, ringCy - ringR * 0.10f, ringTickPaint)
            canvas.drawLine(bx - side * ringR * 0.05f, ringCy + ringR * 0.10f, bx, ringCy + ringR * 0.10f, ringTickPaint)
        }
        // 上部の短いセグメント目盛り
        for (k in -2..2) {
            val ang = (-90f + k * 16f) * Math.PI.toFloat() / 180f
            val r0 = ringR + 4f; val r1 = ringR + 12f
            canvas.drawLine(
                ringCx + cos(ang) * r0, ringCy + sin(ang) * r0,
                ringCx + cos(ang) * r1, ringCy + sin(ang) * r1, ringTickPaint
            )
        }

        // 主役の星（十字フレア）
        val heroX = ringCx
        val heroY = galaxyBaseY - galaxySize * 0.95f
        val heroPulse = sin(animTick * 0.06f) * 0.25f + 0.75f
        heroGlowPaint.color = Color.argb((150 * heroPulse).toInt(), 150, 215, 255)
        canvas.drawCircle(heroX, heroY, screenW * 0.018f * heroPulse, heroGlowPaint)
        heroStarPaint.color = Color.argb(235, 235, 248, 255)
        heroStarPaint.strokeWidth = 2f; heroStarPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(heroX, heroY - screenH * 0.028f, heroX, heroY + screenH * 0.028f, heroStarPaint)
        canvas.drawLine(heroX - screenW * 0.045f, heroY, heroX + screenW * 0.045f, heroY, heroStarPaint)
        canvas.drawCircle(heroX, heroY, screenW * 0.008f, heroStarPaint)

        // タイトル
        val glowA = (sin(animTick * 0.04f) * 30 + 80).toInt().coerceIn(0, 255)
        drawTitleLine(canvas, "GALAXY", galaxyBaseY, galaxySize, 0.04f, glowA)
        drawTitleLine(canvas, "RAID", raidBaseY, raidSize, 0.08f, glowA)

        // アクセント線（中央が明るいピンク）
        val accentY = raidBaseY + raidSize * 0.20f
        val ax0 = screenW * 0.30f; val ax1 = screenW * 0.70f
        accentPaint.shader = LinearGradient(
            ax0, 0f, ax1, 0f,
            intArrayOf(Color.argb(0, 255, 60, 130), Color.argb(255, 255, 90, 150),
                Color.argb(255, 255, 60, 130), Color.argb(255, 255, 90, 150), Color.argb(0, 255, 60, 130)),
            floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawLine(ax0, accentY, ax1, accentY, accentPaint)

        // サブコピー
        taglinePaint.letterSpacing = 0.18f
        val tag = "SURVIVE THE GALAXY RAID"
        canvas.drawText(tag, (screenW - taglinePaint.measureText(tag)) / 2f, accentY + screenH * 0.040f, taglinePaint)

        // ベストスコア
        drawHighScores(canvas, raidBaseY + raidSize * 0.55f)

        // ボタン
        drawButton(canvas, storyButtonRect, if (storyLoading) "NOW LOADING..." else "BOSS MODE",
            if (storyLoading) null else "▶", Color.parseColor("#3FC4FF"), 0f)
        drawButton(canvas, endlessButtonRect, if (endlessLoading) "NOW LOADING..." else "ENDLESS",
            if (endlessLoading) null else "∞", Color.parseColor("#FF4F90"), 1.0f)
        drawButton(canvas, settingsButtonRect, "SETTINGS", "⚙", Color.parseColor("#88A6C4"), 2.0f)

        // ビネット
        canvas.drawRect(0f, 0f, screenW, screenH, vignettePaint)

        // バージョン
        canvas.drawText("v0.1.0", screenW * 0.04f, screenH * 0.975f, versionPaint)
    }

    private fun drawTitleLine(canvas: Canvas, text: String, baseY: Float, size: Float, spacing: Float, glowA: Int) {
        titlePaint.textSize = size
        titleGlowPaint.textSize = size
        titleShadowPaint.textSize = size
        titlePaint.letterSpacing = spacing
        titleGlowPaint.letterSpacing = spacing
        titleShadowPaint.letterSpacing = spacing
        val x = (screenW - titlePaint.measureText(text)) / 2f
        // 発光
        titleGlowPaint.color = Color.argb(glowA, 70, 190, 255)
        canvas.drawText(text, x, baseY, titleGlowPaint)
        // 下エッジの陰（金属の立体感）
        canvas.drawText(text, x, baseY + size * 0.025f, titleShadowPaint)
        // 本体（メタリックグラデ）
        canvas.drawText(text, x, baseY, titlePaint)
    }

    /** 角を斜めにカットした八角形パス */
    private fun cutRectPath(r: RectF, cut: Float): Path = Path().apply {
        moveTo(r.left + cut, r.top)
        lineTo(r.right - cut, r.top)
        lineTo(r.right, r.top + cut)
        lineTo(r.right, r.bottom - cut)
        lineTo(r.right - cut, r.bottom)
        lineTo(r.left + cut, r.bottom)
        lineTo(r.left, r.bottom - cut)
        lineTo(r.left, r.top + cut)
        close()
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, icon: String?, accent: Int, phaseOffset: Float) {
        val cut = rect.height() * 0.32f
        val path = cutRectPath(rect, cut)
        val pulse = sin(animTick * 0.05f + phaseOffset).toFloat() * 0.35f + 0.65f

        // 外周グロー
        btnGlowPaint.color = Color.argb((90 * pulse).toInt(),
            Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, btnGlowPaint)

        // 背景
        btnBgPaint.color = Color.argb(150, 10, 20, 40)
        canvas.drawPath(path, btnBgPaint)

        // 枠線
        btnBorderPaint.color = Color.argb((150 * pulse + 80).toInt().coerceIn(0, 255),
            Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(path, btnBorderPaint)

        // コーナーブラケット
        btnBracketPaint.color = accent
        val bl = rect.height() * 0.30f
        val ins = rect.height() * 0.16f
        // 左上
        canvas.drawLine(rect.left + ins, rect.top + ins, rect.left + ins + bl, rect.top + ins, btnBracketPaint)
        canvas.drawLine(rect.left + ins, rect.top + ins, rect.left + ins, rect.top + ins + bl, btnBracketPaint)
        // 右下
        canvas.drawLine(rect.right - ins, rect.bottom - ins, rect.right - ins - bl, rect.bottom - ins, btnBracketPaint)
        canvas.drawLine(rect.right - ins, rect.bottom - ins, rect.right - ins, rect.bottom - ins - bl, btnBracketPaint)

        // ラベル（アイコン＝システム字形 ＋ 単語＝Saira）
        val textAlpha = (180 * pulse + 75).toInt().coerceIn(0, 255)
        btnTextPaint.color = Color.argb(textAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        btnTextPaint.letterSpacing = 0.14f
        iconPaint.color = btnTextPaint.color
        val wordW = btnTextPaint.measureText(label)
        val gap = if (icon != null) rect.width() * 0.03f else 0f
        val iconW = if (icon != null) iconPaint.measureText(icon) else 0f
        val totalW = iconW + gap + wordW
        var cx = (screenW - totalW) / 2f
        val cy = rect.centerY() + btnTextPaint.textSize * 0.34f
        if (icon != null) {
            canvas.drawText(icon, cx, cy, iconPaint)
            cx += iconW + gap
        }
        canvas.drawText(label, cx, cy, btnTextPaint)
    }

    private fun drawHighScores(canvas: Canvas, topY: Float) {
        val frameW = screenW * 0.66f
        val rowH = screenH * 0.040f
        val padV = screenH * 0.018f
        val hasAny = highScores.any { it > 0 }
        val rows = if (hasAny) 3 else 1
        val frameH = padV * 2 + screenH * 0.028f + rowH * rows
        val frame = RectF((screenW - frameW) / 2f, topY, (screenW + frameW) / 2f, topY + frameH)
        val path = cutRectPath(frame, frame.height() * 0.16f)
        canvas.drawPath(path, scoreFrameBgPaint)
        canvas.drawPath(path, scoreFramePaint)

        scoreHeaderPaint.letterSpacing = 0.22f
        val header = "BEST SCORES"
        canvas.drawText(header, (screenW - scoreHeaderPaint.measureText(header)) / 2f,
            frame.top + padV + screenH * 0.020f, scoreHeaderPaint)

        if (!hasAny) {
            val empty = "No records yet"
            canvas.drawText(empty, (screenW - scoreEmptyPaint.measureText(empty)) / 2f,
                frame.top + padV + screenH * 0.020f + rowH, scoreEmptyPaint)
            return
        }

        val ranks = listOf("#1", "#2", "#3")
        val baseY0 = frame.top + padV + screenH * 0.028f + rowH * 0.8f
        for (i in 0..2) {
            val score = highScores.getOrElse(i) { 0 }
            if (score <= 0) continue
            val ly = baseY0 + rowH * i
            val em = if (i == 0) 255 else if (i == 1) 205 else 165

            scoreRankPaint.alpha = em
            canvas.drawText(ranks[i], frame.left + frameW * 0.10f, ly, scoreRankPaint)

            val valText = "%,d".format(score)
            scoreValuePaint.alpha = em
            val valRight = frame.left + frameW * 0.66f
            canvas.drawText(valText, valRight - scoreValuePaint.measureText(valText), ly, scoreValuePaint)

            val lvlText = "Lv.${GameConfig.levelForScore(score)}"
            scoreLevelPaint.alpha = (em * 0.9f).toInt()
            canvas.drawText(lvlText, frame.left + frameW * 0.74f, ly, scoreLevelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !storyLoading && !endlessLoading) {
            when {
                storyButtonRect.contains(event.x, event.y) -> {
                    storyLoading = true
                    invalidate()
                    onStoryModeTapped?.invoke()
                }
                endlessButtonRect.contains(event.x, event.y) -> {
                    endlessLoading = true
                    invalidate()
                    onEndlessModeTapped?.invoke()
                }
                settingsButtonRect.contains(event.x, event.y) -> {
                    onSettingsTapped?.invoke()
                }
            }
        }
        return true
    }
}
