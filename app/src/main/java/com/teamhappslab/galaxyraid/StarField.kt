package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * どの画面でも背景の上に重ねて使える「動く星」レイヤー。背景そのものは描かない（＝透明）ので、
 * 写真背景でも単色背景でも、その上に薄く星を流せる。ホーム/設定/ステージ選択で共通利用する。
 *
 * 使い方:
 *   private val starField = StarField()
 *   onSizeChanged → starField.configure(w, h)          // 星を生成（1回）
 *   onDraw → 背景を描いた後に starField.draw(canvas, tick)  // 前面に星を重ねる
 * tick は 1フレームごとに +1 するアニメカウンタ（明滅・流れ・揺れに使う）。
 */
class StarField {
    private var w = 0
    private var h = 0

    private class St(
        val x: Float, val baseY: Float, val r: Float,
        val alphaBase: Int, val color: Int,
        val glow: Boolean, val twinkle: Boolean, val phase: Float,
        val speed: Float,  // 縦にゆっくり流れる量（px/フレーム。奥=遅い/手前=速い）
        val sway: Float    // 横方向のわずかな揺れ幅（px。0=揺れなし）
    )
    private val stars = ArrayList<St>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 星を生成する。seed を画面ごとに変えると、それぞれ別の星空になる。 */
    fun configure(width: Int, height: Int, seed: Long = 7L) {
        if (width <= 0 || height <= 0) return
        w = width; h = height
        val rng = Random(seed)
        val px = width / 1080f   // 画面幅基準で星サイズ・速度を端末非依存に
        stars.clear()
        fun add(count: Int, rMin: Float, rMax: Float, aMin: Int, aMax: Int, color: Int, glow: Boolean,
                twChance: Float, spMin: Float, spMax: Float, swayMax: Float) {
            repeat(count) {
                stars.add(St(
                    x = rng.nextFloat() * width,
                    baseY = rng.nextFloat() * height,
                    r = rMin + rng.nextFloat() * (rMax - rMin),
                    alphaBase = aMin + rng.nextInt((aMax - aMin).coerceAtLeast(1)),
                    color = color, glow = glow,
                    twinkle = rng.nextFloat() < twChance,
                    phase = rng.nextFloat() * 6.28f,
                    speed = (spMin + rng.nextFloat() * (spMax - spMin)) * px,
                    sway = rng.nextFloat() * swayMax * px
                ))
            }
        }
        // 遠景（多く・淡い・遅い）／中景／近景（少数・明るい・グローあり）＋ネオンの差し色を少量。
        // 写真の上に重ねる想定なので、以前の手描き背景より控えめの数・明るさにしている。
        add(60, 0.6f * px, 1.2f * px, 22, 60,  Color.rgb(190, 208, 235), false, 0.18f, 0.05f, 0.12f, 0f)
        add(26, 1.0f * px, 1.8f * px, 55, 120, Color.rgb(205, 224, 255), false, 0.30f, 0.10f, 0.22f, 3f)
        add(10, 1.8f * px, 2.8f * px, 110, 190, Color.rgb(232, 242, 255), true, 0.38f, 0.22f, 0.42f, 6f)
        val accent = intArrayOf(Color.rgb(90, 200, 255), Color.rgb(255, 120, 170), Color.rgb(255, 220, 130))
        repeat(4) {
            stars.add(St(
                x = rng.nextFloat() * width, baseY = rng.nextFloat() * height,
                r = (1.8f + rng.nextFloat() * 1.2f) * px,
                alphaBase = 120 + rng.nextInt(70),
                color = accent[rng.nextInt(accent.size)], glow = true,
                twinkle = rng.nextFloat() < 0.5f, phase = rng.nextFloat() * 6.28f,
                speed = (0.14f + rng.nextFloat() * 0.16f) * px,
                sway = (2f + rng.nextFloat() * 4f) * px
            ))
        }
    }

    /** 背景の上に星を描く（透明レイヤー）。tick はアニメカウンタ。 */
    fun draw(canvas: Canvas, tick: Int) {
        if (h <= 0) return
        val hf = h.toFloat()
        for (s in stars) {
            // ゆっくり縦に流れ、画面外へ出たら反対側へ回り込む（ループ）
            var sy = (s.baseY + tick * s.speed) % hf
            if (sy < 0f) sy += hf
            val sx = if (s.sway != 0f) s.x + sin(tick * 0.02f + s.phase) * s.sway else s.x
            val a = if (s.twinkle)
                (s.alphaBase * (0.65f + 0.35f * sin(tick * 0.05f + s.phase))).toInt().coerceIn(0, 255)
            else s.alphaBase
            if (s.glow) {
                glowPaint.color = s.color
                glowPaint.alpha = (a * 0.28f).toInt().coerceIn(0, 255)
                canvas.drawCircle(sx, sy, s.r * 2.4f, glowPaint)
            }
            starPaint.color = s.color
            starPaint.alpha = a
            canvas.drawCircle(sx, sy, s.r, starPaint)
        }
    }
}
