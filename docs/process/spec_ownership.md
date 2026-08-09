# AFK GAME — 正の所在マップ（spec ownership）

> 「同じ数値・仕様を複数ファイルに書かない。正となるファイルを1つ決め、他はリンクする」
> （[documentation_rules.md](documentation_rules.md) §5）を運用するための台帳。
> **境界だけを宣言する**。仕様の中身と確定経緯（ISSUE番号・確定日）は書かない（正ファイルと [changelog.md](../changelog.md) が持つ）。

---

## 1. まず一般原則で判断する

個別の行を引く前に、ファイルの役割から正を決める。**ここで決まる境界は §3 へ登録しない**。

| 内容 | 正の置き場 |
|------|-----------|
| 要求値（性能・容量・期限などの「満たすべき値」） | `docs/design/*_requirements.md` |
| ゲーム仕様の意味・ルール（何がどうなるか） | `docs/design/systems/` |
| 画面の構成・遷移・機能配置（ヘッダ項目・ナビ項目を含む） | `docs/design/systems/ui*.md` |
| ビジュアル・部品規約（色・トーン、モーダル等の見た目と開閉挙動） | `docs/tech/detail/tech_design_system.md` |
| 処理・計算式・分岐（どう計算するか） | `docs/tech/tech_*.md` |
| 数値の具体値（マスターデータ） | `docs/data/` |
| 開発プロセス・工程・規約の運用ルール | `docs/process/` |

- 同じ数値が複数ファイルに現れるときは**導出元が正、他は導出値**（LV別ステータス表は成長式から導出、など）
- 図（`docs/diagrams/`）は視覚化であり正にならない。テキストと食い違ったらテキスト側を起点に揃える（`diagrams-review`）

## 2. 登録と更新

| 場面 | すること |
|------|---------|
| 仕様を書く・直すとき | §1 で正を決める。§3 に行があれば**正ファイルにだけ**書き、他はリンクする |
| 仕様ファイルを追加したとき | 索引（[INDEX.md](../INDEX.md)）へ登録する。既存と記述が重なり、かつ下の登録基準を満たすときだけ §3 へ行を足す |
| `doc-review` が重複を指摘したとき | `fix-specs` で正を決めて修正し、**検出パターンを書けるなら** §3 へ行を足す |
| 正を移すとき | §3 の正ファイル列を更新し、旧・正ファイル側の記載をリンクへ置き換える |

**登録基準**（いずれかを満たす行だけ登録する。満たさない境界は §1 に委ねる）

1. 検出パターンが書ける（機械検出できる）
2. §1 の一般原則から導けない、または原則と逆の置き方をしている
3. 過去に実際の重複が起き、再発しやすい

**書式**（`check_docs.py --owner` が読む）

- 正ファイル・許可の列には**バッククォート囲みのパスだけ**をカンマ区切りで書く。§番号・注記・ワイルドカードを混ぜない（完全一致で比較するため、混ぜた行は許可が効かなくなる）
- 補足は表の下の箇条書きへ書く。**備考列を作らない**（仕様の転載置き場になり台帳が肥大するため）
- 許可列は「現状すでに記載があり、参照として妥当な箇所」の凍結リスト。増やさず縮減する
- 走査対象は `docs/design/` `docs/tech/` `docs/data/` `.claude/**` `CLAUDE.md`。それ以外（`docs/process/` `docs/backlog/` `docs/diagrams/` `docs/glossary.md`）へ書いた許可は機械的には効かない
- パターンを書けない行は `—`。宣言だけでも `doc-review` の照合基準になる

## 3. 対応表

| トピック | 正ファイル | 許可（参照可） | 検出パターン |
|---------|-----------|--------------|-------------|
| 経験値計算式（100×level^1.5） | `docs/data/master/character.md` | `docs/design/systems/character.md`, `docs/design/game_spec.md` | `\^ ?1\.5` |
| ゲスト削除猶予（90日） | `docs/tech/detail/tech_auth.md` | `docs/design/requirements/non_functional_requirements.md`, `docs/tech/nonfunctional/tech_maintenance.md` | `90日` |
| リフレッシュトークンの保管先 | `docs/tech/detail/tech_auth.md` | `docs/tech/basic/tech_architecture.md`, `docs/tech/nonfunctional/tech_security.md` | `httpOnly` |
| 認証入力の長さ制限（メール254・パスワード8〜128） | `docs/tech/detail/tech_auth/account.md` | `docs/tech/nonfunctional/tech_security.md`, `docs/tech/detail/tech_auth/link.md`, `docs/tech/detail/tech_auth/password_reset.md` | `254文字` |
| ログ3種別の定義・出力先・ローテーション・共通部品 | `docs/process/coding_standards_backend/logging.md` | `docs/tech/basic/tech_logging.md`, `docs/tech/nonfunctional/tech_operations.md` | `communication\.log` |
| 到達記録（`towersCleared`）のキー体系 | `docs/tech/basic/tech_data.md` | — | `\{towerId\}_\{difficulty\}` |
| ゲーム設定の選択肢・範囲・刻み・既定値 | `docs/design/systems/ui.md` | `docs/design/systems/battle.md`, `docs/tech/basic/tech_db/player.md`, `docs/tech/basic/tech_data.md`, `docs/tech/nonfunctional/tech_security.md` | `0\.1〜0\.5` |
| コスト規律（サブエージェント運用・読み方・工程区切り） | `.claude/project/profile.md` | `CLAUDE.md`, `.claude/references/review-procedure.md` | `同時最大4体` |
| お知らせの既読管理（保持先） | `docs/design/requirements/operation_requirements.md` | `docs/tech/basic/tech_api.md`, `docs/design/systems/ui.md` | — |
| ログアウトの挙動（フロー・トークン失効） | `docs/tech/detail/tech_auth.md` | `docs/design/systems/ui.md`, `docs/tech/basic/tech_api.md` | — |
| 未確定仕様・調整待ち数値の管理ルール | `docs/process/development_process.md` | `docs/backlog/open_specs.md`, `docs/backlog/balance_backlog.md`, `CLAUDE.md` | — |
| 確率・軽減率の上限（挑発率・状態異常付与率） | `docs/design/systems/battle.md` | `docs/design/systems/character.md`, `docs/tech/detail/tech_battle.md`, `docs/data/skills/006_生存術系統.md` | — |
| Phaseごとの開発進捗（工程の完了状況） | `docs/process/development_process.md` | — | — |
| メール送信の方式（時機・設定値・本文・再送） | `docs/tech/detail/tech_auth/mail.md` | `docs/tech/nonfunctional/tech_operations.md` | — |
| 次セッションの開始タスク（引き継ぎ） | `docs/backlog/next_session.md` | `docs/backlog/carryover_notes.md` | — |
| DBスキーマ（物理テーブル名・列・型・キー・インデックス・制約） | `docs/tech/basic/tech_db.md` | `docs/tech/basic/tech_data.md` | — |
| バックエンドのコーディング規約 | `docs/process/coding_standards_backend.md` | `.claude/references/coding-standards-backend.md`, `.claude/project/profile.md`, `.claude/project/review-code.md`, `.claude/project/test-list.md` | — |

- **経験値計算式**: 正の表記は `100 * (level ^ 1.5)`。パターンは `^` の後の空白有無を吸収する
- **ゲーム設定**: 正は%表記・技術層は小数表記のため、パターンは小数側だけを見る（正ファイル自身には一致しない）
- **ログ3種別**: 出力先と書き方が `logging.md`（3分冊通しの §1〜§7）、形式・項目名・ロガー名体系は `tech_logging.md`、エラーコード体系と統一エラーレスポンス形式は `tech_error_handling.md` が正
- **ログアウト**: エンドポイント定義そのものは `tech_api.md` が正
- **DBスキーマ**: 実装側の正は Flyway DDL（`afkgame-initdb`）。`tech_data.md` が持つのは API/マスターの JSON 構造のみ。反映順は `phases.md` §3.2.1
- **引き継ぎ**: `next_session.md` はポインタ専用。複数セッションにまたがる申し送りは `carryover_notes.md` が持ち、引き継ぎ側へ転記しない
- **コーディング規約**: 正 → 派生（`.claude/**`）の順に同じ変更で改訂する（`phases.md` §3.2.2）
