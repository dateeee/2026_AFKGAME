package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code players} テーブルの Entity。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §1。
 * 1ユーザーにつき1プレイヤー（{@code uq_players_user_id}）。
 *
 * <p>塔の外にいる間は {@code currentTowerId}・{@code currentFloor}・{@code targetFloor}・
 * {@code currentEnemyId}・{@code currentEnemyHp} が {@code null} になる（同 §1 の備考）。
 */
public class Player {

    private String id;
    private String userId;
    private long gold;
    private String currentTowerId;
    private Integer currentFloor;
    private Integer targetFloor;
    private String towerMode;
    private double hpThreshold;
    private String currentEnemyId;
    private Integer currentEnemyHp;
    private long runGold;
    private int highestFloor;
    private Instant lastTickAt;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getGold() {
        return gold;
    }

    public void setGold(long gold) {
        this.gold = gold;
    }

    /**
     * @return 探索中の塔ID。塔外なら null
     */
    public String getCurrentTowerId() {
        return currentTowerId;
    }

    public void setCurrentTowerId(String currentTowerId) {
        this.currentTowerId = currentTowerId;
    }

    /**
     * @return 現在階。塔外なら null
     */
    public Integer getCurrentFloor() {
        return currentFloor;
    }

    public void setCurrentFloor(Integer currentFloor) {
        this.currentFloor = currentFloor;
    }

    /**
     * @return 目標階。塔外なら null
     */
    public Integer getTargetFloor() {
        return targetFloor;
    }

    public void setTargetFloor(Integer targetFloor) {
        this.targetFloor = targetFloor;
    }

    public String getTowerMode() {
        return towerMode;
    }

    public void setTowerMode(String towerMode) {
        this.towerMode = towerMode;
    }

    public double getHpThreshold() {
        return hpThreshold;
    }

    public void setHpThreshold(double hpThreshold) {
        this.hpThreshold = hpThreshold;
    }

    /**
     * @return 交戦中の敵ID。交戦していなければ null
     */
    public String getCurrentEnemyId() {
        return currentEnemyId;
    }

    public void setCurrentEnemyId(String currentEnemyId) {
        this.currentEnemyId = currentEnemyId;
    }

    /**
     * @return 交戦中の敵の残HP。交戦していなければ null
     */
    public Integer getCurrentEnemyHp() {
        return currentEnemyHp;
    }

    public void setCurrentEnemyHp(Integer currentEnemyHp) {
        this.currentEnemyHp = currentEnemyHp;
    }

    public long getRunGold() {
        return runGold;
    }

    public void setRunGold(long runGold) {
        this.runGold = runGold;
    }

    public int getHighestFloor() {
        return highestFloor;
    }

    public void setHighestFloor(int highestFloor) {
        this.highestFloor = highestFloor;
    }

    public Instant getLastTickAt() {
        return lastTickAt;
    }

    public void setLastTickAt(Instant lastTickAt) {
        this.lastTickAt = lastTickAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
