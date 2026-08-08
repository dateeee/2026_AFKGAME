package com.afkgame.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.afkgame.domain.repository.HealthMapper;

/**
 * ヘルスチェックのドメインサービス。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3。
 */
@Service
public class HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthService.class);

    private final HealthMapper healthMapper;

    public HealthService(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    /**
     * DBへ疎通できるかを判定する。
     *
     * <p>疎通失敗は ERROR ログに残す（§12.3 の監視指標「ERROR ログ件数」）。
     *
     * @return 疎通できれば true、DBアクセスに失敗したら false
     */
    public boolean isDatabaseReachable() {
        try {
            healthMapper.selectOne();
            return true;
        } catch (DataAccessException e) {
            logger.error("ヘルスチェック: DB疎通に失敗", e);
            return false;
        }
    }
}
