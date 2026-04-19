package com.example.blobbuster

import android.graphics.Canvas
import kotlin.math.pow
import kotlin.random.Random

class BlobManager(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val blobs: MutableList<Blob> = mutableListOf()
    var wave: Int = 1
        private set

    private val maxBlobs = 22
    private var spawnBudget: Float = 0f

    // Wave 1 = 1コスト/秒 = 1/60コスト/フレーム
    // Wave N = 2^(N-1)/60 per frame, 最大40/60
    private val budgetPerFrame: Float
        get() = minOf(2f.pow(wave - 1) / 60f, 40f / 60f)

    // waveThresholds[i] = wave (i+1) になる累積スコア
    private val waveThresholds = intArrayOf(
        0, 300, 700, 1400, 2600, 4500, 7500, 12000, 19000
    )

    fun update(playerX: Float, playerY: Float, score: Int) {
        // スコアに応じてwave更新（単調増加のみ）
        for (i in waveThresholds.indices.reversed()) {
            if (score >= waveThresholds[i]) {
                val newWave = i + 1
                if (newWave > wave) wave = newWave
                break
            }
        }

        blobs.forEach { it.update(playerX, playerY) }

        // 予算蓄積 & スポーン
        if (blobs.size < maxBlobs) {
            spawnBudget += budgetPerFrame
        }

        while (blobs.size < maxBlobs) {
            val size = pickAffordableSize() ?: break
            spawnBlob(size)
            spawnBudget -= size.spawnCost().toFloat()
        }
    }

    private fun pickAffordableSize(): BlobSize? {
        val rng = Random.Default
        // 重みマップからwave対応の候補を取得 → 予算内でランダム選択
        val candidates = buildWeightMap()
        val affordable = candidates.filter { (size, _) -> size.spawnCost() <= spawnBudget }
        if (affordable.isEmpty()) return null
        val total = affordable.values.sum()
        var roll = rng.nextInt(total)
        for ((size, w) in affordable) {
            roll -= w
            if (roll < 0) return size
        }
        return affordable.keys.first()
    }

    private fun buildWeightMap(): Map<BlobSize, Int> = when {
        wave >= 9 -> mapOf(
            BlobSize.DRAGON to 15, BlobSize.HUGE to 20, BlobSize.LARGE to 20,
            BlobSize.MEDIUM to 18, BlobSize.SPEEDY to 10, BlobSize.SMALL to 10, BlobSize.TINY to 7
        )
        wave >= 7 -> mapOf(
            BlobSize.DRAGON to 8, BlobSize.HUGE to 18, BlobSize.LARGE to 22,
            BlobSize.MEDIUM to 22, BlobSize.SPEEDY to 12, BlobSize.SMALL to 12, BlobSize.TINY to 6
        )
        wave >= 5 -> mapOf(
            BlobSize.HUGE to 10, BlobSize.LARGE to 20, BlobSize.MEDIUM to 25,
            BlobSize.SPEEDY to 18, BlobSize.SMALL to 20, BlobSize.TINY to 7
        )
        wave >= 3 -> mapOf(
            BlobSize.LARGE to 8, BlobSize.MEDIUM to 22, BlobSize.SPEEDY to 22,
            BlobSize.SMALL to 30, BlobSize.TINY to 18
        )
        else -> mapOf(
            BlobSize.MEDIUM to 5, BlobSize.SPEEDY to 12,
            BlobSize.SMALL to 40, BlobSize.TINY to 43
        )
    }

    private fun spawnBlob(size: BlobSize) {
        val margin = screenWidth * 0.08f
        val cx = margin + Random.nextFloat() * (screenWidth - margin * 2)
        val cy = -screenWidth * 0.15f
        blobs.add(Blob(cx, cy, size, screenWidth, screenHeight))
    }

    /** 後方互換性のため残す（現在はスコアでwaveが上がるため何もしない） */
    fun onKill() {}

    fun draw(canvas: Canvas) {
        blobs.forEach { it.draw(canvas) }
    }

    fun reset() {
        blobs.clear()
        wave = 1
        spawnBudget = 0f
    }
}
