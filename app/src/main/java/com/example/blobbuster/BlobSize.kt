package com.example.blobbuster

import android.graphics.Color

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
        TINY   -> 0.0030f
        SMALL  -> 0.0050f
        SPEEDY -> 0.0100f
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

    fun canShoot(): Boolean = when (this) {
        TINY, SMALL, SPEEDY -> false
        else -> true
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
        TINY   -> 0.05f
        SMALL  -> 0.08f
        SPEEDY -> 0.12f
        MEDIUM -> 0.18f
        LARGE  -> 0.28f
        HUGE   -> 0.55f
        DRAGON -> 1.00f
    }
}
