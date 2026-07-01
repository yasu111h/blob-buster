package com.teamhappslab.galaxyraid

/**
 * ストーリーモードの1ステージ分の難易度設定。
 *
 * 設計は projects/blob-buster/ステージ設計案.md 準拠。
 * 既存のエンドレスモード（ステージ概念なし）では使わず、null を渡す。
 *
 * @param stage ステージ番号（1〜5）
 * @param bossTriggerLevel この内部レベルに到達するとボス出現
 * @param enemyTierCap これより強い敵（enum順で上位）は出現させない
 * @param enemyHpMult 敵HP倍率（HP1の雑魚は1のまま、HP複数の敵にのみ実質的に効く）
 * @param enemyAttackIntervalMult 敵の攻撃間隔倍率（大きいほど撃たない＝易しい）
 * @param enemySpeedMult 敵の落下速度倍率
 * @param bossMaxHp ボスの最大HP
 * @param bossMaxPhase ボスが到達できる最大フェーズ（1〜3）
 * @param bossAttackIntervalMult ボスの攻撃間隔倍率（大きいほど弾幕が薄い＝易しい）
 * @param midHealLevel 道中、この内部レベル到達でHP回復（0=道中回復なし）
 * @param midHealAmount 道中回復量（99=全回復）
 * @param preBossHealAmount ボス出現直前の回復量（99=全回復、0=なし）
 * @param useAltBoss trueなら別ボス画像(boss2.png)を使う（Stage3〜5）
 */
data class StageConfig(
    val stage: Int,
    val bossTriggerLevel: Int,
    val enemyTierCap: BlobSize,
    val enemyHpMult: Float,
    val enemyAttackIntervalMult: Float,
    val enemySpeedMult: Float,
    val bossMaxHp: Int,
    val bossMaxPhase: Int,
    val bossAttackIntervalMult: Float,
    val midHealLevel: Int,
    val midHealAmount: Int,
    val preBossHealAmount: Int,
    val useAltBoss: Boolean,
    val enemySpawnLevelMult: Float = 1.0f,  // 全敵の出現レベルを一律で前倒しする倍率（小さいほど早く出る）
) {
    companion object {
        /** ステージ総数（STAGE FINALを含む） */
        const val MAX_STAGE = 6

        // ── 6ステージ分の難易度テーブル ──
        // 道中回復は全ステージなし(0,0)・ボス前回復は全ステージ+1・最大フェーズは全て3。
        // stage / bossLv / 敵上限 / HP倍 / 攻撃間隔倍 / 速度倍 / ボスHP / 最終P / ボス攻撃倍 / 道中回復Lv / 道中量 / ボス前量 / 別ボス画像 / 敵出現Lv倍率
        private val STAGES = listOf(
            StageConfig(1, 20, BlobSize.LARGE,  0.6f,  1.2f,  0.9f,   320, 3, 1.0f,   0,  0,  1, false, 1.0f),
            StageConfig(2, 25, BlobSize.HUGE,   0.75f, 1.1f,  0.9f,   480, 3, 1.0f,   0,  0,  1, false, 1.0f),
            StageConfig(3, 30, BlobSize.DRAGON, 0.9f,  1.0f,  1.0f,   650, 3, 1.0f,   0,  0,  1, false, 1.0f),
            StageConfig(4, 35, BlobSize.ENEMY8, 1.0f,  1.0f,  1.0f,   900, 3, 0.97f,  0,  0,  1, true,  0.9f),
            StageConfig(5, 37, BlobSize.ENEMY8, 1.0f,  0.95f, 1.05f, 1150, 3, 0.97f,  0,  0,  1, true,  0.7f),
            StageConfig(6, 40, BlobSize.ENEMY8, 1.1f,  0.9f,  1.05f, 1400, 3, 0.95f,  0,  0,  1, true,  0.7f),
        )

        /** ステージ番号(1〜5)に対応する設定を返す。範囲外はStage1にフォールバック。 */
        fun forStage(stage: Int): StageConfig =
            STAGES.getOrElse(stage - 1) { STAGES.first() }

        /** 表示用のステージ名 */
        fun titleOf(stage: Int): String = when (stage) {
            1 -> "STAGE 1"
            2 -> "STAGE 2"
            3 -> "STAGE 3"
            4 -> "STAGE 4"
            5 -> "STAGE 5"
            6 -> "FINAL STAGE"
            else -> "STAGE $stage"
        }

        /** 表示用のサブタイトル（難易度の雰囲気） */
        fun subtitleOf(stage: Int): String = when (stage) {
            1 -> "BEGINNER"
            2 -> "EASY"
            3 -> "NORMAL"
            4 -> "HARD"
            5 -> "EXTREME"
            6 -> "FINAL"
            else -> ""
        }

        // ── 攻撃頻度倍率（小さいほど高頻度） ──
        const val FREQ_BASE: Float = 0.9f    // 指定なし
        const val FREQ_MID: Float = 0.8f     // 中
        const val FREQ_HIGH: Float = 0.75f   // 高
        const val FREQ_HIGH_PLUS: Float = 0.7f   // 高+
        const val FREQ_MAX: Float = 0.65f    // 最高
        const val FREQ_SUPER_MAX: Float = 0.6f   // 超最高

        /**
         * ステージごとのボス3フェーズ分の攻撃構成。
         * 「5方向扇弾」=狙い5発の狭い扇(spread)、「扇弾」=広角7発の扇(wideSpread)として区別する。
         */
        fun bossPhasesFor(stage: Int): List<BossPhasePattern> = when (stage) {
            1 -> listOf(
                BossPhasePattern(spread = true),                                       // P1: 5連射＋5方向扇弾
                BossPhasePattern(spread = true),                                       // P2: 5連射＋5方向扇弾
                BossPhasePattern(shockwave = true),                                    // P3: 5連射＋衝撃波
            )
            2 -> listOf(
                BossPhasePattern(spread = true, wideSpread = true),                    // P1: 5連射＋5方向扇弾＋扇弾
                BossPhasePattern(ring = 12, shockwave = true),                         // P2: 5連射＋12リング＋衝撃波
                BossPhasePattern(spread = true, ring = 12, shockwave = true),          // P3: 5連射＋5方向扇弾＋12リング＋衝撃波
            )
            3 -> listOf(
                BossPhasePattern(burst = false, spread = true, ring = 16),             // P1: 5方向扇弾＋16リング（5連射/衝撃波なし）
                BossPhasePattern(wideSpread = true, ring = 16, shockwave = true),      // P2: 5連射＋扇弾＋16リング＋衝撃波
                BossPhasePattern(wideSpread = true, ring = 16, shockwave = true),      // P3: 5連射＋扇弾＋16リング＋衝撃波（頻度ベース）
            )
            4 -> listOf(
                BossPhasePattern(spread = true, ring = 12, shockwave = true),          // P1: 5連射＋5方向扇弾＋12リング＋衝撃波（ベース）
                BossPhasePattern(burst = false, spread = true, wideSpread = true, ring = 16), // P2: 5方向扇弾＋扇弾＋16リング（ベース・5連射/衝撃波なし）
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true), // P3: 全部（ベース）
            )
            5 -> listOf(
                BossPhasePattern(spread = true, wideSpread = true, ring = 12, shockwave = true),  // P1: ベース
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true, freqMult = FREQ_MID),  // P2: 頻度中
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true, freqMult = FREQ_MID),  // P3: 頻度中
            )
            else -> listOf( // STAGE FINAL（stage 6）
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true, freqMult = FREQ_MID),  // P1: 頻度中
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true, freqMult = FREQ_HIGH), // P2: 頻度高
                BossPhasePattern(spread = true, wideSpread = true, ring = 16, shockwave = true, freqMult = FREQ_MAX),  // P3: 頻度最高
            )
        }
    }
}

/**
 * ボスの1フェーズ分の攻撃構成。
 * @param burst 同方向5連射（基本的に常時ON）
 * @param spread 5方向扇弾（狙い5発の狭い扇）
 * @param wideSpread 扇弾（広角7発の扇）
 * @param ring リング弾の弾数（0=なし / 12 / 16）
 * @param shockwave 衝撃波
 * @param freqMult 攻撃頻度倍率（小さいほど高頻度）
 */
data class BossPhasePattern(
    val burst: Boolean = true,
    val spread: Boolean = false,
    val wideSpread: Boolean = false,
    val ring: Int = 0,
    val shockwave: Boolean = false,
    val freqMult: Float = StageConfig.FREQ_BASE,
)
