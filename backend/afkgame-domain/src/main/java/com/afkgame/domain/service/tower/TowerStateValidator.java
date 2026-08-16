package com.afkgame.domain.service.tower;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * 進行状態の不変条件を検証する継ぎ目。
 *
 * <p>仕様: docs/tech/detail/tech_state.md §1.1（不変条件）。読み込んだ進行状態が
 * 不変条件を満たさないときは<b>データ不整合として落とす</b>のが決定で、黙って補正しない
 * （補正すると不整合の発生源が隠れ、次の読み取りでも同じ値が返る）。
 *
 * <p><b>実装はセグメント②（tower。docs/backlog/java_migration.md STEP 3-B）が持つ。</b>
 * 呼び出すのは進行状態を読む側（ゲーム状態の応答構築・tick）で、置き場が
 * {@code service.tower} なのは §1.1 の6条件のうち5条件が塔の探索状態そのものだから。
 */
public interface TowerStateValidator {

    /**
     * 読み込んだ進行状態が不変条件を満たすことを確かめる。
     *
     * <p>満たしていれば何もしない。違反していれば送出して呼び出し元の処理を止める
     * （応答は Web 層が 500 {@code INTERNAL_UNEXPECTED_ERROR} へそろえる。
     * coding_standards_backend/exception.md §4）。
     *
     * @param player プレイヤー
     * @param party  出撃中のパーティ
     * @throws org.terasoluna.gfw.common.exception.SystemException
     *         {@code INTERNAL_STATE_INVARIANT_VIOLATED}（不変条件違反）
     */
    void validate(Player player, List<Character> party);
}
