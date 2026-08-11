package com.afkgame.domain.service.battle;

/**
 * 正規シミュレーション（100tick以下）の戦闘結果。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §3.1 手順2-l（報酬の付与）。
 *
 * <p>個別の戦闘ログは要素型が未確定のため本 record には含めない。tick API が
 * ログを返す回（docs/tech/detail/tech_polling.md）に構成要素を足す。
 *
 * @param gold 獲得したゴールドの合計
 * @param exp  獲得した経験値の合計
 */
public record BattleOutcome(long gold, long exp) {
}
