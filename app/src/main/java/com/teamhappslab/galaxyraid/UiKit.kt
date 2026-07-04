package com.teamhappslab.galaxyraid

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** 画面間で共通のUIユーティリティ（フォント・図形）。 */
object UiKit {

    /**
     * 全画面（イマーシブ）にする。エッジtoエッジ（stable layout）で固定するのがポイント：
     * これによりナビゲーションバーが一時的に出ても、コンテンツ領域が縮まず（＝画面がずれず）バーが上に重なるだけになる。
     * onCreate（setContentView後）と onWindowFocusChanged(hasFocus=true) の両方で呼ぶ。
     */
    @Suppress("DEPRECATION")
    fun applyImmersive(window: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // コンテンツをシステムバーの裏まで広げる＝バーの出入りでレイアウトが変わらない
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { c ->
                c.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

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
