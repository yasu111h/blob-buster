package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.sin
import kotlin.random.Random

/**
 * ゲームクリア画面の紙吹雪／花火風パーティクル演出。
 * 固定長配列を使い回し、画面下に抜けたら上から再出現させて常時表示する。
 */
class ClearCelebration(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    companion object {
        private const val CONFETTI_COUNT = 70
    }

    private val colors = intArrayOf(
        Color.parseColor("#FFD740"),  // ゴールド
        Color.parseColor("#00F5FF"),  // シアン
        Color.parseColor("#FF2D78"),  // マゼンタ
        Color.parseColor("#39FF14"),  // グリーン
        Color.parseColor("#FF8C00"),  // オレンジ
        Color.parseColor("#FFFFFF")   // ホワイト
    )

    private val x = FloatArray(CONFETTI_COUNT)
    private val y = FloatArray(CONFETTI_COUNT)
    private val vy = FloatArray(CONFETTI_COUNT)
    private val size = FloatArray(CONFETTI_COUNT)
    private val colorIdx = IntArray(CONFETTI_COUNT)
    private val phase = FloatArray(CONFETTI_COUNT)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = 0

    init {
        for (i in 0 until CONFETTI_COUNT) {
            x[i] = Random.nextFloat() * screenWidth
            y[i] = Random.nextFloat() * screenHeight
            vy[i] = screenHeight * (0.002f + Random.nextFloat() * 0.004f)
            size[i] = screenWidth * (0.006f + Random.nextFloat() * 0.010f)
            colorIdx[i] = Random.nextInt(colors.size)
            phase[i] = Random.nextFloat() * 6.2832f
        }
    }

    fun update() {
        frame++
        for (i in 0 until CONFETTI_COUNT) {
            y[i] += vy[i]
            // 画面下に抜けたら上から再出現
            if (y[i] > screenHeight + size[i] * 2f) {
                y[i] = -size[i] * 2f
                x[i] = Random.nextFloat() * screenWidth
                colorIdx[i] = Random.nextInt(colors.size)
            }
        }
    }

    fun draw(canvas: Canvas) {
        for (i in 0 until CONFETTI_COUNT) {
            // 左右にゆらゆら揺れる＋きらめき（アルファ脈動）
            val sway = sin(phase[i] + frame * 0.05f)
            val drawX = x[i] + sway * screenWidth * 0.02f
            val twinkle = (160 + sin(phase[i] * 2f + frame * 0.12f) * 80f).toInt().coerceIn(60, 255)
            paint.color = colors[colorIdx[i]]
            paint.alpha = twinkle
            // 揺れに合わせて横幅が変わる細長い矩形（紙吹雪らしい見た目）
            val halfW = size[i] * (0.35f + 0.65f * kotlin.math.abs(sway))
            val halfH = size[i]
            canvas.drawRect(drawX - halfW, y[i] - halfH, drawX + halfW, y[i] + halfH, paint)
        }
    }
}
