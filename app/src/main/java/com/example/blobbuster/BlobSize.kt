package com.example.blobbuster

enum class BlobSize {
    LARGE, MEDIUM, SMALL, HUGE, SPEEDY;

    fun maxHp() = when (this) {
        HUGE   -> 6
        LARGE  -> 3
        MEDIUM -> 2
        SMALL  -> 1
        SPEEDY -> 1
    }

    fun radius(screenWidth: Int) = when (this) {
        HUGE   -> screenWidth * 0.14f
        LARGE  -> screenWidth * 0.10f
        MEDIUM -> screenWidth * 0.065f
        SMALL  -> screenWidth * 0.038f
        SPEEDY -> screenWidth * 0.028f
    }

    fun speed(screenHeight: Int) = when (this) {
        HUGE   -> screenHeight * 0.0015f
        LARGE  -> screenHeight * 0.0022f
        MEDIUM -> screenHeight * 0.0032f
        SMALL  -> screenHeight * 0.0052f
        SPEEDY -> screenHeight * 0.0095f
    }

    fun score() = when (this) {
        HUGE   -> 600
        LARGE  -> 300
        MEDIUM -> 150
        SMALL  -> 100
        SPEEDY -> 80
    }

    fun color() = when (this) {
        HUGE   -> android.graphics.Color.parseColor("#7B00CC")  // ダークパープル
        LARGE  -> android.graphics.Color.parseColor("#39FF14")
        MEDIUM -> android.graphics.Color.parseColor("#FF2D78")
        SMALL  -> android.graphics.Color.parseColor("#00F5FF")
        SPEEDY -> android.graphics.Color.parseColor("#FF6600")  // オレンジ
    }

    fun canShoot() = when (this) {
        HUGE   -> true
        LARGE  -> true
        MEDIUM -> true
        SMALL  -> false
        SPEEDY -> false
    }

    fun shootInterval() = when (this) {
        HUGE   -> 60
        LARGE  -> 80
        MEDIUM -> 110
        else   -> Int.MAX_VALUE
    }

    fun itemDropChance() = when (this) {
        HUGE   -> 0.5f
        LARGE  -> 0.3f
        MEDIUM -> 0.2f
        SMALL  -> 0.1f
        SPEEDY -> 0.15f
    }
}
