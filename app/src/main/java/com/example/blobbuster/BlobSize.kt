package com.example.blobbuster

enum class BlobSize {
    LARGE, MEDIUM, SMALL;

    fun smaller(): BlobSize? = when (this) {
        LARGE -> MEDIUM
        MEDIUM -> SMALL
        SMALL -> null
    }

    fun radius(screenWidth: Int) = when (this) {
        LARGE -> screenWidth * 0.12f
        MEDIUM -> screenWidth * 0.07f
        SMALL -> screenWidth * 0.04f
    }

    fun score() = when (this) {
        LARGE -> 400
        MEDIUM -> 200
        SMALL -> 100
    }

    fun color() = when (this) {
        LARGE -> android.graphics.Color.parseColor("#39FF14")   // ネオングリーン
        MEDIUM -> android.graphics.Color.parseColor("#FF2D78")  // ホットピンク
        SMALL -> android.graphics.Color.parseColor("#00F5FF")   // エレクトリックシアン
    }
}
