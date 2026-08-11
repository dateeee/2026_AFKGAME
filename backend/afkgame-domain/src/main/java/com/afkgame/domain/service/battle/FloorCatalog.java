package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.masterdata.EnemyData;
import com.afkgame.domain.model.Player;

/**
 * 階のデータを読む継ぎ目。
 *
 * <p>仕様: docs/tech/detail/tech_tower.md（階層データと踏破記録）。
 *
 * <p><b>実装はセグメント②（tower。docs/backlog/java_migration.md STEP 3-B）が持つ。</b>
 * 本インタフェースを {@code service.battle} に置くのは、読み手が簡略計算（{@link LapAnalyzer}）
 * だけであり、消費側のパッケージに継ぎ目を置くことで battle → tower の依存を作らずに済むため。
 * 実装クラスは {@code service.tower} 側へ置く。
 */
public interface FloorCatalog {

    /**
     * 指定階に出現する敵を返す。
     *
     * @param towerId 塔ID
     * @param floor   階
     * @return 出現する敵の一覧（Phase 1 は1階1体、Phase 3〜 は1〜3体）
     */
    List<EnemyData> enemiesOf(String towerId, int floor);

    /**
     * 目標階の上限を返す。
     *
     * @param player プレイヤー
     * @return {@code min(塔別highestFloor + 1, 総階数)}
     */
    int targetFloorCap(Player player);
}
