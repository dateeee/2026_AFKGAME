package com.afkgame.domain.service.battle;

/**
 * ダメージの向き。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §2「丸め規則一覧」。最低ダメージ保証の下限が
 * 向きによって分かれるため、計算へ向きを渡して区別する。
 */
public enum DamageDirection {

    /** 味方 → 敵。最低1ダメージを保証する。 */
    ALLY_TO_ENEMY,

    /** 敵 → 味方。0ダメージを許容する。 */
    ENEMY_TO_ALLY
}
