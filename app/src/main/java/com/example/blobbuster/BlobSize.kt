package com.example.blobbuster

enum class BlobSize {
    LARGE, MEDIUM, SMALL;

    fun maxHp() = when (this) {
        LARGE  -> 3
        MEDIUM -> 2
        SMALL  -> 1
    }

    fun radius(screenWidth: Int) = when (this) {
        LARGE  -> screenWidth * 0.10f
        MEDIUM -> screenWidth * 0.065f
        SMALL  -> screenWidth * 0.038f
    }

    fun speed(screenHeight: Int) = when (this) {
        LARGE  -> screenHeight * 0.0022f
        MEDIUM -> screenHeight * 0.0032f
        SMALL  -> screenHeight * 0.0052f
    }

    fun score() = when (this) {
        LARGE  -> 300
        MEDIUM -> 150
        SMALL  -> 100
    }

    fun color() = when (this) {
        LARGE  -> android.graphics.Color.parseColor("#39FF14")
        MEDIUM -> android.graphics.Color.parseColor("#FF2D78")
        SMALL  -> android.graphics.Color.parseColor("#00F5FF")
    }
}
