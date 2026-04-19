package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

class Player(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = screenWidth / 2f
    val y: Float = screenHeight * 0.90f
    val width: Float = screenWidth * 0.08f
    private val speed: Float = screenWidth * 0.012f
    private val shootInterval: Int = 15
    private var shootCooldown: Int = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
    }

    fun update(direction: Direction) {
        when (direction) {
            Direction.LEFT -> x -= speed
            Direction.RIGHT -> x += speed
            Direction.NONE -> { /* no move */ }
        }
        // 画面端クランプ
        x = x.coerceIn(width / 2f, screenWidth - width / 2f)

        if (shootCooldown > 0) shootCooldown--
    }

    fun tryShoot(): Bullet? {
        if (shootCooldown <= 0) {
            shootCooldown = shootInterval
            return Bullet(x, y - width / 2f, screenWidth, screenHeight)
        }
        return null
    }

    fun draw(canvas: Canvas, invincible: Boolean, frameCount: Int) {
        // 無敵中は点滅（偶数フレームのみ描画）
        if (invincible && frameCount % 6 < 3) return

        val halfW = width / 2f
        val height = width * 1.2f

        val path = Path().apply {
            moveTo(x, y - height)          // 上頂点
            lineTo(x - halfW, y)           // 左下
            lineTo(x + halfW, y)           // 右下
            close()
        }
        canvas.drawPath(path, paint)
    }
}

enum class Direction {
    LEFT, RIGHT, NONE
}
