package com.afkgame.domain.service.battle;

import java.util.Random;

/**
 * 通常攻撃1発ぶんのダメージを求める。
 *
 * <p>仕様: 計算式は docs/tech/detail/tech_battle.md §3.1 手順2-j、丸めと下限は
 * docs/tech/detail/tech_numeric.md §2・§4、乱数の消費順序は
 * docs/tech/detail/tech_rng.md §3。
 *
 * <p>実装は {@link DamageCalculatorImpl}。
 */
public interface DamageCalculator {

    /**
     * ダメージを求める。
     *
     * <p>乱数は「①ダメージ分散 → ②クリティカル判定」の順に1回ずつ消費する（tech_rng.md §3）。
     *
     * @param atk       攻撃側の攻撃力
     * @param def       防御側の防御力
     * @param critRate  クリティカル率の**実効値**（0〜1.0）。上限の切り捨ては
     *                  {@link StatCalculator} が呼び出し前に済ませる
     * @param direction ダメージの向き（最低ダメージ保証の下限が分かれる）
     * @param rng       このリクエストの乱数源
     * @return 与えるダメージ
     */
    long calculate(int atk, int def, double critRate, DamageDirection direction, Random rng);
}
