package com.afkgame.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import com.afkgame.domain.model.PlayerSettings;

/**
 * {@code player_settings} テーブルの Mapper のテスト。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §2。
 * {@code auto_sell_rarity} の NULL は「自動売却なし」を表す（同 §2 の備考）。
 *
 * <p>観点: 全列の往復・{@code auto_sell_rarity} の NULL と非 NULL の両側・
 * {@code uq_player_settings_player_id}（1プレイヤー1設定）・未登録の経路。
 */
class PlayerSettingsMapperTest extends MapperTestSupport {

    @Autowired
    private PlayerSettingsMapper playerSettingsMapper;

    private PlayerSettings 設定(String playerId, String autoSellRarity) {
        PlayerSettings settings = new PlayerSettings();
        settings.setId(uuid("settings"));
        settings.setPlayerId(playerId);
        settings.setPotionThreshold(0.3);
        settings.setBattleLogCount(50);
        settings.setToastEnabled(true);
        settings.setAutoSellRarity(autoSellRarity);
        return settings;
    }

    @Test
    void 自動売却を設定した状態がすべての列で往復する() {
        PlayerSettings expected = 設定(givenPlayer(), "uncommon");

        playerSettingsMapper.insert(expected);

        assertThat(playerSettingsMapper.selectByPlayerId(expected.getPlayerId()))
                .usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void 自動売却なしの設定はレアリティがNULLのまま往復する() {
        PlayerSettings expected = 設定(givenPlayer(), null);

        playerSettingsMapper.insert(expected);

        PlayerSettings actual = playerSettingsMapper.selectByPlayerId(expected.getPlayerId());
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(actual.getAutoSellRarity()).isNull();
    }

    @Test
    void 同じプレイヤーに2件目の設定は作れない() {
        String playerId = givenPlayer();
        playerSettingsMapper.insert(設定(playerId, null));

        assertThatThrownBy(() -> playerSettingsMapper.insert(設定(playerId, null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 設定を持たないプレイヤーIDで引くとnullを返す() {
        assertThat(playerSettingsMapper.selectByPlayerId(givenPlayer())).isNull();
    }
}
