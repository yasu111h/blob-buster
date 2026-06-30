package com.teamhappslab.galaxyraid

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

/**
 * メニュー画面（タイトル/設定/ボス選択）共通の宇宙背景。
 * 背景色＋ネオン星雲を起動時に1枚の不透明Bitmapへ焼き、毎フレームは等倍で貼るだけ＝軽量。
 * その上に多層（サイズ・色・明るさ違い）の星を明滅つきで描く。
 *
 * 使い方: onSizeChangedで configure(w,h,seed) → onDrawで draw(canvas, tick)。
 */
class SpaceBackground {

    private var w = 0
    private var h = 0
    private var bg: Bitmap? = null

    private class S(
        val x: Float, val y: Float, val r: Float,
        val alphaBase: Int, val color: Int,
        val glow: Boolean, val twinkle: Boolean, val phase: Float
    )
    private val stars = ArrayList<S>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun configure(width: Int, height: Int, seed: Long = 7L) {
        if (width <= 0 || height <= 0) return
        w = width; h = height
        bg?.recycle()
        bg = buildBg(width, height)
        buildStars(width, height, seed)
    }

    private fun buildBg(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fw = w.toFloat(); val fh = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // 縦グラデの暗い宇宙
        p.shader = LinearGradient(
            0f, 0f, 0f, fh,
            intArrayOf(Color.parseColor("#04060E"), Color.parseColor("#080E1A"), Color.parseColor("#0B0A18")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, fh, p)
        // 左上：薄シアン星雲
        p.shader = RadialGradient(
            fw * 0.22f, fh * 0.18f, fw * 0.85f,
            intArrayOf(Color.argb(38, 64, 196, 255), Color.argb(13, 40, 120, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, fh, p)
        // 右下：薄マゼンタ星雲
        p.shader = RadialGradient(
            fw * 0.82f, fh * 0.82f, fw * 0.9f,
            intArrayOf(Color.argb(30, 255, 64, 129), Color.argb(11, 150, 40, 90), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, fh, p)
        // 中央やや上：ごく淡い青紫で奥行き
        p.shader = RadialGradient(
            fw * 0.5f, fh * 0.40f, fw * 0.6f,
            intArrayOf(Color.argb(18, 90, 80, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, fw, fh, p)
        p.shader = null
        bmp.setHasAlpha(false)   // 不透明＝コピー扱いで貼付が速い
        return bmp
    }

    private fun buildStars(w: Int, h: Int, seed: Long) {
        val rng = Random(seed)
        val px = w / 1080f
        stars.clear()
        fun add(count: Int, rMin: Float, rMax: Float, aMin: Int, aMax: Int, color: Int, glow: Boolean, twChance: Float) {
            repeat(count) {
                stars.add(S(
                    x = rng.nextFloat() * w,
                    y = rng.nextFloat() * h,
                    r = rMin + rng.nextFloat() * (rMax - rMin),
                    alphaBase = aMin + rng.nextInt((aMax - aMin).coerceAtLeast(1)),
                    color = color, glow = glow,
                    twinkle = rng.nextFloat() < twChance,
                    phase = rng.nextFloat() * 6.28f
                ))
            }
        }
        // 遠景・中景・近景・アクセント
        add(55, 0.6f * px, 1.2f * px, 30, 80, Color.rgb(180, 200, 230), false, 0.15f)
        add(30, 1.0f * px, 1.9f * px, 80, 150, Color.rgb(200, 222, 255), false, 0.3f)
        add(10, 1.9f * px, 3.0f * px, 150, 220, Color.rgb(232, 242, 255), true, 0.35f)
        val accent = intArrayOf(Color.rgb(64, 196, 255), Color.rgb(255, 80, 140), Color.rgb(255, 215, 96))
        repeat(5) {
            stars.add(S(
                x = rng.nextFloat() * w, y = rng.nextFloat() * h,
                r = (2.0f + rng.nextFloat() * 1.4f) * px,
                alphaBase = 130 + rng.nextInt(70),
                color = accent[rng.nextInt(accent.size)], glow = true,
                twinkle = rng.nextFloat() < 0.5f, phase = rng.nextFloat() * 6.28f
            ))
        }
    }

    /** @param tick アニメーションカウンタ（明滅に使用） */
    fun draw(canvas: Canvas, tick: Int) {
        val b = bg
        if (b != null) canvas.drawBitmap(b, 0f, 0f, null)
        else { starPaint.color = Color.parseColor("#080E1A"); canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), starPaint) }
        for (s in stars) {
            val a = if (s.twinkle)
                (s.alphaBase * (0.7f + 0.3f * sin(tick * 0.05f + s.phase))).toInt().coerceIn(0, 255)
            else s.alphaBase
            if (s.glow) {
                glowPaint.color = s.color
                glowPaint.alpha = (a * 0.28f).toInt().coerceIn(0, 255)
                canvas.drawCircle(s.x, s.y, s.r * 2.4f, glowPaint)
            }
            starPaint.color = s.color
            starPaint.alpha = a
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
    }

    fun recycle() {
        bg?.recycle(); bg = null
    }
}
