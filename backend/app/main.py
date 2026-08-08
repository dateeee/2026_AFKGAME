"""AFK Game - FastAPI エントリーポイント"""

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.config import (
    APP_VERSION,
    CORS_ORIGINS,
    IS_PRODUCTION,
    validate_production_settings,
)
from app.db.database import Base, engine, get_db
from app.exceptions import register_exception_handlers
from app.logging_config import setup_logging
from app.middleware import RequestLogMiddleware
import app.models  # noqa: F401 — 全モデルをBase.metadataに登録（テーブル自動生成用）
from app.routers import auth, battle, equipment, game, party, shop, skill, tower

# ログシステム初期化
setup_logging()

# 本番で必須の秘密情報が既定値のままなら、ここで起動を中止する（fail-fast）
validate_production_settings()


def init_schema() -> None:
    """起動時のテーブル生成（開発・テスト用のフォールバック）。

    スキーマの正は alembic/versions で、本番は `alembic upgrade head` で適用する
    （運用手順は tech_operations.md §12）。本番でも create_all すると
    マイグレーションの書き漏れが隠れてしまうため、production では実行しない。
    """
    if IS_PRODUCTION:
        return
    Base.metadata.create_all(bind=engine)


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_schema()
    yield


logger = logging.getLogger("afkgame.main")

app = FastAPI(title="AFK Game API", version=APP_VERSION, lifespan=lifespan)

# 例外ハンドラ登録
register_exception_handlers(app)

# ミドルウェア（登録順の逆順で実行される）
app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(RequestLogMiddleware)

app.include_router(auth.router)
app.include_router(game.router)
app.include_router(battle.router)
app.include_router(tower.router)
app.include_router(shop.router)
app.include_router(equipment.router)
app.include_router(party.router)
app.include_router(skill.router)


@app.get("/health")
def health_check(db: Session = Depends(get_db)):
    """死活監視（tech_operations.md §12.3）。認証不要・レート制限対象外。

    DBへの `SELECT 1` が失敗したら 503 を返し、デプロイ先のヘルスチェックを落とす。
    """
    try:
        db.execute(text("SELECT 1"))
    except SQLAlchemyError:
        logger.error("ヘルスチェック: DB疎通に失敗", exc_info=True)
        return JSONResponse(status_code=503, content={"status": "degraded", "db": "error"})

    return {"status": "ok", "version": APP_VERSION, "db": "ok"}
