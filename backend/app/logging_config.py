"""AFK Game - ログ設定"""

import json
import logging
import re
import sys
from datetime import datetime, timezone

from app.config import LOG_FORMAT, LOG_LEVEL


def mask_token(token: str) -> str:
    """トークン値をマスクする（先頭4文字 + **** + 末尾4文字）"""
    if len(token) <= 8:
        return "****"
    return token[:4] + "****" + token[-4:]


def mask_email(email: str) -> str:
    """メールアドレスをマスクする（先頭2文字 + ***@ + ドメイン）"""
    match = re.match(r"^(.{0,2}).*@(.+)$", email)
    if not match:
        return "***"
    return match.group(1) + "***@" + match.group(2)


# `extra=` で渡した属性のうち出力対象とするキー（tech_logging.md）。
# TextFormatter / JsonFormatter で共有し、片側にだけ追加されるドリフトを防ぐ。
# 新しい extra キーを使うときは本定数へ追加すること（未登録のキーは出力されない）。
LOG_EXTRA_KEYS = (
    # 共通・リクエスト
    "reason", "request_id", "method", "path", "status_code", "duration_ms", "client_ip",
    # 認証（token / verification_token / reset_token は開発用のログ出力）
    "user_id", "token", "verification_token", "reset_token", "email",
    # ゲーム
    "player_id", "tower_id", "item_id", "quantity", "gold", "ticks", "calc_method",
    "mode", "hp_threshold", "highest_floor",
    # 装備・日替わりショップ
    "slot", "slot_index", "base_id", "items_sold", "gold_earned",
)


def _extra_items(record: logging.LogRecord) -> list[tuple[str, object]]:
    """出力対象の extra 属性を (key, value) で返す。email はマスクする"""
    items: list[tuple[str, object]] = []
    for key in LOG_EXTRA_KEYS:
        value = getattr(record, key, None)
        if value is None:
            continue
        if key == "email":
            value = mask_email(str(value))
        items.append((key, value))
    return items


class TextFormatter(logging.Formatter):
    """開発用テキストフォーマッター"""

    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(record.created, tz=timezone.utc).strftime(
            "%Y-%m-%d %H:%M:%S"
        )
        # extra属性をkey=value形式で追加
        extras = [f"{key}={value}" for key, value in _extra_items(record)]
        extra_str = " " + " ".join(extras) if extras else ""
        return f"[{timestamp}] {record.levelname:<8} {record.name}: {record.getMessage()}{extra_str}"


class JsonFormatter(logging.Formatter):
    """本番用JSON構造化フォーマッター"""

    def format(self, record: logging.LogRecord) -> str:
        log_data: dict = {
            "timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        # extra属性を追加
        for key, value in _extra_items(record):
            log_data[key] = value
        if record.exc_info and record.exc_info[1]:
            log_data["exception"] = self.formatException(record.exc_info)
        return json.dumps(log_data, ensure_ascii=False)


def setup_logging(level: str | None = None, fmt: str | None = None) -> None:
    """ログシステムを初期化する。

    既定は config の LOG_LEVEL / LOG_FORMAT（環境変数の参照は config.py に集約）。
    引数はテスト・埋め込み利用向けの上書き。
    """
    log_level = (level if level is not None else LOG_LEVEL).upper()
    log_format = (fmt if fmt is not None else LOG_FORMAT).lower()

    formatter: logging.Formatter
    if log_format == "json":
        formatter = JsonFormatter()
    else:
        formatter = TextFormatter()

    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    # afkgameルートロガーを設定
    root_logger = logging.getLogger("afkgame")
    root_logger.setLevel(getattr(logging, log_level, logging.INFO))
    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.propagate = False
