package com.afkgame.domain.service.tower;

import java.util.List;

/**
 * 塔操作のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_tower.md（索引）。一覧は
 * docs/tech/detail/tech_tower/list.md §5、入塔は同 select.md §7。
 * 状態遷移と操作可否は docs/tech/detail/tech_state.md §2・§4。
 *
 * <p>実装は {@link TowerServiceImpl}。リタイア・モード変更・撤退条件（同 control.md §11）は
 * テストリスト作成②-c で本インタフェースへ足す。
 */
public interface TowerService {

    /**
     * 塔の一覧を組み立てる。
     *
     * <p>読み取り専用で状態を変更しない。未解放の塔も含めて全塔を返す（list.md §5 手順3）。
     *
     * @param playerId プレイヤーID
     * @return マスターデータの定義順に並んだ塔情報
     */
    List<TowerInfo> list(String playerId);

    /**
     * 塔へ入る。
     *
     * <p>検証はすべて状態変更の前に行い、最初に失敗した検証のエラーを返す（select.md §7）。
     * 1リクエスト = 1トランザクションで、対象 {@code players} 行を行ロックしてから検証する
     * （tech_tower.md §1）。
     *
     * @param playerId プレイヤーID
     * @param command  入塔の要求値
     * @return 入塔した塔IDと目標階
     * @throws org.terasoluna.gfw.common.exception.BusinessException
     *         {@code TOWER_ALREADY_IN_TOWER}（入塔中）・{@code TOWER_NOT_UNLOCKED}（未解放）・
     *         {@code TOWER_INVALID_DIFFICULTY}（難易度の不整合。Phase 5〜）・
     *         {@code TOWER_INVALID_FLOOR}（目標階が上限超過）・
     *         {@code TOWER_PARTY_WIPED}（パーティ全員HP0）
     * @throws org.terasoluna.gfw.common.exception.ResourceNotFoundException
     *         {@code TOWER_NOT_FOUND}（塔マスターに存在しない）
     */
    TowerSelection select(String playerId, TowerSelectCommand command);
}
