"""AFK Game - 設定・定数

環境変数の参照は本モジュールに集約する（tech_operations.md §12.2）。
アプリケーションコードから `os.environ` を直接読まない。
"""

import os

# アプリバージョン（FastAPI のメタ情報と /health の応答で共有する）
APP_VERSION = "0.1.0"

# tick設定
TICK_INTERVAL_SECONDS = 60
TURNS_PER_TICK = 3
OFFLINE_EFFICIENCY = 1.0
MAX_OFFLINE_HOURS = 24
FAST_CALC_THRESHOLD = 100

# 戦闘ログ設定（ゲーム内データ）
MAX_BATTLE_LOG_RECORDS = 100
MAX_LOG_PER_RESPONSE = 50

# ゲーム上限
MAX_PLAYER_LEVEL = 9999
MAX_GOLD = 9_223_372_036_854_775_807

# ── プレイヤー設定の既定値・値域（正: ui.md §設定画面、systems/battle.md §213）──
# モデル default / API 応答 / 戦闘判定のフォールバックはすべて本定数を参照する
DEFAULT_POTION_THRESHOLD = 0.3
POTION_THRESHOLD_MIN = 0.1
POTION_THRESHOLD_MAX = 0.5
DEFAULT_BATTLE_LOG_COUNT = 50
BATTLE_LOG_COUNT_CHOICES = (20, 50, 100)
DEFAULT_TOAST_ENABLED = True

# ── 戦闘計算の定数（正: tech_battle.md §3 ダメージ計算）──
DAMAGE_VARIANCE = 0.1        # 通常攻撃のダメージ分散 ±10%
CRIT_RATE = 0.05             # 基礎クリティカル率
CRIT_MULTIPLIER = 1.5        # クリティカル倍率
DEFENSE_FACTOR = 0.5         # DEF の減算係数
RECOVERY_HP_RATIO = 0.02     # 非戦闘時のHP自然回復率（maxHP比）
RECOVERY_DEF_FACTOR = 0.5    # 自然回復量へのDEF寄与係数
DEFEAT_EXP_PENALTY = 0.5     # 全滅時に持ち帰れるEXPの割合

# オフライン簡易計算のサンプルtick数（tech_offline.md §4）
OFFLINE_SAMPLE_TICKS = 10

# ボス階の出現数（tech_battle.md §3.2）。範囲指定にかかわらず1体固定
BOSS_ENEMY_COUNT = 1

# 戦闘乱数のシード（tech_rng.md §2）。空 = OSエントロピー、調査時のみ固定する
BATTLE_RNG_SEED = os.environ.get("BATTLE_RNG_SEED", "")

# 実行環境（tech_operations.md §12.2）。production では必須変数の既定値使用を起動時に拒否する
APP_ENV = os.environ.get("APP_ENV", "development")
IS_PRODUCTION = APP_ENV == "production"

# DB（tech_operations.md §12.2）
DATABASE_URL = os.environ.get("DATABASE_URL", "sqlite:///./afkgame.db")

# アプリケーションログ
LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO")   # 参照は logging_config.setup_logging
LOG_FORMAT = os.environ.get("LOG_FORMAT", "text")  # text / json

# ── 認証 (Phase 2) ──
# 開発用の既定値。HS256 の鍵長は 32 バイト以上が必要（RFC 7518 §3.2）
DEFAULT_JWT_SECRET = "afkgame-dev-secret-change-in-production"
JWT_SECRET = os.environ.get("JWT_SECRET", DEFAULT_JWT_SECRET)
JWT_ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 30
REFRESH_TOKEN_EXPIRE_DAYS = 30
BCRYPT_COST_FACTOR = 12
GUEST_EXPIRE_DAYS = 90
EMAIL_VERIFICATION_EXPIRE_HOURS = 24
PASSWORD_MIN_LENGTH = 8

# Google OAuth (空文字列の場合はGoogle認証無効)
GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "")
GOOGLE_CLIENT_SECRET = os.environ.get("GOOGLE_CLIENT_SECRET", "")

# CORS
CORS_ORIGINS = [
    "http://localhost:5173",
]


def validate_production_settings() -> None:
    """本番起動時に必須の秘密情報が既定値のままなら起動を中止する（tech_operations.md §12.2）"""
    if IS_PRODUCTION and JWT_SECRET == DEFAULT_JWT_SECRET:
        raise RuntimeError(
            "APP_ENV=production では JWT_SECRET の設定が必須です（既定値のまま起動はできません）"
        )

# 初期キャラクターステータス
INITIAL_CHARACTER = {
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

# 初期所持アイテム
INITIAL_POTIONS = {"hp_potion": 5}

# ── 装備システム (Phase 2) ──

# レアリティ定義: (倍率, ステータス数min, ステータス数max)
RARITY_TIERS = {
    "common":    {"multiplier": 1.0, "stats_min": 1, "stats_max": 2},
    "uncommon":  {"multiplier": 1.3, "stats_min": 2, "stats_max": 2},
    "rare":      {"multiplier": 1.6, "stats_min": 2, "stats_max": 3},
    "epic":      {"multiplier": 2.0, "stats_min": 3, "stats_max": 3},
    "legendary": {"multiplier": 2.5, "stats_min": 4, "stats_max": 4},
}

# レアリティドロップ基礎確率 (floor 1 時点)
RARITY_BASE_RATES = [
    ("legendary", 0.005),
    ("epic",      0.04),
    ("rare",      0.10),
    ("uncommon",  0.25),
    # common は残余 (≈0.605)
]

# フロア補正係数: adjusted = base × (1 + FLOOR_RARITY_BONUS × floor)
FLOOR_RARITY_BONUS = 0.05

# 基礎値の式 (master/equipment.md §6.1): floor(装備レベル × 係数 + 定数)
EQUIPMENT_BASE_VALUE_COEF = 1.5
EQUIPMENT_BASE_VALUE_OFFSET = 2

# ステータス係数 (base_value に掛ける)
STAT_MODIFIERS = {"atk": 1.0, "def": 0.7, "hp": 5.0, "spd": 0.3}

# 装備の所持枠上限 (economy.md §2.9「倉庫」。倉庫未建設=LV0 の初期値)
EQUIPMENT_STORAGE_LIMIT = 50

# ドロップ確率
NORMAL_ENEMY_DROP_RATE = (0.10, 0.20)
BOSS_ENEMY_DROP_RATE = (0.50, 0.80)

# HP吸収
LIFESTEAL_CHANCE = 0.10  # 吸収付与確率
LIFESTEAL_RANGE = (0.03, 0.08)

# レアリティ順序 (オートセル比較用)
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary"]

# 売却価格の基準値: floor(SELL_PRICE_BASE × レアリティ倍率 × 装備レベル)
SELL_PRICE_BASE = 5

# ── 日替わりショップ (Phase 2) ──
# 仕様: tech_shop.md、数値の正: economy.md §2.5

# ショップ性能係数。「ドロップ品の約70%の性能」を数値化したもの
SHOP_STAT_FACTOR = 0.7

# 枠番号 → カテゴリ (tech_shop.md §2.2)。武器2・防具2・アクセサリー1
SHOP_SLOT_CATEGORIES = ["weapon", "weapon", "armor", "armor", "accessory"]

# カテゴリ → 候補スロット (tech_shop.md §2.2)
SHOP_CATEGORY_SLOTS = {
    "weapon": ["weapon"],
    "armor": ["shield", "head", "body", "arms", "waist", "legs"],
    "accessory": ["ears", "ring"],
}

# 解放帯 (最高到達階層の下限) → レアリティ出現率 (仮置き。balance_backlog.md)
SHOP_RARITY_RATES = {
    0:  {"common": 1.00},
    5:  {"common": 0.70, "uncommon": 0.30},
    15: {"common": 0.55, "uncommon": 0.30, "rare": 0.15},
}

# 価格テーブル: レアリティ → カテゴリ → ゴールド (レベル・到達階層には依存しない)
SHOP_PRICES = {
    "common":   {"weapon": 500,  "armor": 400,  "accessory": 300},
    "uncommon": {"weapon": 1500, "armor": 1200, "accessory": 1000},
    "rare":     {"weapon": 4000, "armor": 3200, "accessory": 2500},
}

# ── パーティ・スキル (Phase 3) ──
# 仕様: tech_party.md、数値の正: master/character.md §9.1・skills/SKILLS_OVERVIEW.md §1

# パーティ編成の人数（正: systems/character.md §2.7）
PARTY_MIN_SIZE = 1
PARTY_MAX_SIZE = 4

# アクティブスキルのセット枠数（0件=全解除も許可する）
ACTIVE_SKILL_SLOT_COUNT = 2

# スキル振り直しコスト = キャラLV × 本定数（G）
SKILL_RESET_COST_PER_LEVEL = 50

# レベルアップ1回あたりの獲得SP
SKILL_POINTS_PER_LEVEL_UP = 1
