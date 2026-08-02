"""単体テスト: ゲーム状態構築サービス（services/game_state_builder.py）

仕様: tech/tech_api.md ゲーム状態レスポンス（GET /api/game/state と共通）
分岐観点:
  - ポーション判定: item_id が "_potion" で終わる所持品のみ集計
  - 塔クリア記録の有無
  - 戦闘中の敵: 敵IDなし / HP未設定 / HP0 / 有効 / 未知ID（KeyError は無視）
  - キャラクター・設定の有無（無ければ既定値の設定レスポンス）
"""

import pytest

from app.models.item import InventoryItem
from app.models.player import Player
from app.services.game_state_builder import build_game_state

pytestmark = pytest.mark.unit


class _StubEnemyData:
    """get_enemy の戻り値スタブ（マスターデータ非依存にする）"""

    id = "goblin"
    name = "ゴブリン"
    hp = 30
    level = 2


class TestBuildGameState:
    def test_標準プレイヤーの状態を構築できる(self, db, player):
        state = build_game_state(player, db)
        assert state.player.id == player.id
        assert state.potions == {"hp_potion": 5}
        assert state.towers_cleared == {}
        assert state.current_enemy is None
        assert len(state.characters) == 1
        assert state.characters[0].effective_max_hp == 100  # 装備なし → 基本max_hp
        assert state.settings.potion_threshold == 0.3
        assert state.equipment == []

    def test_ポーション以外の所持品は含まれない(self, db, player):
        db.add(InventoryItem(player_id=player.id, item_id="iron_ore", quantity=3))
        db.commit()
        db.expire(player)
        state = build_game_state(player, db)
        assert "iron_ore" not in state.potions
        assert state.potions == {"hp_potion": 5}

    def test_塔クリア記録が反映される(self, db, player, tower_record):
        tower_record("goblin_tower", highest_floor=5, cleared=True)
        db.expire(player)
        state = build_game_state(player, db)
        info = state.towers_cleared["goblin_tower"]
        assert info.cleared is True
        assert info.highest_floor == 5

    def test_戦闘中の敵情報を返す(self, db, player, monkeypatch):
        monkeypatch.setattr(
            "app.services.game_state_builder.get_enemy",
            lambda enemy_id: _StubEnemyData(),
        )
        player.current_enemy_id = "goblin"
        player.current_enemy_hp = 10
        state = build_game_state(player, db)
        assert state.current_enemy is not None
        assert state.current_enemy.id == "goblin"
        assert state.current_enemy.hp == 10
        assert state.current_enemy.max_hp == 30
        assert state.current_enemy.level == 2

    def test_敵HPが未設定なら敵情報なし(self, db, player):
        player.current_enemy_id = "goblin"
        player.current_enemy_hp = None
        assert build_game_state(player, db).current_enemy is None

    def test_敵HPが0なら敵情報なし(self, db, player):
        player.current_enemy_id = "goblin"
        player.current_enemy_hp = 0
        assert build_game_state(player, db).current_enemy is None

    def test_不明な敵IDは無視される(self, db, player, monkeypatch):
        def _raise(enemy_id):
            raise KeyError(enemy_id)

        monkeypatch.setattr("app.services.game_state_builder.get_enemy", _raise)
        player.current_enemy_id = "unknown_enemy"
        player.current_enemy_hp = 10
        assert build_game_state(player, db).current_enemy is None

    def test_キャラクターと設定が無いプレイヤーは既定値(self, db):
        bare = Player(id="bare-player", gold=0)
        db.add(bare)
        db.commit()
        db.refresh(bare)
        state = build_game_state(bare, db)
        assert state.characters == []
        assert state.equipped == {}
        assert state.settings.potion_threshold == 0.3
        assert state.settings.battle_log_count == 50
        assert state.settings.toast_enabled is True
        assert state.settings.auto_sell_rarity is None
