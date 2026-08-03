"""テスト共通ヘルパー"""

from contextlib import contextmanager

from sqlalchemy import event


def error_message(response) -> str:
    """統一エラーレスポンス（tech_logging.md）からメッセージを取り出す

    形式: {"error": {"code": "HTTP_400", "message": "...", "request_id": "..."}}
    """
    return response.json()["error"]["message"]


def error_code(response) -> str:
    return response.json()["error"]["code"]


@contextmanager
def count_queries(session):
    """ブロック内で発行されたSQL文を集める（N+1の再発検知用）

    使い方:
        with count_queries(db) as sql:
            ...
        assert len(sql) == 2
    """
    statements: list[str] = []
    engine = session.get_bind()

    def _on_execute(conn, cursor, statement, parameters, context, executemany):
        statements.append(statement)

    event.listen(engine, "before_cursor_execute", _on_execute)
    try:
        yield statements
    finally:
        event.remove(engine, "before_cursor_execute", _on_execute)
