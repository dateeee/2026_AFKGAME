"""SQLAlchemy 2.0 データベース設定"""

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.config import DATABASE_URL

engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(bind=engine, class_=Session, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def get_db():
    """FastAPI依存関数: DBセッションを提供"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
