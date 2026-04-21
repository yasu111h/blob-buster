package com.teamhappslab.galacticraid

import android.content.Context

object HighScoreManager {
    private const val PREFS = "blob_buster_scores"

    fun getTopScores(context: Context): List<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (0..2).map { prefs.getInt("score$it", 0) }
    }

    /**
     * スコアを保存し、ランクインした順位を返す（1〜3）。
     * ランクインしなかった場合は 0 を返す。
     */
    fun saveScore(context: Context, score: Int): Int {
        if (score <= 0) return 0
        val before = getTopScores(context)
        val scores = before.toMutableList()
        scores.add(score)
        scores.sortDescending()
        val top3 = scores.take(3)
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        top3.forEachIndexed { i, s -> editor.putInt("score$i", s) }
        editor.apply()
        val rank = top3.indexOf(score) + 1  // 1-based, not found = 0
        return if (rank in 1..3) rank else 0
    }

    fun resetScores(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
