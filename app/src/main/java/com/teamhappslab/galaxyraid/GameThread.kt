package com.teamhappslab.galaxyraid

class GameThread(private val gameView: GameView) : Thread() {
    @Volatile
    var isRunning: Boolean = false

    companion object {
        const val TARGET_FPS = 60

        /**
         * ★ゲーム全体の速度ノブ★
         * 1.0 = 本来の60fps速度（速い）。値を下げると、一定速度を保ったまま
         * ゲーム全体（移動・弾速・敵出現・アニメ）が同じ割合でゆっくりになる。
         * 例: 0.75 なら本来の75%の速さ。速すぎ/遅すぎる場合はこの数値だけ調整する。
         */
        const val GAME_SPEED = 0.75f

        /** 実際のロジック更新レート（GAME_SPEEDを反映。例: 60 * 0.75 = 45回/秒）。 */
        const val SIM_FPS = TARGET_FPS * GAME_SPEED

        /** ロジック1ステップ分の時間（ナノ秒）。update()は常にこの刻みで1回進める。 */
        val STEP_NS = (1_000_000_000.0 / SIM_FPS).toLong()
        /**
         * 描画1フレーム分の目標時間（ナノ秒）。画面のリフレッシュレート(60Hz)に合わせて
         * ロジック(45回/秒)より多く描く。間のコマは補間(alpha)で埋めるのでカクつかない。
         */
        val RENDER_STEP_NS = 1_000_000_000L / TARGET_FPS
        /**
         * 1ループでまとめて進められる最大遅れ時間（ナノ秒）。
         * 一瞬大きく処理落ちしても、ここで上限を切ることで
         * update()を一度に何十回も呼ぶ「暴走（spiral of death）」を防ぐ。
         * 上限を超える極端な処理落ち時のみ、わずかにスロー寄りになる。
         */
        val MAX_FRAME_NS = STEP_NS * 5
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

            // 貯まった時間ぶんだけ固定刻みで更新（＝実時間に対して常に一定回数）
            while (accumulator >= STEP_NS) {
                gameView.update()
                accumulator -= STEP_NS
            }

            // 次の更新までの進捗（0.0〜1.0）。描画はこの割合で前回位置と現在位置の
            // 中間を描くことで、ロジック45回/秒でも画面60回/秒で滑らかに見える。
            // ただし一時停止/クリア/ゲームオーバー中は敵が動かないので、補間を切り
            // alpha=1（現在位置に固定）にする。これをしないと前回位置との間で振動して見える。
            val alpha = if (gameView.isSimulating()) {
                (accumulator.toFloat() / STEP_NS).coerceIn(0f, 1f)
            } else {
                1f
            }
            gameView.draw(alpha)

            // 画面リフレッシュ(60fps)に合わせて寝る。これで描画回数を論理回数より多く保つ。
            val work = System.nanoTime() - frameStart
            val sleepMs = (RENDER_STEP_NS - work) / 1_000_000L
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
