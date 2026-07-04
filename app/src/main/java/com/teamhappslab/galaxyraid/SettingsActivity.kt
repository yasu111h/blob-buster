package com.teamhappslab.galaxyraid

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsView: SettingsView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsView = SettingsView(this)
        settingsView.onBack = { finish() }
        settingsView.onResetScores = {
            setResult(Activity.RESULT_OK)
        }
        settingsView.onResetBossProgress = {
            // 完了表示はアプリ内モーダルで行う（OSのトーストは出さない）
        }
        setContentView(settingsView)

        // DecorView生成後にフルスクリーン設定（setContentViewの後でないとNPE）
        UiKit.applyImmersive(window)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiKit.applyImmersive(window)
    }

    override fun onResume() {
        super.onResume()
        UiKit.applyImmersive(window)
        settingsView.startAnimation()
        MenuBgm.resume(this)
    }

    override fun onPause() {
        super.onPause()
        settingsView.stopAnimation()
        MenuBgm.pause()
    }
}
