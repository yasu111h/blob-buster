package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sqrt

class Player(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = screenWidth / 2f
    val y: Float = screenHeight * 0.90f
    val width: Float = screenWidth * 0.08f
    private val maxSpeed: Float = screenWidth * 0.018f
    private val shootCooldownMax: Int = 5
    private var shootCooldown: Int = 0

    // 外側グロー
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 64, 196, 255)
    }
    // 翼
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#006699")
    }
    private val wingEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(64, 128, 170, 255)
        style = Paint.Style.STROKE
    }
    // メインボディ
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
    }
    // ボディエッジハイライト
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
    }
    // コックピット
    private val cockpitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 220, 245, 255)
    }
    // エンジン外側グロー
    private val engineOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 110, 0)
    }
    // エンジン内側コア
    private val engineCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCC44")
    }

    fun move(deltaX: Float) {
        x += deltaX.coerceIn(-maxSpeed, maxSpeed)
        x = x.coerceIn(width / 2f, screenWidth - width / 2f)
    }

    fun update() {
        if (shootCooldown > 0) shootCooldown--
    }

    fun shoot(targetX: Float, targetY: Float, pool: BulletPool): Bullet? {
        if (shootCooldown > 0) return null
        shootCooldown = shootCooldownMax

        val startX = x
        val startY = y - width / 2f
        val dx = targetX - startX
        val dy = targetY - startY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return null

        val speed = screenHeight * 0.025f
        val vx = (dx / dist) * speed
        val vy = (dy / dist) * speed
        return pool.obtain(startX, startY, vx, vy)
    }

    fun draw(canvas: Canvas, invincible: Boolean, frameCount: Int) {
        if (invincible && frameCount % 6 < 3) return

        val hw = width / 2f
        val h = width * 1.4f

        // --- 外側グロー ---
        val glowPath = Path().apply {
            moveTo(x, y - h * 1.15f)
            lineTo(x - hw * 2.5f, y + hw * 0.4f)
            lineTo(x + hw * 2.5f, y + hw * 0.4f)
            close()
        }
        canvas.drawPath(glowPath, outerGlowPaint)

        // --- 翼（ボディの後ろに描画）---
        wingEdgePaint.strokeWidth = hw * 0.06f
        val leftWing = Path().apply {
            moveTo(x - hw * 0.45f, y - h * 0.22f)
            lineTo(x - hw * 2.0f, y - h * 0.04f)
            lineTo(x - hw * 1.5f, y + hw * 0.12f)
            lineTo(x - hw * 0.65f, y)
            close()
        }
        canvas.drawPath(leftWing, wingPaint)
        canvas.drawPath(leftWing, wingEdgePaint)

        val rightWing = Path().apply {
            moveTo(x + hw * 0.45f, y - h * 0.22f)
            lineTo(x + hw * 2.0f, y - h * 0.04f)
            lineTo(x + hw * 1.5f, y + hw * 0.12f)
            lineTo(x + hw * 0.65f, y)
            close()
        }
        canvas.drawPath(rightWing, wingPaint)
        canvas.drawPath(rightWing, wingEdgePaint)

        // --- メインボディ ---
        edgePaint.strokeWidth = hw * 0.06f
        val bodyPath = Path().apply {
            moveTo(x, y - h)                         // ノーズ先端
            lineTo(x - hw * 0.55f, y - h * 0.48f)   // 左肩
            lineTo(x - hw * 0.72f, y)                // 左底
            lineTo(x - hw * 0.22f, y - h * 0.08f)   // 底左くびれ
            lineTo(x + hw * 0.22f, y - h * 0.08f)   // 底右くびれ
            lineTo(x + hw * 0.72f, y)                // 右底
            lineTo(x + hw * 0.55f, y - h * 0.48f)   // 右肩
            close()
        }
        canvas.drawPath(bodyPath, bodyPaint)
        canvas.drawPath(bodyPath, edgePaint)

        // --- コックピット ---
        canvas.drawOval(
            RectF(x - hw * 0.24f, y - h * 0.83f, x + hw * 0.24f, y - h * 0.46f),
            cockpitPaint
        )

        // --- エンジングロー ---
        canvas.drawCircle(x, y + hw * 0.06f, hw * 0.42f, engineOuterPaint)
        canvas.drawCircle(x, y + hw * 0.02f, hw * 0.18f, engineCorePaint)
    }
}

enum class Direction {
    LEFT, RIGHT, NONE
}
