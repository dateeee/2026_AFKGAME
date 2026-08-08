package com.afkgame.web.resource;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /health} の応答。
 *
 * <p>仕様: docs/tech/nonfunctional/tech_operations.md §12.3。
 * 正常時は {@code {"status":"ok","version":"x.y.z","db":"ok"}}、
 * 異常時は {@code {"status":"degraded","db":"error"}}（version を含めない）。
 *
 * @param status  {@code ok} / {@code degraded}
 * @param version アプリバージョン。異常時は null（応答へ出力しない）
 * @param db      {@code ok} / {@code error}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResource(String status, String version, String db) {

    public static HealthResource ok(String version) {
        return new HealthResource("ok", version, "ok");
    }

    public static HealthResource degraded() {
        return new HealthResource("degraded", null, "error");
    }
}
