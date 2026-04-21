package com.teamhappslab.galacticraid

import android.graphics.Color

enum class MoveType { STRAIGHT_DOWN, ZIGZAG, RANDOM, CHASE }

// TINY=UFO, SMALL=Sun, SPEEDY=SpeedyBlade, MEDIUM=MediumUFO,
// LARGE=Spider, HUGE=RedEye, DRAGON=Dragon, ENEMY8=Leviathan
enum class BlobSize {
    TINY, SMALL, SPEEDY, MEDIUM, LARGE, HUGE, DRAGON, ENEMY8;

    fun maxHp(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 1
        SPEEDY -> 1
        MEDIUM -> 3
        LARGE  -> 6
        HUGE   -> 12
        DRAGON -> 25
        ENEMY8 -> 30   // Leviathan
    }

    fun radius(screenWidth: Int): Float = screenWidth.toFloat() * when (this) {
        TINY   -> 0.030f
        SMALL  -> 0.038f
        SPEEDY -> 0.026f
        MEDIUM -> 0.058f
        LARGE  -> 0.085f
        HUGE   -> 0.115f
        DRAGON -> 0.150f
        ENEMY8 -> 0.180f  // Leviathan: 最大サイズ
    }

    fun speed(screenHeight: Int): Float = screenHeight.toFloat() * when (this) {
        TINY   -> 0.0050f
        SMALL  -> 0.0055f
        SPEEDY -> 0.0110f
        MEDIUM -> 0.0042f
        LARGE  -> 0.0028f
        HUGE   -> 0.0020f
        DRAGON -> 0.0020f  // 遅い(0.20%)
        ENEMY8 -> 0.0015f  // Leviathan: 最遅(0.15%)
    }

    fun score(): Int = when (this) {
        TINY   -> 10
        SMALL  -> 25
        SPEEDY -> 40
        MEDIUM -> 60
        LARGE  -> 150
        HUGE   -> 500
        DRAGON -> 1200
        ENEMY8 -> 2300  // Leviathan
    }

    fun color(): Int = when (this) {
        TINY   -> Color.parseColor("#FFB3C1")
        SMALL  -> Color.parseColor("#00F5FF")
        SPEEDY -> Color.parseColor("#FF6600")
        MEDIUM -> Color.parseColor("#FF2D78")
        LARGE  -> Color.parseColor("#39FF14")
        HUGE   -> Color.parseColor("#7B00CC")
        DRAGON -> Color.parseColor("#CC0000")
        ENEMY8 -> Color.parseColor("#FF8C00")  // Leviathan: 濃オレンジ
    }

    fun moveType(): MoveType = when (this) {
        TINY   -> MoveType.STRAIGHT_DOWN
        SMALL  -> MoveType.ZIGZAG
        SPEEDY -> MoveType.RANDOM
        MEDIUM -> MoveType.RANDOM
        LARGE  -> MoveType.ZIGZAG
        HUGE   -> MoveType.CHASE
        DRAGON -> MoveType.CHASE
        ENEMY8 -> MoveType.CHASE  // Leviathan
    }

    fun spawnCost(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 2
        SPEEDY -> 3
        MEDIUM -> 5
        LARGE  -> 10
        HUGE   -> 20
        DRAGON -> 35
        ENEMY8 -> 50  // Leviathan
    }

    fun itemDropChance(): Float = when (this) {
        TINY   -> 0.023f
        SMALL  -> 0.030f
        SPEEDY -> 0.045f
        MEDIUM -> 0.068f
        LARGE  -> 0.105f
        HUGE   -> 0.210f
        DRAGON -> 0.375f
        ENEMY8 -> 0.500f  // Leviathan
    }

    fun displayScale(): Float = when (this) {
        TINY   -> 2.0f
        SMALL  -> 2.0f
        SPEEDY -> 2.0f
        MEDIUM -> 2.0f
        LARGE  -> 1.5f
        HUGE   -> 1.3f
        DRAGON -> 1.0f
        ENEMY8 -> 1.0f  // Leviathan: radius=18%で既に最大
    }

    /** この敵が出現するのに必要な最低レベル（実際の出現レベルと一致） */
    fun minLevel(): Int = when (this) {
        TINY   -> 1
        SMALL  -> 3
        SPEEDY -> 5
        MEDIUM -> 7
        LARGE  -> 10
        HUGE   -> 15
        DRAGON -> 22
        ENEMY8 -> 50   // Leviathan
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
        ENEMY8 -> 100  // Leviathan
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
        ENEMY8 -> 50   // Leviathan
    }

    /** グローバルティアに応じた攻撃ダメージ（将来用） */
    fun attackDamage(tier: Int): Int = 1
}
