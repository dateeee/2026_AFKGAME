package com.afkgame.web.resource.tower;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * {@link TowerModeResource} の単体テスト（{@code PUT /api/tower/mode} の入力検証）。
 *
 * <p>仕様: docs/tech/detail/tech_tower/control.md §11「進行モード切替」手順1・§12 #3・#4、
 * 要求の正は docs/tech/detail/tech_tower.md §3。制約違反が 422 になること自体は
 * docs/tech/basic/tech_api/common.md §5.0 と {@code ApiExceptionHandler} の責務で、
 * 本クラスは<b>制約が付いているか</b>だけを {@link Validator} で直接見る
 * （{@code TowerSelectResourceTest} と同じ分担）。
 *
 * <p>状態に依存する検証（塔外なら {@code TOWER_NOT_IN_TOWER}・入塔中なら即時反映）は §12 #5・#6 で、
 * {@code TowerServiceImpl} が持つため {@code TowerServiceImplTest}（afkgame-domain）が担当する。
 *
 * <p>分岐観点: 列挙値以外・欠落・空文字の違反（#3）と、2値のいずれかなら違反しないこと（#4）。
 *
 * <p><b>適合側（#4）に制約メタデータの検証を添えている理由</b>: 制約が1つも付いていない record は
 * 「違反が無い」ことが常に成り立ち、そのままでは実装前に PASS してしまって分岐を検証できない
 * （.claude/skills/test-list/SKILL.md §4）。{@code mode} プロパティに制約が存在することを
 * あわせて確かめ、Red が成立する形にしている。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * {@link TowerModeResource} は record だけを置いてあり、<b>Bean Validation は未付与</b>。
 * 製造②で次を満たす。<b>別名の表層を新設しない</b>。
 * <ul>
 *   <li>{@code mode}: {@code @NotNull} と、{@code auto_repeat} / {@code stop_on_clear} の
 *       いずれかであること。標準制約だけで表すなら
 *       {@code @Pattern(regexp = "auto_repeat|stop_on_clear")}（{@code @Pattern} は null を
 *       素通りさせるため {@code @NotNull} との併記が要る）。
 *       <b>select と違い既定値は補わない</b> — {@code PUT /api/tower/mode} は {@code mode} が
 *       必須で、欠落は 422（control.md §11 手順1）</li>
 *   <li>Controller（{@code TowerApi}）は本セッションでは作らない（{@code TowerSelectResourceTest}
 *       と同じ理由。{@code TowerService} の Bean が無いまま {@code @RestController} を置くと
 *       結合テストのコンテキスト起動が壊れる）。製造②で {@code @Service} と同じ回に作り、
 *       422 応答としての検証を {@code TowerApiTest} へ足す（本クラスは残す）</li>
 * </ul>
 */
@Tag("unit")
class TowerModeResourceTest {

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

    private static Set<ConstraintViolation<TowerModeResource>> validate(String mode) {
        return validator.validate(new TowerModeResource(mode));
    }

    @Nested
    @DisplayName("入力検証")
    class TestValidation {

        /**
         * 2値以外・欠落・空文字は制約違反になる（応答は 422 で、モードは変更しない）。
         * 欠落（null）は select と違い既定値で補わない。
         *
         * <p>分岐: tech_tower/control.md §12 #3
         */
        @ParameterizedTest(name = "mode={0} → 違反あり")
        @NullSource                        // mode の欠落（既定値で補わない）
        @ValueSource(strings = {
            "repeat_forever",  // 2値以外の未知の値
            "AUTO_REPEAT",     // 大文字（列挙値は小文字スネークケース）
            "",                // 空文字
        })
        void test_2値以外や欠落は違反になる(String mode) {
            assertThat(validate(mode)).isNotEmpty();
        }

        /**
         * 2値のいずれかなら違反は出ず、状態の判定（§12 #5・#6）へ進む。
         * 制約そのものが付いていることもあわせて確かめる（クラス Javadoc の「適合側」参照）。
         *
         * <p>分岐: tech_tower/control.md §12 #4
         */
        @ParameterizedTest(name = "mode={0} → 違反なし")
        @ValueSource(strings = {
            "auto_repeat",    // 自動周回
            "stop_on_clear",  // 目標到達で停止
        })
        void test_2値のいずれかなら違反しない(String mode) {
            assertThat(validate(mode)).isEmpty();
            assertThat(validator.getConstraintsForClass(TowerModeResource.class)
                    .getConstraintsForProperty("mode")).isNotNull();
        }
    }
}
