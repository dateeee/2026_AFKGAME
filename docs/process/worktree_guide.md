# git worktree 運用ガイド

> **ファイルを編集する作業は worktree 内で行う**（セッション運用 §5 の本体は分冊 [worktree_guide/session.md](worktree_guide/session.md)）。その使い方と、競合を避ける運用ルールの正。
> ヘルパーは `scripts/worktree.py`、追記型ファイルの自動マージ設定は `.gitattributes`。

## 1. 仕組みと使い方

worktree は1つのリポジトリから複数の作業ツリーを同時に開く仕組み。タスクごとに独立したディレクトリ + ブランチで作業し、終わったら main へ統合して消す。

- **配置**: `<リポジトリの親>/2026_AFKGAME.worktrees/<名前>`（リポジトリの隣。git 管理外なので .gitignore 不要）
- **ブランチ命名**: `wt/<名前>`（名前はタスクが分かるもの。例: `step3a2-auth`）

| 操作 | コマンド |
|------|---------|
| 作成 | `python scripts/worktree.py add <名前> [--base main]` |
| 統合 | `python scripts/worktree.py merge <名前> [--keep]`（main 取り込み → ff 統合 → worktree・ブランチ削除まで一括。main 側で実行） |
| 削除 | `python scripts/worktree.py remove <名前> [--delete-branch]` |
| 一覧 | `python scripts/worktree.py list` |

`add` は worktree 作成に加えて、git 管理外のローカル設定（`.claude/settings.local.json` 等）のコピーと rerere の有効化（§3）まで行う。**frontend を触るタスクは worktree 側で `npm install` が必要**（`node_modules` は共有されない）。Maven のローカルリポジトリ（`~/.m2`）は共有で問題ない。

## 2. マージ競合を避ける運用ルール

| # | ルール |
|---|-------|
| 1 | **1 worktree = 1 タスク（工程スキル1件）**。完了したら即 main へ統合して worktree を削除する。長生きブランチが競合の最大要因 |
| 2 | **触るファイル領域が重ならないタスクだけ並行させる**（backend 実装 × docs 整備は○、同一システムを触る2タスクは×）。`next_session.md` の候補キューは行ごとに「wt 名 / 領域」を持つので、選ぶ時点でそれを突き合わせる |
| 3 | **統合前に main を取り込む**（`merge` コマンドが自動で行う）。手順は §5.3 |
| 4 | 追記型ファイル（changelog・効率メモ）は自動解決に任せる（§3） |
| 5 | 単独更新ファイル（§3 の表）は **main のセッションでのみ更新**し、worktree では触らない |
| 6 | Stop フックの自動コミットは各 worktree で独立に働くため、未コミットのまま放置されない。`remove` が「未コミットあり」で失敗したら消す前にコミットする |
| 7 | **ホットスポット（§3 の表・残量が逼迫したファイル）を worktree 側で触ると決めた時点で main の進行を確認する**。着手時に clean でも作業中に main は動く。**main で `git restore`・`git checkout --` を打つ前も同じ**（他セッションの未コミット＝Stop フックが書いた効率メモ等まで巻き込んで消す） |
| 8 | main 側が同じファイルを触っている間は、**圧縮・再配置など可逆でない調整を worktree 側で作り込まない**。本体の変更だけ入れて統合し、字数調整は統合後に測り直す |

## 3. ファイル別の競合ポリシー

全 worktree が書きに行く「ホットスポット」を性質別に扱いを決めておく。

| ファイル | 性質 | 扱い |
|---------|------|------|
| `docs/changelog.md` | 先頭ブロックへ行追記 | `merge=union` で自動統合（`.gitattributes`） |
| `docs/backlog/efficiency_memo.md` | 末尾へ追記（Stop フック） | `merge=union` で自動統合 |
| `docs/backlog/carryover_notes.md` | 節の末尾へ行追記 | `merge=union` で自動統合。**worktree から追記してよい**（引き継ぎへ書けない申し送りの受け皿。既存行の書き換えは避ける） |
| `docs/backlog/next_session.md` | 全面書き換え | **main でのみ・統合の直後に1回**更新する（worktree では触らない）。着手中であることは書かず、worktree の存在で示す（同ファイル §0） |
| `docs/backlog/java_migration.md`（進捗） | 状態の更新 | main でのみ更新、または進捗を持つ STEP を担当する worktree を1つに限定。分冊 `java_migration/**` も同じ扱い |
| `docs/backlog/` のその他（open_specs 等） | 行の追加・削除 | 触る worktree を1つに限定 |

- **`merge=union` の注意**: 両方の追加行を残すだけで行順は保証しない。changelog で同じ日付見出しが二重になったら統合後に1つへ畳む。追記型以外のファイルには適用しないこと（黙って壊れる）。**削除は伝播しない**ため、行を消す作業（効率メモの消化・changelog の重複畳み）は worktree 側で行わず**統合後に main で**行う（worktree で消すと両側の行が残って復活する）
- **rerere**: 一度手で解消した競合は記録され、同じ競合に再適用される（`add` 実行時に有効化済み。設定は全 worktree 共通）

## 4. 実行環境の競合（ポート・DB）

マージ以外に、サーバー・DB などの実行資源も worktree 間で衝突する。**既定はサーバー起動を同時に1 worktree のみとする**のが最も安全。並行起動が必要な場合のみ下表で分離する。

| リソース | 共有状況 | 並行起動する場合 |
|---------|---------|----------------|
| PostgreSQL `localhost:5432/afkgame` | 全 worktree で共有（docker-compose は1つ） | 別DBを作って分離: `docker exec afkgame-postgres createdb -U afkgame afkgame_wt1` → `$env:DATABASE_URL="jdbc:postgresql://localhost:5432/afkgame_wt1"` |
| バックエンド :8080 | ポート競合 | `$env:SERVER_PORT=8081` を設定して起動 |
| フロントエンド :5173 | ポート競合 | `npm run dev -- --port 5174`（Vite は自動で空きポートへ逃げるが明示が確実） |
| 結合テスト用DB | zonky 埋め込み（worktree ごとに独立） | 対応不要。`mvn verify` は並行実行できる |

**DB 分離が特に重要な理由**: Flyway が起動時にマイグレーションを適用するため、スキーマの異なるブランチのバックエンドを同一DBへ向けると相互に壊し合う。スキーマを変えるブランチは必ず専用DBを使う。

## 5. セッション運用（編集は必ず worktree で）

**ファイルを編集する作業は worktree 内で行う**。セッション自身が worktree を作り・入り・統合して片付けるため、人が別ウィンドウを開く必要はない。
**手順と注意点の正は分冊 [worktree_guide/session.md](worktree_guide/session.md)**（節番号は分割前のまま）。

| 節 | 内容 |
|----|------|
| [5.1](worktree_guide/session.md#51-main-のまま進めてよい作業) | main のまま進めてよい作業（読み取り・§3 の単独更新ファイル・統合）と、worktree へ移る判定 |
| [5.2](worktree_guide/session.md#52-開始手順) | 開始手順（`worktree.py add` → `EnterWorktree` に `path` で入る → worktree 側の絶対パスで統一） |
| [5.3](worktree_guide/session.md#53-完了手順統合) | 完了手順（コミット → `ExitWorktree` `"keep"` → main で `worktree.py merge` → `next_session.md` 更新） |
| [5.4](worktree_guide/session.md#54-前提と注意) | 前提と注意（内蔵 worktree 機能との違い・worktree 内での Bash / 解析ツールの制約） |
