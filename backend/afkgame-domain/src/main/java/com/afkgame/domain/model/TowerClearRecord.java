package com.afkgame.domain.model;

import java.time.Instant;

/**
 * {@code tower_clear_records} テーブルの Entity。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §3。
 * 1プレイヤー・1塔につき1行（{@code uq_tower_clear_records_player_tower}）で、
 * 行が無い塔は未挑戦（{@code cleared = false}・{@code highestFloor = 0}）として扱う
 * （docs/tech/detail/tech_tower.md §2）。
 *
 * <p>行の作成は<b>階クリア時</b>に行い、一覧・入塔では作らない（同 §2）。
 */
public class TowerClearRecord {

    private String id;
    private String playerId;
    private String towerId;
    private boolean cleared;
    private int highestFloor;
    private Instant highestFloorAt;

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

    /**
     * @return 塔ID。イベントダンジョン（Phase 5〜）は難易度を畳み込んだキー
     */
    public String getTowerId() {
        return towerId;
    }

    public void setTowerId(String towerId) {
        this.towerId = towerId;
    }

    /**
     * @return 最上階のボスを討伐済みなら true
     */
    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(boolean cleared) {
        this.cleared = cleared;
    }

    public int getHighestFloor() {
        return highestFloor;
    }

    public void setHighestFloor(int highestFloor) {
        this.highestFloor = highestFloor;
    }

    /**
     * @return 最高到達階を更新した時刻。<b>Phase 5・未実装</b>のため現状は null
     */
    public Instant getHighestFloorAt() {
        return highestFloorAt;
    }

    public void setHighestFloorAt(Instant highestFloorAt) {
        this.highestFloorAt = highestFloorAt;
    }
}
