package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link OfflineCalculator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは周回ループの制御（全滅・在庫・レベルアップ・
 * 残tickの判定）だけを担い、1周回ぶんの期待値計算は {@link LapAnalyzer} へ閉じ込める。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-iii（tick・オフラインの Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} も同じ回で付ける。
 */
public class OfflineCalculatorImpl implements OfflineCalculator {

    private final LapAnalyzer lapAnalyzer;

    private final CharacterGrowth characterGrowth;

    private final PlayerRepository playerRepository;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param lapAnalyzer      1周回ぶんの期待値計算
     * @param characterGrowth  レベルアップの適用
     * @param playerRepository ポーション在庫の参照
     * @param gameSettings     オフライン効率などの供給元
     */
    public OfflineCalculatorImpl(LapAnalyzer lapAnalyzer, CharacterGrowth characterGrowth,
            PlayerRepository playerRepository, GameSettings gameSettings) {
        this.lapAnalyzer = lapAnalyzer;
        this.characterGrowth = characterGrowth;
        this.playerRepository = playerRepository;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OfflineSummary calculate(Player player, List<Character> party, int ticks) {
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void applyIdleRegen(List<Character> party, int ticks) {
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }
}
