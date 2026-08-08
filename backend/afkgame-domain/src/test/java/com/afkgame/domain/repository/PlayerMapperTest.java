package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.Player;

/**
 * {@code players} テーブルの Mapper のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §1。
 * 塔外は {@code current_tower_id} / {@code current_floor} / {@code target_floor} /
 * {@code current_enemy_id} / {@code current_enemy_hp} が NULL になる（同 §1 の備考）。
 *
 * <p>観点: 全列の往復・NULL 許容列の両側・{@code uq_players_user_id}（1ユーザー1プレイヤー）・
 * 未登録の経路。
 */
class PlayerMapperTest extends MapperTestSupport {

    @Autowired
    private PlayerMapper playerMapper;

    /** 塔に潜っている状態の Player（NULL 許容列にすべて値が入る）。 */
    private Player 塔内のプレイヤー(String userId) {
        Player player = new Player();
        player.setId(uuid("player"));
        player.setUserId(userId);
        player.setGold(1000L);
        player.setCurrentTowerId("tower_001");
        player.setCurrentFloor(12);
        player.setTargetFloor(20);
        player.setTowerMode("stop_on_clear");
        player.setHpThreshold(0.4);
        player.setCurrentEnemyId("slime_001");
        player.setCurrentEnemyHp(37);
        player.setRunGold(250L);
        player.setHighestFloor(15);
        player.setLastTickAt(FIXED_NOW);
        player.setCreatedAt(FIXED_NOW);
        return player;
    }

    /** 塔の外にいる状態の Player（NULL 許容列がすべて NULL）。 */
    private Player 塔外のプレイヤー(String userId) {
        Player player = new Player();
        player.setId(uuid("player"));
        player.setUserId(userId);
        player.setGold(0L);
        player.setTowerMode("auto_repeat");
        player.setHpThreshold(0.3);
        player.setRunGold(0L);
        player.setHighestFloor(0);
        player.setLastTickAt(FIXED_NOW);
        player.setCreatedAt(FIXED_NOW);
        return player;
    }

    @Test
    void 塔内のプレイヤーを登録するとすべての列が往復する() {
        Player expected = 塔内のプレイヤー(givenUser());

        playerMapper.insert(expected);

        assertThat(playerMapper.selectById(expected.getId()))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void 塔外のプレイヤーは進行状況の列がNULLのまま往復する() {
        Player expected = 塔外のプレイヤー(givenUser());

        playerMapper.insert(expected);

        Player actual = playerMapper.selectById(expected.getId());
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(actual.getCurrentTowerId()).isNull();
        assertThat(actual.getCurrentFloor()).isNull();
        assertThat(actual.getTargetFloor()).isNull();
        assertThat(actual.getCurrentEnemyId()).isNull();
        assertThat(actual.getCurrentEnemyHp()).isNull();
    }

    @Test
    void 同じユーザーに2人目のプレイヤーは作れない() {
        String userId = givenUser();
        playerMapper.insert(塔外のプレイヤー(userId));

        assertThatThrownBy(() -> playerMapper.insert(塔外のプレイヤー(userId)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void ユーザーIDでプレイヤーを引ける() {
        String userId = givenUser();
        Player expected = 塔外のプレイヤー(userId);
        playerMapper.insert(expected);

        assertThat(playerMapper.selectByUserId(userId))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void 未登録のIDで引くとnullを返す() {
        assertThat(playerMapper.selectById("player_not_exists")).isNull();
    }

    @Test
    void プレイヤーを持たないユーザーIDで引くとnullを返す() {
        assertThat(playerMapper.selectByUserId(givenUser())).isNull();
    }
}
