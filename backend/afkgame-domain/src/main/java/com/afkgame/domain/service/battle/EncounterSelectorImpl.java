package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

/**
 * {@link EncounterSelector} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。乱数は {@code nextInt(重み合計)} を1回だけ消費する
 * （tech_rng.md §3「消費順序の固定」）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-ii（戦闘計算の Green。docs/backlog/java_migration.md STEP 3-B）で、
 * {@code @Service} と、重み合計0以下のときに送出するシステム例外のエラーコード
 * （{@code INTERNAL_MASTER_DATA_INVALID}。docs/tech/basic/tech_error_handling.md への登録が要る）を
 * 同じ回で入れる。起動時ではなくリクエスト中の検知のため {@code MasterDataException} は使わない
 * （coding_standards_backend/exception.md §3 #2・#3）。
 */
public class EncounterSelectorImpl implements EncounterSelector {

    /**
     * {@inheritDoc}
     */
    @Override
    public String select(List<EncounterEntry> pool, Random rng) {
        throw new UnsupportedOperationException("製造①-ii（戦闘計算の Green）で実装する");
    }
}
