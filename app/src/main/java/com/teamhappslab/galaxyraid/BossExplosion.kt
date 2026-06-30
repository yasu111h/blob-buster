package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * ボス撃破時専用の爆発エフェクト。
 *
 * - burst(x, y) を呼ぶたびに「拡大しながらフェードする円（オレンジ→黄→白）」＋
 *   飛散パーティクルを発生させる
 * - 毎フレームのallocを抑えるため、パーティクル・リングは固定長配列を
 *   リングバッファとして使い回す
 */
class BossExplosion(screenWidth: Int, screenHeight: Int) {

    companion object {
        private const val MAX_PARTICLES = 96
        private const val MAX_RINGS = 10
        private const val PARTICLES_PER_BURST = 10
        private const val RING_LIFE = 26       // リングの寿命（フレーム）
        private const val PARTICLE_LIFE = 34   // パーティクルの寿命（フレーム）
    }

    // ── パーティクル（固定長配列・使い回し） ──────────────
    private val px = FloatArray(MAX_PARTICLES)
    private val py = FloatArray(MAX_PARTICLES)
    private val pvx = FloatArray(MAX_PARTICLES)
    private val pvy = FloatArray(MAX_PARTICLES)
    private val pLife = IntArray(MAX_PARTICLES)
    private val pSize = FloatArray(MAX_PARTICLES)
    private var pIdx = 0

    // ── 拡大円（固定長配列・使い回し） ────────────────────
    private val rx = FloatArray(MAX_RINGS)
    private val ry = FloatArray(MAX_RINGS)
    private val rLife = IntArray(MAX_RINGS)
    private var rIdx = 0

    private val baseSpeed = screenHeight * 0.006f
    private val ringMaxR = screenWidth * 0.16f
    private val particleBaseSize = screenWidth * 0.012f

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** (x, y) に爆発を1回発生させる */
    fun burst(x: Float, y: Float) {
        // 拡大円
        rx[rIdx] = x; ry[rIdx] = y; rLife[rIdx] = RING_LIFE
        rIdx = (rIdx + 1) % MAX_RINGS
        // 飛散パーティクル
        repeat(PARTICLES_PER_BURST) {
            val a = Random.nextFloat() * 6.2832f
            val sp = baseSpeed * (0.5f + Random.nextFloat() * 1.5f)
            px[pIdx] = x; py[pIdx] = y
            pvx[pIdx] = cos(a) * sp; pvy[pIdx] = sin(a) * sp
            pLife[pIdx] = PARTICLE_LIFE
            pSize[pIdx] = particleBaseSize * (0.6f + Random.nextFloat())
            pIdx = (pIdx + 1) % MAX_PARTICLES
        }
    }

    fun update() {
        for (i in 0 until MAX_PARTICLES) {
            if (pLife[i] <= 0) continue
            px[i] += pvx[i]; py[i] += pvy[i]
            pvx[i] *= 0.96f; pvy[i] *= 0.96f
            pLife[i]--
        }
        for (i in 0 until MAX_RINGS) {
            if (rLife[i] > 0) rLife[i]--
        }
    }

    fun draw(canvas: Canvas) {
        // 拡大円（オレンジ→黄→白に変化しながら拡大・フェード）
        for (i in 0 until MAX_RINGS) {
            if (rLife[i] <= 0) continue
            val progress = 1f - rLife[i].toFloat() / RING_LIFE   // 0→1
            val alpha = ((1f - progress) * 220).toInt().coerceIn(0, 255)
            ringPaint.color = fireColor(progress, alpha)
            val radius = ringMaxR * (0.25f + 0.75f * progress)
            canvas.drawCircle(rx[i], ry[i], radius, ringPaint)
        }
        // 飛散パーティクル
        for (i in 0 until MAX_PARTICLES) {
            if (pLife[i] <= 0) continue
            val progress = 1f - pLife[i].toFloat() / PARTICLE_LIFE  // 0→1
            val alpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
            particlePaint.color = fireColor(progress, alpha)
            canvas.drawCircle(px[i], py[i], pSize[i] * (1f - progress * 0.5f), particlePaint)
        }
    }

    /** 進行度に応じてオレンジ→黄→白に変化する炎カラー */
    private fun fireColor(t: Float, alpha: Int): Int = when {
        t < 0.33f -> Color.argb(alpha, 255, 110, 30)   // オレンジ
        t < 0.66f -> Color.argb(alpha, 255, 215, 64)   // 黄
        else      -> Color.argb(alpha, 255, 255, 255)  // 白
    }
}
