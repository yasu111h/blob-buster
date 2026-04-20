package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 衝撃波（ショックウェーブ）攻撃。
 * 発生源 (cx, cy) から外側に広がるリング状の波。
 * プレイヤーが波面に触れるとダメージ。
 */
class Shockwave(
    val cx: Float,
    val cy: Float,
    private val screenWidth: Int,
    screenHeight: Int,
    baseColor: Int
) {
    private val maxRadius = hypot(screenWidth.toDouble(), screenHeight.toDouble()).toFloat()
    private val speed = screenWidth * 0.015f
    private val baseThickness = screenWidth * 0.038f

    var radius = 0f
    var isDead = false

    private val r = Color.red(baseColor)
    private val g = Color.green(baseColor)
    private val b = Color.blue(baseColor)

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun update() {
        radius += speed
        if (radius > maxRadius) isDead = true
    }

    /** プレイヤー (px, py, playerHitRadius) が波面に触れているか */
    fun hitsPlayer(px: Float, py: Float, playerHitRadius: Float): Boolean {
        if (isDead) return false
        val dist = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
        return dist + playerHitRadius > radius - baseThickness &&
               dist - playerHitRadius < radius + baseThickness
    }

    fun draw(canvas: Canvas) {
        val progress = radius / maxRadius
        val alpha = ((1f - progress) * 210).toInt().coerceIn(0, 255)
        val thickness = baseThickness * (1f - progress * 0.45f)

        // 外縁（本体リング）
        outerPaint.color = Color.argb(alpha, r, g, b)
        outerPaint.strokeWidth = thickness
        canvas.drawCircle(cx, cy, radius, outerPaint)

        // 内側グロー
        glowPaint.color = Color.argb(alpha / 3, r, g, b)
        glowPaint.strokeWidth = thickness * 2.2f
        canvas.drawCircle(cx, cy, radius - thickness * 0.2f, glowPaint)
    }
}
