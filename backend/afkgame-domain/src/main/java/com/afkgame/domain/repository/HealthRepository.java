package com.afkgame.domain.repository;

/**
 * DB疎通確認用の Repository。
 *
 * <p>ヘルスチェック（docs/tech/nonfunctional/tech_operations.md §12.3）が使う {@code SELECT 1} のみを持つ。
 * Entity を持たないため、主体 Entity 単位で作る規約の対象外
 * （docs/process/coding_standards_backend/domain.md §3 #9）。
 */
public interface HealthRepository {

    /**
     * DBへ {@code SELECT 1} を発行する。
     *
     * @return 常に 1
     */
    Integer findOne();
}
