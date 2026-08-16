package com.afkgame.domain.service.tower;

import java.util.List;

import com.afkgame.domain.model.Character;
import com.afkgame.domain.model.Player;

/**
 * {@link TowerStateValidator} の実装。
 *
 * <p>仕様・契約はインタフェース側が持つ。検証だけを行い、依存を持たない
 * （{@link Player} と出撃中のパーティだけで判定できる条件に限る。範囲の上限側
 * {@code targetFloor ≤ min(highestFloor + 1, 総階数)} はマスターデータが要るため
 * 対象外＝テストクラスの申し送りを参照）。
 *
 * <p><b>本クラスはテストリスト作成②-d で用意した表層であり、本体は未実装。</b>
 * 分岐は {@code TowerStateValidatorImplTest} が Red のテストとして持ち、実装は製造②で行う。
 * {@code @Service} も製造②で付ける（同じ回に載る {@code TowerServiceImpl}・
 * {@code FloorProgressionImpl} とスキャン対象へ入る単位をそろえるため）。
 */
public class TowerStateValidatorImpl implements TowerStateValidator {

    @Override
    public void validate(Player player, List<Character> party) {
        throw new UnsupportedOperationException("製造②（tower）で実装する");
    }
}
