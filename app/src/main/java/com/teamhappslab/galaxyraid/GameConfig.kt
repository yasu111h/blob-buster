package com.teamhappslab.galaxyraid

object GameConfig {
    /** 敵弾スピード倍率（1.0f = デフォルト、大きいほど速い） */
    const val ENEMY_BULLET_SPEED_MULT: Float = 1.0f

    // ── ボス（ストーリーモード・レベル50） ─────────────────
    /** ボスが出現するレベル */
    const val BOSS_TRIGGER_LEVEL: Int = 50
    /** ボスの最大HP（プレイヤー弾1発=1ダメージ。弾段数1で毎秒12発 → 約60〜90秒の戦闘想定） */
    const val BOSS_MAX_HP: Int = 900
    /** ボス撃破ボーナススコア */
    const val BOSS_DEFEAT_BONUS: Int = 10000
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
}
