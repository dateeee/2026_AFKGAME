# 単体テスト — プロジェクト固有プロファイル

> 一般手順は [.claude/skills/unit-test/SKILL.md](../skills/unit-test/SKILL.md) を参照。本書は AFK GAME 固有の値のみを持つ。
> テストの書き方・フィクスチャは [test-list.md](test-list.md)、実装パターンは [test-patterns.md](test-patterns.md)。

## 1. 前提

| 項目 | 値 |
|------|-----|
| 対象 | `backend/app/`（`services/` `routers/` `master_data/` を主、`models/` `schemas/` は副次的） |
| 配置 | `backend/tests/unit/test_<対象モジュール>.py` |
| 設定 | `backend/pytest.ini`（`--cov=app --cov-branch`、term-missing + HTML を設定済み）、`backend/.coveragerc`（除外・整形） |
| レポート | ターミナル（term-missing）と `backend/htmlcov/index.html`（未実行行=赤、部分分岐=黄。gitignore済み） |
| カバレッジ基準 | **C1（分岐網羅）100%** |
| フロントエンド | 対象外。単体レベルの検証は結合テスト（Playwright）に統合し、型検証は `vue-tsc` を製造工程で実施 |

## 2. コマンド

| 目的 | コマンド |
|------|---------|
| 現状測定 | `cd backend && python -m pytest --cov=app --cov-branch --cov-report=term-missing -q` |
| 簡易実行（同じ計測が走る） | `cd backend && python -m pytest -q` |
| モジュールを絞る | `--cov=app.services.battle_service` |
| 完了判定 | `cd backend && python -m pytest --cov=app --cov-branch --cov-fail-under=100 -q` |

## 3. 固有の分岐観点

一般的な構文別チェックリストは [.claude/skills/unit-test/references/c1_checklist.md](../skills/unit-test/references/c1_checklist.md)。以下は AFK GAME でのみ必要な観点。

| 領域 | 押さえる分岐 |
|------|------------|
| 乱数 | ダメージ分散の上振れ/下振れ/中央、クリティカル発生/非発生、ドロップ有/無、エンカウント抽選。`random.random() < rate` は **rate 未満と rate 以上の両方**を固定して通す |
| 境界値 | `>=` と `>` の境界そのものの値、0、負値、上限クランプ（例: `target_floor_cap` の `highest == total_floors`） |
| マスターデータ | 既知ID / **未知ID**（`None` またはフォールバック）、空リスト、有限塔 `total_floors=int` と無限塔 `None` |
| DB状態 | レコードなし（初回） / あり、塔別に独立していること、複数レコードでの絞り込み |
| 認証 | ゲスト / 正規ユーザー、トークンなし / 期限切れ / 署名不正 |
| tick・オフライン | `TURNS_PER_TICK` 内で決着 / 決着せず持ち越し、経過0tick / 1tick / 大量tick、上限クランプ |
| 戦闘 | 先攻/後攻（SPD比較の同値含む）、HP がちょうど0、最低ダメージのクランプ（プレイヤー1 / 敵0）、全滅、ボス/通常敵 |
| 回復・在庫 | ポーション閾値の境界（`hp/max == threshold`）、在庫あり / 0個 |
| 経済 | gold 不足 / ちょうど / 十分、インベントリ上限、自動売却 有効/無効 |
| ルーター | 正常系 200 + **各エラーコードごと**の 4xx、Pydantic バリデーションエラー、権限違反 |

## 4. 除外規則

`# pragma: no cover` は**理由コメント必須**。許容されるのは `if __name__ == "__main__":` などの起動コードや、実行環境依存で再現できない例外ハンドラのみ。

```python
except (AttributeError, OSError):  # pragma: no cover - 実行環境依存
```

カバレッジを通すためだけに `pragma: no cover` を付けることは**禁止**（新規に付ける場合は理由コメントを必須とし、完了報告で明示する）。

## 5. 現在の整備状況

**単体テストゲート通過（2026-08-02）**: `app/` 全モジュール C1 100% 達成済み。現況の件数・分岐数は `pytest` の実行結果を正とする（本書へ転記しない）。

| # | ルール |
|---|-------|
| 1 | 実装済み（Phase 1〜2）のテストは遡及整備で C1 100% に到達済みのため**書き直さない** |
| 2 | 本工程で補完した分岐は、[detail-design.md](detail-design.md) §4 の分岐一覧へ**逆反映**する |
| 3 | 検出した実装の疑義（仕様乖離・デッドコード等）は `docs/backlog/known_issues.md` へ記録する |

## 6. 完了基準

一般スキルの完了基準に加え、以下を満たすこと。

- 全テスト PASS かつ **C1カバレッジ100%**（`--cov-fail-under=100` が exit 0）
- 補完した分岐が詳細設計の分岐一覧へ反映されている
- 検出した実装の疑義が `docs/backlog/known_issues.md` に登録されている

## 7. 次工程

| 次にやること | 手段 |
|------------|------|
| 結合テストへ | `integration-test` スキル |
