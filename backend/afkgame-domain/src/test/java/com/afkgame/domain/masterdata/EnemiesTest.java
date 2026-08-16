package com.afkgame.domain.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * {@link Enemies} の単体テスト。
 *
 * <p>仕様: docs/data/towers/001_ゴブリンの塔.md §2（敵データ）。
 * 実際に配布する {@code masterdata/enemies.yml} が定義書どおりの値かを固定する
 * （YAML は再ビルドなしで差し替えられるため、値の取り違えをここで検出する）。
 */
@Tag("unit")
class EnemiesTest {

    /** Phase 1 のクリティカル率は敵側も基礎値5%（000_テンプレート.md §2・tech_rng.md §6）。 */
    private static final double BASE_CRIT_RATE = 0.05;

    private static final Validator VALIDATOR =
            Validation.buildDefaultValidatorFactory().getValidator();

    private final MasterDataLoader loader = new MasterDataLoader(VALIDATOR);

    /**
     * ゴブリンの塔の敵9種を、定義書 §2 の表そのままの値で公開する。
     *
     * <p>1行 = 表の1行。列の並びも表に合わせてあるので、定義書と目視で突き合わせられる。
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "slime,         スライム,           1,  20,  5,  2,  3,  5,  10",
            "goblin,        ゴブリン,           2,  35,  8,  4,  5,  8,  18",
            "wolf,          オオカミ,           3,  30, 12,  3,  8, 10,  22",
            "goblin_archer, ゴブリンアーチャー, 4,  40, 14,  5,  7, 12,  28",
            "dire_wolf,     ダイアウルフ,       6,  60, 18,  8, 10, 18,  40",
            "hobgoblin,     ホブゴブリン,       8,  90, 22, 12,  7, 25,  55",
            "wolf_leader,   ウルフリーダー,     9, 100, 25, 10, 12, 30,  65",
            "goblin_shaman, ゴブリンシャーマン, 10, 80, 28,  8,  9, 28,  60",
            "goblin_king,   ゴブリンキング,     12, 200, 32, 16, 8, 80, 150",
    })
    @DisplayName("ゴブリンの塔の敵を定義書どおりの値で公開する")
    void test_敵を定義書どおりに公開する(String id, String name, int level, int hp, int atk, int def,
            int spd, long gold, long exp) {
        EnemyData actual = new Enemies(loader).get(id);

        assertThat(actual).isNotNull();
        assertThat(actual.name()).isEqualTo(name);
        assertThat(actual.level()).isEqualTo(level);
        assertThat(actual.hp()).isEqualTo(hp);
        assertThat(actual.atk()).isEqualTo(atk);
        assertThat(actual.def()).isEqualTo(def);
        assertThat(actual.spd()).isEqualTo(spd);
        assertThat(actual.gold()).isEqualTo(gold);
        assertThat(actual.exp()).isEqualTo(exp);
        assertThat(actual.critRate()).isEqualTo(BASE_CRIT_RATE);
    }

    /**
     * 未定義の敵IDは {@code null} を返す（{@code CharacterTypes#get} と同じレジストリの形）。
     *
     * <p>Phase 2 以降の塔の敵はまだ載せないため、参照側は不在を扱えなければならない。
     */
    @Test
    @DisplayName("未定義の敵IDは null を返す")
    void test_未定義の敵IDはnull() {
        assertThat(new Enemies(loader).get("unknown_enemy")).isNull();
    }

    /**
     * 読み込み元が無ければ起動を中止する（マスターデータの欠損を実行時へ持ち込まない）。
     *
     * <p>分岐一覧の行ではなく、レジストリ共通の横断検証（{@link Items} と同じ扱い）。
     */
    @Test
    @DisplayName("リソースが存在しなければ例外（起動を中止する）")
    void test_リソース不在で例外() {
        assertThatThrownBy(() -> new Enemies(loader, "masterdata/not-exists.yml"))
                .isInstanceOf(MasterDataException.class)
                .hasMessageContaining("masterdata/not-exists.yml");
    }
}
