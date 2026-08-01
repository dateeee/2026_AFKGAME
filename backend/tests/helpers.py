"""テスト共通ヘルパー"""


def error_message(response) -> str:
    """統一エラーレスポンス（tech_logging.md）からメッセージを取り出す

    形式: {"error": {"code": "HTTP_400", "message": "...", "request_id": "..."}}
    """
    return response.json()["error"]["message"]


def error_code(response) -> str:
    return response.json()["error"]["code"]
