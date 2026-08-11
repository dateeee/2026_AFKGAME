package com.afkgame.domain.service.battle;

import java.util.List;

/**
 * 簡略計算（101tick以上）のサマリー。
 *
 * <p>仕様: docs/tech/detail/tech_offline.md §2・§4。簡略計算では個別の戦闘ログを生成せず、
 * 本 record だけを返す。
 *
 * @param gold          獲得したゴールドの合計
 * @param exp           獲得した経験値の合計（キャラ1体あたりではなく付与済みの総量）
 * @param itemIds       獲得したアイテムIDの一覧。1件も無ければ空リスト
 * @param wiped         周回中に全滅したか
 * @param ticksConsumed 周回に消化したtick数。全滅で破棄した残tickは含めない（§4 手順4 の待機扱いへ回す）
 * @param laps          周回した回数
 */
public record OfflineSummary(long gold, long exp, List<String> itemIds, boolean wiped,
        int ticksConsumed, int laps) {
}
