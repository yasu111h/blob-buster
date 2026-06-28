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
) {
    companion object {
        /** ステージ総数 */
        const val MAX_STAGE = 5

        // ── 5ステージ分の難易度テーブル（ステージ設計案.md 3-1準拠） ──
        // stage / bossLv / 敵上限 / HP倍 / 攻撃間隔倍 / 速度倍 / ボスHP / 最終P / ボス攻撃倍 / 道中回復Lv / 道中量 / ボス前量 / 別ボス画像
        private val STAGES = listOf(
            StageConfig(1, 20, BlobSize.LARGE,  0.6f,  1.5f,  0.85f,  320, 1, 1.5f,  10, 99, 99, false),
            StageConfig(2, 23, BlobSize.HUGE,   0.75f, 1.3f,  0.9f,   480, 2, 1.25f,  0,  0,  2, false),
            StageConfig(3, 27, BlobSize.DRAGON, 0.9f,  1.15f, 1.0f,   650, 3, 1.1f,   0,  0,  1, true),
            StageConfig(4, 32, BlobSize.ENEMY8, 1.0f,  1.0f,  1.0f,   900, 3, 1.0f,   0,  0,  0, true),
            StageConfig(5, 40, BlobSize.ENEMY8, 1.2f,  0.85f, 1.1f,  1150, 3, 0.85f,  0,  0,  0, true),
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
            else -> "STAGE $stage"
        }

        /** 表示用のサブタイトル（難易度の雰囲気） */
        fun subtitleOf(stage: Int): String = when (stage) {
            1 -> "BEGINNER"
            2 -> "EASY"
            3 -> "NORMAL"
            4 -> "HARD"
            5 -> "EXTREME"
            else -> ""
        }
    }
}
