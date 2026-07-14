package com.teamhappslab.galaxyraid

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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
    // プール初期化スレッドを起動済みか。これで「一度だけ」生成を保証し、
    // リトライのたびにAudioTrackを18本ずつ作り直して枯渇→クラッシュするのを防ぐ。
    @Volatile private var sfxInitStarted = false
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
    private var clearPlayer: MediaPlayer? = null  // クリアジングル用

    // このSoundManagerが破棄済みか。生成中のSFXプールスレッドや遅延再生から参照し、
    // release後に新しいAudioTrack/再生が湧いてリークするのを防ぐ。
    @Volatile private var released = false

    // BGM遅延再生用（旧実装は使い捨てThread+sleepでキャンセル不能だった）。
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bgmStartRunnable: Runnable? = null

    // bgm_clear のリソースID（getIdentifierは重いので初回だけ引く。0=音源なし、-1=未取得）
    private var clearResIdCache: Int = -1

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

    /**
     * SFXプール（AudioTrack計18本）をバックグラウンドで初回のみ生成する。
     * sfxInitStarted で二重起動を防ぐため、startBgm/restartBgmから何度呼んでも安全。
     * 生成に失敗しても（AudioTrack上限到達など）例外を握り潰し、無音で続行する。
     */
    private fun ensureSfxPool() {
        if (sfxInitStarted || released) return
        sfxInitStarted = true
        Thread {
            try {
                val kb = genKillBuf(); val db = genDamageBuf(); val ib = genItemBuf()
                for (i in killPool.indices)   killPool[i]   = makeStaticTrack(kb)
                for (i in damagePool.indices) damagePool[i] = makeStaticTrack(db)
                for (i in itemPool.indices)   itemPool[i]   = makeStaticTrack(ib)
                if (released) {
                    // 生成中に画面が閉じられた（release済み）。このままだと18本が
                    // 誰にも解放されずネイティブメモリに残り、繰り返すとAudioTrackの
                    // システム上限に達して端末再起動までSEが全て無音になる。自分で始末する。
                    releaseAllTracks()
                } else {
                    sfxReady = true
                }
            } catch (_: Exception) { /* SFX生成失敗時は無音で続行 */ }
        }.apply { isDaemon = true; start() }
    }

    /** SFXプールのAudioTrackを全て停止・解放して参照を切る。 */
    private fun releaseAllTracks() {
        for (pool in arrayOf(killPool, damagePool, itemPool)) {
            for (i in pool.indices) {
                try { pool[i]?.stop(); pool[i]?.release() } catch (_: Exception) {}
                pool[i] = null
            }
        }
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
        if (bgmRunning || released) return
        bgmRunning = true

        // SFXプールをバックグラウンドスレッドで初期化（UIをブロックしない・初回のみ）
        ensureSfxPool()

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

    /** リトライ時など: BGMを先頭から0.5秒後に再生する */
    fun restartBgm(context: Context) {
        // 破棄済みなら何もしない。この関数はGameThreadからinitGame()経由で呼ばれるため、
        // 画面を閉じた直後（release()済み）にゲームスレッドが追い越して実行し得る。
        // ガードがないと、破棄済みActivityを掴んだままMediaPlayerを作り直してリークする。
        if (released) return

        // SFXプールは初回のみ生成（sfxInitStartedでガード）。
        // 以前は「mediaPlayer==null」を初回条件にしていたため、BGM設定OFFのユーザーは
        // リトライのたびにAudioTrackを18本ずつ作り直し、旧トラックを解放せず枯渇→クラッシュしていた。
        ensureSfxPool()
        bgmRunning = true
        bgmUserPaused = false
        if (!bgmEnabled) return

        // MediaPlayerは未生成なら作り、生成済みなら頭出しだけ行う（再生成しない）。
        if (mediaPlayer == null) {
            val mp = try {
                MediaPlayer.create(context, R.raw.bgm)
            } catch (_: Exception) { null }   /* BGM失敗しても続行 */
            // create()は数百msかかる。その間にrelease()された場合は、作ったものを
            // そのまま捨てる（保持するとActivityごとリークする）。
            if (released) {
                try { mp?.release() } catch (_: Exception) {}
                return
            }
            mediaPlayer = mp?.apply {
                isLooping = true
                setVolume(1.0f, 1.0f)
            }
        } else {
            try { mediaPlayer?.seekTo(0) } catch (_: Exception) {}
        }
        // 0.5秒後に再生。旧実装は使い捨てThread+sleepでキャンセルできず、
        // 「Retry直後にホームへ戻る」とpause済みのBGMを後から再生してしまい、
        // バックグラウンドで鳴り続けていた。Handlerにしてpause/release時に取り消す。
        scheduleBgmStart()
    }

    /** 0.5秒後のBGM再生を予約する（既存の予約はキャンセル）。 */
    private fun scheduleBgmStart() {
        cancelBgmStart()
        val r = Runnable {
            if (!bgmUserPaused && !bgmActivityPaused && !released && bgmEnabled) {
                try { mediaPlayer?.start() } catch (_: Exception) {}
            }
        }
        bgmStartRunnable = r
        mainHandler.postDelayed(r, 500)
    }

    /** 予約済みのBGM遅延再生を取り消す。 */
    private fun cancelBgmStart() {
        bgmStartRunnable?.let { mainHandler.removeCallbacks(it) }
        bgmStartRunnable = null
    }

    /**
     * ゲームクリア時のジングルを再生する。
     * res/raw/bgm_clear.(mp3|ogg) が存在すればそれをMediaPlayerで再生
     * （boss.pngと同じgetIdentifier方式: 音源ファイルを置くだけで自動的に使われる）。
     * 無ければアイテム取得音を3回連続再生してファンファーレ風に代用する。
     */
    fun playClearJingle(context: Context) {
        if (released) return
        // この関数はGameThreadから呼ばれる（クリア判定時）。MediaPlayer.createは
        // ファイルを開いてデコーダをprepareする同期処理で数十〜数百msかかる。
        // ゲームスレッドで実行すればクリア演出がカクつき、メインスレッドで実行すれば
        // 今度はクリア画面のボタンが反応しなくなる。
        // → 「重い生成はワーカースレッド、再生開始とclearPlayerの操作だけメインスレッド」に分ける。
        //   （clearPlayerの読み書きをメインに一本化することで、pause/resume/releaseとの競合も防ぐ）
        val appCtx = context.applicationContext
        Thread {
            var mp: MediaPlayer? = null
            try {
                if (clearResIdCache < 0) {
                    clearResIdCache = try {
                        appCtx.resources.getIdentifier("bgm_clear", "raw", appCtx.packageName)
                    } catch (_: Exception) { 0 }
                }
                val resId = clearResIdCache
                if (resId != 0 && bgmEnabled && !released) {
                    mp = MediaPlayer.create(appCtx, resId)   // ← 重い処理はここ（ワーカー上）
                }
            } catch (_: Exception) { /* ジングル失敗しても続行 */ }

            val player = mp
            if (player == null) {
                // 音源が無い/生成失敗 → 代用ファンファーレ（アイテム取得音×3回）
                if (sfxEnabled && !released) {
                    repeat(3) {
                        if (released) return@Thread
                        playItemPickup()
                        try { Thread.sleep(220) } catch (_: InterruptedException) { return@Thread }
                    }
                }
                return@Thread
            }

            mainHandler.post {
                if (released) {
                    try { player.release() } catch (_: Exception) {}
                    return@post
                }
                try { clearPlayer?.release() } catch (_: Exception) {}
                player.isLooping = false
                player.setVolume(1.0f, 1.0f)
                // 再生し終えたら自分で解放する（旧実装は次のジングルかonDestroyまで保持していた）
                player.setOnCompletionListener { p ->
                    try { p.release() } catch (_: Exception) {}
                    if (clearPlayer === p) clearPlayer = null
                }
                clearPlayer = player
                // アプリがバックグラウンドなら鳴らさない
                // （ボス撃破直後にホームへ戻ると裏でジングルが流れてしまうため）
                if (!bgmActivityPaused) {
                    try { player.start() } catch (_: Exception) {}
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun pauseBgmByUser() {
        bgmUserPaused = true
        cancelBgmStart()   // 予約済みの遅延再生も取り消す
        try { mediaPlayer?.pause() } catch (_: Exception) {}
    }
    fun resumeBgmByUser() {
        bgmUserPaused = false
        if (bgmEnabled && !bgmActivityPaused) try { mediaPlayer?.start() } catch (_: Exception) {}
    }
    fun pauseBgmBySystem() {
        bgmActivityPaused = true
        cancelBgmStart()   // バックグラウンドで後から鳴り出すのを防ぐ
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        try { clearPlayer?.pause() } catch (_: Exception) {}
    }
    fun resumeBgmBySystem() {
        bgmActivityPaused = false
        if (bgmEnabled && !bgmUserPaused) try { mediaPlayer?.start() } catch (_: Exception) {}
        // クリアジングルの途中で離席していた場合は続きから再開する
        // （再生完了済みならOnCompletionListenerで解放済み=nullなので何もしない）
        if (bgmEnabled) try { clearPlayer?.start() } catch (_: Exception) {}
    }

    fun release() {
        // 先にreleasedを立てる。生成中のSFXプールスレッドや遅延再生・ジングル生成が
        // これを見て自分で後始末する（release後に新しい資源が湧くのを防ぐ）。
        released = true
        cancelBgmStart()
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        try { clearPlayer?.stop(); clearPlayer?.release() } catch (_: Exception) {}
        clearPlayer = null
        bgmRunning = false
        sfxReady = false
        // sfxInitStarted は false に戻さない。SoundManagerはGameActivityごとに新規生成され
        // release後に再利用されないため、戻すと生成中スレッドとの競合でプールが
        // 作り直されてリークする危険だけが残る。
        releaseAllTracks()
    }
}
