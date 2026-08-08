package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code characters} テーブルの Entity。
 *
 * <p>列・NULL 可否の正は docs/tech/basic/tech_db/player.md §4。
 * ステータスの基礎値はタイプ別マスター（{@code character_types.yml}）から書き写す
 * （docs/tech/detail/tech_auth.md §8.2 手順4）。
 *
 * <p>{@code rarity} は Phase 3 の列で V1 スキーマに存在しないため、本 Entity にも持たない。
 * 追加は同列をスキーマへ入れる Phase の製造で行う。
 *
 * <p>単純名が {@link java.lang.Character} と衝突するが、テーブル名 {@code characters} に対する
 * Entity 名という命名規則（{@code users} → {@code User}）を優先している。
 */
public class Character {

    private String id;
    private String playerId;
    private String name;
    private String type;
    private int level;
    private long exp;
    private int hp;
    private int maxHp;
    private int baseAtk;
    private int baseDef;
    private int baseSpd;
    private int limitBreak;
    private int skillPoints;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getExp() {
        return exp;
    }

    public void setExp(long exp) {
        this.exp = exp;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getBaseAtk() {
        return baseAtk;
    }

    public void setBaseAtk(int baseAtk) {
        this.baseAtk = baseAtk;
    }

    public int getBaseDef() {
        return baseDef;
    }

    public void setBaseDef(int baseDef) {
        this.baseDef = baseDef;
    }

    public int getBaseSpd() {
        return baseSpd;
    }

    public void setBaseSpd(int baseSpd) {
        this.baseSpd = baseSpd;
    }

    public int getLimitBreak() {
        return limitBreak;
    }

    public void setLimitBreak(int limitBreak) {
        this.limitBreak = limitBreak;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
