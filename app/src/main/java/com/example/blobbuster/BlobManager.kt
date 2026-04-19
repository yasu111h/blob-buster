package com.example.blobbuster

import android.graphics.Canvas

class BlobManager(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    val blobs: MutableList<Blob> = mutableListOf()
    var round: Int = 1
        private set

    init {
        initRound(round)
    }

    private fun initRound(r: Int) {
        blobs.clear()
        val largeCount = r / 2 + 1
        val mediumCount = if (r >= 2) r / 3 else 0

        when (r) {
            1 -> {
                repeat(2) { i -> blobs.add(createBlob(BlobSize.LARGE, i)) }
            }
            2 -> {
                repeat(2) { i -> blobs.add(createBlob(BlobSize.LARGE, i)) }
                blobs.add(createBlob(BlobSize.MEDIUM, 2))
            }
            else -> {
                repeat(largeCount) { i -> blobs.add(createBlob(BlobSize.LARGE, i)) }
                repeat(mediumCount) { i -> blobs.add(createBlob(BlobSize.MEDIUM, i + largeCount)) }
            }
        }
    }

    private fun createBlob(size: BlobSize, index: Int): Blob {
        val section = screenWidth / 4f
        val cx = section * (index % 3 + 1).toFloat()
        val cy = screenHeight * 0.2f
        val vxBase = when (size) {
            BlobSize.LARGE -> screenWidth * 0.005f
            BlobSize.MEDIUM -> screenWidth * 0.007f
            BlobSize.SMALL -> screenWidth * 0.009f
        }
        val vx = if (index % 2 == 0) vxBase else -vxBase
        val vy = when (size) {
            BlobSize.LARGE -> -screenHeight * 0.015f
            BlobSize.MEDIUM -> -screenHeight * 0.019f
            BlobSize.SMALL -> -screenHeight * 0.022f
        }
        return Blob(cx, cy, vx, vy, size, screenWidth, screenHeight)
    }

    fun update() {
        blobs.toList().forEach { it.update() }
    }

    fun draw(canvas: Canvas) {
        blobs.toList().forEach { it.draw(canvas) }
    }

    fun isEmpty(): Boolean = blobs.isEmpty()

    fun nextRound() {
        round++
        initRound(round)
    }

    fun reset() {
        round = 1
        initRound(round)
    }
}
