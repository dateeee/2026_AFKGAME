package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code users} テーブルの Entity。
 *
 * <p>スキーマ定義の正は docs/tech/basic/tech_db.md、認証仕様は
 * docs/tech/detail/tech_auth.md §6。ID は {@code user_<UUID>} または {@code guest_<UUID>}。
 */
public class User {

    private String id;
    private String email;
    private String passwordHash;
    private String googleId;
    private String displayName;
    private boolean guest;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant lastLoginAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isGuest() {
        return guest;
    }

    public void setGuest(boolean guest) {
        this.guest = guest;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
