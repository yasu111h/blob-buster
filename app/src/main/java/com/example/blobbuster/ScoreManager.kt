package com.example.blobbuster

class ScoreManager {
    var score: Int = 0
        private set
    var round: Int = 1

    fun addScore(points: Int) {
        score += points
    }

    fun reset() {
        score = 0
        round = 1
    }
}
