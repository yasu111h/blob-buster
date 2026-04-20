package com.example.blobbuster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Semaphore
import kotlin.math.PI
import kotlin.math.sin

/**
 * SFX (敵撃破・ダメージ) + BGM (チップチューン生成) を管理するクラス。
 *
 * BGM は Am→G→F→E のコード進行をスクエアウェーブのアルペジオで4秒ループ再生。
 * AudioTrack MODE_STATIC + setLoopPoints でシームレスループ。
 *
 * 一時停止 2フラグ方式:
 *   - bgmUserPaused   : ゲーム内ポーズボタン
 *   - bgmActivityPaused: Activityのライフサイクル（バックグラウンド）
 * どちらか一方でも true ならBGMが止まる。
 */
class SoundManager {

    private val sampleRate = 22050

    // BGM スレッド管理
    @Volatile private var bgmRunning = false
    @Volatile private var bgmUserPaused = false
    @Volatile private var bgmActivityPaused = false
    private var bgmThread: Thread? = null

    // SFX 同時再生上限 (3本まで)
    private val sfxSemaphore = Semaphore(3)

    // ── BGM データ ──────────────────────────────────────────────
    // Am - G - F - E 進行（各8ノート、125ms/ノート = 1小節1秒 × 4小節 = 4秒ループ）
    private val bgmNotes = listOf(
        // Bar 1: Am arpeggio
        220.00 to 125, 261.63 to 125, 329.63 to 125, 440.00 to 125,
        329.63 to 125, 261.63 to 125, 220.00 to 125,   0.00 to 125,
        // Bar 2: G arpeggio
        196.00 to 125, 246.94 to 125, 293.66 to 125, 392.00 to 125,
        293.66 to 125, 246.94 to 125, 196.00 to 125,   0.00 to 125,
        // Bar 3: F arpeggio
        174.61 to 125, 220.00 to 125, 261.63 to 125, 349.23 to 125,
        261.63 to 125, 220.00 to 125, 174.61 to 125,   0.00 to 125,
        // Bar 4: E (V chord - 解決前のテンション)
        164.81 to 125, 246.94 to 125, 329.63 to 125, 415.30 to 125,
        329.63 to 125, 246.94 to 125, 164.81 to 125,   0.00 to 125
    )
    // 各小節のベース音: A2, G2, F2, E2
    private val bgmBassFreqs = listOf(110.0, 98.0, 87.31, 82.41)
    // ────────────────────────────────────────────────────────────

    // ── SFX ─────────────────────────────────────────────────────

    /** 敵撃破SE: 高音→低音スイープ (120ms) */
    fun playEnemyKilled() {
        if (!sfxSemaphore.tryAcquire()) return
        Thread {
            try {
                val durationMs = 120
                val n = sampleRate * durationMs / 1000
                val buf = ShortArray(n)
                val startFreq = 880.0; val endFreq = 200.0
                for (i in 0 until n) {
                    val t = i.toDouble() / sampleRate
                    val freq = startFreq + (endFreq - startFreq) * i.toDouble() / n
                    val env = 1.0 - i.toDouble() / n
                    buf[i] = (sin(2 * PI * freq * t) * env * 0.6 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playOnce(buf)
            } finally { sfxSemaphore.release() }
        }.also { it.isDaemon = true }.start()
    }

    /** ダメージSE: 低音スクエアウェーブバズ (200ms) */
    fun playPlayerDamaged() {
        if (!sfxSemaphore.tryAcquire()) return
        Thread {
            try {
                val durationMs = 200
                val n = sampleRate * durationMs / 1000
                val buf = ShortArray(n)
                val freq = 110.0
                for (i in 0 until n) {
                    val t = i.toDouble() / sampleRate
                    val square = if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
                    val env = (1.0 - i.toDouble() / n).let { it * it } // 二乗で急速フェード
                    buf[i] = (square * env * 0.55 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                playOnce(buf)
            } finally { sfxSemaphore.release() }
        }.also { it.isDaemon = true }.start()
    }

    private fun playOnce(buf: ShortArray) {
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(maxOf(minBuf, buf.size * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            track.play()
            track.write(buf, 0, buf.size)
            track.stop()
        } finally { track.release() }
    }

    // ── BGM ─────────────────────────────────────────────────────

    /** BGMループバッファをプログラムで生成（スクエアウェーブ + サインベース） */
    private fun generateBgmLoop(): ShortArray {
        val totalSamples = bgmNotes.sumOf { (_, ms) -> sampleRate * ms / 1000 }
        val buf = ShortArray(totalSamples)
        var offset = 0
        for ((noteIndex, noteData) in bgmNotes.withIndex()) {
            val (freq, durationMs) = noteData
            val barIndex = (noteIndex / 8).coerceAtMost(bgmBassFreqs.size - 1)
            val bassFreq = bgmBassFreqs[barIndex]
            val n = sampleRate * durationMs / 1000
            for (i in 0 until n) {
                val t = (offset + i).toDouble() / sampleRate
                // Lead: スクエアウェーブ（チップチューン感）+ ノート末尾フェード
                val lead = if (freq > 0.0) {
                    val sq = if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
                    val env = if (i > n * 0.8) (1.0 - (i - n * 0.8) / (n * 0.2)) else 1.0
                    sq * env * 0.28
                } else 0.0
                // Bass: サイン波（低音感）
                val bass = sin(2 * PI * bassFreq * t) * 0.18
                buf[offset + i] = ((lead + bass).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            offset += n
        }
        return buf
    }

    /** BGM開始（既に実行中なら何もしない） */
    fun startBgm() {
        if (bgmRunning) return
        bgmRunning = true
        bgmThread = Thread {
            val loopBuf = generateBgmLoop()
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(loopBuf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(loopBuf, 0, loopBuf.size)
            track.setLoopPoints(0, loopBuf.size, -1) // 無限ループ
            track.play()
            try {
                while (bgmRunning) {
                    val shouldPause = bgmUserPaused || bgmActivityPaused
                    when {
                        shouldPause && track.playState == AudioTrack.PLAYSTATE_PLAYING -> track.pause()
                        !shouldPause && track.playState == AudioTrack.PLAYSTATE_PAUSED -> track.play()
                    }
                    Thread.sleep(50)
                }
            } catch (_: InterruptedException) {
            } finally {
                track.stop()
                track.release()
            }
        }.also { it.isDaemon = true }
        bgmThread!!.start()
    }

    /** ゲーム内ポーズボタンによる一時停止 */
    fun pauseBgmByUser() { bgmUserPaused = true }
    /** ゲーム内ポーズボタンによる再開 */
    fun resumeBgmByUser() { bgmUserPaused = false }

    /** Activityのライフサイクルによる一時停止 */
    fun pauseBgmBySystem() { bgmActivityPaused = true }
    /** Activityのライフサイクルによる再開 */
    fun resumeBgmBySystem() { bgmActivityPaused = false }

    /** リソース解放（onDestroy時に呼ぶ） */
    fun release() {
        bgmRunning = false
        bgmThread?.interrupt()
        bgmThread = null
    }
}
