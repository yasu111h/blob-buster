package com.example.blobbuster

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * SFX + BGM を管理するクラス。
 *
 * 【SE設計】
 * Thread生成+Semaphoreによる旧方式は「3本上限で音がドロップする」問題があった。
 * AudioTrack MODE_STATIC を事前にN本プール（キル音12本、ダメージ3本、アイテム3本）して
 * ラウンドロビンで再生。Thread生成なし、生成音は1回だけ計算。常に鳴る。
 *
 * 【BGM設計】
 * Am→G→F→E コード進行のチップチューン4秒ループ。
 * AudioTrack MODE_STATIC + setLoopPoints(-1)でシームレスループ。
 * ポーズ2フラグ方式: bgmUserPaused / bgmActivityPaused
 */
class SoundManager {

    private val sampleRate = 22050

    // ── SFXプール ──────────────────────────────────────────────────
    // BGMスレッド内で初期化 → sfxReady=trueになってから play が有効
    @Volatile private var sfxReady = false
    private val killPool   = arrayOfNulls<AudioTrack>(12)  // キル音12本（最大12敵同時撃破に対応）
    private val damagePool = arrayOfNulls<AudioTrack>(3)
    private val itemPool   = arrayOfNulls<AudioTrack>(3)
    private var killIdx   = 0
    private var damageIdx = 0
    private var itemIdx   = 0

    // ── BGM ───────────────────────────────────────────────────────
    @Volatile private var bgmRunning        = false
    @Volatile private var bgmUserPaused     = false
    @Volatile private var bgmActivityPaused = false
    private var bgmThread: Thread? = null

    private val bgmNotes = listOf(
        220.00 to 125, 261.63 to 125, 329.63 to 125, 440.00 to 125,
        329.63 to 125, 261.63 to 125, 220.00 to 125,   0.00 to 125,
        196.00 to 125, 246.94 to 125, 293.66 to 125, 392.00 to 125,
        293.66 to 125, 246.94 to 125, 196.00 to 125,   0.00 to 125,
        174.61 to 125, 220.00 to 125, 261.63 to 125, 349.23 to 125,
        261.63 to 125, 220.00 to 125, 174.61 to 125,   0.00 to 125,
        164.81 to 125, 246.94 to 125, 329.63 to 125, 415.30 to 125,
        329.63 to 125, 246.94 to 125, 164.81 to 125,   0.00 to 125
    )
    private val bgmBassFreqs = listOf(110.0, 98.0, 87.31, 82.41)

    // ── SE バッファ生成 ────────────────────────────────────────────

    /** 敵撃破音: 880Hz→200Hz スイープ */
    private fun genKillBuf(): ShortArray {
        val n = sampleRate * 120 / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val freq = 880.0 + (200.0 - 880.0) * i.toDouble() / n
            val env = 1.0 - i.toDouble() / n
            (sin(2 * PI * freq * t) * env * 0.6 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** ダメージ音: 110Hz スクエアウェーブ */
    private fun genDamageBuf(): ShortArray {
        val n = sampleRate * 200 / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val sq = if (sin(2 * PI * 110.0 * t) >= 0) 1.0 else -1.0
            val env = (1.0 - i.toDouble() / n).let { it * it }
            (sq * env * 0.55 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    /** アイテム取得音: 上昇スイープ（パワーアップ感） */
    private fun genItemBuf(): ShortArray {
        val n = sampleRate * 180 / 1000
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val freq = 300.0 + (900.0 - 300.0) * i.toDouble() / n
            val env = if (i < n * 0.1) i.toDouble() / (n * 0.1)
                      else 1.0 - (i.toDouble() - n * 0.1) / (n * 0.9)
            (sin(2 * PI * freq * t) * env * 0.5 * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    // ── AudioTrack ファクトリ ──────────────────────────────────────

    private fun makeStaticTrack(buf: ShortArray): AudioTrack {
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
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        return track
    }

    private fun playFromPool(pool: Array<AudioTrack?>, idxRef: IntArray): Boolean {
        if (!sfxReady) return false
        val idx = idxRef[0] % pool.size
        idxRef[0]++
        val track = pool[idx] ?: return false
        return try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
            true
        } catch (_: Exception) { false }
    }

    // ── 公開SFX API ───────────────────────────────────────────────

    fun playEnemyKilled()  { playFromPool(killPool,   intArrayOf(killIdx++  )) }
    fun playPlayerDamaged(){ playFromPool(damagePool, intArrayOf(damageIdx++)) }
    fun playItemPickup()   { playFromPool(itemPool,   intArrayOf(itemIdx++  )) }

    // ── BGM ───────────────────────────────────────────────────────

    private fun generateBgmLoop(): ShortArray {
        val totalSamples = bgmNotes.sumOf { (_, ms) -> sampleRate * ms / 1000 }
        val buf = ShortArray(totalSamples)
        var offset = 0
        for ((ni, nd) in bgmNotes.withIndex()) {
            val (freq, ms) = nd
            val bassFreq = bgmBassFreqs[(ni / 8).coerceAtMost(bgmBassFreqs.size - 1)]
            val n = sampleRate * ms / 1000
            for (i in 0 until n) {
                val t = (offset + i).toDouble() / sampleRate
                val lead = if (freq > 0.0) {
                    val sq = if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
                    val env = if (i > n * 0.8) (1.0 - (i - n * 0.8) / (n * 0.2)) else 1.0
                    sq * env * 0.28
                } else 0.0
                val bass = sin(2 * PI * bassFreq * t) * 0.18
                buf[offset + i] = ((lead + bass).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            }
            offset += n
        }
        return buf
    }

    fun startBgm() {
        if (bgmRunning) return
        bgmRunning = true
        bgmThread = Thread {
            // SFXプール初期化（BGMスレッドでまとめて実行）
            val kb = genKillBuf(); val db = genDamageBuf(); val ib = genItemBuf()
            for (i in killPool.indices)   killPool[i]   = makeStaticTrack(kb)
            for (i in damagePool.indices) damagePool[i] = makeStaticTrack(db)
            for (i in itemPool.indices)   itemPool[i]   = makeStaticTrack(ib)
            sfxReady = true

            // BGM生成と再生
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
            track.setLoopPoints(0, loopBuf.size, -1)
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
                track.stop(); track.release()
            }
        }.also { it.isDaemon = true }
        bgmThread!!.start()
    }

    fun pauseBgmByUser()    { bgmUserPaused     = true  }
    fun resumeBgmByUser()   { bgmUserPaused     = false }
    fun pauseBgmBySystem()  { bgmActivityPaused = true  }
    fun resumeBgmBySystem() { bgmActivityPaused = false }

    fun release() {
        bgmRunning = false
        bgmThread?.interrupt()
        bgmThread = null
        sfxReady = false
        for (pool in arrayOf(killPool, damagePool, itemPool)) {
            for (t in pool) { try { t?.stop(); t?.release() } catch (_: Exception) {} }
        }
    }
}
