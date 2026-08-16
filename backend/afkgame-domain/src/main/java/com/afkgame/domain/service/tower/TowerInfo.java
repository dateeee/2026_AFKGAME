package com.afkgame.domain.service.tower;

/**
 * 塔一覧の1エントリ。
 *
 * <p>フィールドの正は docs/tech/detail/tech_tower.md §3（{@code GET /api/tower/list} の応答）、
 * 組み立て手順は docs/tech/detail/tech_tower/list.md §5。
 *
 * <p>未解放の塔も含めて全塔を返す（解放条件の表示に使うため。表示の制御はフロント側）。
 *
 * @param id             塔ID。イベントダンジョン（Phase 5〜）は {@code {towerId}_{difficulty}}
 *                       のキー（キー体系の正は docs/tech/basic/tech_data.md §1.1）
 * @param name           塔名
 * @param dungeonName    所属ダンジョン名
 * @param totalFloors    総階数。{@code null} は階数無限（深淵の塔。Phase 5〜）
 * @param unlockTowerId  解放条件となる前提塔のID。{@code null} は最初から解放
 * @param unlocked       解放済みか（tech_tower.md §2 の解放判定）
 * @param cleared        最上階のボスを討伐済みか。記録が無ければ {@code false}
 * @param highestFloor   その塔での最高到達階。記録が無ければ {@code 0}
 * @param targetFloorCap 目標階の上限（tech_tower.md §2 の {@code cap} 式）
 */
public record TowerInfo(
        String id,
        String name,
        String dungeonName,
        Integer totalFloors,
        String unlockTowerId,
        boolean unlocked,
        boolean cleared,
        int highestFloor,
        int targetFloorCap) {
}
