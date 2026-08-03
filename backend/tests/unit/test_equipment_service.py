"""単体テスト: 装備サービス（services/equipment_service.py）

仕様: design/systems/equipment.md「装備スロット（9スロット）」「売却価格」
      「装備ステータス」、data/master/equipment.md
分岐観点:
  - try_drop: ドロップ無し / 設定レコード無し / オートセル未設定 / 未知レアリティ設定 /
              閾値以下・同値は自動売却 / 閾値超えは所持へ
  - equip_item: キャラ不在 / スロット不正 / 解除 / 装備不在 / スロット不一致 /
                両手武器での盾外し（盾スロット有無）/ 両手武器装備中の盾拒否
                （武器スロット無し・空・実体無し・片手）/ 他キャラからの付け替え
  - sell_items: 空リスト / 他人の装備 / ロック中 / 装着中 / 正常売却
  - toggle_lock: 装備不在 / False→True / True→False
  - get_effective_stats: 空スロット / 参照先不明 / 各ステータスの有無
"""

import pytest

from app.models.character import Character
from app.models.equipment import CharacterEquipSlot, Equipment
from app.models.player import Player
from app.services import equipment_service as es
from tests.helpers import count_queries

pytestmark = pytest.mark.unit

_CHAR_BASE = {
    "name": "勇者",
    "type": "melee",
    "level": 1,
    "exp": 0,
    "hp": 100,
    "max_hp": 100,
    "base_atk": 10,
    "base_def": 5,
    "base_spd": 5,
}


def _drop_dict(**overrides) -> dict:
    """generate_equipment_drop の戻り値相当の dict"""
    drop = {
        "base_id": "sword",
        "slot": "weapon",
        "rarity": "common",
        "level": 10,
        "enhance_level": 0,
        "is_two_handed": False,
        "lifesteal": None,
        "stat_atk": 15,
        "stat_def": None,
        "stat_hp": None,
        "stat_spd": None,
    }
    drop.update(overrides)
    return drop


@pytest.fixture
def make_equipment(db, player):
    """プレイヤー所有の装備を作るファクトリ"""

    def _make(**overrides) -> Equipment:
        data = {
            "player_id": player.id,
            "base_id": "sword",
            "slot": "weapon",
            "rarity": "common",
            "level": 1,
            "enhance_level": 0,
            "stat_atk": None,
            "stat_def": None,
            "stat_hp": None,
            "stat_spd": None,
            "lifesteal": None,
            "is_two_handed": False,
            "locked": False,
        }
        data.update(overrides)
        equip = Equipment(**data)
        db.add(equip)
        db.commit()
        return equip

    return _make


@pytest.fixture
def make_character(db, player):
    """任意のスロット構成を持つキャラクターを作るファクトリ（既定は9スロット）"""

    def _make(slots: list[str] | None = None) -> Character:
        char = Character(player_id=player.id, **_CHAR_BASE)
        db.add(char)
        db.flush()
        if slots is None:
            es.create_equip_slots(char.id, db)
        else:
            for slot in slots:
                db.add(CharacterEquipSlot(character_id=char.id, slot=slot))
        db.commit()
        return char

    return _make


def _slot_of(db, character_id: str, slot: str) -> CharacterEquipSlot:
    return db.query(CharacterEquipSlot).filter_by(
        character_id=character_id, slot=slot
    ).first()


class TestCreateEquipSlots:
    def test_9スロット分のレコードが生成される(self, db, character):
        es.create_equip_slots(character.id, db)
        db.commit()
        slots = db.query(CharacterEquipSlot).filter_by(character_id=character.id).all()
        assert sorted(s.slot for s in slots) == sorted(es.EQUIPMENT_SLOTS)
        assert all(s.equipment_id is None for s in slots)


class TestTryDrop:
    @staticmethod
    def _patch_drop(monkeypatch, drop):
        monkeypatch.setattr(
            es, "generate_equipment_drop", lambda enemy_level, floor, is_boss, rng: drop
        )

    def test_ドロップしなければ何も返さない(self, db, player, monkeypatch):
        self._patch_drop(monkeypatch, None)
        assert es.try_drop(player, 10, 1, False, db) == (None, None)

    def test_設定レコードが無ければ通常ドロップになる(self, db, monkeypatch):
        p = Player(id="player-no-settings", gold=0)
        db.add(p)
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict(rarity="common"))

        equip, sold = es.try_drop(p, 10, 1, False, db)
        assert sold is None
        assert equip is not None and equip.id is not None  # flush でID確定
        assert (equip.player_id, equip.rarity) == (p.id, "common")
        assert p.gold == 0

    def test_オートセル未設定なら通常ドロップになる(self, db, player, monkeypatch):
        assert player.settings.auto_sell_rarity is None
        self._patch_drop(monkeypatch, _drop_dict())

        equip, sold = es.try_drop(player, 10, 1, False, db)
        assert sold is None
        assert db.get(Equipment, equip.id) is not None
        assert player.gold == 1000

    def test_未知のレアリティ設定は無視される(self, db, player, monkeypatch):
        player.settings.auto_sell_rarity = "mythic"
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict())

        equip, sold = es.try_drop(player, 10, 1, False, db)
        assert (equip is not None, sold) == (True, None)
        assert player.gold == 1000

    def test_閾値より低いレアリティは自動売却される(self, db, player, monkeypatch):
        player.settings.auto_sell_rarity = "rare"
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict(rarity="uncommon", level=10))

        equip, sold = es.try_drop(player, 10, 1, False, db)
        assert equip is None
        # 5 × 1.3 × 10 = 65
        assert sold == {"name": "剣", "rarity": "uncommon", "gold": 65}
        assert player.gold == 1065
        assert db.query(Equipment).count() == 0

    def test_自動売却益は塔内取得ゴールドにも計上される(self, db, player, monkeypatch):
        """全滅ペナルティの没収対象を取得経路で非対称にしない（backend-review ISSUE-106）"""
        player.settings.auto_sell_rarity = "rare"
        player.run_gold = 200
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict(rarity="uncommon", level=10))

        _, sold = es.try_drop(player, 10, 1, False, db)

        assert player.run_gold == 200 + sold["gold"]

    def test_閾値と同じレアリティも自動売却される(self, db, player, monkeypatch):
        player.settings.auto_sell_rarity = "common"
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict(rarity="common", level=4))

        equip, sold = es.try_drop(player, 4, 1, False, db)
        assert equip is None
        assert sold["gold"] == 20  # 5 × 1.0 × 4
        assert player.gold == 1020

    def test_閾値より高いレアリティは所持品に残る(self, db, player, monkeypatch):
        player.settings.auto_sell_rarity = "common"
        db.commit()
        self._patch_drop(monkeypatch, _drop_dict(rarity="rare"))

        equip, sold = es.try_drop(player, 10, 1, False, db)
        assert sold is None
        assert equip.rarity == "rare"
        assert player.gold == 1000


class TestEquipItem:
    def test_他プレイヤーのキャラクターは装備できない(self, db, player, make_equipment):
        with pytest.raises(ValueError, match="キャラクターが見つかりません"):
            es.equip_item(player, "no-such-character", "weapon", None, db)

    def test_存在しないスロットは拒否される(self, db, player, make_character):
        char = make_character()
        with pytest.raises(ValueError, match="無効なスロット: tail"):
            es.equip_item(player, char.id, "tail", None, db)

    def test_装備を解除できる(self, db, player, make_character, make_equipment):
        char = make_character()
        equip = make_equipment(slot="head", base_id="helm")
        es.equip_item(player, char.id, "head", equip.id, db)
        db.commit()

        es.equip_item(player, char.id, "head", None, db)
        db.commit()
        assert _slot_of(db, char.id, "head").equipment_id is None

    def test_所有していない装備は拒否される(self, db, player, make_character):
        char = make_character()
        with pytest.raises(ValueError, match="装備が見つかりません"):
            es.equip_item(player, char.id, "weapon", "no-such-equipment", db)

    def test_スロットが一致しない装備は拒否される(self, db, player, make_character, make_equipment):
        char = make_character()
        equip = make_equipment(slot="head", base_id="helm")
        with pytest.raises(ValueError, match="この装備はスロット 'weapon' に装着できません"):
            es.equip_item(player, char.id, "weapon", equip.id, db)

    def test_片手武器を装着しても盾は外れない(self, db, player, make_character, make_equipment):
        char = make_character()
        shield = make_equipment(slot="shield", base_id="shield")
        sword = make_equipment(slot="weapon", base_id="sword", is_two_handed=False)
        es.equip_item(player, char.id, "shield", shield.id, db)
        es.equip_item(player, char.id, "weapon", sword.id, db)
        db.commit()

        assert _slot_of(db, char.id, "weapon").equipment_id == sword.id
        assert _slot_of(db, char.id, "shield").equipment_id == shield.id

    def test_両手武器を装着すると盾が外れる(self, db, player, make_character, make_equipment):
        char = make_character()
        shield = make_equipment(slot="shield", base_id="shield")
        greatsword = make_equipment(slot="weapon", base_id="greatsword", is_two_handed=True)
        es.equip_item(player, char.id, "shield", shield.id, db)
        es.equip_item(player, char.id, "weapon", greatsword.id, db)
        db.commit()

        assert _slot_of(db, char.id, "weapon").equipment_id == greatsword.id
        assert _slot_of(db, char.id, "shield").equipment_id is None

    def test_盾スロットが無いキャラでも両手武器を装着できる(self, db, player, make_character, make_equipment):
        char = make_character(slots=["weapon"])
        greatsword = make_equipment(slot="weapon", base_id="greatsword", is_two_handed=True)
        es.equip_item(player, char.id, "weapon", greatsword.id, db)
        db.commit()
        assert _slot_of(db, char.id, "weapon").equipment_id == greatsword.id

    def test_両手武器装備中は盾を装着できない(self, db, player, make_character, make_equipment):
        char = make_character()
        greatsword = make_equipment(slot="weapon", base_id="greatsword", is_two_handed=True)
        shield = make_equipment(slot="shield", base_id="shield")
        es.equip_item(player, char.id, "weapon", greatsword.id, db)
        db.commit()

        with pytest.raises(ValueError, match="両手武器を装備中は盾を装着できません"):
            es.equip_item(player, char.id, "shield", shield.id, db)
        assert _slot_of(db, char.id, "shield").equipment_id is None

    def test_武器スロットが空なら盾を装着できる(self, db, player, make_character, make_equipment):
        char = make_character()
        shield = make_equipment(slot="shield", base_id="shield")
        es.equip_item(player, char.id, "shield", shield.id, db)
        db.commit()
        assert _slot_of(db, char.id, "shield").equipment_id == shield.id

    def test_武器スロットが無いキャラでも盾を装着できる(self, db, player, make_character, make_equipment):
        char = make_character(slots=["shield"])
        shield = make_equipment(slot="shield", base_id="shield")
        es.equip_item(player, char.id, "shield", shield.id, db)
        db.commit()
        assert _slot_of(db, char.id, "shield").equipment_id == shield.id

    def test_武器スロットの参照先が消えていても盾を装着できる(self, db, player, make_character, make_equipment):
        char = make_character()
        weapon_slot = _slot_of(db, char.id, "weapon")
        weapon_slot.equipment_id = "ghost-weapon"  # 実体の無いID
        db.commit()

        shield = make_equipment(slot="shield", base_id="shield")
        es.equip_item(player, char.id, "shield", shield.id, db)
        db.commit()
        assert _slot_of(db, char.id, "shield").equipment_id == shield.id

    def test_他キャラが装備中なら付け替えられる(self, db, player, make_character, make_equipment):
        char_a = make_character()
        char_b = make_character()
        helm = make_equipment(slot="head", base_id="helm")

        es.equip_item(player, char_a.id, "head", helm.id, db)
        db.commit()
        es.equip_item(player, char_b.id, "head", helm.id, db)
        db.commit()

        assert _slot_of(db, char_a.id, "head").equipment_id is None
        assert _slot_of(db, char_b.id, "head").equipment_id == helm.id


class TestSellItems:
    def test_空リストなら何も売却されない(self, db, player):
        assert es.sell_items(player, [], db) == (0, 0)
        assert player.gold == 1000

    def test_所有していない装備は無視される(self, db, player):
        assert es.sell_items(player, ["no-such-equipment"], db) == (0, 0)
        assert player.gold == 1000

    def test_ロック中の装備は売却されない(self, db, player, make_equipment):
        equip = make_equipment(locked=True, rarity="rare", level=10)
        assert es.sell_items(player, [equip.id], db) == (0, 0)
        assert db.get(Equipment, equip.id) is not None

    def test_装着中の装備は売却されない(self, db, player, make_character, make_equipment):
        char = make_character()
        equip = make_equipment(slot="head", base_id="helm")
        es.equip_item(player, char.id, "head", equip.id, db)
        db.commit()

        assert es.sell_items(player, [equip.id], db) == (0, 0)
        assert db.get(Equipment, equip.id) is not None

    def test_複数の装備を売却してゴールドが加算される(self, db, player, make_equipment):
        a = make_equipment(rarity="common", level=10)   # 5 × 1.0 × 10 = 50
        b = make_equipment(rarity="epic", level=7)      # 5 × 2.0 × 7  = 70
        total, sold = es.sell_items(player, [a.id, b.id], db)
        db.commit()

        assert (total, sold) == (120, 2)
        assert player.gold == 1120
        assert db.query(Equipment).count() == 0


class TestToggleLock:
    def test_所有していない装備はエラー(self, db, player):
        with pytest.raises(ValueError, match="装備が見つかりません"):
            es.toggle_lock(player, "no-such-equipment", db)

    def test_ロックとロック解除が交互に切り替わる(self, db, player, make_equipment):
        equip = make_equipment(locked=False)
        assert es.toggle_lock(player, equip.id, db) is True
        assert equip.locked is True
        assert es.toggle_lock(player, equip.id, db) is False
        assert equip.locked is False


class TestCalcSellPrice:
    @pytest.mark.parametrize(
        ("rarity", "level", "expected"),
        [
            ("common", 10, 50),      # 5 × 1.0 × 10
            ("uncommon", 3, 19),     # floor(5 × 1.3 × 3) = floor(19.5)
            ("rare", 5, 40),         # 5 × 1.6 × 5
            ("epic", 1, 10),         # 5 × 2.0 × 1
            ("legendary", 20, 250),  # 5 × 2.5 × 20
        ],
    )
    def test_レアリティ倍率とレベルで価格が決まる(self, make_equipment, rarity, level, expected):
        equip = make_equipment(rarity=rarity, level=level)
        assert es.calc_sell_price(equip) == expected

    def test_ドロップdictからも同じ価格を算出する(self):
        assert es.calc_sell_price_from_dict({"rarity": "uncommon", "level": 3}) == 19


class TestGetEffectiveStats:
    def test_装着数に比例してクエリが増えない(self, db, make_character, make_equipment):
        """N+1防止（backend-review ISSUE-110）。tick毎・レベルアップ毎に呼ばれる"""

        def _measure(slots) -> int:
            char = make_character()
            for slot, base_id in slots:
                equip = make_equipment(slot=slot, base_id=base_id, stat_atk=1)
                _slot_of(db, char.id, slot).equipment_id = equip.id
            db.commit()
            db.expire_all()
            with count_queries(db) as sql:
                es.get_effective_stats(char, db)
            return len(sql)

        one = _measure([("head", "helm")])
        three = _measure([("head", "helm"), ("ring", "ring"), ("body", "chest_armor")])
        assert one == three, f"装着数でクエリ数が変わる（1件={one} / 3件={three}）"

    def test_装備が無ければ基礎値のみを返す(self, db, make_character):
        char = make_character()
        assert es.get_effective_stats(char, db) == {
            "atk": 10,
            "def": 5,
            "spd": 5,
            "hp_bonus": 0,
            "lifesteal": 0.0,
        }

    def test_参照先の装備が存在しないスロットは無視される(self, db, make_character):
        char = make_character()
        _slot_of(db, char.id, "head").equipment_id = "ghost-equipment"
        db.commit()
        db.expire_all()

        assert es.get_effective_stats(char, db)["atk"] == 10

    def test_装備のステータスが加算される(self, db, make_character, make_equipment):
        char = make_character()
        head = make_equipment(
            slot="head", base_id="helm",
            stat_atk=7, stat_def=3, stat_hp=40, stat_spd=2, lifesteal=0.05,
        )
        ring = make_equipment(
            slot="ring", base_id="ring",
            stat_atk=1, stat_def=1, stat_hp=10, stat_spd=1, lifesteal=0.02,
        )
        _slot_of(db, char.id, "head").equipment_id = head.id
        _slot_of(db, char.id, "ring").equipment_id = ring.id
        db.commit()

        stats = es.get_effective_stats(char, db)
        assert stats["atk"] == 18       # 10 + 7 + 1
        assert stats["def"] == 9        # 5 + 3 + 1
        assert stats["spd"] == 8        # 5 + 2 + 1
        assert stats["hp_bonus"] == 50  # 40 + 10
        assert stats["lifesteal"] == pytest.approx(0.07)

    def test_未付与のステータスは加算されない(self, db, make_character, make_equipment):
        char = make_character()
        blank = make_equipment(slot="body", base_id="chest_armor")  # 全ステータス None
        _slot_of(db, char.id, "body").equipment_id = blank.id
        db.commit()

        assert es.get_effective_stats(char, db) == {
            "atk": 10,
            "def": 5,
            "spd": 5,
            "hp_bonus": 0,
            "lifesteal": 0.0,
        }


class TestGetEquippedMap:
    def test_スロットと装備IDの対応を返す(self, db, player, make_character, make_equipment):
        char = make_character()
        helm = make_equipment(slot="head", base_id="helm")
        es.equip_item(player, char.id, "head", helm.id, db)
        db.commit()

        mapping = es.get_equipped_map(char.id, db)
        assert set(mapping) == set(es.EQUIPMENT_SLOTS)
        assert mapping["head"] == helm.id
        assert mapping["weapon"] is None

    def test_スロット未作成なら空辞書を返す(self, db, make_character):
        char = make_character(slots=[])
        assert es.get_equipped_map(char.id, db) == {}
