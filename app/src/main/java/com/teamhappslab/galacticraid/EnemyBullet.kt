package com.teamhappslab.galacticraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * tint: 0=green(通常弾), 1=orange(高速弾), 2=red(DRAGON強攻撃弾)
 */
class EnemyBullet(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    private val screenWidth: Int,
    private val screenHeight: Int,
    val tint: Int = 0
) {
    val radius: Float = screenWidth * 0.018f
    var isDead: Boolean = false
    private var ttl: Int = 240  // 最大4秒（60fps）で強制消去

    companion object {
        // 丸1つ描画用（tint別）
        private val paints = arrayOfNulls<Paint>(3)

        fun initSharedPaints(screenWidth: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 255, 255) }
            paints[0] = paint
            paints[1] = paint
            paints[2] = paint
        }
    }

    fun update() {
        x += vx
        y += vy
        ttl--
        if (ttl <= 0 || y > screenHeight * 0.88f || y < -radius ||
            x < -radius || x > screenWidth + radius) {
            isDead = true
        }
    }

    fun draw(canvas: Canvas) {
        if (isDead) return
        val p = paints[tint] ?: paints[0] ?: return
        canvas.drawCircle(x, y, radius, p)
    }
}
