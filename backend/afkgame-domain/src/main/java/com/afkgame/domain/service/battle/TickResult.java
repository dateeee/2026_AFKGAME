package com.afkgame.domain.service.battle;

/**
 * tick 処理1回の結果。
 *
 * <p>仕様: docs/tech/detail/tech_tick.md §1〜§2、処理方式の切り替えは
 * docs/tech/detail/tech_offline.md §2。
 *
 * <p>戦闘処理を行わなかった経路（未処理tick0・塔外待機・パーティ空・時刻の巻き戻し）では
 * {@code outcome} と {@code offlineSummary} がともに {@code null} になる。正規シミュレーションなら
 * {@code outcome} だけ、簡略計算なら {@code offlineSummary} だけが入り、両方が入ることはない。
 *
 * @param pendingTicks   処理した未処理tick数
 * @param capped         24時間の上限クランプが発生したか（§2）
 * @param outcome        正規シミュレーションの結果。簡略計算・戦闘なしなら {@code null}
 * @param offlineSummary 簡略計算のサマリー。正規シミュレーション・戦闘なしなら {@code null}
 */
public record TickResult(int pendingTicks, boolean capped, BattleOutcome outcome,
        OfflineSummary offlineSummary) {
}
