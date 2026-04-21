package com.teamhappslab.galacticraid

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
import kotlin.math.sqrt
import kotlin.random.Random

private class BlobPaints(val body: Paint) {
    companion object {
        fun create(size: BlobSize): BlobPaints =
            BlobPaints(body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = size.color() })
    }
}

class Blob(
    var cx: Float,
    var cy: Float,
    val size: BlobSize,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val speedMult: Float = 1f,         // BlobManagerから渡される速度倍率
    private val attackIntervalMult: Float = 1f  // BlobManagerから渡される攻撃間隔倍率
) {
    val radius: Float = size.radius(screenWidth) * size.displayScale()
    var hp: Int = size.maxHp()
    private val maxHp: Int = size.maxHp()
    var isDead: Boolean = false
    private var flashTimer: Int = 0

    private val moveSpeed: Float = size.speed(screenHeight) * speedMult
    private val moveType: MoveType = size.moveType()

    // ZIGZAGアニメーション用
    private var zigzagAngle: Float = Random.nextFloat() * 6.28f

    // RANDOM移動用
    private var randomDirX: Float = (Random.nextFloat() * 2f - 1f)
    private var randomDirY: Float = 0.5f + Random.nextFloat() * 0.5f
    private var randomTimer: Int = Random.nextInt(60)

    // 攻撃タイマー
    private var atkTimer1: Int = Random.nextInt(120)
    private var atkTimer2: Int = if (size == BlobSize.HUGE || size == BlobSize.DRAGON || size == BlobSize.ENEMY8) Random.nextInt(60) else 0
    private var atkTimer3: Int = if (size == BlobSize.ENEMY8) Random.nextInt(60) else 0

    companion object {
        private val cache = HashMap<BlobSize, BlobPaints>(8)
        private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255) }
        private val hpBarBgPaint = Paint().apply { color = Color.argb(120, 0, 0, 0) }
        private val hpBarGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00FF88") }
        private val hpBarYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFD740") }
        private val hpBarRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF3030") }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // BlobSizeごとのビットマップ (null = 円で描画)
        private val bitmaps = HashMap<BlobSize, Bitmap>(8)

        fun initSharedPaints(screenWidth: Int, context: Context) {
            cache.clear()
            for (size in BlobSize.values()) cache[size] = BlobPaints.create(size)

            // TINY(UFO)→enemy1, SMALL(Sun)→enemy2, SPEEDY(SpeedyBlade)→enemy3, MEDIUM(MediumUFO)→enemy4,
            // LARGE(Spider)→enemy5, HUGE(RedEye)→enemy6, DRAGON(Dragon)→enemy7, ENEMY8(Leviathan)→enemy8
            val rawMap = mapOf(
                BlobSize.TINY   to BitmapFactory.decodeResource(context.resources, R.drawable.enemy1),
                BlobSize.SMALL  to BitmapFactory.decodeResource(context.resources, R.drawable.enemy2),
                BlobSize.SPEEDY to BitmapFactory.decodeResource(context.resources, R.drawable.enemy3),
                BlobSize.MEDIUM to BitmapFactory.decodeResource(context.resources, R.drawable.enemy4),
                BlobSize.LARGE  to BitmapFactory.decodeResource(context.resources, R.drawable.enemy5),
                BlobSize.HUGE   to BitmapFactory.decodeResource(context.resources, R.drawable.enemy6),
                BlobSize.DRAGON to BitmapFactory.decodeResource(context.resources, R.drawable.enemy7),
                BlobSize.ENEMY8 to BitmapFactory.decodeResource(context.resources, R.drawable.enemy8)
            )
            bitmaps.clear()
            for (size in BlobSize.values()) {
                val raw = rawMap[size] ?: continue
                val d = (size.radius(screenWidth) * size.displayScale() * 2).toInt().coerceAtLeast(4)
                bitmaps[size] = Bitmap.createScaledBitmap(raw, d, d, true)
                raw.recycle()
            }
        }
    }

    fun update(playerX: Float, playerY: Float) {
        val edgePad = screenWidth * 0.09f
        val minX = radius + edgePad
        val maxX = screenWidth - radius - edgePad

        when (moveType) {
            MoveType.STRAIGHT_DOWN -> {
                cy += moveSpeed
            }
            MoveType.ZIGZAG -> {
                zigzagAngle += 0.07f
                cx += sin(zigzagAngle) * moveSpeed * 3.0f
                cy += moveSpeed * 0.8f
                cx = cx.coerceIn(minX, maxX)
            }
            MoveType.RANDOM -> {
                randomTimer++
                if (randomTimer >= 70) {
                    randomTimer = 0
                    val angle = (Random.nextFloat() * 2f - 0.5f) * Math.PI.toFloat()
                    randomDirX = cos(angle)
                    randomDirY = sin(angle).coerceAtLeast(-0.3f)
                }
                cx += randomDirX * moveSpeed * 1.5f
                cy += randomDirY * moveSpeed * 1.5f
                cx = cx.coerceIn(minX, maxX)
                if (cy < -radius * 2f) cy += moveSpeed
            }
            MoveType.CHASE -> {
                val dx = playerX - cx
                val dy = playerY - cy
                val dist = sqrt(dx * dx + dy * dy)
                if (dist > 1f) {
                    cx += (dx / dist) * moveSpeed
                    cy += (dy / dist) * moveSpeed
                }
                cx = cx.coerceIn(minX, maxX)
            }
        }
        val bottomLimit = if (moveType == MoveType.CHASE) screenHeight.toFloat() else screenHeight * 0.96f
        if (cy > bottomLimit) isDead = true
        if (flashTimer > 0) flashTimer--
    }

    /**
     * @param bulletCount 現在画面上の敵弾数
     * @param maxBullets  敵弾の上限
     * @param shockwaves  衝撃波リスト
     */
    fun tryShoot(
        playerX: Float, playerY: Float,
        bulletCount: Int, maxBullets: Int,
        shockwaves: MutableList<Shockwave>
    ): List<EnemyBullet> {
        if (bulletCount >= maxBullets) return emptyList()

        val congestion = 1f + (bulletCount.toFloat() / maxBullets) * 2f

        val result = mutableListOf<EnemyBullet>()

        when (size) {
            BlobSize.TINY -> {
                // UFO: 照準弾（約6秒間隔）
                atkTimer1++
                if (atkTimer1 >= (360 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.SMALL -> {
                // Sun: 照準弾（約5秒間隔）
                atkTimer1++
                if (atkTimer1 >= (300 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.SPEEDY -> {
                atkTimer1++
                if (atkTimer1 >= (250 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.MEDIUM -> {
                atkTimer1++
                if (atkTimer1 >= (150 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    aimShot(playerX, playerY, tint = 0)?.let { result.add(it) }
                }
            }
            BlobSize.LARGE -> {
                atkTimer1++
                if (atkTimer1 >= (100 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    if (shockwaves.size < 3) {
                        val angleDeg = Math.toDegrees(atan2((playerY - cy).toDouble(), (playerX - cx).toDouble())).toFloat()
                        shockwaves.add(Shockwave(cx, cy, screenWidth, screenHeight, angleDeg, sweepAngle = 10f))
                    }
                }
            }
            BlobSize.HUGE -> {
                atkTimer1++
                if (atkTimer1 >= (85 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    if (shockwaves.size < 3) {
                        val angleDeg = Math.toDegrees(atan2((playerY - cy).toDouble(), (playerX - cx).toDouble())).toFloat()
                        shockwaves.add(Shockwave(cx, cy, screenWidth, screenHeight, angleDeg, sweepAngle = 10f))
                    }
                }
                atkTimer2++
                if (atkTimer2 >= (55 * attackIntervalMult * congestion).toInt()) { atkTimer2 = 0
                    aimShot(playerX, playerY, tint = 1, speedMult = 1.6f)?.let { result.add(it) }
                }
            }
            BlobSize.DRAGON -> {
                atkTimer1++
                if (atkTimer1 >= (120 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 5, spread = 0.30f, tint = 2, speedMult = 1.6f))
                }
                atkTimer2++
                if (atkTimer2 >= (180 * attackIntervalMult).toInt()) { atkTimer2 = 0
                    if (shockwaves.size < 3) {
                        val angleDeg = Math.toDegrees(atan2((playerY - cy).toDouble(), (playerX - cx).toDouble())).toFloat()
                        shockwaves.add(Shockwave(cx, cy, screenWidth, screenHeight, angleDeg, sweepAngle = 10f))
                    }
                }
            }
            BlobSize.ENEMY8 -> {
                // Leviathan: 3種類の攻撃
                // 攻撃1: 5方向弾（約1.3秒間隔）
                atkTimer1++
                if (atkTimer1 >= (78 * attackIntervalMult * congestion).toInt()) { atkTimer1 = 0
                    result.addAll(spreadShot(playerX, playerY, count = 5, spread = 0.35f, tint = 2, speedMult = 1.5f))
                }
                // 攻撃2: 高速照準弾×5（約1.1秒間隔）速度差で縦に連なる
                atkTimer2++
                if (atkTimer2 >= (66 * attackIntervalMult * congestion).toInt()) { atkTimer2 = 0
                    listOf(2.0f, 1.75f, 1.5f, 1.25f, 1.0f).forEach { s ->
                        aimShot(playerX, playerY, tint = 1, speedMult = s)?.let { result.add(it) }
                    }
                }
                // 攻撃3: 衝撃波（約2秒間隔）
                atkTimer3++
                if (atkTimer3 >= (120 * attackIntervalMult).toInt()) { atkTimer3 = 0
                    if (shockwaves.size < 3) {
                        val angleDeg = Math.toDegrees(atan2((playerY - cy).toDouble(), (playerX - cx).toDouble())).toFloat()
                        shockwaves.add(Shockwave(cx, cy, screenWidth, screenHeight, angleDeg, sweepAngle = 10f))
                    }
                }
            }
        }
        return result
    }

    private fun aimShot(playerX: Float, playerY: Float, tint: Int, speedMult: Float = 1f): EnemyBullet? {
        val dx = playerX - cx; val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return null
        val speed = screenHeight * 0.009f * speedMult * GameConfig.ENEMY_BULLET_SPEED_MULT
        return EnemyBullet(cx, cy, (dx / dist) * speed, (dy / dist) * speed, screenWidth, screenHeight, tint)
    }

    private fun spreadShot(playerX: Float, playerY: Float, count: Int, spread: Float, tint: Int, speedMult: Float = 1f): List<EnemyBullet> {
        val dx = playerX - cx; val dy = playerY - cy
        val dist = sqrt(dx * dx + dy * dy)
        if (dist == 0f) return emptyList()
        val base = atan2(dy, dx)
        val speed = screenHeight * 0.009f * speedMult * GameConfig.ENEMY_BULLET_SPEED_MULT
        val step = if (count > 1) spread * 2f / (count - 1) else 0f
        return (0 until count).map { i ->
            val a = base - spread + i * step
            EnemyBullet(cx, cy, cos(a) * speed, sin(a) * speed, screenWidth, screenHeight, tint)
        }
    }

    fun takeDamage(): Boolean {
        hp--; flashTimer = 8
        if (hp <= 0) { isDead = true; return true }
        return false
    }

    fun draw(canvas: Canvas) {
        val bmp = bitmaps[size]
        if (bmp != null) {
            canvas.drawBitmap(bmp, cx - radius, cy - radius, bitmapPaint)
        } else {
            val p = cache[size] ?: return
            canvas.drawCircle(cx, cy, radius, p.body)
        }
        if (flashTimer > 0) canvas.drawCircle(cx, cy, radius, flashPaint)

        // HPバー（HP複数の敵のみ）
        if (maxHp > 1) {
            val bw = radius * 1.8f; val bh = radius * 0.22f
            val bx = cx - bw / 2f; val by = cy + radius * 1.25f
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), bh / 2, bh / 2, hpBarBgPaint)
            val ratio = hp.toFloat() / maxHp
            val fgPaint = when (size) {
                BlobSize.DRAGON -> hpBarRedPaint
                BlobSize.HUGE   -> hpBarYellowPaint
                BlobSize.ENEMY8 -> hpBarRedPaint
                else            -> hpBarGreenPaint
            }
            canvas.drawRoundRect(RectF(bx, by, bx + bw * ratio, by + bh), bh / 2, bh / 2, fgPaint)
        }
    }
}
