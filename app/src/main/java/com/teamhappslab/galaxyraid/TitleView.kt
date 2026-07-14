package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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
        // メニュー画面は30fpsで十分（星の流れ・明滅・ボタンの脈動しか動かない）。
        // 60fps(16ms)で全画面Bitmap＋グロー描画を回し続けると、放置時の発熱・電池消費が
        // 無駄に大きい。tickを2ずつ進めるのでアニメの速度は従来と変わらない。
        override fun run() {
            animTick += 2
            invalidate()
            handler.postDelayed(this, 33L)
        }
    }

    // 実際のアプリバージョン（versionName）に自動連動して表示
    private val versionText: String = try {
        "v" + (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "")
    } catch (e: Exception) { "" }

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

    // ── フォント（Saira 可変フォント。太字＝800 / UI＝600）。共有の UiKit.loadSaira に統一 ──
    private val titleTypeface: Typeface = UiKit.loadSaira(context, 800)
    private val uiTypeface: Typeface = UiKit.loadSaira(context, 600)

    // ホーム背景（地球の写真）。最背面に敷き、上に薄い暗幕を重ねて文字の可読性を確保する。
    private var bgScaled: Bitmap? = null
    private val scrimPaint = Paint()
    // 背景写真の「空の部分（地球より上）」だけに薄く重ねる動く星。惑星の上には出さない。
    private val starField = StarField()

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

    // Paints
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
        scoreLevelPaint.textSize  = w * 0.044f  // スコア値と同じサイズに統一
        scoreEmptyPaint.textSize  = w * 0.034f

        btnTextPaint.textSize = w * 0.052f
        iconPaint.textSize    = w * 0.052f
        versionPaint.textSize = w * 0.026f

        galaxyBaseY = h * 0.285f
        raidBaseY   = h * 0.392f
        titleTopY   = galaxyBaseY - galaxySize * 0.80f
        titleBotY   = raidBaseY + raidSize * 0.06f

        ringCx = w / 2f

        // ホーム背景（地球写真）を画面サイズにカバー配置（縦横比維持・はみ出しは中央クロップ）で
        // スケールして用意する。毎フレームの拡縮を避けるため1回だけ作る。
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val src = BitmapFactory.decodeResource(resources, R.drawable.title_bg, opts)
        bgScaled?.recycle()
        bgScaled = if (src != null) buildCoverBitmap(src, w, h).also { src.recycle() } else null

        // 文字を読みやすくする薄い暗幕（縦グラデ）。上部の銀河は見せ、日の出直下（サブコピー帯）と
        // 下端（ボタン）だけ少し落とす。alphaを上げるほど背景が暗く＝主張が弱くなる（調整用ノブ）。
        scrimPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.argb(60, 3, 7, 18),   // 上端
                Color.argb(30, 3, 7, 18),   // 上部：銀河を見せる（最も薄い）
                Color.argb(85, 3, 7, 18),   // 中央：サブコピー帯の可読性
                Color.argb(55, 3, 7, 18),   // 下部：スコア/ボタン域（各UIに自前の下地あり）
                Color.argb(110, 3, 7, 18)   // 下端：ボタン・バージョンを引き締める
            ),
            floatArrayOf(0f, 0.30f, 0.46f, 0.70f, 1f), Shader.TileMode.CLAMP
        )

        // 動く星を生成。地球の空（上部35%）だけに配置し、地平線手前で自然にフェードさせる
        starField.configure(w, h, 7L, 0.35f)

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
    }

    override fun onDraw(canvas: Canvas) {
        if (screenW == 0f) return

        // 背景（地球の写真＋可読性のための薄い暗幕）。未取得時は従来の単色でフォールバック。
        val bg = bgScaled
        if (bg != null) canvas.drawBitmap(bg, 0f, 0f, null)
        else canvas.drawColor(Color.parseColor("#080E1A"))
        canvas.drawRect(0f, 0f, screenW, screenH, scrimPaint)
        // 空（地球より上）にだけ動く星を薄く重ねる
        starField.draw(canvas, animTick)

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
        val taglineY = accentY + screenH * 0.040f
        canvas.drawText(tag, (screenW - taglinePaint.measureText(tag)) / 2f, taglineY, taglinePaint)

        // ベストスコア（サブコピーと重ならないよう余白を空けて配置）
        drawHighScores(canvas, taglineY + screenH * 0.030f)

        // ボタン
        drawButton(canvas, storyButtonRect, "BOSS MODE", "▶", Color.parseColor("#FF4557"), 0f)   // 赤
        drawButton(canvas, endlessButtonRect, if (endlessLoading) "NOW LOADING..." else "ENDLESS MODE",
            if (endlessLoading) null else "∞", Color.parseColor("#B06BFF"), 1.0f)                 // 紫
        drawButton(canvas, settingsButtonRect, "SETTINGS", "⚙", Color.parseColor("#88A6C4"), 2.0f)

        // ビネット
        canvas.drawRect(0f, 0f, screenW, screenH, vignettePaint)

        // バージョン（実際のversionNameに連動）
        canvas.drawText(versionText, screenW * 0.04f, screenH * 0.975f, versionPaint)
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

    /** srcを dstW×dstH にカバー配置（縦横比維持・はみ出しは中央クロップ）でスケールした不透明ビットマップを返す。 */
    private fun buildCoverBitmap(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val scale = maxOf(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val sw = src.width * scale
        val sh = src.height * scale
        val left = (dstW - sw) / 2f
        val top = (dstH - sh) / 2f
        c.drawBitmap(src, null, RectF(left, top, left + sw, top + sh), Paint(Paint.FILTER_BITMAP_FLAG))
        // 全面不透明なので合成ではなくコピー扱いにして貼付を高速化。
        // これがないと ARGB_8888 のまま毎フレーム全画面アルファブレンドされる
        // （ホーム画面は常時再描画しているため、その分がまるごと無駄になる）。
        out.setHasAlpha(false)
        return out
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

        // ラベル（アイコン＝システム字形 ＋ 単語＝Saira）。
        // 文字・アイコンは明滅させず常に明るい固定色にする（枠のグロー/ブラケットは脈動のまま）。
        btnTextPaint.color = accent
        btnTextPaint.alpha = 255
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
            // 1〜3位とも同じ明るさで表示
            val em = 235

            scoreRankPaint.alpha = em
            canvas.drawText(ranks[i], frame.left + frameW * 0.10f, ly, scoreRankPaint)

            val valText = "%,d".format(score)
            scoreValuePaint.alpha = em
            val valRight = frame.left + frameW * 0.66f
            canvas.drawText(valText, valRight - scoreValuePaint.measureText(valText), ly, scoreValuePaint)

            val lvlText = "Lv.${GameConfig.levelForScore(score)}"
            scoreLevelPaint.alpha = em
            canvas.drawText(lvlText, frame.left + frameW * 0.74f, ly, scoreLevelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && !storyLoading && !endlessLoading) {
            when {
                storyButtonRect.contains(event.x, event.y) -> {
                    // BOSS MODEはステージ選択画面へ即遷移（ローディング表示は出さない）
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
