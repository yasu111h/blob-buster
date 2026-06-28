package com.teamhappslab.galaxyraid

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

/** ストーリーモードのステージ選択画面を表示するActivity。 */
class StageSelectActivity : AppCompatActivity() {

    private lateinit var stageSelectView: StageSelectView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        stageSelectView = StageSelectView(this)
        stageSelectView.onStageSelected = { stage ->
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("game_mode", "story")
            intent.putExtra("stage", stage)
            startActivity(intent)
        }
        stageSelectView.onBackTapped = { finish() }
        setContentView(stageSelectView)

        // DecorView生成後にフルスクリーン設定
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
        stageSelectView.startAnimation()
        stageSelectView.refresh()   // ゲームから戻ったとき解放状況を更新
    }

    override fun onPause() {
        super.onPause()
        stageSelectView.stopAnimation()
    }
}
