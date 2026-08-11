package com.afkgame.domain.service.battle;

/**
 * 確率・軽減率の上限を適用する。
 *
 * <p>仕様: docs/tech/detail/tech_numeric.md §3「キャップ・下限一覧」・§4「適用順序」、
 * 上限値のゲーム仕様は docs/design/systems/battle.md「確率・軽減率の上限」。
 *
 * <p>合算そのもの（装備・パッシブ・バフの足し合わせ）は Phase 2〜3 で供給元が増えるため、
 * 本インタフェースは**合算済みの値を受け取り**上限だけを担う。切り捨ては乱数判定・ダメージ計算へ
 * 渡す前に済ませる（{@code r < p} 規約が成立する前提を作るため。§4 の注記）。
 *
 * <p>実装は {@link StatCalculatorImpl}。
 */
public interface StatCalculator {

    /**
     * クリティカル率の実効値を返す。
     *
     * @param summedCritRate 合算済みのクリティカル率
     * @return 上限1.0で切り捨てた実効値
     */
    double effectiveCritRate(double summedCritRate);

    /**
     * 被ダメージ軽減率の実効値を返す。
     *
     * @param summedReduction 合算済みの軽減率
     * @return 上限0.8で切り捨てた実効値
     */
    double effectiveDamageReduction(double summedReduction);
}
