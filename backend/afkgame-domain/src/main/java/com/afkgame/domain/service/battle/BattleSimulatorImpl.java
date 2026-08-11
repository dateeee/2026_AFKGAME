package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.masterdata.Enemies;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.rng.RandomFactory;
import com.afkgame.env.config.GameSettings;

/**
 * {@link BattleSimulator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは行動順の決定と打ち切り判定、報酬の付与を担い、
 * ダメージ計算・ターゲット抽選・階進行はそれぞれの協調オブジェクトへ委譲する。
 *
 * <p>乱数源の生成点は本クラスである（{@link BattleServiceImpl} は {@link RandomFactory} を
 * 受け取らない）。{@code simulate} の入口で1回だけ生成し、協調オブジェクトへ引数で引き渡す
 * （tech_rng.md §2）。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、処理は未実装。</b>
 * 解消は製造①-iii（tick・オフラインの Green。docs/backlog/java_migration.md STEP 3-B）。
 * {@code @Service} も同じ回で付ける（{@link FloorProgression}・{@link Enemies} の実体が
 * セグメント②まで無く、先に Bean 登録するとコンテキスト起動が失敗するため）。
 */
public class BattleSimulatorImpl implements BattleSimulator {

    private final TargetSelector targetSelector;

    private final DamageCalculator damageCalculator;

    private final FloorProgression floorProgression;

    private final Enemies enemies;

    private final RandomFactory randomFactory;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param targetSelector   ターゲット抽選
     * @param damageCalculator ダメージ計算
     * @param floorProgression 階進行の継ぎ目
     * @param enemies          敵マスター
     * @param randomFactory    乱数源のファクトリ
     * @param gameSettings     1tickあたりのターン数・ゴールド上限の供給元
     */
    public BattleSimulatorImpl(TargetSelector targetSelector, DamageCalculator damageCalculator,
            FloorProgression floorProgression, Enemies enemies, RandomFactory randomFactory,
            GameSettings gameSettings) {
        this.targetSelector = targetSelector;
        this.damageCalculator = damageCalculator;
        this.floorProgression = floorProgression;
        this.enemies = enemies;
        this.randomFactory = randomFactory;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BattleOutcome simulate(Player player, List<Character> party, int ticks) {
        throw new UnsupportedOperationException("製造①-iii（tick・オフラインの Green）で実装する");
    }
}
