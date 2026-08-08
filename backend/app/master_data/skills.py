"""スキルマスターデータ（Phase 3〜）

正は docs/data/skills/ の系統別ファイル §3「スキル一覧」・§4「スキル詳細」。
本モジュールは習得・セット・リセットに必要な属性（種別・必要SP・前提・CD）だけを持つ。
ダメージ倍率・対象・状態異常などの戦闘内効果は製造②（skill_service）で追加する。
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class SkillData:
    id: str
    name: str
    category: str  # 系統ID（sword / magic / heal / buff / debuff / surv）
    tier: int
    skill_type: str  # active（セット枠が要る） / passive（習得だけで常時有効）
    sp_cost: int
    cooldown: int | None  # ターン数。パッシブは None
    # 前提スキル。空 = Tier1（前提なし）。複数ある場合は **いずれか1つ** の習得で充足する
    # （heal_3 のみ「heal_2 または heal_p2」。docs/data/skills/003_回復系統.md §4）
    prerequisites: tuple[str, ...] = ()


SKILLS: dict[str, SkillData] = {
    # ── 剣術系統（物理単体攻撃）──
    "sword_1":   SkillData("sword_1",   "強撃",       "sword",  1, "active",  1, 3),
    "sword_2":   SkillData("sword_2",   "連続斬り",   "sword",  2, "active",  1, 4, ("sword_1",)),
    "sword_p1":  SkillData("sword_p1",  "剣の心得",   "sword",  3, "passive", 2, None, ("sword_2",)),
    "sword_3":   SkillData("sword_3",   "渾身の一撃", "sword",  4, "active",  3, 5, ("sword_p1",)),
    # ── 魔法系統（魔法攻撃）──
    "magic_1":   SkillData("magic_1",   "ファイアボルト", "magic", 1, "active",  1, 3),
    "magic_p1":  SkillData("magic_p1",  "魔力増幅",       "magic", 2, "passive", 1, None, ("magic_1",)),
    "magic_2":   SkillData("magic_2",   "アイスストーム", "magic", 3, "active",  2, 5, ("magic_p1",)),
    "magic_3":   SkillData("magic_3",   "メテオ",         "magic", 4, "active",  3, 8, ("magic_2",)),
    # ── 回復系統（HP回復・蘇生）──
    "heal_1":    SkillData("heal_1",    "ヒール",       "heal", 1, "active",  1, 4),
    "heal_p1":   SkillData("heal_p1",   "回復の心得",   "heal", 2, "passive", 1, None, ("heal_1",)),
    "heal_2":    SkillData("heal_2",    "全体回復",     "heal", 3, "active",  2, 6, ("heal_p1",)),
    "heal_p2":   SkillData("heal_p2",   "リジェネ",     "heal", 3, "passive", 2, None, ("heal_p1",)),
    "heal_3":    SkillData("heal_3",    "蘇生",         "heal", 4, "active",  3, 8, ("heal_2", "heal_p2")),
    # ── 強化系統（バフ）──
    "buff_1":    SkillData("buff_1",    "力の祝福",   "buff", 1, "active",  1, 6),
    "buff_p1":   SkillData("buff_p1",   "バフ延長",   "buff", 2, "passive", 1, None, ("buff_1",)),
    "buff_2":    SkillData("buff_2",    "守りの祝福", "buff", 3, "active",  2, 6, ("buff_p1",)),
    "buff_3":    SkillData("buff_3",    "英雄の号令", "buff", 4, "active",  3, 8, ("buff_2",)),
    # ── 弱体系統（デバフ・状態異常）──
    "debuff_1":  SkillData("debuff_1",  "威圧",     "debuff", 1, "active",  1, 4),
    "debuff_p1": SkillData("debuff_p1", "弱体延長", "debuff", 2, "passive", 1, None, ("debuff_1",)),
    "debuff_2":  SkillData("debuff_2",  "毒付与",   "debuff", 3, "active",  2, 5, ("debuff_p1",)),
    "debuff_3":  SkillData("debuff_3",  "全体弱化", "debuff", 4, "active",  3, 8, ("debuff_2",)),
    # ── 生存術系統（耐久・防御）──
    "surv_p1":   SkillData("surv_p1",   "体力強化",   "surv", 1, "passive", 1, None),
    "surv_1":    SkillData("surv_1",    "挑発",       "surv", 2, "active",  1, 5, ("surv_p1",)),
    "surv_p2":   SkillData("surv_p2",   "被ダメ軽減", "surv", 3, "passive", 2, None, ("surv_1",)),
    "surv_2":    SkillData("surv_2",    "反撃",       "surv", 4, "passive", 3, None, ("surv_p2",)),
}

#: セット枠へ入れられるスキルID。未知IDもここに含まれないため同じ経路で弾ける
ACTIVE_SKILL_IDS: frozenset[str] = frozenset(
    s.id for s in SKILLS.values() if s.skill_type == "active"
)


def get_skill(skill_id: str) -> SkillData | None:
    """スキルマスターを引く。未知IDは None（tech_party.md §3 の 422 SKILL_UNKNOWN 経路）"""
    return SKILLS.get(skill_id)
