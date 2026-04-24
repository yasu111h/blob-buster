package com.teamhappslab.galaxyraid

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var soundManager: SoundManager

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        soundManager = SoundManager()
        soundManager.bgmEnabled = AppPrefs.isBgmEnabled(this)
        soundManager.sfxEnabled = AppPrefs.isSfxEnabled(this)
        gameView = GameView(this, soundManager)
        gameView.onGoHome = { finish() }
        setContentView(gameView)

        // DecorView生成後にフルスクリーン設定（setContentViewの後でないとNPE）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(
                    android.view.WindowInsets.Type.statusBars() or
                    android.view.WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        gameView.pause()
        soundManager.pauseBgmBySystem()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
        soundManager.resumeBgmBySystem()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
