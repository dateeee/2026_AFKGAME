"""共通の応答スキーマ

素の dict を返すエンドポイントをなくし、OpenAPI に応答型が出るようにする
（profile.md §3 FastAPI規約: APIRouter は prefix / tags / response_model を指定）。
"""

from app.schemas import CamelModel


class StatusResponse(CamelModel):
    """処理成功のみを返す応答"""

    status: str = "ok"


class MessageResponse(CamelModel):
    """処理成功 + 利用者向けメッセージを返す応答"""

    status: str = "ok"
    message: str
