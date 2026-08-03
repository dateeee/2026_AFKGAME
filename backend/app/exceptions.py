"""AFK Game - 統一エラーレスポンス・グローバル例外ハンドラ"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger("afkgame.middleware")


class AppError(Exception):
    """アプリケーション固有のエラー（エラーコード付き）"""

    def __init__(self, code: str, message: str, status_code: int = 400):
        self.code = code
        self.message = message
        self.status_code = status_code
        super().__init__(message)


def _get_request_id(request: Request) -> str | None:
    return getattr(request.state, "request_id", None)


def _error_response(status_code: int, code: str, message: str, request_id: str | None) -> JSONResponse:
    body: dict = {
        "error": {
            "code": code,
            "message": message,
        }
    }
    if request_id:
        body["error"]["request_id"] = request_id
    return JSONResponse(status_code=status_code, content=body)


def register_exception_handlers(app: FastAPI) -> None:
    """FastAPIアプリに例外ハンドラを登録する"""

    @app.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError) -> JSONResponse:
        return _error_response(exc.status_code, exc.code, exc.message, _get_request_id(request))

    @app.exception_handler(StarletteHTTPException)
    async def http_exception_handler(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        # HTTPExceptionのdetailからエラーコードを推定
        detail = exc.detail if isinstance(exc.detail, str) else str(exc.detail)
        code = f"HTTP_{exc.status_code}"
        return _error_response(exc.status_code, code, detail, _get_request_id(request))

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
        logger.warning(
            "バリデーションエラー",
            extra={"request_id": _get_request_id(request), "path": request.url.path},
        )
        return _error_response(422, "VALIDATION_ERROR", "リクエストの入力値が不正です", _get_request_id(request))

    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception) -> JSONResponse:
        request_id = _get_request_id(request)
        logger.error(
            "未捕捉例外: %s",
            str(exc),
            exc_info=True,
            extra={"request_id": request_id, "path": request.url.path},
        )
        return _error_response(500, "INTERNAL_UNEXPECTED_ERROR", "サーバー内部エラーが発生しました", request_id)
