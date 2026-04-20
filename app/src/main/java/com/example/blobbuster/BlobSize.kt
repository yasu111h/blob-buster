package com.example.blobbuster

import android.graphics.Color

enum class MoveType { STRAIGHT_DOWN, ZIGZAG, RANDOM, CHASE }

enum class BlobSize {
    TINY, SMALL, SPEEDY, MEDIUM, LARGE, HUGE, DRAGON;

    fun maxHp(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 1
        SPEEDY -> 1
        MEDIUM -> 3
        LARGE  -> 6
        HUGE   -> 12
        DRAGON -> 25
    }

    fun radius(screenWidth: Int): Float = screenWidth.toFloat() * when (this) {
        TINY   -> 0.030f
        SMALL  -> 0.038f
        SPEEDY -> 0.026f
        MEDIUM -> 0.058f
        LARGE  -> 0.085f
        HUGE   -> 0.115f
        DRAGON -> 0.150f
    }

    fun speed(screenHeight: Int): Float = screenHeight.toFloat() * when (this) {
        TINY   -> 0.0050f
        SMALL  -> 0.0055f
        SPEEDY -> 0.0110f
        MEDIUM -> 0.0042f
        LARGE  -> 0.0028f
        HUGE   -> 0.0020f
        DRAGON -> 0.0015f
    }

    fun score(): Int = when (this) {
        TINY   -> 10
        SMALL  -> 20
        SPEEDY -> 30
        MEDIUM -> 60
        LARGE  -> 150
        HUGE   -> 350
        DRAGON -> 800
    }

    fun color(): Int = when (this) {
        TINY   -> Color.parseColor("#FFB3C1")
        SMALL  -> Color.parseColor("#00F5FF")
        SPEEDY -> Color.parseColor("#FF6600")
        MEDIUM -> Color.parseColor("#FF2D78")
        LARGE  -> Color.parseColor("#39FF14")
        HUGE   -> Color.parseColor("#7B00CC")
        DRAGON -> Color.parseColor("#CC0000")
    }

    fun moveType(): MoveType = when (this) {
        TINY   -> MoveType.STRAIGHT_DOWN
        SMALL  -> MoveType.ZIGZAG
        SPEEDY -> MoveType.RANDOM
        MEDIUM -> MoveType.RANDOM
        LARGE  -> MoveType.ZIGZAG
        HUGE   -> MoveType.CHASE
        DRAGON -> MoveType.CHASE
    }

    fun spawnCost(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 2
        SPEEDY -> 3
        MEDIUM -> 5
        LARGE  -> 10
        HUGE   -> 20
        DRAGON -> 35
    }

    fun itemDropChance(): Float = when (this) {
        TINY   -> 0.023f
        SMALL  -> 0.030f
        SPEEDY -> 0.045f
        MEDIUM -> 0.068f
        LARGE  -> 0.105f
        HUGE   -> 0.210f
        DRAGON -> 0.375f
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
    }
}
