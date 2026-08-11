package com.afkgame.domain.service.health;

/**
 * ヘルスチェックのドメインサービス。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3。
 *
 * <p>実装は {@link HealthServiceImpl}。
 */
public interface HealthService {

    /**
     * DBへ疎通できるかを判定する。
     *
     * @return 疎通できれば true、DBアクセスに失敗したら false
     */
    boolean isDatabaseReachable();
}
