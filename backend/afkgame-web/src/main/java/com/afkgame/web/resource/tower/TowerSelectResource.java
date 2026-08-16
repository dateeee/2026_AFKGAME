package com.afkgame.web.resource.tower;

/**
 * {@code POST /api/tower/select} のリクエストボディ。
 *
 * <p>仕様: docs/tech/detail/tech_tower/select.md §7 手順1、要求の正は
 * docs/tech/detail/tech_tower.md §3。制約違反は 422
 * （docs/tech/basic/tech_api/common.md「HTTPステータスコードの使い分け」）。
 *
 * <p><b>本 record はテストリスト作成②-a で用意した表層であり、Bean Validation は未付与。</b>
 * 制約と既定値の適用は {@code TowerSelectResourceTest} が Red のテストとして持ち、製造②で
 * 満たす（付ける制約は同テストクラスの Javadoc「製造工程への申し送り」が正）。
 *
 * @param towerId     塔ID（必須）
 * @param targetFloor 目標階（必須・1以上。上限は到達状況に依存するため Service が判定する）
 * @param mode        周回モード（{@code auto_repeat} / {@code stop_on_clear}。省略時 {@code auto_repeat}）
 * @param difficulty  難易度。<b>Phase 5〜</b>のイベントダンジョンのみ指定する
 */
public record TowerSelectResource(
        String towerId,
        Integer targetFloor,
        String mode,
        String difficulty) {
}
