package com.afkgame.domain.service.battle;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.terasoluna.gfw.common.exception.SystemException;

/**
 * {@link EncounterSelector} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。乱数は {@code nextInt(重み合計)} を1回だけ消費する
 * （tech_rng.md §3「消費順序の固定」）。
 *
 * <p>重み合計が0以下のプールはマスターデータ不正として {@link SystemException} を投げる。
 * 起動時ではなくリクエスト中の検知のため {@code MasterDataException} は使わない
 * （coding_standards_backend/exception.md §3 #2・#3）。コードは応答に出ずログにだけ残る（同 §4）。
 */
@Service
public class EncounterSelectorImpl implements EncounterSelector {

    /** 抽選できないプールを検知したときのエラーコード（tech_error_handling.md「エラーコード体系」）。 */
    private static final String MASTER_DATA_INVALID = "INTERNAL_MASTER_DATA_INVALID";

    /**
     * {@inheritDoc}
     */
    @Override
    public String select(List<EncounterEntry> pool, Random rng) {
        int totalWeight = pool.stream().mapToInt(EncounterEntry::weight).sum();
        if (totalWeight <= 0) {
            throw new SystemException(MASTER_DATA_INVALID,
                    "encounter pool has no positive weight. pool size [" + pool.size()
                            + "] total weight [" + totalWeight + "]");
        }

        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        // roll < 重み合計 が保証されるため、末尾の要素が残りの重みをすべて受け持つ。
        // 末尾を判定の外へ出すことで「どの要素にも当たらない」到達不能な経路を作らない。
        for (int i = 0; i < pool.size() - 1; i++) {
            cumulative += pool.get(i).weight();
            if (roll < cumulative) {
                return pool.get(i).enemyId();
            }
        }
        return pool.get(pool.size() - 1).enemyId();
    }
}
