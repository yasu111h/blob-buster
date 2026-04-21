package com.teamhappslab.galaxyraid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlin.math.PI
import kotlin.math.sin

/**
 * SFX + BGM を管理するクラス。
 *
 * 【SE設計】
 * AudioTrack MODE_STATIC プールをバックグラウンドスレッドで事前生成。
 * killPool=12本, damagePool=3本, itemPool=3本 のラウンドロビン再生。
 *
 * 【BGM設計】
 * MediaPlayer で res/raw/bgm.mp3 を再生（isLooping=true）。
 * ポーズ2フラグ方式: bgmUserPaused / bgmActivityPaused
 */
class SoundManager {

    private val sampleRate = 22050

    // ── BGM/SE ON/OFFフラグ ─────────────────────────────────────────
    var bgmEnabled: Boolean = true
    var sfxEnabled: Boolean = true

    // ── SFXプール ──────────────────────────────────────────────────
    @Volatile private var sfxReady = false
    private val killPool   = arrayOfNulls<AudioTrack>(12)
    private val damagePool = arrayOfNulls<AudioTrack>(3)
    private val itemPool   = arrayOfNulls<AudioTrack>(3)
    private var killIdx   = 0
    private var damageIdx = 0
    private var itemIdx   = 0

    // ── BGM ───────────────────────────────────────────────────────
    @Volatile private var bgmRunning        = false
    @Volatile private var bgmUserPaused     = false
    @Volatile private var bgmActivityPaused = false
    private var mediaPlayer: MediaPlayer? = null

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

    /** アイテム取得音: 上昇スイープ */
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

    fun playEnemyKilled()  { if (sfxEnabled) playFromPool(killPool,   intArrayOf(killIdx++  )) }
    fun playPlayerDamaged(){ if (sfxEnabled) playFromPool(damagePool, intArrayOf(damageIdx++)) }
    fun playItemPickup()   { if (sfxEnabled) playFromPool(itemPool,   intArrayOf(itemIdx++  )) }

    // ── BGM ───────────────────────────────────────────────────────

    /**
     * BGMを開始する。初回のみ有効（bgmRunning=trueなら即return）。
     * SFXプールはバックグラウンドスレッドで初期化。
     * BGMはMediaPlayerで res/raw/bgm.mp3 を再生。
     */
    fun startBgm(context: Context) {
        if (bgmRunning) return
        bgmRunning = true

        // SFXプールをバックグラウンドスレッドで初期化（UIをブロックしない）
        Thread {
            val kb = genKillBuf(); val db = genDamageBuf(); val ib = genItemBuf()
            for (i in killPool.indices)   killPool[i]   = makeStaticTrack(kb)
            for (i in damagePool.indices) damagePool[i] = makeStaticTrack(db)
            for (i in itemPool.indices)   itemPool[i]   = makeStaticTrack(ib)
            sfxReady = true
        }.apply { isDaemon = true; start() }

        // BGM: MediaPlayer で MP3再生（bgmEnabledがtrueのときのみ）
        if (bgmEnabled) {
            try {
                mediaPlayer = MediaPlayer.create(context, R.raw.bgm)?.apply {
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    if (!bgmUserPaused && !bgmActivityPaused) start()
                }
            } catch (_: Exception) { /* BGM失敗しても続行 */ }
        }
    }

    /** リトライ時など: BGMを先頭からやり直す */
    fun restartBgm(context: Context) {
        if (!bgmRunning || mediaPlayer == null) {
            startBgm(context)
        } else {
            bgmUserPaused = false
            if (bgmEnabled) {
                try {
                    mediaPlayer?.seekTo(0)
                    if (!bgmActivityPaused) mediaPlayer?.start()
                } catch (_: Exception) {}
            }
        }
    }

    fun pauseBgmByUser() {
        bgmUserPaused = true
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }
    fun resumeBgmByUser() {
        bgmUserPaused = false
        if (bgmEnabled && !bgmActivityPaused) try { mediaPlayer?.start() } catch (_: Exception) {}
    }
    fun pauseBgmBySystem() {
        bgmActivityPaused = true
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }
    fun resumeBgmBySystem() {
        bgmActivityPaused = false
        if (bgmEnabled && !bgmUserPaused) try { mediaPlayer?.start() } catch (_: Exception) {}
    }

    fun release() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        bgmRunning = false
        sfxReady = false
        for (pool in arrayOf(killPool, damagePool, itemPool)) {
            for (t in pool) { try { t?.stop(); t?.release() } catch (_: Exception) {} }
        }
    }
}
