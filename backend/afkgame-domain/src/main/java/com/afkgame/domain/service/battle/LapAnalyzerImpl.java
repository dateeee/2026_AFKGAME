package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.masterdata.Items;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link LapAnalyzer} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは tech_offline.md §4.1 の期待値計算式
 * （期待与ダメージ・期待被ダメージ・撃破ターン数・ポーション消費モデル）を担う。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-iii（tick・オフラインの Green。docs/backlog/java_migration.md STEP 3-B）。
 * {@code @Service} も同じ回で付ける（{@link FloorCatalog} の実体がセグメント②まで無いため、
 * 先に Bean 登録するとコンテキスト起動が失敗する）。
 */
public class LapAnalyzerImpl implements LapAnalyzer {

    private final FloorCatalog floorCatalog;

    private final StatCalculator statCalculator;

    private final CharacterGrowth characterGrowth;

    private final Items items;

    private final PlayerRepository playerRepository;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param floorCatalog     階のデータを読む継ぎ目
     * @param statCalculator   確率・軽減率の上限適用
     * @param characterGrowth  次レベルまでの必要経験値の参照
     * @param items            ポーションの回復割合の参照
     * @param playerRepository プレイヤー設定（自動使用閾値）の参照
     * @param gameSettings     1tickあたりのターン数の供給元
     */
    public LapAnalyzerImpl(FloorCatalog floorCatalog, StatCalculator statCalculator,
            CharacterGrowth characterGrowth, Items items, PlayerRepository playerRepository,
            GameSettings gameSettings) {
        this.floorCatalog = floorCatalog;
        this.statCalculator = statCalculator;
        this.characterGrowth = characterGrowth;
        this.items = items;
        this.playerRepository = playerRepository;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LapAnalysis analyze(Player player, List<Character> party, int potionStock) {
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }
}
