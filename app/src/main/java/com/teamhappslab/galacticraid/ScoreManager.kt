package com.teamhappslab.galacticraid

class ScoreManager {
    var score: Int = 0
        private set
    var round: Int = 1

    fun addScore(points: Int) {
        score += points
    }

    fun setScore(value: Int) {
        score = value.coerceAtLeast(0)
    }

    fun reset() {
        score = 0
        round = 1
    }
}
