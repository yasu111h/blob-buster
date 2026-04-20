package com.example.blobbuster

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    companion object {
        private var playerBitmap: Bitmap? = null

        fun initBitmap(context: Context, playerWidth: Float) {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.player_ship)
            val size = playerWidth.toInt().coerceAtLeast(4)
            playerBitmap = Bitmap.createScaledBitmap(raw, size, size, true)
            raw.recycle()
        }
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
        if (invincible && frameCount % 4 < 2) return
        val bmp = playerBitmap
        if (bmp != null) {
            val half = width / 2f
            canvas.drawBitmap(bmp, null, RectF(x - half, y - half, x + half, y + half), bitmapPaint)
        } else {
            canvas.drawCircle(x, y, width / 2f, bodyPaint)
        }
    }
}

enum class Direction {
    LEFT, RIGHT, NONE
}
