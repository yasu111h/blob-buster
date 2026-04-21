package com.teamhappslab.galaxyraid

class GameThread(private val gameView: GameView) : Thread() {
    @Volatile
    var isRunning: Boolean = false

    companion object {
        const val TARGET_FPS = 60
        const val FRAME_INTERVAL = 1000L / TARGET_FPS
    }

    override fun run() {
        while (isRunning) {
            val startTime = System.currentTimeMillis()

            gameView.update()
            gameView.draw()

            val elapsed = System.currentTimeMillis() - startTime
            val sleepTime = FRAME_INTERVAL - elapsed
            if (sleepTime > 0) {
                try {
                    sleep(sleepTime)
                } catch (e: InterruptedException) {
                    // スレッド割り込み時は終了
                    break
                }
            }
        }
    }
}
