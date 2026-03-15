"""認証スキーマ（Phase 2）"""

from app.schemas import CamelModel


class UserInfo(CamelModel):
    id: str
    email: str | None = None
    display_name: str
    is_guest: bool
    email_verified: bool = False


class AuthResponse(CamelModel):
    access_token: str
    refresh_token: str
    user: UserInfo


class RegisterRequest(CamelModel):
    email: str
    password: str
    display_name: str | None = None


class LoginRequest(CamelModel):
    email: str
    password: str


class RefreshRequest(CamelModel):
    refresh_token: str


class LinkAccountRequest(CamelModel):
    email: str | None = None
    password: str | None = None
    google_auth_code: str | None = None


class GoogleAuthRequest(CamelModel):
    auth_code: str


class PasswordResetRequest(CamelModel):
    email: str


class PasswordResetConfirm(CamelModel):
    token: str
    new_password: str
