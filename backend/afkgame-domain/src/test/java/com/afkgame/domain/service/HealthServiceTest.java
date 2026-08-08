package com.afkgame.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.afkgame.domain.repository.HealthMapper;

/**
 * {@link HealthService} の単体テスト。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3（ヘルスチェック）。
 * DBへの {@code SELECT 1} が成功したか失敗したかだけを真偽値で表す。
 *
 * <p>分岐観点: 疎通成功 / 疎通失敗（{@code DataAccessException}）。
 * 骨格構築（java_migration.md STEP 2）の横断基盤であり詳細設計の分岐一覧を持たないため、
 * 分岐マーカーは付けない。
 */
@Tag("unit")
class HealthServiceTest {

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("isDatabaseReachable")
    class TestIsDatabaseReachable {

        @Mock
        private HealthMapper healthMapper;

        @InjectMocks
        private HealthService healthService;

        @Test
        void test_DB疎通に成功したらtrueを返す() {
            when(healthMapper.selectOne()).thenReturn(1);

            assertThat(healthService.isDatabaseReachable()).isTrue();
        }

        @Test
        void test_DB疎通に失敗したらfalseを返す() {
            when(healthMapper.selectOne())
                    .thenThrow(new DataAccessResourceFailureException("DB接続に失敗"));

            assertThat(healthService.isDatabaseReachable()).isFalse();
        }
    }
}
