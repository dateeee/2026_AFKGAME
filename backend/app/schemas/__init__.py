"""共通スキーマベースクラス"""

from pydantic import BaseModel, ConfigDict


def to_camel(string: str) -> str:
    """snake_case → camelCase 変換"""
    parts = string.split("_")
    return parts[0] + "".join(word.capitalize() for word in parts[1:])


class CamelModel(BaseModel):
    """camelCaseエイリアス付きベースモデル"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        from_attributes=True,
    )
