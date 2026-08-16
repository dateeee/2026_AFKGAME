package com.afkgame.domain.service.tower;

import java.util.List;
import java.util.Random;

import com.afkgame.domain.masterdata.Towers;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.TowerClearRecordRepository;
import com.afkgame.domain.service.battle.FloorProgression;

/**
 * {@link FloorProgression} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_tower/progress.md §9（階クリア後の判定順序）。
 * エンカウント抽選は docs/tech/detail/tech_battle.md §3.2、全滅時の後始末は
 * docs/tech/detail/tech_state.md §3。
 *
 * <p><b>本クラスはテストリスト作成②-b で用意した表層であり、本体は未実装。</b>
 * {@code onEnemyDefeated} の分岐は {@code FloorProgressionImplTest} が Red のテストとして持ち、
 * 実装は製造②で行う。{@code @Service} も製造②で付ける — {@link Towers} がまだ
 * {@code @Component} を持たず（YAML 未搭載）、実体が無いままスキャンに載せると結合テストの
 * コンテキスト起動が壊れるため（{@code TowerServiceImpl} と同じ理由）。
 *
 * <p><b>コンストラクタの依存は {@code onEnemyDefeated} が要るものだけ</b>。
 * {@code ensureEncounter}（階のエンカウントプール）と {@code onPartyWiped}（全滅ペナルティ）は
 * 別セグメントが分岐一覧を持つため、必要な依存はその回に足す。
 */
public class FloorProgressionImpl implements FloorProgression {

    private final Towers towers;

    private final TowerClearRecordRepository towerClearRecordRepository;

    /**
     * 依存を受け取る。
     *
     * @param towers                     塔マスター（総階数・環境効果の供給元）
     * @param towerClearRecordRepository 塔別クリア記録の Repository
     */
    public FloorProgressionImpl(Towers towers,
            TowerClearRecordRepository towerClearRecordRepository) {
        this.towers = towers;
        this.towerClearRecordRepository = towerClearRecordRepository;
    }

    @Override
    public void ensureEncounter(Player player, Random rng) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }

    @Override
    public void onEnemyDefeated(Player player, List<Character> party, Random rng) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }

    @Override
    public void onPartyWiped(Player player, List<Character> party) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }
}
