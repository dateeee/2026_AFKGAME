package com.afkgame.domain.service.battle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import com.afkgame.domain.masterdata.CharacterTypes;
import com.afkgame.domain.masterdata.Enemies;
import com.afkgame.domain.masterdata.EnemyData;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.rng.RandomFactory;
import com.afkgame.env.config.GameSettings;

/**
 * {@link BattleSimulator} の実装。
 *
 * <p>仕様: docs/tech/detail/tech_battle.md §3.1 手順1〜2・§5（1tick内のターン処理）、
 * 乱数源の扱いは docs/tech/detail/tech_rng.md §2、ゴールドの飽和は
 * docs/tech/detail/tech_numeric.md §3。
 *
 * <p>本クラスは行動順の決定と打ち切り判定、撃破報酬の付与を担い、ダメージ計算・ターゲット抽選・
 * 階進行はそれぞれの協調オブジェクトへ委譲する。
 *
 * <p>乱数源の生成点は本クラスである（{@link BattleServiceImpl} は {@link RandomFactory} を
 * 受け取らない）。{@code simulate} の入口で1回だけ生成し、協調オブジェクトへ引数で引き渡す
 * （tech_rng.md §2）。
 *
 * <p><b>{@code @Service} はまだ付けない。</b>{@link FloorProgression}・{@link Enemies} の実体が
 * セグメント②（tower）まで無く、先に Bean 登録するとコンテキスト起動が失敗するため。
 * 付与はセグメント②で行う（docs/backlog/java_migration.md STEP 3-B）。
 *
 * <p><b>Phase 1 の範囲に絞った未実装</b>（いずれも分岐一覧に行が無く、テストも持たない）:
 * ①パーティへの EXP 配分（{@link CharacterGrowth#addExp}）は成長のしきい値到達分岐が
 * 未確定のため行わず、撃破 EXP は {@link BattleOutcome} へ集計するだけにとどめる
 * （tech_battle.md §3.1.5・§3.4 は Phase 3〜）。②ターン最終アクターの行動で全滅した場合の
 * 全滅処理（§3.1 手順6）は、§5 #4 が「以降のアクターの行動を行わず」と定める行動前判定だけを
 * 実装しているため次ターン先頭で検知する。③クリティカル率の合算・上限
 * （{@link StatCalculator#effectiveCritRate}）は装備・スキルが載る Phase 3〜。
 */
public class BattleSimulatorImpl implements BattleSimulator {

    /** 撃破が無かったターンの報酬。 */
    private static final BattleOutcome NO_REWARD = new BattleOutcome(0L, 0L);

    private final TargetSelector targetSelector;

    private final DamageCalculator damageCalculator;

    private final FloorProgression floorProgression;

    private final Enemies enemies;

    private final CharacterTypes characterTypes;

    private final RandomFactory randomFactory;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param targetSelector   ターゲット抽選
     * @param damageCalculator ダメージ計算
     * @param floorProgression 階進行の継ぎ目
     * @param enemies          敵マスター（敵のクリティカル率もここから読む）
     * @param characterTypes   キャラタイプ別マスター（味方のクリティカル率の供給元。tech_rng.md §6）
     * @param randomFactory    乱数源のファクトリ
     * @param gameSettings     1tickあたりのターン数・ゴールド上限の供給元
     */
    public BattleSimulatorImpl(TargetSelector targetSelector, DamageCalculator damageCalculator,
            FloorProgression floorProgression, Enemies enemies, CharacterTypes characterTypes,
            RandomFactory randomFactory, GameSettings gameSettings) {
        this.targetSelector = targetSelector;
        this.damageCalculator = damageCalculator;
        this.floorProgression = floorProgression;
        this.enemies = enemies;
        this.characterTypes = characterTypes;
        this.randomFactory = randomFactory;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BattleOutcome simulate(Player player, List<Character> party, int ticks) {
        Random rng = randomFactory.create();
        int totalTurns = ticks * gameSettings.turnsPerTick();
        BattleOutcome total = NO_REWARD;
        for (int turn = 0; turn < totalTurns; turn++) {
            TurnResult result = runTurn(player, party, rng);
            total = merge(total, result.gained());
            if (!result.partyAlive()) {
                return total;
            }
        }
        return total;
    }

    /**
     * 1ターン分のアクター進行を行う（tech_battle.md §3.1 手順2）。
     *
     * @param player 対象プレイヤー
     * @param party  パーティ
     * @param rng    このリクエストの乱数源
     * @return このターンの獲得報酬と、パーティが生存しているか
     */
    private TurnResult runTurn(Player player, List<Character> party, Random rng) {
        floorProgression.ensureEncounter(player, rng);
        EnemyData enemy = enemies.get(player.getCurrentEnemyId());
        BattleOutcome gained = NO_REWARD;
        for (Actor actor : turnOrder(party, enemy)) {
            if (!hasAliveAlly(party)) {
                // §5 #4: 味方全滅。以降のアクターは行動せず強制撤退へ移す
                floorProgression.onPartyWiped(player, party);
                return new TurnResult(gained, false);
            }
            if (isEnemyOffField(player)) {
                // §5 #5: 撃破済み、または階進行で場から除かれた。撃破済みの敵は同一ターン内に反撃しない
                return new TurnResult(gained, true);
            }
            if (actor.isEnemy()) {
                attackAlly(party, enemy, rng);
            } else {
                gained = merge(gained, attackEnemy(player, party, actor.ally(), enemy, rng));
            }
        }
        return new TurnResult(gained, true);
    }

    /**
     * 味方の一撃。撃破したら報酬を付与し、階進行へ通知する（§3.1 手順2-j・2-l）。
     *
     * @return 撃破していれば敵の報酬、していなければ {@link #NO_REWARD}
     */
    private BattleOutcome attackEnemy(Player player, List<Character> party, Character ally,
            EnemyData enemy, Random rng) {
        long damage = damageCalculator.calculate(ally.getBaseAtk(), enemy.def(), critRateOf(ally),
                DamageDirection.ALLY_TO_ENEMY, rng);
        int remaining = (int) Math.max(0L, player.getCurrentEnemyHp() - damage);
        player.setCurrentEnemyHp(remaining);
        if (remaining > 0) {
            return NO_REWARD;
        }
        // 上限は飽和させる（超過を例外にすると上限到達後にプレイが止まる。tech_numeric.md §3）
        player.setGold(Math.min(gameSettings.maxGold(), player.getGold() + enemy.gold()));
        floorProgression.onEnemyDefeated(player, party, rng);
        return new BattleOutcome(enemy.gold(), enemy.exp());
    }

    /**
     * 敵の一撃。ターゲットは生存者から抽選する（§3.1 手順2-i・§3.3）。
     */
    private void attackAlly(List<Character> party, EnemyData enemy, Random rng) {
        Character target = targetSelector.selectRandom(party, rng);
        long damage = damageCalculator.calculate(enemy.atk(), target.getBaseDef(), enemy.critRate(),
                DamageDirection.ENEMY_TO_ALLY, rng);
        target.setHp((int) Math.max(0L, target.getHp() - damage));
    }

    /**
     * SPD降順で行動順を組む。同速は味方を先にし、味方どうしはキャラID順で並べる（§3.1 手順1）。
     */
    private List<Actor> turnOrder(List<Character> party, EnemyData enemy) {
        List<Actor> actors = new ArrayList<>();
        for (Character ally : party) {
            actors.add(new Actor(ally, ally.getBaseSpd(), ally.getId()));
        }
        actors.add(new Actor(null, enemy.spd(), enemy.id()));
        actors.sort(Comparator.comparingInt(Actor::spd).reversed()
                .thenComparing(Actor::isEnemy)
                .thenComparing(Actor::id));
        return actors;
    }

    /**
     * 敵が場にいないかを返す。撃破（HP0）に加えて、階クリア・撤退で敵ID・敵HPが
     * {@code null} になった直後も捕捉する（§5 の注記）。
     */
    private boolean isEnemyOffField(Player player) {
        Integer enemyHp = player.getCurrentEnemyHp();
        return enemyHp == null || enemyHp <= 0;
    }

    private boolean hasAliveAlly(List<Character> party) {
        return party.stream().anyMatch(ally -> ally.getHp() > 0);
    }

    /** 味方のクリティカル率はキャラタイプ別マスターから読む（tech_rng.md §6）。 */
    private double critRateOf(Character ally) {
        return characterTypes.get(ally.getType()).critRate();
    }

    private static BattleOutcome merge(BattleOutcome left, BattleOutcome right) {
        return new BattleOutcome(left.gold() + right.gold(), left.exp() + right.exp());
    }

    /**
     * 行動順に並べる1アクター。
     *
     * @param ally 味方キャラクター。{@code null} なら敵
     * @param spd  行動順の決定に使う素早さ
     * @param id   同速時のタイブレークに使うID
     */
    private record Actor(Character ally, int spd, String id) {

        boolean isEnemy() {
            return ally == null;
        }
    }

    /**
     * 1ターンの結果。
     *
     * @param gained     このターンに獲得した報酬
     * @param partyAlive パーティが1体でも生存していれば {@code true}
     */
    private record TurnResult(BattleOutcome gained, boolean partyAlive) {
    }
}
