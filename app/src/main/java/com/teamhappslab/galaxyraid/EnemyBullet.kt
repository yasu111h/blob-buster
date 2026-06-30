package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * tint: 0=通常弾, 1=高速弾, 2=強攻撃弾, 3=ボス扇弾（現状はすべて白で描画）
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
        // 丸1つ描画用（tint別。現状はすべて同じ白）
        private val paints = arrayOfNulls<Paint>(4)

        fun initSharedPaints(screenWidth: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 255, 255, 255) }
            paints[0] = paint
            paints[1] = paint
            paints[2] = paint
            paints[3] = paint
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
        val safeTint = tint.coerceIn(0, paints.size - 1)
        val p = paints[safeTint] ?: paints[0] ?: return
        canvas.drawCircle(x, y, radius, p)
    }
}
