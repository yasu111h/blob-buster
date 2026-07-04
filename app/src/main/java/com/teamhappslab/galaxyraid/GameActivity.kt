package com.teamhappslab.galaxyraid

import android.content.Intent
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
        val gameMode = intent.getStringExtra("game_mode") ?: "endless"
        val stage = intent.getIntExtra("stage", 0)
        gameView = GameView(this, soundManager, gameMode, stage)
        gameView.onGoHome = { finish() }
        // Homeボタンはタイトル(MainActivity)まで戻る。ボスモードは
        // ホーム→ステージ選択→ゲームの順で開かれるため、finish()だけだと
        // ステージ選択に戻ってしまう。間の画面をクリアしてタイトルへ。
        gameView.onGoTitle = {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
            finish()
        }
        setContentView(gameView)

        // DecorView生成後にフルスクリーン設定（setContentViewの後でないとNPE）
        UiKit.applyImmersive(window)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) UiKit.applyImmersive(window)
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
