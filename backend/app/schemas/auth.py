"""認証スキーマ（Phase 2）

入力の形式・長さ検証はスキーマ側に寄せる（範囲外は 422 / tech_api.md §エラー）。
"""

from pydantic import EmailStr, Field

from app.config import PASSWORD_MIN_LENGTH
from app.schemas import CamelModel

# パスワードの最短長（tech_auth.md）。応答スキーマには使わない
Password = Field(min_length=PASSWORD_MIN_LENGTH)


class UserInfo(CamelModel):
    id: str
    # 応答側は保存済みの値をそのまま返すため検証しない
    email: str | None = None
    display_name: str
    is_guest: bool
    email_verified: bool = False


class AuthResponse(CamelModel):
    access_token: str
    refresh_token: str
    user: UserInfo


class RegisterRequest(CamelModel):
    email: EmailStr
    password: str = Password
    display_name: str | None = None


class LoginRequest(CamelModel):
    # ログインは既存アカウントの照合のみ。形式検証を課すと既存利用者を弾くため str のまま
    email: str
    password: str


class RefreshRequest(CamelModel):
    refresh_token: str


class LinkAccountRequest(CamelModel):
    email: EmailStr | None = None
    password: str | None = Field(default=None, min_length=PASSWORD_MIN_LENGTH)
    google_auth_code: str | None = None


class GoogleAuthRequest(CamelModel):
    auth_code: str


class PasswordResetRequest(CamelModel):
    email: EmailStr


class PasswordResetConfirm(CamelModel):
    token: str
    new_password: str = Password
