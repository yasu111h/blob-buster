package com.teamhappslab.galaxyraid

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var titleView: TitleView

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // 設定画面から戻ったとき（ハイスコアリセットされた可能性あり）
        titleView.updateHighScores(HighScoreManager.getTopScores(this))
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        titleView = TitleView(this)
        titleView.onStartTapped = {
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, GameActivity::class.java))
            }, 600L)
        }
        titleView.onSettingsTapped = {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }
        setContentView(titleView)

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

    override fun onResume() {
        super.onResume()
        titleView.startAnimation()
        titleView.resetLoading()
        titleView.updateHighScores(HighScoreManager.getTopScores(this))
    }

    override fun onPause() {
        super.onPause()
        titleView.stopAnimation()
    }
}
