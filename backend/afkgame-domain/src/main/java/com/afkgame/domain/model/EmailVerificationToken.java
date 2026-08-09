package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code email_verification_tokens} テーブルの Entity。
 *
 * <p>スキーマ定義の正は docs/tech/basic/tech_db/auth.md §3、発行の仕様は
 * docs/tech/detail/tech_auth_account.md §10 手順6。生のトークンは保存せず、SHA-256 ハッシュのみを持つ。
 */
public class EmailVerificationToken {

    private Integer id;
    private String userId;
    private String tokenHash;
    private String purpose;
    private Instant expiresAt;
    private boolean used;
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

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
