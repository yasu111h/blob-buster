package com.example.blobbuster

import android.content.Context

object HighScoreManager {
    private const val PREFS = "blob_buster_scores"

    fun getTopScores(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (0..2).map { prefs.getInt("score$it", 0) }
    }

    fun saveScore(context: Context, score: Int) {
        if (score <= 0) return
        val scores = getTopScores(context).toMutableList()
        scores.add(score)
        scores.sortDescending()
        val top3 = scores.take(3)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        top3.forEachIndexed { i, s -> editor.putInt("score$i", s) }
        editor.apply()
    }

    fun resetScores(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
