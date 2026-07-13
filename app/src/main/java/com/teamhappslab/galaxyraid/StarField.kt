package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * どの画面でも背景の上に重ねて使える「動く星」レイヤー。背景そのものは描かない（＝透明）。
 *
 * 星は画面の「上部の空だけ」に出せる（skyFraction）。地球写真のように下側が惑星の絵の場合、
 * 惑星の上に星が乗って不自然になるのを防ぐため、地平線の手前で自然にフェードして消す。
 *
 * 使い方:
 *   private val starField = StarField()
 *   onSizeChanged → starField.configure(w, h, seed, skyFraction)   // 星を生成（1回）
 *   onDraw → 背景を描いた後に starField.draw(canvas, tick)          // 前面に星を重ねる
 * tick は 1フレームごとに +1 するアニメカウンタ（明滅・流れ・揺れに使う）。
 */
class StarField {
    private var w = 0
    private var h = 0
    private var band = 0f       // 星が存在できる縦範囲（0〜band px）。地球より上の空だけに限定するのに使う。
    private var fadeStart = 0f  // この y を超えたら地平線に向けてフェード開始

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

    /**
     * 星を生成する。
     * @param seed 変えると配置が変わる（画面ごとに変えると別の星空になる）
     * @param skyFraction 星を出す縦範囲（画面高に対する割合）。1.0=全画面 / 0.35=上35%だけ（地球の空用）
     */
    fun configure(width: Int, height: Int, seed: Long = 7L, skyFraction: Float = 1.0f) {
        if (width <= 0 || height <= 0) return
        w = width; h = height
        band = height * skyFraction.coerceIn(0.05f, 1.0f)
        fadeStart = band * 0.72f   // 空の下端（地平線手前）でフェードして惑星に星が乗らないようにする
        val rng = Random(seed)
        val px = width / 1080f      // 画面幅基準で星サイズ・速度を端末非依存に
        stars.clear()
        fun add(count: Int, rMin: Float, rMax: Float, aMin: Int, aMax: Int, color: Int, glow: Boolean,
                twChance: Float, spMin: Float, spMax: Float, swayMax: Float) {
            repeat(count) {
                stars.add(St(
                    x = rng.nextFloat() * width,
                    baseY = rng.nextFloat() * band,   // ★空の範囲内にだけ配置
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
        // 上部の空だけに載せる前提なので数は控えめ。数値を変えれば密度・明るさ・速さを調整できる。
        add(40, 0.6f * px, 1.2f * px, 22, 60,  Color.rgb(190, 208, 235), false, 0.18f, 0.05f, 0.12f, 0f)
        add(16, 1.0f * px, 1.8f * px, 55, 120, Color.rgb(205, 224, 255), false, 0.30f, 0.10f, 0.22f, 3f)
        add(6,  1.8f * px, 2.6f * px, 100, 175, Color.rgb(232, 242, 255), true, 0.38f, 0.18f, 0.34f, 5f)
        val accent = intArrayOf(Color.rgb(90, 200, 255), Color.rgb(255, 120, 170), Color.rgb(255, 220, 130))
        repeat(3) {
            stars.add(St(
                x = rng.nextFloat() * width, baseY = rng.nextFloat() * band,
                r = (1.7f + rng.nextFloat() * 1.1f) * px,
                alphaBase = 115 + rng.nextInt(65),
                color = accent[rng.nextInt(accent.size)], glow = true,
                twinkle = rng.nextFloat() < 0.5f, phase = rng.nextFloat() * 6.28f,
                speed = (0.12f + rng.nextFloat() * 0.14f) * px,
                sway = (2f + rng.nextFloat() * 4f) * px
            ))
        }
    }

    /** 背景の上に星を描く（透明レイヤー）。tick はアニメカウンタ。 */
    fun draw(canvas: Canvas, tick: Int) {
        if (band <= 0f) return
        for (s in stars) {
            // 空の範囲内でゆっくり縦に流れ、下端（地平線手前）に達したら上へ回り込む
            var sy = (s.baseY + tick * s.speed) % band
            if (sy < 0f) sy += band
            val sx = if (s.sway != 0f) s.x + sin(tick * 0.02f + s.phase) * s.sway else s.x
            var a = if (s.twinkle)
                (s.alphaBase * (0.65f + 0.35f * sin(tick * 0.05f + s.phase))).toInt().coerceIn(0, 255)
            else s.alphaBase
            // 地平線手前でフェードアウト（惑星の上に星がはっきり出ないように）
            if (sy > fadeStart) {
                val t = ((band - sy) / (band - fadeStart)).coerceIn(0f, 1f)
                a = (a * t).toInt()
            }
            if (a <= 0) continue
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
