"""AFK Game - FastAPI エントリーポイント"""

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.config import APP_VERSION, CORS_ORIGINS
from app.db.database import Base, engine, get_db
from app.exceptions import register_exception_handlers
from app.logging_config import setup_logging
from app.middleware import RequestLogMiddleware
import app.models  # noqa: F401 — 全モデルをBase.metadataに登録（テーブル自動生成用）
from app.routers import auth, battle, equipment, game, shop, tower

# ログシステム初期化
setup_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
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
