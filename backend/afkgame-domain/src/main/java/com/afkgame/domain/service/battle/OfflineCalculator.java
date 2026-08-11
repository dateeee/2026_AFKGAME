package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * 簡略計算（101tick以上）のオフライン処理。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §2・§4（簡略計算の詳細アルゴリズム）。
 * 個別の戦闘ログは生成せず、サマリーだけを返す。
 *
 * <p>実装は {@link OfflineCalculatorImpl}。
 */
public interface OfflineCalculator {

    /**
     * 指定tick数ぶんを周回単位で計算し、報酬をまとめて求める。
     *
     * @param player プレイヤー
     * @param party  出撃中のパーティ
     * @param ticks  処理するtick数
     * @return 周回の結果サマリー
     */
    OfflineSummary calculate(Player player, List<Character> party, int ticks);

    /**
     * 待機中のHP自然回復を適用する。
     *
     * <p>§4 手順4 の {@code HP += (maxHP × 0.02 + DEF × 0.5) × 待機tick数}（maxHP で上限）。
     * 塔外待機のtickと、全滅で破棄した残tickの双方から使う。
     *
     * @param party 対象のパーティ
     * @param ticks 待機したtick数
     */
    void applyIdleRegen(List<Character> party, int ticks);
}
