package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class Bullet(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    var x: Float = 0f
    var y: Float = 0f
    var prevX: Float = 0f   // 補間用: 前回更新時の位置
    var prevY: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var isDead: Boolean = true  // プールから取り出す前はdead扱い

    val radius: Float = screenWidth * 0.012f

    /** プールから再利用するときに呼ぶ */
    fun reset(newX: Float, newY: Float, newVx: Float, newVy: Float) {
        x = newX; y = newY; prevX = newX; prevY = newY; vx = newVx; vy = newVy
        isDead = false
    }

    fun update() {
        prevX = x; prevY = y
        x += vx
        y += vy
        if (y + radius < 0f || y - radius > screenHeight.toFloat() ||
            x + radius < 0f || x - radius > screenWidth.toFloat()) {
            isDead = true
        }
    }

    fun draw(canvas: Canvas, alpha: Float = 0f) {
        if (isDead) return
        val p = sharedPaint ?: return
        val ix = prevX + (x - prevX) * alpha
        val iy = prevY + (y - prevY) * alpha
        canvas.drawCircle(ix, iy, radius, p)
    }

    companion object {
        private var sharedPaint: Paint? = null

        fun initSharedPaints(@Suppress("UNUSED_PARAMETER") screenWidth: Int) {
            sharedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#40C4FF")  // プレイヤーと同じシアン
            }
        }
    }
}
