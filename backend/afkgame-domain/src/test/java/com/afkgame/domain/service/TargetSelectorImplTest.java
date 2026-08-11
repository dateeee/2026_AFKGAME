package com.afkgame.domain.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.afkgame.domain.model.Character;

/**
 * {@link TargetSelectorImpl} の単体テスト（通常攻撃のターゲット抽選）。
 *
 * <p>仕様: docs/tech/detail/tech_rng.md §5（分岐一覧）・§1 #3、選択規則は
 * docs/tech/detail/tech_battle.md §3.3。
 *
 * <p>分岐観点: 生存者が0体のときの呼び出し（呼び出し側の打ち切り漏れ）。
 * 打ち切りそのもの（味方全滅・敵撃破で以降の行動を行わない）は
 * {@link BattleSimulatorImplTest} が tech_battle.md §5 #4・#5 で持つ。
 *
 * <p><b>製造工程への申し送り（本セッションでは未実装。テストが要求する表層）</b>:
 * <ul>
 *   <li>{@code interface TargetSelector}:
 *       {@code Character selectRandom(List<Character> candidates, Random rng)}。
 *       候補は絞り込まずに渡し、{@code hp > 0} の生存者だけを対象にするのは実装側が行う
 *       （tech_battle.md §3.3 の {@code [e for e in enemies if e.hp > 0]}）</li>
 *   <li>生存者が0体の呼び出しは<b>到達しないはずの経路</b>（呼び出し側が全滅判定で
 *       打ち切っている前提）。理由コメントを添えて {@code IllegalStateException} を投げる
 *       ＝ 分類3・バグとして扱う（coding_standards_backend/exception.md §3 #4）。
 *       握りつぶして {@code null} を返すと、その先で
 *       {@code NullPointerException} になり原因が追えない</li>
 * </ul>
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class TargetSelectorImplTest {

    @Mock
    private Random random;

    private TargetSelector selector() {
        return new TargetSelectorImpl();
    }

    private Character character(String id, int hp) {
        Character character = new Character();
        character.setId(id);
        character.setHp(hp);
        character.setMaxHp(100);
        return character;
    }

    @Nested
    @DisplayName("生存者0体での呼び出し")
    class TestNoSurvivor {

        /**
         * 候補が全員HP0なら抽選できない。呼び出し側（{@code BattleSimulatorImpl}）が
         * 全滅判定で打ち切る契約になっており、ここへ来ること自体がバグなので例外にする。
         * 候補の絞り込みで空になった時点で判明するため、乱数は消費しない。
         *
         * <p>分岐: tech_rng.md §5 #7
         */
        @Test
        void test_生存者が0体なら抽選せず例外になる() {
            List<Character> allDead = List.of(character("char_001", 0), character("char_002", 0));

            assertThrows(IllegalStateException.class, () -> selector().selectRandom(allDead, random));

            verifyNoInteractions(random);
        }
    }
}
