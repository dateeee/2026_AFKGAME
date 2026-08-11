package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * 簡略計算の1周回ぶんの期待値を求める。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §4 手順3a〜3c・§4.1（期待値計算式）。
 * 期待与ダメージ・期待被ダメージ・回復・ポーション消費モデルを本インタフェースへ閉じ込め、
 * 周回ループの制御は {@link OfflineCalculator} が持つ。
 *
 * <p>実装は {@link LapAnalyzerImpl}。
 */
public interface LapAnalyzer {

    /**
     * 現在の編成で1周回したときの期待値を求める。
     *
     * @param player      プレイヤー
     * @param party       出撃中のパーティ
     * @param potionStock 残りのポーション在庫数
     * @return 1周回ぶんの分析結果
     */
    LapAnalysis analyze(Player player, List<Character> party, int potionStock);
}
