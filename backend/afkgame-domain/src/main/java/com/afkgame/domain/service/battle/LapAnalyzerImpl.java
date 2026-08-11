package com.afkgame.domain.service.battle;

import java.util.List;

import com.afkgame.domain.masterdata.EnemyData;
import com.afkgame.domain.masterdata.Items;
import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;
import com.afkgame.domain.repository.PlayerRepository;
import com.afkgame.env.config.GameSettings;

/**
 * {@link LapAnalyzer} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。本クラスは tech_offline.md §4.1 の期待値計算式
 * （期待与ダメージ・期待被ダメージ・撃破ターン数・ポーション消費モデル）を担い、丸めと下限は
 * docs/tech/detail/tech_numeric.md §2 に従う。乱数は使わず、すべて期待値へ換算して確定計算する。
 *
 * <p><b>{@code @Service} はまだ付けない。</b>{@link FloorCatalog} の実体がセグメント②（tower）まで
 * 無く、先に Bean 登録するとコンテキスト起動が失敗するため。付与はセグメント②で行う
 * （docs/backlog/java_migration.md STEP 3-B）。
 *
 * <p><b>Phase 1 の範囲に絞った未実装</b>（いずれも分岐一覧に行が無く、テストも持たない）:
 * ①{@code lapsToLevelUp} は常に {@link Integer#MAX_VALUE}（＝到達しない）を返す。次レベルまでの
 * 必要EXPを返す口が {@link CharacterGrowth} に無く、追加はキャラ成長のテストリスト作成
 * （経験値テーブルは docs/data/master/character.md §1.4、成長率は {@code character_types.yml} が未搭載）
 * を先に要するため。周回中のレベルアップ分岐 tech_offline.md §5 #7・#8 は
 * {@link OfflineCalculator} 側が持つ。
 * ②クリティカル率の合算値は {@link #SUMMED_CRIT_RATE}。③回復スキル・リジェネによる
 * 純被ダメの差し引き（§4.1「回復期待値」）と範囲攻撃・挑発・軽減パッシブは tech_offline.md §7
 * が持つ分岐で、Phase 1 の編成では到達しない（展開は Phase 3 の製造）。
 * ④ドロップアイテムは供給元（階ごとのドロップテーブル）がセグメント②まで無いため空リストを返す。
 */
public class LapAnalyzerImpl implements LapAnalyzer {

    /** 回復に使うポーションのアイテムID（docs/data/master/item.md）。 */
    private static final String POTION_ITEM_ID = "hp_potion";

    /** 1周回の起点。1周回は1階から目標階までを通しで攻略する（tech_offline.md §4 手順3a）。 */
    private static final int FIRST_FLOOR = 1;

    /** DEF の減算係数（§4.1 の {@code DEF × 0.5}）。 */
    private static final double DEF_FACTOR = 0.5;

    /** クリティカルの期待値換算の重み（§4.1 {@code crit_factor = 1 + crit_rate × 0.5}）。 */
    private static final double CRIT_FACTOR_WEIGHT = 0.5;

    /**
     * {@link StatCalculator#effectiveCritRate(double)} へ通す合算クリティカル率。
     *
     * <p>味方の基礎クリティカル率は {@code character_types.yml} が持つ（tech_rng.md §6）が、
     * 本クラスの表層（テストが定める注入）に {@code CharacterTypes} が無く読めないため0を渡す。
     * 供給元の配線はセグメント②以降で表層ごと見直す。
     */
    private static final double SUMMED_CRIT_RATE = 0.0;

    /** 味方→敵の期待与ダメージの下限（tech_numeric.md §2）。 */
    private static final double MIN_EXPECTED_HIT = 1.0;

    /** 敵→味方の期待被ダメージの下限。0ダメージを許容する（tech_numeric.md §2）。 */
    private static final double MIN_EXPECTED_TAKEN = 0.0;

    /** 撃破ターン数・必要tick数の下限（tech_numeric.md §2）。 */
    private static final long MIN_TURNS = 1L;

    /** ポーション回復量の下限（tech_numeric.md §2）。 */
    private static final long MIN_POTION_HEAL = 1L;

    private final FloorCatalog floorCatalog;

    private final StatCalculator statCalculator;

    private final CharacterGrowth characterGrowth;

    private final Items items;

    private final PlayerRepository playerRepository;

    private final GameSettings gameSettings;

    /**
     * 依存を受け取る。
     *
     * @param floorCatalog     階のデータを読む継ぎ目
     * @param statCalculator   確率・軽減率の上限適用
     * @param characterGrowth  次レベルまでの必要経験値の参照。参照する口が未追加のため
     *                         現時点では読まない（クラス Javadoc の未実装①）
     * @param items            ポーションの回復割合の参照
     * @param playerRepository プレイヤー設定（自動使用閾値）の参照
     * @param gameSettings     1tickあたりのターン数の供給元
     */
    public LapAnalyzerImpl(FloorCatalog floorCatalog, StatCalculator statCalculator,
            CharacterGrowth characterGrowth, Items items, PlayerRepository playerRepository,
            GameSettings gameSettings) {
        this.floorCatalog = floorCatalog;
        this.statCalculator = statCalculator;
        this.characterGrowth = characterGrowth;
        this.items = items;
        this.playerRepository = playerRepository;
        this.gameSettings = gameSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LapAnalysis analyze(Player player, List<Character> party, int potionStock) {
        double critRate = statCalculator.effectiveCritRate(SUMMED_CRIT_RATE);
        long turnsPerLap = 0L;
        double takenPerLap = 0.0;
        long goldPerLap = 0L;
        long expPerLap = 0L;
        for (int floor = FIRST_FLOOR; floor <= player.getTargetFloor(); floor++) {
            List<EnemyData> enemies = floorCatalog.enemiesOf(player.getCurrentTowerId(), floor);
            long turns = turnsToClear(party, enemies, critRate);
            turnsPerLap += turns;
            takenPerLap += takenPerTurn(party, enemies) * turns;
            for (EnemyData enemy : enemies) {
                goldPerLap += enemy.gold();
                expPerLap += enemy.exp();
            }
        }

        // 純被ダメはポーション回復を差し引いた値にする。全滅判定（§4.1）が「ポーション回復込み」で
        // 行われるため、在庫の増減がそのまま呼び出し側の §5 #5・#6 の分岐に効く
        long rawDamage = (long) Math.floor(takenPerLap);
        long heal = potionHeal(party);
        double spareHp = partyMaxHp(party) * (1 - potionThresholdOf(player));
        long needed = (long) Math.ceil(Math.max(0.0, rawDamage - spareHp) / heal);
        int potions = (int) Math.min(needed, potionStock);
        long netDamage = Math.max(0L, rawDamage - potions * heal);

        int ticksPerLap = (int) Math.max(MIN_TURNS,
                (long) Math.ceil((double) turnsPerLap / gameSettings.turnsPerTick()));
        // 周回が成立した時点で目標階まで到達している（全滅なら周回自体が発生しない）。
        // 「新しい階か」の判定は目標階と上限の一致（§5 #9・#10）が持つ
        return new LapAnalysis(ticksPerLap, netDamage, potions, goldPerLap, expPerLap, List.of(),
                Integer.MAX_VALUE, true, floorCatalog.targetFloorCap(player));
    }

    /**
     * 1階ぶんの撃破ターン数を求める（§4.1「階の撃破ターン数」）。
     *
     * @param party    パーティ
     * @param enemies  その階の敵編成
     * @param critRate 実効クリティカル率
     * @return 撃破ターン数（{@code ceil}・下限1）
     */
    private long turnsToClear(List<Character> party, List<EnemyData> enemies, double critRate) {
        double critFactor = 1 + critRate * CRIT_FACTOR_WEIGHT;
        double damagePerTurn = 0.0;
        for (Character ally : party) {
            damagePerTurn += baseHit(ally, enemies) * critFactor;
        }
        long enemyHp = 0L;
        for (EnemyData enemy : enemies) {
            enemyHp += enemy.hp();
        }
        return Math.max(MIN_TURNS, (long) Math.ceil(enemyHp / damagePerTurn));
    }

    /**
     * 味方1体・1ターンあたりの期待与ダメージを求める（§4.1 {@code base_hit}）。
     *
     * <p>敵のDEFは階の平均を用いる。Phase 1 は1階1体なのでその敵のDEFそのものになり、
     * 複数体（Phase 3〜）の扱いは範囲攻撃とあわせて §7 で確定する。
     *
     * @param ally    対象の味方
     * @param enemies その階の敵編成
     * @return 期待与ダメージ（下限1）
     */
    private double baseHit(Character ally, List<EnemyData> enemies) {
        double enemyDef = 0.0;
        for (EnemyData enemy : enemies) {
            enemyDef += enemy.def();
        }
        return Math.max(MIN_EXPECTED_HIT,
                ally.getBaseAtk() - enemyDef / enemies.size() * DEF_FACTOR);
    }

    /**
     * 1ターンあたりのパーティ合計の期待被ダメージを求める（§4.1 {@code E_taken} の総和）。
     *
     * <p>ランダムターゲットのため敵の攻撃は生存味方数で均等分散する。
     *
     * @param party   パーティ
     * @param enemies その階の敵編成
     * @return パーティ合計の期待被ダメージ（各敵の寄与は下限0）
     */
    private double takenPerTurn(List<Character> party, List<EnemyData> enemies) {
        double total = 0.0;
        for (Character ally : party) {
            for (EnemyData enemy : enemies) {
                total += Math.max(MIN_EXPECTED_TAKEN, enemy.atk() - ally.getBaseDef() * DEF_FACTOR);
            }
        }
        return total / party.size();
    }

    /**
     * ポーション1個あたりの回復量を求める（§4.1「ポーション消費モデル」）。
     *
     * <p>純被ダメをパーティ単位でプールして扱うため、{@code maxHP} も平均を用いる
     * （Phase 1 の1体編成ではそのキャラの {@code maxHP} と一致する）。
     *
     * @param party パーティ
     * @return 回復量（{@code floor}・下限1）
     */
    private long potionHeal(List<Character> party) {
        double healRatio = items.all().get(POTION_ITEM_ID).healRatio();
        return Math.max(MIN_POTION_HEAL,
                (long) Math.floor(healRatio * partyMaxHp(party) / party.size()));
    }

    /** ポーション自動使用閾値を読む（既定30%。プレイヤー設定が正）。 */
    private double potionThresholdOf(Player player) {
        return playerRepository.findSettingsByPlayerId(player.getId()).getPotionThreshold();
    }

    private static long partyMaxHp(List<Character> party) {
        return party.stream().mapToLong(Character::getMaxHp).sum();
    }
}
