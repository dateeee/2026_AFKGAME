"""テスト共通フィクスチャ

DBはインメモリSQLite（StaticPool）を使い、テストごとにテーブルを作り直す。
`app.db.database.get_db` を差し替えるため、アプリ本体のDBファイルには一切触れない。
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.config import INITIAL_CHARACTER
from app.db.database import Base, get_db
from app.main import app
from app.models.character import Character
from app.models.item import InventoryItem
from app.models.player import Player, PlayerSettings, TowerClearRecord
from app.models.user import User
from app.services.auth_service import create_access_token


@pytest.fixture
def db() -> Session:
    """テスト単位で独立したインメモリDBセッション"""
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,  # 同一コネクションを共有し、複数セッションから同じDBを見る
    )
    Base.metadata.create_all(bind=engine)
    session_factory = sessionmaker(bind=engine, class_=Session, expire_on_commit=False)
    session = session_factory()
    try:
        yield session
    finally:
        session.close()
        engine.dispose()


@pytest.fixture
def user(db: Session) -> User:
    u = User(id="test-user", email="test@example.com", is_guest=False)
    db.add(u)
    db.commit()
    return u


@pytest.fixture
def player(db: Session, user: User) -> Player:
    """キャラクター・設定・ポーションを持つ標準プレイヤー"""
    p = Player(id="test-player", user_id=user.id, gold=1000)
    db.add(p)
    db.flush()
    db.add(PlayerSettings(player_id=p.id, potion_threshold=0.3))
    db.add(InventoryItem(player_id=p.id, item_id="hp_potion", quantity=5))
    db.add(Character(player_id=p.id, **INITIAL_CHARACTER))
    db.commit()
    db.refresh(p)
    return p


@pytest.fixture
def character(db: Session, player: Player) -> Character:
    return db.query(Character).filter_by(player_id=player.id).first()


@pytest.fixture
def client(db: Session, player: Player) -> TestClient:
    """認証済みリクエストを送る TestClient（Authorization ヘッダを既定で付与）"""
    app.dependency_overrides[get_db] = lambda: db
    token = create_access_token(player.user_id, is_guest=False)
    with TestClient(app) as c:
        c.headers.update({"Authorization": f"Bearer {token}"})
        yield c
    app.dependency_overrides.clear()


@pytest.fixture
def tower_record(db: Session, player: Player):
    """塔別クリア記録を作るファクトリ"""

    def _make(tower_id: str, highest_floor: int = 0, cleared: bool = False) -> TowerClearRecord:
        record = TowerClearRecord(
            player_id=player.id,
            tower_id=tower_id,
            highest_floor=highest_floor,
            cleared=cleared,
        )
        db.add(record)
        db.commit()
        return record

    return _make
