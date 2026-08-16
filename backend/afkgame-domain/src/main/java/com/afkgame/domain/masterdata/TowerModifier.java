package com.afkgame.domain.masterdata;

/**
 * 塔の環境効果（modifier）1件。
 *
 * <p>定義形式の正は docs/tech/basic/tech_data.md §1.5、適用契機は
 * docs/tech/detail/tech_tower/progress.md §9 手順2（階クリア後の {@code recovery}）。
 *
 * <p><b>本 record はテストリスト作成②-b で用意した表層であり、YAML との対応づけは未実装。</b>
 * 保持するのは階進行が読む3項目（{@code type} / {@code trigger} / {@code value}）だけで、
 * {@code stat_modifier} が使う {@code target} / {@code stat} は、その効果を実装する
 * Phase 3 で足す（読み手のいない列を先に持たない。{@code TowerData#floorEncounters} と同じ方針）。
 *
 * @param id      環境効果ID
 * @param type    効果の種別。{@code recovery} / {@code stat_modifier} / {@code dot}
 * @param trigger 適用契機。{@code floor_clear} / {@code turn_start}
 * @param value   効果量。{@code recovery} は最大HPに対する割合（{@code 0.03} なら3%）
 */
public record TowerModifier(String id, String type, String trigger, double value) {
}
