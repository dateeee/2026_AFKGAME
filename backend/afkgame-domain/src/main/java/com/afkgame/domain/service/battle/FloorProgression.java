package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * 階進行の継ぎ目。
 *
 * <p>仕様: docs/tech/detail/tech_tower/progress.md（階進行・撤退・踏破記録）、
 * エンカウントの位置づけは docs/tech/detail/tech_battle.md §3.2。
 *
 * <p><b>実装はセグメント②（tower。docs/backlog/java_migration.md STEP 3-B）が持つ。</b>
 * 本インタフェースを {@code service.battle} に置く理由は {@link FloorCatalog} と同じで、
 * 呼び出すのは正規シミュレーション（{@link BattleSimulator}）だけである。
 */
public interface FloorProgression {

    /**
     * 交戦相手がいなければエンカウントさせる。
     *
     * @param player プレイヤー
     * @param rng    このリクエストの乱数源
     */
    void ensureEncounter(Player player, Random rng);

    /**
     * 敵を撃破したときの階進行を反映する。
     *
     * @param player プレイヤー
     * @param party  出撃中のパーティ
     * @param rng    このリクエストの乱数源
     */
    void onEnemyDefeated(Player player, List<Character> party, Random rng);

    /**
     * パーティが全滅したときの撤退処理を反映する。
     *
     * @param player プレイヤー
     * @param party  出撃中のパーティ
     */
    void onPartyWiped(Player player, List<Character> party);
}
