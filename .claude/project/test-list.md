# テストリスト作成 — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/test-list/SKILL.md](../skills/test-list/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> 共通の値は [profile.md](profile.md)。

## 1. 成果物

| 成果物 | パス | 状態 |
|-------|------|------|
| 単体テストコード | `backend/tests/unit/test_<対象モジュール>.py` | 実装前。**全件 FAIL または ERROR** |

## 2. 対象と適用範囲

| 対象 | 適用 |
|------|------|
| `backend/app/services/` | **厳格に適用**（すべての分岐にテストを先に書く） |
| `backend/app/master_data/` | **厳格に適用** |
| `backend/app/routers/` | TestClient で先行作成 |
| `backend/app/models/` `schemas/` | 定義のみのため副次的 |
| `frontend/` | **対象外**（TDD非適用。`vue-tsc` と結合テストで検証する） |

**適用時期**: TDDは**新規実装から**適用する。Phase 2 の残り（日替わりショップ）と Phase 3〜5 が対象。実装済み（Phase 1〜2）のテストは遡及整備で C1 100% に到達済みのため**書き直さない**。既存機能の修正・リファクタ時は、先にその変更を表すテストを追加してから実装に着手する。

## 3. 入力

[detail-design.md](detail-design.md) §4 の**分岐一覧（単体テスト観点）**。分岐一覧に無いテストを勝手に足さない。

| 順 | 参照先 | 読む範囲 |
|----|--------|---------|
| 1 | `docs/tech/tech_<対象処理>.md` | 分岐一覧のセクションのみ |
| 2 | `docs/data/master/` | テストで使う数値のみ |
| 3 | `backend/tests/conftest.py` | フィクスチャ一覧（後述） |
| 4 | `backend/tests/unit/test_target_floor.py` | スタイルの参考（最も整っている） |

## 4. 共通フィクスチャ（`backend/tests/conftest.py`）

| フィクスチャ | 内容 |
|------------|------|
| `db` | インメモリSQLite（StaticPool）のセッション。テストごとに作り直す |
| `user` | `test-user` / 非ゲスト |
| `player` | gold=1000、`PlayerSettings(potion_threshold=0.3)`、hp_potion×5、初期キャラを持つ |
| `character` | `player` の初期キャラクター |
| `client` | `get_db` を差し替え済み・Authorization ヘッダ付与済みの `TestClient` |
| `tower_record` | `tower_record(tower_id, highest_floor=0, cleared=False)` で塔別クリア記録を作るファクトリ |

不足するフィクスチャはテストモジュール内にローカル定義する（`conftest.py` は全テスト共通のものだけ）。

## 5. 記述規約

| # | 規約 |
|---|------|
| 1 | モジュール先頭に `pytestmark = pytest.mark.unit` を置く |
| 2 | モジュール docstring に**仕様書の参照先**と**分岐観点**を書く |
| 3 | 観点ごとに `class Test<対象>` でグループ化する |
| 4 | テスト関数名は**日本語**で期待する振る舞いを書く（例: `test_目標階が上限と一致していれば追従する`） |
| 5 | 境界値・等価クラスは `@pytest.mark.parametrize` にまとめ、各ケースに `#` コメントで意図を書く |
| 6 | **1テスト1観点**。複数分岐を1つのテストに詰め込まない |
| 7 | テスト関数の docstring に対応マーカー「`分岐: tech_<対象>.md §<節> #<行番号>`」を書く（一覧が1つの文書は §省略可、`#3,4` と複数可）。`check_branch_list.py --tests` がこれで対応を照合する |

実装パターンは一般形を [.claude/skills/test-list/references/patterns.md](../skills/test-list/references/patterns.md)、AFK GAME のモジュール名・エラーコードを使った実例を [test-patterns.md](test-patterns.md) に置いている。

## 6. 固有の観点

| # | 観点 | 内容 |
|---|------|------|
| 1 | 乱数 | ダメージ分散・ドロップ抽選・エンカウント抽選は `random.seed` またはモックで固定する |
| 2 | 時刻 | オフライン計算・tick進行は現在時刻をモックし、経過時間を確定させる |
| 3 | マスターデータ | 未知IDを渡したときの経路を必ず1件持つ |
| 4 | DB | 未登録レコード（塔記録なし・装備なし）の経路を必ず1件持つ |
| 5 | 認証 | ルーターは「認証あり / なし」の両方を持つ |
| 6 | 境界値 | HP0、最大階、所持金不足、レベル上限、しきい値ちょうど（`<=` か `<` か）を分ける |

## 7. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 分岐一覧の全項目にテストが対応している: `python scripts/check_branch_list.py --tests` が exit 0（マーカーで機械照合）
- 実行して**期待どおりに失敗する**（Red の確認）: `cd backend && python -m pytest -q`
- 実装を先に書いていない（テストの後追いで書いていない）

## 8. 次工程

| 次にやること | 手段 |
|------------|------|
| 製造へ | `dev` スキル（Red-Green-Refactor を1テストずつ回す） |
