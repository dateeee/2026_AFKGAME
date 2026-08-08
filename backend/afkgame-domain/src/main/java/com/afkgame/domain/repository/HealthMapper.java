package com.afkgame.domain.repository;

/**
 * DB疎通確認用の Mapper。
 *
 * <p>ヘルスチェック（docs/tech/nonfunctional/tech_operations.md §12.3）が使う {@code SELECT 1} のみを持つ。
 */
public interface HealthMapper {

    /**
     * DBへ {@code SELECT 1} を発行する。
     *
     * @return 常に 1
     */
    Integer selectOne();
}
