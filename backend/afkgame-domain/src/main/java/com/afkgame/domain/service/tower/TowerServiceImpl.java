package com.afkgame.domain.service.tower;

import java.util.List;

import com.afkgame.domain.masterdata.Towers;
import com.afkgame.domain.repository.CharacterRepository;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.domain.repository.TowerClearRecordRepository;

/**
 * {@link TowerService} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_tower/list.md §5・同 select.md §7。
 *
 * <p><b>本クラスはテストリスト作成②-a で用意した表層であり、本体は未実装。</b>
 * 分岐は {@code TowerServiceImplTest} が Red のテストとして持ち、実装は製造②で行う。
 * {@code @Service} と {@code @Transactional} も製造②で付ける — {@link Towers} がまだ
 * {@code @Component} を持たず（YAML 未搭載）、実体が無いままスキャンに載せると
 * 結合テストのコンテキスト起動が壊れるため。
 */
public class TowerServiceImpl implements TowerService {

    private final Towers towers;

    private final TowerClearRecordRepository towerClearRecordRepository;

    private final PlayerRepository playerRepository;

    private final CharacterRepository characterRepository;

    /**
     * 依存を受け取る。
     *
     * @param towers                     塔マスター
     * @param towerClearRecordRepository 塔別クリア記録の Repository
     * @param playerRepository           プレイヤーの Repository
     * @param characterRepository        キャラクターの Repository
     */
    public TowerServiceImpl(Towers towers, TowerClearRecordRepository towerClearRecordRepository,
            PlayerRepository playerRepository, CharacterRepository characterRepository) {
        this.towers = towers;
        this.towerClearRecordRepository = towerClearRecordRepository;
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
    }

    @Override
    public List<TowerInfo> list(String playerId) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }

    @Override
    public TowerSelection select(String playerId, TowerSelectCommand command) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }
}
