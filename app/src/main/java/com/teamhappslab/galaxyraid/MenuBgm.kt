package com.teamhappslab.galaxyraid

import android.content.Context
import android.media.MediaPlayer

/**
 * メニュー系画面（ホーム / ステージ選択 / 設定）で流すループBGM。
 *
 * アプリ全体で1つのMediaPlayerを共有し、画面を移動してもシームレスに継続する
 * （resume/pauseで位置を保持）。プレイ中(GameActivity)は pause() で止めるため、
 * 「プレイ中以外の画面」でだけ流れる。BGMのON/OFF設定(AppPrefs)を尊重する。
 */
object MenuBgm {
    private var mp: MediaPlayer? = null

    /** メニューBGMを再生（BGM設定OFF時は鳴らさない）。すでに再生中なら継続。 */
    fun resume(context: Context) {
        val app = context.applicationContext
        if (!AppPrefs.isBgmEnabled(app)) { pause(); return }
        if (mp == null) {
            mp = try {
                MediaPlayer.create(app, R.raw.bgm_menu)?.apply {
                    isLooping = true
                    // エラー時は破棄してnullに戻す。これがないと一度ERROR状態に落ちた
                    // MediaPlayerを保持し続け、アプリを再起動するまでメニューBGMが
                    // 永久に無音になる（次のresumeで作り直せるようにする）。
                    setOnErrorListener { player, _, _ ->
                        try { player.release() } catch (_: Exception) {}
                        if (mp === player) mp = null
                        true   // ハンドル済み（OnCompletionListenerを呼ばせない）
                    }
                }
            } catch (_: Exception) { null }
        }
        try { mp?.let { if (!it.isPlaying) it.start() } } catch (_: Exception) {}
    }

    /** 一時停止（再生位置は保持。次のresumeで続きから）。 */
    fun pause() {
        try { mp?.let { if (it.isPlaying) it.pause() } } catch (_: Exception) {}
    }

    /** BGM設定が変わった時に呼ぶ。ONなら再生、OFFなら停止。 */
    fun applyEnabled(context: Context) {
        if (AppPrefs.isBgmEnabled(context.applicationContext)) resume(context) else pause()
    }
}
