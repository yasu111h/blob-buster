package com.teamhappslab.galaxyraid

object GameConfig {
    /** 敵弾スピード倍率（1.0f = デフォルト、大きいほど速い） */
    const val ENEMY_BULLET_SPEED_MULT: Float = 0.75f

    // ── 敵弾の速さノブ ───────────────────────────────────
    // 敵・ボスが撃つ弾の「飛ぶ速さ」だけを変える倍率（移動速度や発射頻度には影響しない）。
    // 1.0=従来 / 小さいほど弾がゆっくり飛んで避けやすくなる。例: 0.75で25%遅い。
    // ※全体速度ノブ GameThread.GAME_SPEED とは別物。こちらは「弾速だけ」を調整する。

    // ── スコア⇔レベル換算 ─────────────────────────────────
    /**
     * レベルnに到達するために必要なスコア閾値。
     * BlobManager.levelThreshold と同一式（80 * (n-1) * n / 2）。
     */
    fun levelThreshold(n: Int): Int = 80 * (n - 1) * n / 2

    /**
     * 任意のスコアから到達レベルを逆算する（最小レベル1）。
     * レベルはスコアから一意に決まるため、過去に保存されたスコアにも適用できる。
     */
    fun levelForScore(score: Int): Int {
        if (score <= 0) return 1
        var n = 1
        while (levelThreshold(n + 1) <= score) n++
        return n
    }

    // ── ボス（ストーリーモード・レベル50） ─────────────────
    /** ボスが出現するレベル */
    const val BOSS_TRIGGER_LEVEL: Int = 50
    /** ボスの最大HP（プレイヤー弾1発=1ダメージ。弾段数1で毎秒12発 → 約60〜90秒の戦闘想定） */
    const val BOSS_MAX_HP: Int = 900
    /** ボス撃破ボーナスの基準値（ステージ1相当） */
    const val BOSS_DEFEAT_BONUS_BASE: Int = 8000
    /**
     * ステージごとのボス撃破ボーナス。後半ステージほど高くする（S1=8000 … S5=40000）。
     * ステージ番号が0以下（エンドレス等）の場合は基準値を返す。
     */
    fun bossDefeatBonus(stage: Int): Int = BOSS_DEFEAT_BONUS_BASE * stage.coerceAtLeast(1)
    /** ボスの横幅（画面幅比） */
    const val BOSS_WIDTH_RATIO: Float = 0.60f
    /** ボス戦中の敵弾上限（リング弾で同時数が増えるため通常より多め） */
    const val BOSS_MAX_ENEMY_BULLETS: Int = 70
    /** レベル50到達後、残存敵が掃けるのを待つ最大フレーム数（超えたら強制的にボス登場） */
    const val BOSS_WAIT_MAX_FRAMES: Int = 300   // 5秒 @ 60fps
    /** WARNING演出の長さ（フレーム） */
    const val BOSS_WARNING_FRAMES: Int = 120    // 2秒 @ 60fps
    /** フェーズ移行直後の無敵時間（フレーム） */
    const val BOSS_PHASE_INVINCIBLE_FRAMES: Int = 60
    /** 撃破演出（連続爆発）の長さ（フレーム） */
    const val BOSS_DYING_FRAMES: Int = 150      // 2.5秒 @ 60fps

    // ── ボス攻撃間隔の基準値（データ駆動フェーズ用・フレーム@60fps） ──
    // 実際の間隔 = 基準値 × フェーズの攻撃頻度倍率 × ステージのボス攻撃間隔倍率
    /** 同方向5連射の基準間隔（約1.0秒） */
    const val BOSS_ATK_BURST_BASE: Int = 60
    /** 5方向扇弾（狭い狙い扇）の基準間隔（約1.4秒） */
    const val BOSS_ATK_SPREAD_BASE: Int = 84
    /** 扇弾（広角扇）の基準間隔（約1.6秒） */
    const val BOSS_ATK_WIDE_SPREAD_BASE: Int = 96
    /** リング弾の基準間隔（約1.85秒） */
    const val BOSS_ATK_RING_BASE: Int = 110
    /** 衝撃波の基準間隔（約2.2秒） */
    const val BOSS_ATK_SHOCKWAVE_BASE: Int = 130

    // ── ボス攻撃間隔（旧・フェーズ固定方式。現在は未使用） ──────────
    // フェーズ1（HP > 66%）
    /** 同方向5連射の間隔（約1.2秒） */
    const val BOSS_P1_BURST_INTERVAL: Int = 72
    /** 5-way扇状弾の間隔（約1.6秒） */
    const val BOSS_P1_SPREAD_INTERVAL: Int = 96
    // フェーズ2（33%〜66%）
    /** 同方向5連射の間隔（約1.0秒） */
    const val BOSS_P2_BURST_INTERVAL: Int = 60
    /** リング弾12発の間隔（約2.0秒） */
    const val BOSS_P2_RING_INTERVAL: Int = 120
    /** 衝撃波の間隔（約2.5秒） */
    const val BOSS_P2_SHOCKWAVE_INTERVAL: Int = 150
    // フェーズ3（HP < 33%）
    /** 同方向5連射の間隔（約1.0秒） */
    const val BOSS_P3_BURST_INTERVAL: Int = 60
    /** 5-way扇状弾の間隔（約1.1秒） */
    const val BOSS_P3_SPREAD_INTERVAL: Int = 66
    /** リング弾16発の間隔（約1.8秒） */
    const val BOSS_P3_RING_INTERVAL: Int = 108
    /** 衝撃波の間隔（約2.0秒） */
    const val BOSS_P3_SHOCKWAVE_INTERVAL: Int = 120
    /** ボス衝撃波の扇開き角度（通常敵の10fより強化） */
    const val BOSS_SHOCKWAVE_SWEEP_ANGLE: Float = 14f
    /** 画面内の衝撃波数の上限（通常敵と同じ） */
    const val BOSS_MAX_SHOCKWAVES: Int = 3

    // ── ボス戦中の雑魚敵（アイテム供給用） ──────────────────
    /** 雑魚敵の出現間隔（約11秒 @60fps） */
    const val BOSS_MINION_INTERVAL: Int = 660
    /** ボス戦中に画面上に存在できる雑魚敵の最大数 */
    const val BOSS_MINION_MAX: Int = 2

    // ── ボス撃破爆発演出 ─────────────────────────────────
    /** 爆発バーストの発生間隔（フレーム） */
    const val BOSS_EXPLOSION_BURST_INTERVAL: Int = 8
    /** 撃破演出の最後に入れる白フラッシュの長さ（約0.3秒） */
    const val BOSS_FLASH_FRAMES: Int = 18
}
