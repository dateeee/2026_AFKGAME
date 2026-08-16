package com.afkgame.domain.service.tower;

/**
 * 入塔が成立したときの結果。
 *
 * <p>応答の正は docs/tech/detail/tech_tower.md §3（{@code POST /api/tower/select} の 200）。
 * {@code status} は Web 層が付けるため本 record は持たない。
 *
 * @param towerId     入塔した塔ID
 * @param targetFloor 反映された目標階
 */
public record TowerSelection(String towerId, int targetFloor) {
}
