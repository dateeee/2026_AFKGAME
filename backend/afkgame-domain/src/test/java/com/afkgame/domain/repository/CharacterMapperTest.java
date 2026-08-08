package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.afkgame.domain.model.Character;

/**
 * {@code characters} テーブルの Mapper のテスト。
 *
 * <p>列・NULL 可否の正は docs/tech/basic/tech_db/player.md §4。{@code rarity} は Phase 3 の列で
 * V1 スキーマに無いため対象外（アーキテクチャ不変条件「Phase厳守」）。
 *
 * <p>観点: 全列の往復・プレイヤー単位の取得件数（0件 / 1件 / 2件）・他プレイヤーの混入・未登録の経路。
 */
class CharacterMapperTest extends MapperTestSupport {

    @Autowired
    private CharacterMapper characterMapper;

    private Character キャラクター(String playerId, String name) {
        Character character = new Character();
        character.setId(uuid("character"));
        character.setPlayerId(playerId);
        character.setName(name);
        character.setType("melee");
        character.setLevel(1);
        character.setExp(0L);
        character.setHp(100);
        character.setMaxHp(100);
        character.setBaseAtk(10);
        character.setBaseDef(5);
        character.setBaseSpd(8);
        character.setLimitBreak(0);
        character.setSkillPoints(0);
        character.setCreatedAt(FIXED_NOW);
        return character;
    }

    @Test
    void キャラクターを登録するとすべての列が往復する() {
        Character expected = キャラクター(givenPlayer(), "冒険者");

        characterMapper.insert(expected);

        assertThat(characterMapper.selectById(expected.getId()))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    /**
     * 0件（初期化前・パーティ未編成）／1件（ゲスト作成直後）／2件（仲間加入後）を分ける。
     */
    @ParameterizedTest(name = "登録{0}件なら{0}件返す")
    @ValueSource(ints = {0, 1, 2})
    void プレイヤーIDで引くと登録件数分を返す(int count) {
        String playerId = givenPlayer();
        for (int i = 0; i < count; i++) {
            characterMapper.insert(キャラクター(playerId, "冒険者" + i));
        }

        assertThat(characterMapper.selectByPlayerId(playerId)).hasSize(count);
    }

    @Test
    void プレイヤーIDで引くと他プレイヤーのキャラクターは含まない() {
        String playerId = givenPlayer();
        Character mine = キャラクター(playerId, "自分のキャラ");
        characterMapper.insert(mine);
        characterMapper.insert(キャラクター(givenPlayer(), "他人のキャラ"));

        assertThat(characterMapper.selectByPlayerId(playerId))
                .extracting(Character::getId).containsExactly(mine.getId());
    }

    @Test
    void 未登録のIDで引くとnullを返す() {
        assertThat(characterMapper.selectById("character_not_exists")).isNull();
    }
}
