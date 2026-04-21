package com.teamhappslab.galacticraid

/**
 * Bulletのオブジェクトプール。
 * 毎フレームnew Bullet()するのをやめ、使い終わった弾を再利用することでGCを抑制する。
 */
class BulletPool(
    private val screenWidth: Int,
    private val screenHeight: Int,
    initialSize: Int = 30
) {
    private val available = ArrayDeque<Bullet>(initialSize)

    init {
        // ゲーム開始前にあらかじめプールを満たしておく（プレウォーム）
        repeat(initialSize) {
            available.addLast(Bullet(screenWidth, screenHeight))
        }
    }

    /** プールからBulletを取り出して初期化して返す */
    fun obtain(x: Float, y: Float, vx: Float, vy: Float): Bullet {
        val bullet = available.removeLastOrNull() ?: Bullet(screenWidth, screenHeight)
        bullet.reset(x, y, vx, vy)
        return bullet
    }

    /** 使い終わったBulletをプールに返す */
    fun recycle(bullet: Bullet) {
        bullet.isDead = true
        available.addLast(bullet)
    }
}
