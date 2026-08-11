package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code refresh_tokens} テーブルの Entity。
 *
 * <p>スキーマ定義の正は docs/tech/basic/tech_db/auth.md §2、ローテーション仕様は
 * docs/tech/detail/tech_auth.md §4。生のトークンは保存せず、SHA-256 ハッシュのみを持つ。
 */
public class RefreshToken {

    private Integer id;
    private String userId;
    private String tokenHash;
    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
