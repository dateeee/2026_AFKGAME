"""AFK Game - 設定・定数"""

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

# DB
DATABASE_URL = "sqlite:///./afkgame.db"

# アプリケーションログ
LOG_LEVEL = "INFO"   # 環境変数 LOG_LEVEL で上書き可
LOG_FORMAT = "text"  # text / json、環境変数 LOG_FORMAT で上書き可

# ── 認証 (Phase 2) ──
import os

JWT_SECRET = os.environ.get("JWT_SECRET", "dev-secret-change-in-production")
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

# ステータス係数 (base_value に掛ける)
STAT_MODIFIERS = {"atk": 1.0, "def": 0.7, "hp": 5.0, "spd": 0.3}

# ドロップ確率
NORMAL_ENEMY_DROP_RATE = (0.10, 0.20)
BOSS_ENEMY_DROP_RATE = (0.50, 0.80)

# HP吸収
LIFESTEAL_CHANCE = 0.10  # 吸収付与確率
LIFESTEAL_RANGE = (0.03, 0.08)

# レアリティ順序 (オートセル比較用)
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary"]
