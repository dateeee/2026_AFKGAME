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
 * {@link TowerRetreatConditionsResource} の単体テスト
 * （{@code PUT /api/tower/retreat-conditions} の入力検証）。
 *
 * <p>仕様: docs/tech/detail/tech_tower/control.md §11「撤退条件更新」手順1・§12 #7・#8、
 * 要求の正は docs/tech/detail/tech_tower.md §3、値域の正は
 * docs/tech/detail/tech_numeric.md「入力値の範囲」。制約違反が 422 になること自体は
 * docs/tech/basic/tech_api/common.md §5.0 と {@code ApiExceptionHandler} の責務で、
 * 本クラスは<b>制約が付いているか</b>だけを {@link Validator} で直接見る。
 *
 * <p>保存そのもの（塔外でも変更できる・{@code players.hpThreshold} へ反映する）は
 * {@code TowerServiceImpl} が持つため {@code TowerServiceImplTest}（afkgame-domain）が
 * §12 #8 の残りとして担当する。しきい値が撤退判定へ効くこと（§12 #9・#10）は
 * {@code FloorProgressionImplTest} が持つ。
 *
 * <p>分岐観点: 下限未満・上限超過・欠落の違反（#7）と、両端 {@code 0.0}・{@code 1.0} を含む
 * 範囲内なら違反しないこと（#8）。
 *
 * <p><b>「型違反」を本クラスで扱わない理由</b>: 数値でない JSON は Bean Validation ではなく
 * バインドで落ち、{@code ApiExceptionHandler} が 422 {@code VALIDATION_ERROR} へ変換する
 * （coding_standards_backend/exception.md §4 #10）。同ハンドラの単体テストが持つ経路で、
 * ここで二重に持たない。
 *
 * <p><b>適合側（#8）に制約メタデータの検証を添えている理由</b>: 制約が1つも付いていない record は
 * 「違反が無い」ことが常に成り立ち、そのままでは実装前に PASS してしまって分岐を検証できない
 * （.claude/skills/test-list/SKILL.md §4）。{@code hpThreshold} プロパティに制約が存在することを
 * あわせて確かめ、Red が成立する形にしている。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * {@link TowerRetreatConditionsResource} は record だけを置いてあり、
 * <b>Bean Validation は未付与</b>。製造②で次を満たす。<b>別名の表層を新設しない</b>。
 * <ul>
 *   <li>{@code hpThreshold}: {@code @NotNull} と {@code 0.0}〜{@code 1.0}（<b>両端を含む</b>）。
 *       標準制約なら {@code @DecimalMin("0.0") @DecimalMax("1.0")}
 *       （{@code inclusive} の既定は true）</li>
 *   <li>型は <b>{@code Double}</b>（プリミティブにしない）。{@code double} だと欠落が {@code 0.0} に
 *       化けて「HP閾値撤退の無効化」（§12 #9）と区別できなくなる</li>
 *   <li>Controller（{@code TowerApi}）は本セッションでは作らない（{@code TowerModeResourceTest}
 *       と同じ理由）。製造②で作り、422 応答としての検証を {@code TowerApiTest} へ足す</li>
 * </ul>
 */
@Tag("unit")
class TowerRetreatConditionsResourceTest {

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

    private static Set<ConstraintViolation<TowerRetreatConditionsResource>> validate(Double hpThreshold) {
        return validator.validate(new TowerRetreatConditionsResource(hpThreshold));
    }

    @Nested
    @DisplayName("入力検証")
    class TestValidation {

        /**
         * 範囲外・欠落は制約違反になる（応答は 422 で、しきい値は変更しない）。
         *
         * <p>分岐: tech_tower/control.md §12 #7
         */
        @ParameterizedTest(name = "hpThreshold={0} → 違反あり")
        @NullSource                      // hpThreshold の欠落
        @ValueSource(doubles = {
            -0.1,  // 下限をわずかに下回る
            1.1,   // 上限をわずかに上回る
            2.0,   // 明らかな範囲外
        })
        void test_範囲外や欠落は違反になる(Double hpThreshold) {
            assertThat(validate(hpThreshold)).isNotEmpty();
        }

        /**
         * 範囲内なら違反は出ない。両端（{@code 0.0}・{@code 1.0}）ちょうども含む。
         * 制約そのものが付いていることもあわせて確かめる（クラス Javadoc の「適合側」参照）。
         *
         * <p>分岐: tech_tower/control.md §12 #8
         */
        @ParameterizedTest(name = "hpThreshold={0} → 違反なし")
        @ValueSource(doubles = {
            0.0,  // 下限ちょうど（HP閾値撤退の無効化）
            0.3,  // 既定値（tech_db/player.md §1）
            1.0,  // 上限ちょうど（全快でない限り毎階撤退）
        })
        void test_範囲内なら違反しない(Double hpThreshold) {
            assertThat(validate(hpThreshold)).isEmpty();
            assertThat(validator.getConstraintsForClass(TowerRetreatConditionsResource.class)
                    .getConstraintsForProperty("hpThreshold")).isNotNull();
        }
    }
}
