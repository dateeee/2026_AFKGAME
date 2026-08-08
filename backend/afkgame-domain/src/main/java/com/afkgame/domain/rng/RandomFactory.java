package com.afkgame.domain.rng;

import java.util.Random;

import org.springframework.stereotype.Component;

import com.afkgame.env.config.GameProperties;

/**
 * 乱数源のファクトリ。
 *
 * <p>仕様: docs/tech/detail/tech_rng.md §2。静的・共有インスタンスの直接参照は禁止で、
 * tick 処理の入口が本ファクトリで1リクエストにつき1インスタンスを生成し、
 * 戦闘・ドロップ・装備生成・スカウトへ**メソッド引数で**引き渡して乱数源を1本化する。
 *
 * <p>生成したインスタンスは DI コンテナに載せない（テストのシード固定がプロセス全体へ波及し、
 * 並行リクエスト間で干渉するため）。
 */
@Component
public class RandomFactory {

    private final Long seed;

    public RandomFactory(GameProperties gameProperties) {
        this.seed = gameProperties.battleRngSeed();
    }

    /**
     * 1リクエストにつき1インスタンスを生成する。
     *
     * <p>{@code BATTLE_RNG_SEED} が設定されていればシードを固定し、同じ乱数列を再生する（調査用）。
     *
     * @return 新しい乱数源
     */
    public Random create() {
        if (seed == null) {
            return new Random();
        }
        return new Random(seed);
    }
}
