package com.teamhappslab.galacticraid

import android.graphics.Color

enum class MoveType { STRAIGHT_DOWN, ZIGZAG, RANDOM, CHASE }

enum class BlobSize {
    TINY, SMALL, SPEEDY, MEDIUM, LARGE, HUGE, DRAGON, ENEMY8, ENEMY9;

    fun maxHp(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 1
        SPEEDY -> 1
        MEDIUM -> 3
        LARGE  -> 6
        HUGE   -> 12
        DRAGON -> 25
        ENEMY8 -> 50
        ENEMY9 -> 100
    }

    fun radius(screenWidth: Int): Float = screenWidth.toFloat() * when (this) {
        TINY   -> 0.030f
        SMALL  -> 0.038f
        SPEEDY -> 0.026f
        MEDIUM -> 0.058f
        LARGE  -> 0.085f
        HUGE   -> 0.115f
        DRAGON -> 0.150f
        ENEMY8 -> 0.110f
        ENEMY9 -> 0.130f
    }

    fun speed(screenHeight: Int): Float = screenHeight.toFloat() * when (this) {
        TINY   -> 0.0050f
        SMALL  -> 0.0055f
        SPEEDY -> 0.0110f
        MEDIUM -> 0.0042f
        LARGE  -> 0.0028f
        HUGE   -> 0.0020f
        DRAGON -> 0.0015f
        ENEMY8 -> 0.0025f
        ENEMY9 -> 0.0022f
    }

    fun score(): Int = when (this) {
        TINY   -> 10
        SMALL  -> 20
        SPEEDY -> 30
        MEDIUM -> 60
        LARGE  -> 150
        HUGE   -> 350
        DRAGON -> 800
        ENEMY8 -> 1500
        ENEMY9 -> 3000
    }

    fun color(): Int = when (this) {
        TINY   -> Color.parseColor("#FFB3C1")
        SMALL  -> Color.parseColor("#00F5FF")
        SPEEDY -> Color.parseColor("#FF6600")
        MEDIUM -> Color.parseColor("#FF2D78")
        LARGE  -> Color.parseColor("#39FF14")
        HUGE   -> Color.parseColor("#7B00CC")
        DRAGON -> Color.parseColor("#CC0000")
        ENEMY8 -> Color.parseColor("#FF8C00")
        ENEMY9 -> Color.parseColor("#9400D3")
    }

    fun moveType(): MoveType = when (this) {
        TINY   -> MoveType.STRAIGHT_DOWN
        SMALL  -> MoveType.ZIGZAG
        SPEEDY -> MoveType.RANDOM
        MEDIUM -> MoveType.RANDOM
        LARGE  -> MoveType.ZIGZAG
        HUGE   -> MoveType.CHASE
        DRAGON -> MoveType.CHASE
        ENEMY8 -> MoveType.CHASE
        ENEMY9 -> MoveType.CHASE
    }

    fun spawnCost(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 2
        SPEEDY -> 3
        MEDIUM -> 5
        LARGE  -> 10
        HUGE   -> 20
        DRAGON -> 35
        ENEMY8 -> 50
        ENEMY9 -> 80
    }

    fun itemDropChance(): Float = when (this) {
        TINY   -> 0.023f
        SMALL  -> 0.030f
        SPEEDY -> 0.045f
        MEDIUM -> 0.068f
        LARGE  -> 0.105f
        HUGE   -> 0.210f
        DRAGON -> 0.375f
        ENEMY8 -> 0.500f
        ENEMY9 -> 0.800f
    }

    /**
     * 画像表示サイズ倍率（当たり判定にも反映）。
     * SurfaceView はソフトウェアキャンバスのため、大型敵の倍率を下げて
     * bitmap描画コストを抑える（DRAGON 2.0→1.0 で描画コスト1/4）。
     */
    fun displayScale(): Float = when (this) {
        TINY   -> 2.0f
        SMALL  -> 2.0f
        SPEEDY -> 2.0f
        MEDIUM -> 2.0f
        LARGE  -> 1.5f   // 2.0→1.5（性能改善）
        HUGE   -> 1.3f   // 2.0→1.3（性能改善・baseRadiusが既に大きい）
        DRAGON -> 1.0f   // 2.0→1.0（性能改善・baseRadius=0.15*w で十分大きい）
        ENEMY8 -> 1.2f
        ENEMY9 -> 1.1f
    }

    /** この敵が出現するのに必要な最低レベル */
    fun minLevel(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 2
        SPEEDY -> 3
        MEDIUM -> 4
        LARGE  -> 6
        HUGE   -> 8
        DRAGON -> 10
        ENEMY8 -> 50
        ENEMY9 -> 100
    }

    /** この敵が最大出現重みに達するレベル */
    fun peakLevel(): Int = when (this) {
        TINY   -> 5
        SMALL  -> 10
        SPEEDY -> 15
        MEDIUM -> 20
        LARGE  -> 30
        HUGE   -> 50
        DRAGON -> 80
        ENEMY8 -> 100
        ENEMY9 -> 200
    }

    /** ピーク時の出現重み（最大値） */
    fun maxSpawnWeight(): Int = when (this) {
        TINY   -> 100
        SMALL  -> 80
        SPEEDY -> 70
        MEDIUM -> 90
        LARGE  -> 80
        HUGE   -> 70
        DRAGON -> 60
        ENEMY8 -> 50
        ENEMY9 -> 40
    }

    /** グローバルティアに応じた攻撃ダメージ（将来用） */
    fun attackDamage(tier: Int): Int = 1
}
