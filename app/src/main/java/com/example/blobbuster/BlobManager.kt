package com.example.blobbuster

import android.graphics.Canvas
import kotlin.random.Random

class BlobManager(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val blobs: MutableList<Blob> = mutableListOf()
    var wave: Int = 1
        private set
    var killCount: Int = 0
        private set

    private val maxBlobs = 20
    private var spawnTimer = 0
    private var spawnInterval = 75  // フレーム数（初期: 約1.25秒）

    // ゲーム開始直後は少し遅らせてスポーン
    init {
        spawnTimer = -60  // 最初の1秒はスポーンしない
    }

    fun update(playerX: Float, playerY: Float) {
        blobs.forEach { it.update(playerX, playerY) }

        spawnTimer++
        if (spawnTimer >= spawnInterval && blobs.size < maxBlobs) {
            spawnBlob()
            spawnTimer = 0
        }
    }

    private fun spawnBlob() {
        val rng = Random.Default
        val margin = screenWidth * 0.1f

        // Wave3以上で25%の確率で2体同時スポーン
        val count = if (wave >= 3 && rng.nextFloat() < 0.25f) 2 else 1
        repeat(count) {
            if (blobs.size >= maxBlobs) return
            val cx = margin + rng.nextFloat() * (screenWidth - margin * 2)
            val cy = -screenWidth * 0.15f

            val size = when {
                wave >= 8 -> when (rng.nextInt(10)) {
                    0       -> BlobSize.HUGE
                    in 1..3 -> BlobSize.LARGE
                    in 4..5 -> BlobSize.SPEEDY
                    in 6..7 -> BlobSize.MEDIUM
                    else    -> BlobSize.SMALL
                }
                wave >= 5 -> when (rng.nextInt(10)) {
                    in 0..1 -> BlobSize.HUGE
                    in 2..4 -> BlobSize.LARGE
                    5       -> BlobSize.SPEEDY
                    in 6..7 -> BlobSize.MEDIUM
                    else    -> BlobSize.SMALL
                }
                wave >= 3 -> when (rng.nextInt(10)) {
                    0       -> BlobSize.HUGE
                    in 1..2 -> BlobSize.LARGE
                    in 3..4 -> BlobSize.SPEEDY
                    in 5..6 -> BlobSize.MEDIUM
                    else    -> BlobSize.SMALL
                }
                else -> when (rng.nextInt(10)) {
                    0       -> BlobSize.LARGE
                    in 1..3 -> BlobSize.MEDIUM
                    4       -> BlobSize.SPEEDY
                    else    -> BlobSize.SMALL
                }
            }
            blobs.add(Blob(cx, cy, size, screenWidth, screenHeight))
        }
    }

    /** 敵を倒したときに呼ぶ */
    fun onKill() {
        killCount++
        // 20キルごとにWaveアップ
        if (killCount % 20 == 0) {
            wave++
            spawnInterval = maxOf(45, 110 - wave * 8)
        }
    }

    fun draw(canvas: Canvas) {
        blobs.forEach { it.draw(canvas) }
    }

    fun reset() {
        blobs.clear()
        wave = 1
        killCount = 0
        spawnTimer = -60
        spawnInterval = 75
    }
}
