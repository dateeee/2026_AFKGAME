package com.afkgame.domain.service.tower;

import java.util.List;

/**
 * 塔操作のドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_tower.md（索引）。一覧は
 * docs/tech/detail/tech_tower/list.md §5、入塔は同 select.md §7。
 * 状態遷移と操作可否は docs/tech/detail/tech_state.md §2・§4。
 *
 * <p>実装は {@link TowerServiceImpl}。リタイア・モード変更・撤退条件は同 control.md §11。
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

    /**
     * 塔から離脱する（リタイア）。
     *
     * <p>エンカウント待ち・戦闘中のどちらでも<b>即時に</b>成立し、塔・敵情報をクリアして
     * {@code IDLE} へ戻す。探索セッションは確定（没収なし）してリセットし、HPは変更しない
     * （control.md §11「リタイア」・tech_state.md §2・§3）。
     *
     * @param playerId プレイヤーID
     * @throws org.terasoluna.gfw.common.exception.BusinessException
     *         {@code TOWER_NOT_IN_TOWER}（塔外）
     */
    void retire(String playerId);

    /**
     * 進行モードを変更する。
     *
     * <p>入塔中のみ変更でき、次の目標到達・撤退判定（progress.md §9 手順4・5）から適用する
     * （control.md §11「進行モード切替」）。値そのものの検証は Bean Validation の担当。
     *
     * @param playerId プレイヤーID
     * @param mode     {@code auto_repeat} または {@code stop_on_clear}
     * @throws org.terasoluna.gfw.common.exception.BusinessException
     *         {@code TOWER_NOT_IN_TOWER}（塔外）
     */
    void changeMode(String playerId, String mode);

    /**
     * HP閾値撤退のしきい値を更新する。
     *
     * <p>塔外・入塔中のどちらでも変更でき、次の階クリア判定（progress.md §9 手順5）から適用する
     * （control.md §11「撤退条件更新」）。範囲（{@code 0.0}〜{@code 1.0}）の検証は
     * Bean Validation の担当。
     *
     * @param playerId    プレイヤーID
     * @param hpThreshold しきい値。{@code 0} はHP閾値撤退の無効化
     */
    void updateRetreatConditions(String playerId, double hpThreshold);
}
