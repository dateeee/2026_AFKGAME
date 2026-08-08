package com.afkgame.domain.model;

/**
 * {@code player_settings} テーブルの Entity。
 *
 * <p>列・NULL 可否・一意制約の正は docs/tech/basic/tech_db/player.md §2。
 * 1プレイヤーにつき1設定（{@code uq_player_settings_player_id}）。
 */
public class PlayerSettings {

    private String id;
    private String playerId;
    private double potionThreshold;
    private int battleLogCount;
    private boolean toastEnabled;
    private String autoSellRarity;

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

    public double getPotionThreshold() {
        return potionThreshold;
    }

    public void setPotionThreshold(double potionThreshold) {
        this.potionThreshold = potionThreshold;
    }

    public int getBattleLogCount() {
        return battleLogCount;
    }

    public void setBattleLogCount(int battleLogCount) {
        this.battleLogCount = battleLogCount;
    }

    public boolean isToastEnabled() {
        return toastEnabled;
    }

    public void setToastEnabled(boolean toastEnabled) {
        this.toastEnabled = toastEnabled;
    }

    /**
     * @return 自動売却の対象レアリティ。自動売却なしなら null（tech_db/player.md §2）
     */
    public String getAutoSellRarity() {
        return autoSellRarity;
    }

    public void setAutoSellRarity(String autoSellRarity) {
        this.autoSellRarity = autoSellRarity;
    }
}
