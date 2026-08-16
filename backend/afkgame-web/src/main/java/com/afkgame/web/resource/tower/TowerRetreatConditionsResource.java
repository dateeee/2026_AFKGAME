package com.afkgame.web.resource.tower;

/**
 * {@code PUT /api/tower/retreat-conditions} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_tower/control.md §11「撤退条件更新」手順1、要求の正は
 * docs/tech/detail/tech_tower.md §3、値域の正は docs/tech/detail/tech_numeric.md
 * 「入力値の範囲」。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p><b>本 record はテストリスト作成②-c で用意した表層であり、Bean Validation は未付与。</b>
 * 制約は {@code TowerRetreatConditionsResourceTest} が Red のテストとして持ち、製造②で満たす
 * （付ける制約は同テストクラスの Javadoc「製造工程への申し送り」が正）。
 *
 * @param hpThreshold HP閾値撤退のしきい値（{@code 0.0}〜{@code 1.0}・両端を含む。必須）
 */
public record TowerRetreatConditionsResource(Double hpThreshold) {
}
