package com.example.blobbuster

import android.graphics.Canvas
import kotlin.math.pow
import kotlin.random.Random

class BlobManager(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val blobs: MutableList<Blob> = mutableListOf()
    var level: Int = 1
        private set

    private val maxBlobs = 12
    private var spawnBudget: Float = 0f
    private var targetSize: BlobSize? = null

    // Level N: 2^(N-1)/60 コスト/フレーム、最大 40/60
    private val budgetPerFrame: Float
        get() = minOf(2f.pow(level - 1) / 60f, 40f / 60f)

    // Levelアップに必要な累積スコア: level(n) = 80 * n * (n-1) / 2
    // Level 1→2: 80, 2→3: 160 (合計240), 3→4: 240 (合計480), ...
    private fun levelThreshold(n: Int): Int = 80 * (n - 1) * n / 2

    fun update(playerX: Float, playerY: Float, score: Int) {
        // スコアベースでlevel更新（無限）
        while (score >= levelThreshold(level + 1)) {
            level++
        }

        blobs.forEach { it.update(playerX, playerY) }
        blobs.removeAll { it.isDead }  // 画面外に出た敵を削除

        // 予算蓄積
        if (blobs.size < maxBlobs) {
            spawnBudget += budgetPerFrame
        }

        // targetSizeが未設定なら次の敵を選ぶ
        if (targetSize == null) {
            targetSize = pickNextTarget()
        }

        // 予算が足りたらスポーン
        val target = targetSize
        if (target != null && spawnBudget >= target.spawnCost() && blobs.size < maxBlobs) {
            spawnBlob(target)
            spawnBudget -= target.spawnCost().toFloat()
            targetSize = null
        }
    }

    /** 次にスポーンする敵を選ぶ（コスト比例の重みで高コスト敵を優先） */
    private fun pickNextTarget(): BlobSize {
        val available = availableSizes()
        val total = available.sumOf { it.spawnCost() }
        var roll = Random.nextInt(total)
        for (size in available) {
            roll -= size.spawnCost()
            if (roll < 0) return size
        }
        return available.last()
    }

    /** 現在のlevelで出現可能な敵リスト（minLevel以下はフィルタ） */
    private fun availableSizes(): List<BlobSize> =
        BlobSize.values().filter { level >= it.minLevel() }

    private fun spawnBlob(size: BlobSize) {
        val margin = screenWidth * 0.08f
        val cx = margin + Random.nextFloat() * (screenWidth - margin * 2)
        val cy = -screenWidth * 0.15f
        blobs.add(Blob(cx, cy, size, screenWidth, screenHeight))
    }

    fun onKill() {}  // スコアベースに変更したため不要

    fun draw(canvas: Canvas) {
        blobs.forEach { it.draw(canvas) }
    }

    fun reset() {
        blobs.clear()
        level = 1
        spawnBudget = 0f
        targetSize = null
    }
}
