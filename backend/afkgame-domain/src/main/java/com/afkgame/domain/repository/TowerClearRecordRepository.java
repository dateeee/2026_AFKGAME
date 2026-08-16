package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.TowerClearRecord;

/**
 * {@code tower_clear_records} の Repository。
 *
 * <p>塔一覧（docs/tech/detail/tech_tower/list.md §5 手順1）と入塔の上限判定
 * （同 select.md §7 手順6）が使う読み取りと、階クリア時の作成・更新（同 progress.md §9 手順1）を持つ。
 *
 * <p><b>マッピング XML はテストリスト作成②-a・②-b では未作成。</b>本インタフェースは Red の
 * テストがモックする継ぎ目として置いてあり、{@code src/main/resources} 配下の
 * {@code TowerClearRecordRepository.xml} は製造②で書く。
 */
public interface TowerClearRecordRepository {

    /**
     * プレイヤーの全塔のクリア記録を取得する。
     *
     * @param playerId プレイヤーID
     * @return クリア記録の一覧。1件も無ければ空リスト
     */
    List<TowerClearRecord> findAllByPlayerId(String playerId);

    /**
     * 塔を指定してクリア記録を取得する。
     *
     * @param playerId プレイヤーID
     * @param towerId  塔ID（イベントダンジョンは難易度を畳み込んだキー）
     * @return 該当する記録。未挑戦で行が無ければ null
     */
    TowerClearRecord findByPlayerIdAndTowerId(String playerId, String towerId);

    /**
     * クリア記録を登録する。
     *
     * <p>初めてその塔の階をクリアしたときに1回だけ呼ぶ（progress.md §9 手順1）。
     * {@code highest_floor_at} は Phase 5 まで未実装のため書かない
     * （列の正は docs/tech/basic/tech_db/player.md §3）。
     *
     * @param record 登録する記録
     */
    void save(TowerClearRecord record);

    /**
     * クリア記録の到達状況を更新する。
     *
     * <p>更新するのは {@code cleared} と {@code highest_floor} だけで、
     * {@code player_id} / {@code tower_id} は一意キーとして検索条件に使う。
     *
     * @param record 更新する記録
     */
    void updateProgress(TowerClearRecord record);
}
