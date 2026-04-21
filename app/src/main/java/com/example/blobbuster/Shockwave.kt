package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 衝撃波（扇形）攻撃。
 * 発生源 (cx, cy) から directionAngle 方向へ、sweepAngle 度の扇形に広がる。
 * プレイヤーが波面（角度 + 距離）に触れるとダメージ。
 */
class Shockwave(
    val cx: Float,
    val cy: Float,
    private val screenWidth: Int,
    screenHeight: Int,
    baseColor: Int,
    /** 扇の中心方向（Androidキャンバス座標系: 東=0, 時計回り, degrees） */
    private val directionAngle: Float,
    /** 扇の総開き角度（degrees）。中心から ±sweepAngle/2 の範囲に判定あり */
    private val sweepAngle: Float
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

    /** プレイヤーが扇形の波面に触れているか */
    fun hitsPlayer(px: Float, py: Float, playerHitRadius: Float): Boolean {
        if (isDead) return false
        // 距離チェック
        val dist = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
        if (dist + playerHitRadius < radius - baseThickness) return false
        if (dist - playerHitRadius > radius + baseThickness) return false
        // 角度チェック（プレイヤーが扇の角度内にいるか）
        val playerAngle = Math.toDegrees(atan2((py - cy).toDouble(), (px - cx).toDouble())).toFloat()
        var diff = ((playerAngle - directionAngle) % 360f + 360f) % 360f
        if (diff > 180f) diff -= 360f
        return abs(diff) <= sweepAngle / 2f
    }

    fun draw(canvas: Canvas) {
        val progress = radius / maxRadius
        val alpha = ((1f - progress) * 210).toInt().coerceIn(0, 255)
        val thickness = baseThickness * (1f - progress * 0.45f)

        val startAngle = directionAngle - sweepAngle / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        // 外縁（扇形アーク）
        outerPaint.color = Color.argb(alpha, r, g, b)
        outerPaint.strokeWidth = thickness
        canvas.drawArc(oval, startAngle, sweepAngle, false, outerPaint)

        // 内側グロー（少し太め・薄い）
        glowPaint.color = Color.argb(alpha / 3, r, g, b)
        glowPaint.strokeWidth = thickness * 2.2f
        val innerOval = RectF(cx - (radius - thickness * 0.2f), cy - (radius - thickness * 0.2f),
                              cx + (radius - thickness * 0.2f), cy + (radius - thickness * 0.2f))
        canvas.drawArc(innerOval, startAngle, sweepAngle, false, glowPaint)
    }
}
