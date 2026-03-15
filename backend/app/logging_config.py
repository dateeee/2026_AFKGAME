"""AFK Game - ログ設定"""

import json
import logging
import os
import re
import sys
from datetime import datetime, timezone


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


class TextFormatter(logging.Formatter):
    """開発用テキストフォーマッター"""

    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.fromtimestamp(record.created, tz=timezone.utc).strftime(
            "%Y-%m-%d %H:%M:%S"
        )
        # extra属性をkey=value形式で追加
        extras = []
        for key in ("reason", "token", "player_id", "request_id", "method", "path",
                     "status_code", "duration_ms", "tower_id", "item_id", "quantity",
                     "gold", "ticks", "calc_method", "mode", "hp_threshold"):
            value = getattr(record, key, None)
            if value is not None:
                extras.append(f"{key}={value}")
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
        for key in ("reason", "token", "player_id", "request_id", "client_ip",
                     "method", "path", "status_code", "duration_ms", "tower_id",
                     "item_id", "quantity", "gold", "ticks", "calc_method",
                     "mode", "hp_threshold"):
            value = getattr(record, key, None)
            if value is not None:
                log_data[key] = value
        if record.exc_info and record.exc_info[1]:
            log_data["exception"] = self.formatException(record.exc_info)
        return json.dumps(log_data, ensure_ascii=False)


def setup_logging() -> None:
    """ログシステムを初期化する"""
    log_level = os.environ.get("LOG_LEVEL", "INFO").upper()
    log_format = os.environ.get("LOG_FORMAT", "text").lower()

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
