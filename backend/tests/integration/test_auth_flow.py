"""結合テスト: 認証 → ゲーム状態取得 / ゲスト → 正規ユーザー移行

シナリオ導出元: diagrams/api_sequence/core.md §1・§2、diagrams/api_sequence/auth.md §14
検証対象は**基本設計**（API設計・データ構造）であり、条件分岐の網羅は単体テストが持つ。
"""

import pytest

pytestmark = pytest.mark.integration


class TestHealthCheck:
    """運用の死活監視（tech_operations.md §12.3）。認証不要で `/api` の外に置く"""

    def test_認証なしでヘルスチェックへ到達できる(self, api):
        res = api.get("/health")
        assert res.status_code == 200
        body = res.json()
        assert body["status"] == "ok"
        assert body["db"] == "ok"
        assert body["version"]


class TestScenario01認証からゲーム状態取得:
    """必須シナリオ #1: トークン発行と初期状態の返却"""

    def test_ゲスト作成から初期ゲーム状態の取得まで通しで成立する(self, api):
        # 未認証では取得できない
        assert api.get("/api/game/state").status_code == 401

        # ゲスト作成（core.md §1）
        res = api.post("/api/auth/guest")
        assert res.status_code == 200
        auth = res.json()
        assert auth["user"]["isGuest"] is True
        assert auth["user"]["email"] is None
        assert auth["accessToken"] and auth["refreshToken"]

        # 発行されたアクセストークンでゲーム状態を取得
        api.headers.update({"Authorization": f"Bearer {auth['accessToken']}"})
        res = api.get("/api/game/state")
        assert res.status_code == 200
        state = res.json()

        # 初期状態が仕様どおりに組み上がっている
        assert state["player"]["gold"] == 0
        assert state["player"]["currentTowerId"] is None
        assert state["player"]["currentFloor"] is None
        assert state["potions"] == {"hp_potion": 5}
        assert state["equipment"] == []
        assert state["towersCleared"] == {}
        assert state["currentEnemy"] is None

        character = state["characters"][0]
        assert character["name"] == "勇者"
        assert character["level"] == 1
        assert character["effectiveMaxHp"] == character["maxHp"]

        # 装備スロットは9枠が空で用意される（equipment.md 装備スロット）
        assert len(state["equipped"]) == 9
        assert set(state["equipped"].values()) == {None}

        # レスポンスのキーは camelCase（tech_api.md §5.0 ボディのキー）
        snake_keys = [k for k in list(state) + list(state["player"]) + list(state["characters"][0]) if "_" in k]
        assert snake_keys == []

    def test_設定更新がゲーム状態へ反映され再取得しても保たれる(self, api, guest):
        res = api.put("/api/game/settings", json={"potionThreshold": 0.6, "autoSellRarity": "common"})
        assert res.status_code == 200

        settings = api.get("/api/game/state").json()["settings"]
        assert settings["potionThreshold"] == 0.6
        assert settings["autoSellRarity"] == "common"
        assert settings["battleLogCount"] == 50  # 未指定の項目は変わらない

    def test_リフレッシュでトークンが更新され旧トークンは使えなくなる(self, api):
        auth = api.post("/api/auth/guest").json()

        rotated = api.post("/api/auth/refresh", json={"refreshToken": auth["refreshToken"]})
        assert rotated.status_code == 200
        new_tokens = rotated.json()
        assert new_tokens["refreshToken"] != auth["refreshToken"]

        # ローテーション済みの旧トークンは再利用できない
        assert api.post("/api/auth/refresh", json={"refreshToken": auth["refreshToken"]}).status_code == 401

        # 新しいアクセストークンで同じプレイヤーへ到達できる
        api.headers.update({"Authorization": f"Bearer {new_tokens['accessToken']}"})
        assert api.get("/api/game/state").json()["characters"][0]["name"] == "勇者"

    def test_ログアウトしたリフレッシュトークンは無効になる(self, api):
        auth = api.post("/api/auth/guest").json()

        assert api.post("/api/auth/logout", json={"refreshToken": auth["refreshToken"]}).status_code == 200
        assert api.post("/api/auth/refresh", json={"refreshToken": auth["refreshToken"]}).status_code == 401


class TestScenario07ゲストから正規ユーザーへの移行:
    """必須シナリオ #7: データ引き継ぎ"""

    def test_移行前の進行状況が移行後もログインし直しても保たれる(self, api, db, guest, guest_player):
        # ゲストとして進行させる（ゴールド・入塔・購入）
        guest_player.gold = 500
        db.commit()
        assert api.post(
            "/api/tower/select",
            json={"towerId": "goblin_tower", "targetFloor": 1, "mode": "auto_repeat"},
        ).status_code == 200
        assert api.post("/api/shop/buy", json={"itemId": "hp_potion", "quantity": 2}).status_code == 200

        before = api.get("/api/game/state").json()
        assert before["player"]["gold"] == 450
        assert before["potions"]["hp_potion"] == 7

        # 本登録へ移行（auth.md §14 ゲスト→本登録）
        res = api.post(
            "/api/auth/link-account",
            json={"email": "hero@example.com", "password": "password123"},
        )
        assert res.status_code == 200
        linked = res.json()
        assert linked["user"]["isGuest"] is False
        assert linked["user"]["email"] == "hero@example.com"
        assert linked["user"]["emailVerified"] is False

        # 移行後のトークンで取得した状態がゲスト時代と一致する
        api.headers.update({"Authorization": f"Bearer {linked['accessToken']}"})
        assert api.get("/api/game/state").json() == before

        # ログインし直しても同じデータへ到達する（永続）
        api.headers.pop("Authorization")
        login = api.post("/api/auth/login", json={"email": "hero@example.com", "password": "password123"})
        assert login.status_code == 200
        api.headers.update({"Authorization": f"Bearer {login.json()['accessToken']}"})
        assert api.get("/api/game/state").json() == before

    def test_移行で発行された確認トークンでメール確認まで到達できる(self, api, guest, app_logs):
        res = api.post(
            "/api/auth/link-account",
            json={"email": "verify@example.com", "password": "password123"},
        )
        assert res.status_code == 200

        # 確認メールのリンクに載るトークン（メール送信が未実装のため実装はログへ出力する）
        token = next(
            r.verification_token for r in app_logs.records if hasattr(r, "verification_token")
        )
        verified = api.get("/api/auth/verify-email", params={"token": token})
        assert verified.status_code == 200
        assert verified.json()["status"] == "ok"

        # 同じトークンは二度使えない
        assert api.get("/api/auth/verify-email", params={"token": token}).status_code == 400

    def test_メール登録から確認とログインまで通しで成立する(self, api, app_logs):
        """ゲストを経由しない直接登録の導線（screen_transition.md 新規登録）"""
        res = api.post(
            "/api/auth/register",
            json={"email": "new@example.com", "password": "password123"},
        )
        assert res.status_code == 200
        auth = res.json()
        assert auth["user"]["isGuest"] is False
        assert auth["user"]["emailVerified"] is False

        # 登録直後から初期ゲーム状態を取得できる
        api.headers.update({"Authorization": f"Bearer {auth['accessToken']}"})
        state = api.get("/api/game/state").json()
        assert state["potions"] == {"hp_potion": 5}
        assert state["characters"][0]["level"] == 1

        # 確認トークンでメール確認まで到達する
        token = next(r.verification_token for r in app_logs.records if hasattr(r, "verification_token"))
        assert api.get("/api/auth/verify-email", params={"token": token}).status_code == 200

        # 同じメールでの二重登録は409
        assert api.post(
            "/api/auth/register",
            json={"email": "new@example.com", "password": "password123"},
        ).status_code == 409

    def test_パスワードリセットで新しいパスワードに切り替わる(self, api, app_logs):
        assert api.post(
            "/api/auth/register",
            json={"email": "reset@example.com", "password": "oldpassword"},
        ).status_code == 200

        # リセット申請（存在しないメールでも同じ応答を返す）
        assert api.post("/api/auth/password-reset/request", json={"email": "reset@example.com"}).status_code == 200
        assert api.post("/api/auth/password-reset/request", json={"email": "nobody@example.com"}).status_code == 200

        reset_token = next(r.reset_token for r in app_logs.records if hasattr(r, "reset_token"))
        res = api.post(
            "/api/auth/password-reset/confirm",
            json={"token": reset_token, "newPassword": "newpassword"},
        )
        assert res.status_code == 200

        # 旧パスワードでは入れず、新パスワードで入れる
        assert api.post("/api/auth/login", json={"email": "reset@example.com", "password": "oldpassword"}).status_code == 401
        login = api.post("/api/auth/login", json={"email": "reset@example.com", "password": "newpassword"})
        assert login.status_code == 200

        # リセット後のトークンで同じプレイヤーへ到達できる
        api.headers.update({"Authorization": f"Bearer {login.json()['accessToken']}"})
        assert api.get("/api/game/state").status_code == 200

    def test_移行済みのアカウントは再移行できない(self, api, guest):
        assert api.post(
            "/api/auth/link-account",
            json={"email": "once@example.com", "password": "password123"},
        ).status_code == 200

        res = api.post(
            "/api/auth/link-account",
            json={"email": "twice@example.com", "password": "password123"},
        )
        assert res.status_code == 400
