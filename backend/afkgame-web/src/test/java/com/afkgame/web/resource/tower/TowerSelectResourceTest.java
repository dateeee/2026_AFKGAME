package com.afkgame.web.resource.tower;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * {@link TowerSelectResource} の単体テスト（{@code POST /api/tower/select} の入力検証）。
 *
 * <p>仕様: docs/tech/detail/tech_tower/select.md §7 手順1・§8 #1・#2、要求の正は
 * docs/tech/detail/tech_tower.md §3。制約違反が 422 になること自体は
 * docs/tech/basic/tech_api/common.md §5.0 と {@code ApiExceptionHandler} の責務で、
 * 本クラスは<b>制約が付いているか</b>だけを {@link Validator} で直接見る。
 *
 * <p>状態に依存する検証（入塔中・未知の塔・未解放・上限超過・全滅）は {@code TowerServiceImpl}
 * が持つため {@code TowerServiceImplTest}（afkgame-domain）が担当する。
 *
 * <p>分岐観点: 必須・値域・列挙値の違反（{@code towerId} 欠落 / {@code targetFloor} 欠落・1未満 /
 * {@code mode} が2値以外）と、すべて適合した場合の既定値（{@code mode} 省略 = {@code auto_repeat}）。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * {@code TowerSelectResource} は record だけを置いてあり、<b>Bean Validation と既定値は未付与</b>。
 * 製造②で次を満たす。<b>別名の表層を新設しない</b>。
 * <ul>
 *   <li>{@code towerId}: {@code @NotBlank}</li>
 *   <li>{@code targetFloor}: {@code @NotNull @Min(1)}。<b>下限だけ</b>をここで見る
 *       （上限は到達状況に依存するため Service が {@code 400 TOWER_INVALID_FLOOR} で判定する。
 *       §7 手順1・手順6）。型は {@code Integer} で、非整数の JSON は Bean Validation ではなく
 *       バインドで落ちる（{@code ApiExceptionHandler} が 422 {@code VALIDATION_ERROR} へ変換。
 *       exception.md §4 #10）</li>
 *   <li>{@code mode}: {@code auto_repeat} / {@code stop_on_clear} のいずれか。null は素通りさせ
 *       （省略可のため）、<b>コンパクトコンストラクタで {@code auto_repeat} を補う</b>。
 *       標準制約だけで表すなら {@code @Pattern(regexp = "auto_repeat|stop_on_clear")}、
 *       カスタム制約を作る場合は test.md §5 新規 #2 のとおり本クラスへ検証を足す</li>
 *   <li>{@code difficulty}: <b>Phase 5〜</b>のイベントダンジョン用。制約を付けない
 *       （塔種別との整合は Service が {@code TOWER_INVALID_DIFFICULTY} で判定する。§8 #9・#10）</li>
 *   <li>Controller（{@code TowerApi}）は本セッションでは作らない。{@code @RestController} は
 *       {@code SpringMvcConfig} のスキャン対象で、{@code TowerService} の Bean が無いまま
 *       置くと結合テストのコンテキスト起動が壊れるため、製造②で {@code @Service} と同じ回に作る。
 *       そのとき 422 応答としての検証を {@code TowerApiTest} へ足す（本クラスは残す）</li>
 * </ul>
 */
@Tag("unit")
class TowerSelectResourceTest {

    private static ValidatorFactory factory;

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    private static Set<ConstraintViolation<TowerSelectResource>> validate(TowerSelectResource resource) {
        return validator.validate(resource);
    }

    @Nested
    @DisplayName("入力検証")
    class TestValidation {

        /**
         * 必須の欠落・値域外・列挙値以外は制約違反になる（応答は 422）。
         *
         * <p>分岐: tech_tower/select.md §8 #1
         */
        @ParameterizedTest(name = "towerId={0} targetFloor={1} mode={2} → 違反あり")
        @CsvSource(nullValues = "null", value = {
            "null,         1, auto_repeat",     // towerId 欠落
            "'',           1, auto_repeat",     // towerId が空文字
            "goblin_tower, null, auto_repeat",  // targetFloor 欠落
            "goblin_tower, 0, auto_repeat",     // targetFloor が下限（1）未満
            "goblin_tower, 1, repeat_forever",  // mode が2値以外
        })
        void test_必須と値域の違反を検出する(String towerId, Integer targetFloor, String mode) {
            var violations = validate(new TowerSelectResource(towerId, targetFloor, mode, null));

            assertThat(violations).isNotEmpty();
        }

        /**
         * すべて適合していれば違反は出ず、{@code mode} の省略は {@code auto_repeat} として扱う。
         *
         * <p>分岐: tech_tower/select.md §8 #2
         */
        @Test
        void test_適合していれば違反が無くmodeの省略は自動周回になる() {
            var resource = new TowerSelectResource("goblin_tower", 1, null, null);

            assertThat(validate(resource)).isEmpty();
            assertThat(resource.mode()).isEqualTo("auto_repeat");
        }
    }
}
