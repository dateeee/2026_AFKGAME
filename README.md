# AFK GAME

放置系ファンタジーRPGのWebブラウザゲーム。

プレイヤーは冒険者ギルドのマスターとして冒険者を育成・編成し、ダンジョン（塔）へ派遣する。
アプリを閉じている間も探索・戦闘は自動で進み、復帰時に報酬をまとめて受け取れる。

## 技術スタック

| レイヤー | 技術 |
|---------|------|
| フロントエンド | Vue.js 3 (SPA / Composition API / TypeScript) + Vite + Pinia + Tailwind CSS |
| バックエンド | Java 17 / Terasoluna 5.11（war + Tomcat 11）+ MyBatis3 + Flyway |
| DB | PostgreSQL |
| 描画方式 | テキストベース（Canvas不使用）。UIアイコンはSVG、アイテムは画像 |

## セットアップ

### バックエンド

```bash
docker compose up -d   # PostgreSQL :5432
cd backend && mvn clean install
cp afkgame-web/target/afkgame-web.war "$CATALINA_HOME/webapps/ROOT.war"
SPRING_PROFILES_ACTIVE=local "$CATALINA_HOME/bin/catalina.sh" run   # :8080
```

要 Tomcat 11.0（`CATALINA_HOME`）。Windows は `catalina.bat`。確認は `curl localhost:8080/health`。

### フロントエンド

```bash
cd frontend && npm install
npm run dev   # http://localhost:5173（/api は :8080 へプロキシ）
```

VS Code は実行構成 **Full Stack** で同時起動できる（`.vscode/launch.json`）。

### 環境変数

| 変数 | 既定値 | 用途 |
|------|-------|------|
| `DATABASE_URL` / `_USER` / `_PASSWORD` | `…localhost:5432/afkgame` / `afkgame` / `afkgame` | DB接続情報（本番は変更必須） |
| `SPRING_PROFILES_ACTIVE` | なし（必須） | 環境識別（`local` / `production`）。未設定なら起動失敗 |
| `JWT_SECRET` | `local` のみ既定値あり | JWT署名鍵（本番必須。未設定なら起動失敗） |

既定値は `META-INF/spring/*.properties`。Google OAuth・ログ設定を含む全一覧は `tech_operations.md` §12.2 が正。

### 主なコマンド

| コマンド | 内容 |
|---------|------|
| `npm run dev` / `npm run build` | フロント開発サーバー / 本番ビルド |
| `npm run type-check` | 型チェック（vue-tsc + E2E） |
| `npm run test:e2e` | E2Eテスト（Playwright。専用ポート/DBで自動起動）。要 `docker compose up -d` + war ビルド済み |
| `mvn verify` | バックエンドテスト（JUnit 5 + JaCoCo。branch 100%・`target/site/jacoco/`） |
| `python scripts/check_doc_size.py` | ドキュメント文字数チェック |
| `python scripts/check_docs.py` | ドキュメント機械検証（リンク・索引・曖昧語・正の逸脱ほか） |
| `python scripts/check_branch_list.py` | 分岐一覧の構造検証（`--tests` でテスト対応照合） |
| `python scripts/rotate_reviews.py --apply` | レビュー結果の退避（直下は最新10件、超過分は `archive/` へ） |

## ディレクトリ構成

```
2026_AFKGAME/
├── README.md  CLAUDE.md         # 本ファイル / AIエージェント向け開発ルール
├── .claude/                     # エージェント定義
│   ├── skills/                  # 工程7件 + 支援10件（プロジェクト非依存）
│   ├── references/              # スキル共通リファレンス（同上）
│   └── project/                 # プロジェクト固有プロファイル（索引: INDEX.md）
├── docs/                        # ドキュメント（分類軸は documentation_rules/directories.md §10）
│   ├── design/ tech/ data/      # 成果物: ゲーム仕様 / 技術仕様 / マスターデータ
│   ├── diagrams/                # 成果物: 設計図（Mermaid）。索引 + 同名ディレクトリ
│   ├── process/                 # 進め方: 工程定義・ドキュメント規約
│   ├── backlog/                 # 状態: 未処理項目の台帳（工程で増減する）
│   └── reviews/                 # 記録: レビュー結果（自動生成。スキル名/日時.md）
├── scripts/                     # 開発補助スクリプト
├── frontend/                    # Vue.js SPA。src/{views,components,stores,composables,api,types} + tests/e2e/
└── backend/                     # Terasoluna（war + Tomcat）。afkgame-{domain,web,env,initdb}
```

各ディレクトリの責務は [.claude/project/profile.md](.claude/project/profile.md) §2 が正。

## アーキテクチャ方針

- **ハイブリッドtick制**: 60秒間隔のtickで戦闘処理。オンライン中はポーリング、オフライン中は復帰時に一括計算
- **サーバー権威**: 戦闘計算はバックエンドで実行（チート対策）。フロントはログ表示のみ
- **シングルプレイ専用**: マルチプレイは想定しない

## 開発フェーズ

| Phase | 内容 |
|-------|------|
| Phase 1 (MVP) | キャラ1体の自動戦闘、レベルアップ、オフライン報酬、常設ショップ |
| Phase 2 | 装備システム、複数の塔、ショップ拡張（日替わり装備）、認証 |
| Phase 3 | パーティ編成、タイプ（素質）・スキルシステム |
| Phase 4 | 拠点建設（酒場・鍛冶屋・訓練場・倉庫・市場）、素材・生産システム |
| Phase 5 | エンドコンテンツ（ボスラッシュ、転生等） |

仕様は全Phase(1-5)を先に確定し、実装をPhase 1から順に進める方針。進捗の正は [開発工程](docs/process/development_process.md) §5。

## ドキュメント索引

[docs/INDEX.md](docs/INDEX.md) が正（`docs/**` 全ファイルの入口）。エージェント向けのプロファイル索引は [.claude/project/INDEX.md](.claude/project/INDEX.md)。
