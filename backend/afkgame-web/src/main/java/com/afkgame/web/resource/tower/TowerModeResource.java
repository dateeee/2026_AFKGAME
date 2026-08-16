package com.afkgame.web.resource.tower;

/**
 * {@code PUT /api/tower/mode} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_tower/control.md §11「進行モード切替」手順1、要求の正は
 * docs/tech/detail/tech_tower.md §3。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p><b>本 record はテストリスト作成②-c で用意した表層であり、Bean Validation は未付与。</b>
 * 制約は {@code TowerModeResourceTest} が Red のテストとして持ち、製造②で満たす
 * （付ける制約は同テストクラスの Javadoc「製造工程への申し送り」が正）。
 *
 * @param mode 周回モード（{@code auto_repeat} / {@code stop_on_clear}。必須）
 */
public record TowerModeResource(String mode) {
}
