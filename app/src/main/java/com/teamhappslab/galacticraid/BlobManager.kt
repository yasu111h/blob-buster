package com.teamhappslab.galacticraid

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

    // グローバルティア（level50, 100, 150...で節目）
    val globalTier: Int get() = level / 50 + 1

    // Lv100〜から能力アップが始まるティア数（Lv50=tier2は変動なし）
    private val statTierCount: Int get() = (globalTier - 2).coerceAtLeast(0)

    // 攻撃間隔倍率（Lv100〜で0.9倍ずつ短縮→攻撃が速くなる）
    val attackIntervalMult: Float get() = 0.9.pow(statTierCount.toDouble()).toFloat()

    // 敵移動速度倍率（Lv100〜で1.1倍ずつ加速）
    val enemySpeedMult: Float get() = 1.1.pow(statTierCount.toDouble()).toFloat()

    // スポーンバジェット
    private var spawnBudget: Float = 0f
    private val budgetPerFrame: Float
        get() = minOf(2f.pow(level - 1) / 60f, 40f / 60f)

    // クォータ（バッチ方式：10体分の出現予定リスト）
    private val spawnQueue: ArrayDeque<BlobSize> = ArrayDeque()

    // 前回のグローバルティア（ティアアップ検知用）
    private var prevTier: Int = 1

    // ティアアップフラグ（GameViewがこれを見てエフェクト表示）
    var tierUpEvent: Boolean = false

    // スコアベースのレベル閾値（公開: デバッグ用スコア同期に使用）
    fun levelThreshold(n: Int): Int = 80 * (n - 1) * n / 2

    fun update(playerX: Float, playerY: Float, score: Int) {
        // スコアベースでlevel更新
        var levelChanged = false
        while (score >= levelThreshold(level + 1)) {
            level++
            levelChanged = true
        }
        // レベルアップ時はキューをクリアして即座に新レベルの敵構成で再生成
        if (levelChanged) spawnQueue.clear()

        // ティアアップ検知
        val currentTier = globalTier
        if (currentTier > prevTier) {
            tierUpEvent = true
            prevTier = currentTier
        }

        blobs.forEach { it.update(playerX, playerY) }
        blobs.removeAll { it.isDead }

        // 予算蓄積
        if (blobs.size < maxBlobs) {
            spawnBudget += budgetPerFrame
        }

        // キューが空なら次のバッチを生成
        if (spawnQueue.isEmpty()) {
            generateNextBatch()
        }

        // 予算が足りたらキューから先頭を取り出してスポーン
        val next = spawnQueue.firstOrNull()
        if (next != null && spawnBudget >= next.spawnCost() && blobs.size < maxBlobs) {
            spawnQueue.removeFirst()
            spawnBlob(next)
            spawnBudget -= next.spawnCost().toFloat()
        }
    }

    /**
     * 次の10体分のスポーンキューを生成（クォータ方式）。
     * 各BlobSizeの出現重みを計算し、10体分の構成を確定してシャッフル。
     */
    private fun generateNextBatch() {
        val batchSize = 10
        val weights = BlobSize.values()
            .filter { level >= it.minLevel() }
            .map { size ->
                val w = if (level >= size.peakLevel()) {
                    size.maxSpawnWeight().toFloat()
                } else {
                    val progress = (level - size.minLevel()).toFloat() / (size.peakLevel() - size.minLevel()).toFloat()
                    size.maxSpawnWeight() * progress
                }
                size to w
            }
            .filter { it.second > 0f }

        if (weights.isEmpty()) {
            spawnQueue.add(BlobSize.TINY)
            return
        }

        val totalWeight = weights.sumOf { it.second.toDouble() }.toFloat()

        // 各BlobSizeに割り当てる体数を決定
        val counts = mutableMapOf<BlobSize, Int>()
        var remaining = batchSize
        weights.forEachIndexed { i, (size, w) ->
            val n = if (i == weights.size - 1) {
                remaining  // 最後は残りを全部割り当て
            } else {
                (w / totalWeight * batchSize).toInt().coerceAtLeast(0)
            }
            counts[size] = n
            remaining -= n
        }

        // リストを作成してシャッフル
        val batch = mutableListOf<BlobSize>()
        counts.forEach { (size, count) -> repeat(count) { batch.add(size) } }
        batch.shuffle()
        spawnQueue.addAll(batch)
    }

    private fun spawnBlob(size: BlobSize) {
        val margin = screenWidth * 0.08f
        val cx = margin + Random.nextFloat() * (screenWidth - margin * 2)
        val cy = -screenWidth * 0.15f
        blobs.add(Blob(cx, cy, size, screenWidth, screenHeight, enemySpeedMult, attackIntervalMult))
    }

    fun onKill() {}

    /** デバッグ用: レベルを直接設定 */
    fun setLevel(newLevel: Int) {
        level = newLevel.coerceAtLeast(1)
        spawnBudget = 0f
        spawnQueue.clear()
        prevTier = globalTier
    }

    fun draw(canvas: Canvas) {
        blobs.forEach { it.draw(canvas) }
    }

    fun reset() {
        blobs.clear()
        level = 1
        spawnBudget = 0f
        spawnQueue.clear()
        prevTier = 1
        tierUpEvent = false
    }
}
