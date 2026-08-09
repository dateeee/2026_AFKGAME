package com.afkgame.web.api;

import org.springframework.boot.info.BuildProperties;
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
 */
@RestController
public class HealthApi {

    private final HealthService healthService;
    private final BuildProperties buildProperties;

    public HealthApi(HealthService healthService, BuildProperties buildProperties) {
        this.healthService = healthService;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResource> health() {
        if (healthService.isDatabaseReachable()) {
            return ResponseEntity.ok(HealthResource.ok(buildProperties.getVersion()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResource.degraded());
    }
}
