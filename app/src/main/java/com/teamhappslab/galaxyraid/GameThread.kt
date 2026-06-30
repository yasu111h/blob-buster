package com.teamhappslab.galaxyraid

class GameThread(private val gameView: GameView) : Thread() {
    @Volatile
    var isRunning: Boolean = false

    companion object {
        const val TARGET_FPS = 60
        /** ロジック1ステップ分の時間（ナノ秒）。update()は常にこの刻みで1回進める。 */
        const val STEP_NS = 1_000_000_000L / TARGET_FPS
        /**
         * 1ループでまとめて進められる最大遅れ時間（ナノ秒）。
         * 一瞬大きく処理落ちしても、ここで上限を切ることで
         * update()を一度に何十回も呼ぶ「暴走（spiral of death）」を防ぐ。
         * 上限を超える極端な処理落ち時のみ、わずかにスロー寄りになる。
         */
        const val MAX_FRAME_NS = STEP_NS * 5
    }

    /**
     * 固定タイムステップ＋キャッチアップ方式のゲームループ。
     *
     * 旧実装は「1フレーム固定量だけ動かして余り時間を寝る」方式だったため、
     * 敵が増えて1フレームの処理が16.6msを超えるとFPSが落ち、
     * 1秒あたりの更新回数が減って全体がスローモーションになっていた。
     *
     * 本実装は経過時間（accumulator）を貯め、貯まったぶんだけ update() を
     * 固定刻みで複数回呼ぶ。描画が遅れても進行速度は実時間に対して一定になる。
     */
    override fun run() {
        var lastTime = System.nanoTime()
        var accumulator = 0L

        while (isRunning) {
            val frameStart = System.nanoTime()
            var frameTime = frameStart - lastTime
            lastTime = frameStart

            // 大きすぎる遅れは上限で切る（暴走防止）
            if (frameTime > MAX_FRAME_NS) frameTime = MAX_FRAME_NS
            accumulator += frameTime

            // 貯まった時間ぶんだけ固定刻みで更新（＝実時間に対して常に毎秒60回）
            while (accumulator >= STEP_NS) {
                gameView.update()
                accumulator -= STEP_NS
            }

            gameView.draw()

            // 処理が軽いときは目標フレーム時間まで寝てCPUを休める
            val work = System.nanoTime() - frameStart
            val sleepMs = (STEP_NS - work) / 1_000_000L
            if (sleepMs > 1) {
                try {
                    sleep(sleepMs)
                } catch (e: InterruptedException) {
                    // スレッド割り込み時は終了
                    break
                }
            }
        }
    }
}
