package com.teamhappslab.galaxyraid

import android.graphics.Canvas
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

class BlobManager(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val stage: StageConfig? = null   // ストーリーのステージ設定（null=エンドレス）
) {
    val blobs: MutableList<Blob> = mutableListOf()
    var level: Int = 1
        private set

    private val maxBlobs = 12

    // グローバルティア（level50, 100, 150...で節目）
    val globalTier: Int get() = level / 50 + 1

    // Lv100〜から能力アップが始まるティア数（Lv50=tier2は変動なし）
    private val statTierCount: Int get() = (globalTier - 2).coerceAtLeast(0)

    // 攻撃間隔倍率（Lv100〜で0.9倍ずつ短縮→攻撃が速くなる）×ステージ倍率
    val attackIntervalMult: Float
        get() = 0.9.pow(statTierCount.toDouble()).toFloat() * (stage?.enemyAttackIntervalMult ?: 1f)

    // 敵移動速度倍率（Lv100〜で1.03倍ずつ加速）×ステージ倍率×全体ノブ
    val enemySpeedMult: Float
        get() = 1.03.pow(statTierCount.toDouble()).toFloat() * (stage?.enemySpeedMult ?: 1f) * GameConfig.ENEMY_MOVE_SPEED_MULT

    // 敵HP倍率（ステージ難易度由来。エンドレスは1.0）
    private val enemyHpMult: Float get() = stage?.enemyHpMult ?: 1f

    // スコア倍率（Lv50〜で1.1倍ずつ増加）
    val scoreMultiplier: Float get() = 1.1.pow((globalTier - 1).toDouble()).toFloat()

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

    // 通常敵の新規出現を許可するか（ボス戦準備〜ボス戦中はfalse）
    var spawningEnabled: Boolean = true

    // スコアベースのレベル閾値（公開: デバッグ用スコア同期に使用）
    fun levelThreshold(n: Int): Int = 80 * (n - 1) * n / 2

    // ── 敵出現レベルのステージ倍率（小さいほど全敵が早く出現） ──
    private val spawnLevelMult: Float get() = stage?.enemySpawnLevelMult ?: 1f
    /** ステージ倍率を掛けた実効出現レベル（最低1） */
    private fun effMinLevel(size: BlobSize): Int =
        (size.minLevel() * spawnLevelMult).roundToInt().coerceAtLeast(1)
    /** ステージ倍率を掛けた実効ピークレベル（最低でも実効出現レベル+1：0除算防止） */
    private fun effPeakLevel(size: BlobSize): Int =
        (size.peakLevel() * spawnLevelMult).roundToInt().coerceAtLeast(effMinLevel(size) + 1)

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

        // ボス戦準備〜ボス戦中は新規出現しない
        if (!spawningEnabled) return

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
            .filter { level >= effMinLevel(it) }
            // ステージ上限を超える強敵は出さない（エンドレスは制限なし）
            .filter { stage == null || it.ordinal <= stage.enemyTierCap.ordinal }
            .map { size ->
                val emin = effMinLevel(size); val epeak = effPeakLevel(size)
                val w = if (level >= epeak) {
                    size.maxSpawnWeight().toFloat()
                } else {
                    val progress = (level - emin).toFloat() / (epeak - emin).toFloat()
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
        blobs.add(Blob(cx, cy, size, screenWidth, screenHeight, enemySpeedMult, attackIntervalMult, enemyHpMult))
    }

    fun onKill() {}

    /**
     * ボス戦中の救済用雑魚を1体出現させる。
     * spawningEnabled=falseのままでも出現でき、撃破時は必ずアイテムをドロップする。
     */
    fun spawnBossMinion() {
        val size = if (Random.nextBoolean()) BlobSize.TINY else BlobSize.SMALL
        val margin = screenWidth * 0.08f
        val cx = margin + Random.nextFloat() * (screenWidth - margin * 2)
        val cy = -screenWidth * 0.15f
        blobs.add(Blob(cx, cy, size, screenWidth, screenHeight, enemySpeedMult, attackIntervalMult, enemyHpMult)
            .apply { guaranteedDrop = true })
    }

    /** デバッグ用: レベルを直接設定 */
    fun setLevel(newLevel: Int) {
        level = newLevel.coerceAtLeast(1)
        spawnBudget = 0f
        spawnQueue.clear()
        prevTier = globalTier
    }

    fun draw(canvas: Canvas, alpha: Float = 0f) {
        blobs.forEach { it.draw(canvas, alpha) }
    }

    fun reset() {
        blobs.clear()
        level = 1
        spawnBudget = 0f
        spawnQueue.clear()
        prevTier = 1
        tierUpEvent = false
        spawningEnabled = true
    }
}
