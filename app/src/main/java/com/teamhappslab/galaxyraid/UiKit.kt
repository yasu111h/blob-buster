package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface

/** 画面間で共通のUIユーティリティ（フォント・図形）。 */
object UiKit {

    /** Saira可変フォントを指定ウェイトで読み込む（失敗時はDEFAULT_BOLDへフォールバック）。 */
    fun loadSaira(context: Context, weight: Int): Typeface = try {
        Typeface.Builder(context.assets, "fonts/saira_var.ttf")
            .setFontVariationSettings("'wght' $weight")
            .build() ?: Typeface.createFromAsset(context.assets, "fonts/saira_var.ttf")
    } catch (e: Exception) {
        try { Typeface.createFromAsset(context.assets, "fonts/saira_var.ttf") }
        catch (e2: Exception) { Typeface.DEFAULT_BOLD }
    }

    /** 角を斜めにカットした八角形パス（SFパネル風）。 */
    fun cutRectPath(r: RectF, cut: Float): Path = Path().apply {
        moveTo(r.left + cut, r.top)
        lineTo(r.right - cut, r.top)
        lineTo(r.right, r.top + cut)
        lineTo(r.right, r.bottom - cut)
        lineTo(r.right - cut, r.bottom)
        lineTo(r.left + cut, r.bottom)
        lineTo(r.left, r.bottom - cut)
        lineTo(r.left, r.top + cut)
        close()
    }
}
