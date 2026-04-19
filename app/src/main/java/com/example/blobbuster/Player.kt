package com.example.blobbuster

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class Player(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = screenWidth / 2f
    var y: Float = screenHeight * 0.90f
    val width: Float = screenWidth * 0.08f
    private val maxSpeed: Float = screenWidth * 0.018f
    private val shootCooldownMax: Int = 5
    private var shootCooldown: Int = 0
    var bulletLevel: Int = 1
        private set
    val playerRadius get() = width * 1.5f

    // バレットレベルタイマー（1以上ならカウントダウン中、0なら非アクティブ）
    private var bulletLevelTimer: Int = 0
    private val level3Duration: Int = 360  // lv3: 6秒 @ 60fps
    private val level5Duration: Int = 150  // lv5: 2.5秒 @ 60fps（早めに終わる）

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
    }

    fun move(deltaX: Float) {
        x += deltaX.coerceIn(-maxSpeed, maxSpeed)
        x = x.coerceIn(width / 2f, screenWidth - width / 2f)
    }

    fun update() {
        if (shootCooldown > 0) shootCooldown--
        // バレットレベルタイマー処理
        if (bulletLevelTimer > 0) {
            bulletLevelTimer--
            if (bulletLevelTimer == 0) {
                when (bulletLevel) {
                    5 -> { bulletLevel = 3; bulletLevelTimer = level3Duration }  // 5→3、タイマー再起動
                    3 -> { bulletLevel = 1 }                                      // 3→1、終了
                }
            }
        }
    }

    /** アイテム取得時に呼ぶ */
    fun increaseBulletLevel() {
        when (bulletLevel) {
            1 -> { bulletLevel = 3; bulletLevelTimer = level3Duration }
            3 -> { bulletLevel = 5; bulletLevelTimer = level5Duration }
            5 -> { bulletLevelTimer = level5Duration }  // タイマーリセットのみ
        }
    }

    /** bulletLevelに応じて弾数を変える。クールダウン中は空リストを返す */
    fun shootSpread(targetX: Float, targetY: Float, pool: BulletPool): List<Bullet> {
        if (shootCooldown > 0) return emptyList()
        shootCooldown = shootCooldownMax

        val startX = x
        val startY = y - width / 2f
        val dx = targetX - startX
        val dy = targetY - startY
        if (dx == 0f && dy == 0f) return emptyList()

        val baseAngle = atan2(dy, dx)
        val speed = screenHeight * 0.025f

        val offsets = when (bulletLevel) {
            1    -> floatArrayOf(0f)
            3    -> floatArrayOf(-0.26f, 0f, 0.26f)
            else -> floatArrayOf(-0.56f, -0.26f, 0f, 0.26f, 0.56f)
        }

        return offsets.map { offset ->
            val angle = baseAngle + offset
            pool.obtain(startX, startY, cos(angle) * speed, sin(angle) * speed)
        }
    }

    fun draw(canvas: Canvas, invincible: Boolean, frameCount: Int) {
        if (invincible && frameCount % 6 < 3) return
        canvas.drawCircle(x, y, width / 2f, bodyPaint)
    }
}

enum class Direction {
    LEFT, RIGHT, NONE
}
