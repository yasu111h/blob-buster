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
 *
 * 持続時間は sweepAngle から自動計算:
 *   ≤10° → 消えない（画面外で自動消滅）
 *   ≤30° → 2.5秒（150f）
 *   ≤50° → 2.0秒（120f）
 *   ≤70° → 1.0秒（60f）
 *   100°+ → 1.0秒（60f）
 *
 * 色は統一（電気シアン）。フェードなし、時間切れで即消滅。
 */
class Shockwave(
    val cx: Float,
    val cy: Float,
    private val screenWidth: Int,
    screenHeight: Int,
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
    private var frameCount = 0

    // 角度に基づいて持続時間を自動計算
    private val durationFrames: Int = when {
        sweepAngle <= 10f -> Int.MAX_VALUE  // 消えない（画面外で自動消滅）
        sweepAngle <= 30f -> 150            // 2.5秒
        sweepAngle <= 50f -> 120            // 2.0秒
        sweepAngle <= 70f -> 60             // 1.0秒
        else              -> 60             // 1.0秒（100°以上）
    }

    // 統一カラー（電気シアン）
    private val r = 0; private val g = 245; private val b = 255  // #00F5FF

    private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun update() {
        radius += speed
        frameCount++
        if (durationFrames != Int.MAX_VALUE && frameCount >= durationFrames) isDead = true
        if (radius > maxRadius) isDead = true
    }

    /** プレイヤーが扇形の波面に触れているか */
    fun hitsPlayer(px: Float, py: Float, playerHitRadius: Float): Boolean {
        if (isDead) return false
        val dist = sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy))
        if (dist + playerHitRadius < radius - baseThickness) return false
        if (dist - playerHitRadius > radius + baseThickness) return false
        val playerAngle = Math.toDegrees(atan2((py - cy).toDouble(), (px - cx).toDouble())).toFloat()
        var diff = ((playerAngle - directionAngle) % 360f + 360f) % 360f
        if (diff > 180f) diff -= 360f
        return abs(diff) <= sweepAngle / 2f
    }

    fun draw(canvas: Canvas) {
        // 色は常に一定（フェードなし）。isDead になった瞬間に描画されなくなり急消滅。
        val alpha = 210
        val thickness = baseThickness

        val startAngle = directionAngle - sweepAngle / 2f
        val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        outerPaint.color = Color.argb(alpha, r, g, b)
        outerPaint.strokeWidth = thickness
        canvas.drawArc(oval, startAngle, sweepAngle, false, outerPaint)

        glowPaint.color = Color.argb(alpha / 3, r, g, b)
        glowPaint.strokeWidth = thickness * 2.2f
        val innerR = (radius - thickness * 0.2f).coerceAtLeast(0f)
        val innerOval = RectF(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        canvas.drawArc(innerOval, startAngle, sweepAngle, false, glowPaint)
    }
}
