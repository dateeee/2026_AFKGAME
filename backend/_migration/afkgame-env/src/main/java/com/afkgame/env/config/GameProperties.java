package com.afkgame.env.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ゲーム進行の設定値（{@code afkgame.*}）。
 *
 * <p>値の正は docs/tech/basic/tech_structure.md §4「設定値」、環境変数の正は
 * docs/tech/nonfunctional/tech_operations.md §12.2。
 * アプリケーションコードから環境変数を直接読まず、本クラス経由で参照する。
 *
 * <p>数値をコードへ埋め込まないための受け皿であり、ここに計算やバランス調整の判断を持たせない。
 *
 * @param tickIntervalSeconds  1 tick の間隔（秒）
 * @param turnsPerTick         1 tick あたりのターン数
 * @param offlineEfficiency    オフライン時の報酬効率
 * @param maxOfflineHours      オフライン報酬の最大蓄積時間
 * @param fastCalcThreshold    これを超える未処理tickは簡略計算へ切り替える境界
 * @param maxBattleLogRecords  DB保持ログ件数の上限
 * @param maxLogPerResponse    1レスポンスあたりのログ件数の上限
 * @param maxPlayerLevel       プレイヤーLVの上限
 * @param maxGold              ゴールドの上限
 * @param battleRngSeed        戦闘乱数のシード（{@code BATTLE_RNG_SEED}）。未設定なら {@code null}
 */
@ConfigurationProperties(prefix = "afkgame")
public record GameProperties(
        int tickIntervalSeconds,
        int turnsPerTick,
        double offlineEfficiency,
        int maxOfflineHours,
        int fastCalcThreshold,
        int maxBattleLogRecords,
        int maxLogPerResponse,
        int maxPlayerLevel,
        long maxGold,
        Long battleRngSeed) {
}
