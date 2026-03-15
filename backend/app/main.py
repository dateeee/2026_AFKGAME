"""AFK Game - FastAPI エントリーポイント"""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import CORS_ORIGINS
from app.db.database import Base, engine
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


app = FastAPI(title="AFK Game API", version="0.1.0", lifespan=lifespan)

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


@app.get("/api/health")
def health_check():
    return {"status": "ok"}
