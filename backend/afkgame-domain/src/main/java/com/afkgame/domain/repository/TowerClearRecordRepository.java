package com.afkgame.domain.repository;

import java.util.List;

import com.afkgame.domain.model.TowerClearRecord;

/**
 * {@code tower_clear_records} の Repository。
 *
 * <p>塔一覧（docs/tech/detail/tech_tower/list.md §5 手順1）と入塔の上限判定
 * （同 select.md §7 手順6）が使う読み取りのみを持つ。行の作成・更新は階クリア時に行うため
 * （tech_tower.md §2）、更新系は階進行を実装するセグメント②で追加する。
 *
 * <p><b>マッピング XML はテストリスト作成②-a では未作成。</b>本インタフェースは Red の
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
}
