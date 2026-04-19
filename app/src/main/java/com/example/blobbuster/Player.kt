package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.sqrt

class Player(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = screenWidth / 2f
    val y: Float = screenHeight * 0.90f
    val width: Float = screenWidth * 0.08f
    private val maxSpeed: Float = screenWidth * 0.018f
    private val shootCooldownMax: Int = 10
    private var shootCooldown: Int = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
    }

    /** ドラッグによる移動。deltaXはフレーム間の指の移動量 */
    fun move(deltaX: Float) {
        x += deltaX.coerceIn(-maxSpeed, maxSpeed)
        x = x.coerceIn(width / 2f, screenWidth - width / 2f)
    }

    fun update() {
        if (shootCooldown > 0) shootCooldown--
    }

    /** 指定座標に向けて弾を発射。クールダウン中はnullを返す */
    fun shoot(targetX: Float, targetY: Float): Bullet? {
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
        return Bullet(startX, startY, screenWidth, screenHeight, vx, vy)
    }

    fun draw(canvas: Canvas, invincible: Boolean, frameCount: Int) {
        if (invincible && frameCount % 6 < 3) return

        val halfW = width / 2f
        val h = width * 1.2f

        val path = Path().apply {
            moveTo(x, y - h)
            lineTo(x - halfW, y)
            lineTo(x + halfW, y)
            close()
        }
        canvas.drawPath(path, paint)
    }
}

enum class Direction {
    LEFT, RIGHT, NONE
}
