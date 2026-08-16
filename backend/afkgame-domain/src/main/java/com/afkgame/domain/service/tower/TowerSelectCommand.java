package com.afkgame.domain.service.tower;

/**
 * 入塔の要求値。
 *
 * <p>仕様: docs/tech/detail/tech_tower/select.md §7（処理フロー）、要求の正は
 * docs/tech/detail/tech_tower.md §3。
 *
 * <p>Web 層の Resource をドメインへ持ち込まないための入力型
 * （docs/process/coding_standards_backend/common.md §2 の依存方向）。値そのものの形式検証
 * （必須・値域・2値のいずれか）は Resource の Bean Validation が終えている前提で、
 * 本 record を受ける Service は<b>状態に依存する検証だけ</b>を行う（§7 手順2〜7）。
 *
 * @param towerId     塔ID
 * @param targetFloor 目標階（1以上。上限は到達状況に依存するため Service が判定する）
 * @param mode        周回モード（{@code auto_repeat} / {@code stop_on_clear}）
 * @param difficulty  難易度。<b>Phase 5〜</b>のイベントダンジョンのみ非 null で、
 *                    通常塔・深淵の塔は null
 */
public record TowerSelectCommand(
        String towerId,
        int targetFloor,
        String mode,
        String difficulty) {
}
