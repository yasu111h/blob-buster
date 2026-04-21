package com.teamhappslab.galacticraid

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
}
