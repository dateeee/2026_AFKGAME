package com.afkgame.domain.service.battle;

/**
 * tick 処理のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_tick.md §1〜§4（未処理tick数の決定・上限クランプ・
 * 同時実行制御・トランザクション境界）、正規シミュレーションと簡略計算の切り替えは
 * docs/tech/detail/tech_offline.md §2。
 *
 * <p>実装は {@link BattleServiceImpl}。
 */
public interface BattleService {

    /**
     * 前回tickからの経過ぶんをまとめて処理する。
     *
     * <p>1リクエスト = 1トランザクションで、対象プレイヤーの行を占有ロックして読む（§3.1・§4）。
     * ロック競合で待機上限を超えた場合はビジネス例外（{@code BATTLE_TICK_BUSY}）になる。
     *
     * @param playerId プレイヤーID
     * @return 処理した未処理tick数と、正規シミュレーションの結果または簡略計算のサマリー
     */
    TickResult tick(String playerId);
}
