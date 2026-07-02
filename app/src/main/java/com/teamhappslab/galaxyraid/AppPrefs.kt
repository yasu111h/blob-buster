package com.teamhappslab.galaxyraid

import android.content.Context

object AppPrefs {
    private const val PREFS = "blob_buster_prefs"

    fun isBgmEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("bgm_enabled", true)

    fun setBgmEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("bgm_enabled", enabled).apply()
    }

    fun isSfxEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("sfx_enabled", true)

    fun setSfxEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("sfx_enabled", enabled).apply()
    }

    // ── ストーリーモードのステージ解放管理 ─────────────────
    /** クリア済みの最大ステージ番号（0=未クリア）。Stage N をクリアすると Stage N+1 が解放。 */
    fun getStoryClearedStage(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("story_cleared_stage", 0)

    /** ステージクリアを記録（過去の最高値を超えたときだけ更新）。 */
    fun setStoryStageCleared(context: Context, stage: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (stage > prefs.getInt("story_cleared_stage", 0)) {
            prefs.edit().putInt("story_cleared_stage", stage).apply()
        }
    }

    /** そのステージが解放済みか。Stage1・2は常に解放、以降は(クリア済み+1)まで解放。 */
    fun isStageUnlocked(context: Context, stage: Int): Boolean =
        stage <= maxOf(2, getStoryClearedStage(context) + 1)

    /** ボスモード（ストーリー）の進捗をリセット。クリア済みステージを0に戻す。 */
    fun resetStoryProgress(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("story_cleared_stage").apply()
    }
}
