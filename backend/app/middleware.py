"""AFK Game - リクエストログミドルウェア"""

import logging
import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

logger = logging.getLogger("afkgame.middleware")


class RequestLogMiddleware(BaseHTTPMiddleware):
    """全APIリクエストに対してリクエストID付与・処理時間計測・INFOログ出力を行う"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        request_id = str(uuid.uuid4())
        request.state.request_id = request_id

        start_time = time.perf_counter()
        response = await call_next(request)
        duration_ms = round((time.perf_counter() - start_time) * 1000)

        response.headers["X-Request-ID"] = request_id

        # player_idの取得（認証済みリクエストの場合）
        player_id = getattr(request.state, "player_id", None)

        logger.info(
            "%s %s %s %dms",
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
            extra={
                "method": request.method,
                "path": request.url.path,
                "status_code": response.status_code,
                "duration_ms": duration_ms,
                "player_id": player_id,
                "request_id": request_id,
            },
        )

        return response
