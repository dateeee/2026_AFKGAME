package com.afkgame.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.afkgame.domain.repository.HealthRepository;

/**
 * {@link HealthService} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは疎通の判定手段（Repository への1件参照）を担う。
 */
@Service
public class HealthServiceImpl implements HealthService {

    private static final Logger logger = LoggerFactory.getLogger(HealthServiceImpl.class);

    private final HealthRepository healthRepository;

    public HealthServiceImpl(HealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>疎通失敗は ERROR ログに残す（tech_operations.md §12.3 の監視指標「ERROR ログ件数」）。
     * 参照のみのため境界を持たない。
     */
    @Override
    public boolean isDatabaseReachable() {
        try {
            healthRepository.findOne();
            return true;
        } catch (DataAccessException e) {
            logger.error("ヘルスチェック: DB疎通に失敗", e);
            return false;
        }
    }
}
