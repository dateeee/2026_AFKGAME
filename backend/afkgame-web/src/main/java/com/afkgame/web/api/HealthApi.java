package com.afkgame.web.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.afkgame.domain.service.HealthService;
import com.afkgame.web.resource.HealthResource;

/**
 * 死活監視エンドポイント。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3。認証不要・レート制限対象外。
 * DBへの {@code SELECT 1} が失敗したら 503 を返し、デプロイ先のヘルスチェックを落とす。
 *
 * <p>返すバージョンは Maven のリソースフィルタで埋めた {@code application.version}
 * （{@code src/main/resources-filtered/META-INF/spring/build.properties}）から読む。war の
 * マニフェストではなくリソースにするのは、war を作らない単体・結合テストでも同じ値を返すため
 * （移行 STEP 2R-0 の確定結果）。
 */
@RestController
public class HealthApi {

    private final HealthService healthService;
    private final String version;

    public HealthApi(HealthService healthService, @Value("${application.version}") String version) {
        this.healthService = healthService;
        this.version = version;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResource> health() {
        if (healthService.isDatabaseReachable()) {
            return ResponseEntity.ok(HealthResource.ok(version));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResource.degraded());
    }
}
