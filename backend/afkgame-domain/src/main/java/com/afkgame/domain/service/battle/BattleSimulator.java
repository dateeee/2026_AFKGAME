package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * 正規シミュレーション（100tick以下）の戦闘処理。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §3.1・§5（1tick内のターン処理）、乱数源の扱いは
 * docs/tech/detail/tech_rng.md §2。
 *
 * <p>実装は {@link BattleSimulatorImpl}。
 */
public interface BattleSimulator {

    /**
     * 指定tick数ぶんのターン処理を行い、報酬を付与する。
     *
     * <p>乱数源は本メソッドの入口で1リクエストにつき1インスタンス生成し、協調オブジェクトへ
     * 引数で引き渡す（tech_rng.md §2）。
     *
     * @param player プレイヤー
     * @param party  出撃中のパーティ
     * @param ticks  処理するtick数
     * @return 付与した報酬の合計
     */
    BattleOutcome simulate(Player player, List<Character> party, int ticks);
}
