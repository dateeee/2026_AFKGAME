# Java/Terasoluna 移行 — 実装の起点と仕様変更点

> [java_migration.md](../java_migration.md) の分冊。担当は **§3・§5**。手順と進捗の正は親（索引）にある。

---

## 3. 実装の起点

Python 実装は削除済み（STEP 6）。**移植の起点は仕様書であってコードではない** — API契約は [tech_api.md](../../tech/basic/tech_api.md)、DBスキーマは `tech_db/`、数値は `docs/data/master/`、分岐は詳細設計の「分岐一覧」が正。旧コードを見る必要が出たらタグ `python-backend-final` から取り出す。

層の割り当ては [tech_selection.md](tech_selection.md) §2 のモジュール構成、実装規約は [coding_standards_backend.md](../../process/coding_standards_backend.md) が正。定数の振り分けは**値の正の所在**で決める（正が `docs/data/master/`・`docs/design/systems/` にある初期キャラ・初期所持アイテム・装備スロットは YAML マスターデータへ、それ以外は §5「設定ファイル」の方式へ）。

`scripts/*.py`・`.claude/{scripts,hooks}/*.py`（ドキュメント検証・レビュー退避・フック）は開発補助のため**Python のまま維持**する（依存はルートの `requirements-dev.txt`）。

## 5. 移行に伴う仕様変更点

API契約は不変だが、以下は言語差・実行形態の違いにより仕様側の見直しが必要。

| 項目 | 内容 |
|------|------|
| 乱数の再現性 | Python の Mersenne Twister と Java の乱数は互換性がない。**同一シードでも結果は一致しない**。シード固定テストの期待値は Java 側で再生成する |
| tick の排他 | SQLAlchemy のセッション前提から、Spring の `@Transactional` + 行ロック前提へ読み替える |
| マイグレーション履歴 | Alembic の既存5リビジョンは Flyway の `V1` 初期スキーマへ畳む（移行前後で同一スキーマになることを確認する） |
| DBMS の統一 | SQLite を廃止し `local`・`production` とも PostgreSQL にする。段階移行の前提が消えるため、型マッピングの SQLite 列・ロック方式の分岐・バックアップの二本立て・容量による移行判断ラインを削除する |
| tick のロック | SQLite の `BEGIN IMMEDIATE` 前提をやめ、`SELECT ... FOR UPDATE` の行ロックに一本化する（[tech_tick.md](../../tech/detail/tech_tick.md) §3.1 が正） |
| マスターデータ | Python 定数 → YAML リソース。数値の正は `docs/data/master/` のまま変わらないが、**再ビルドなしで差し替え可能**になる。ローダは起動時にスキーマ検証し、不正なら起動を中止する |
| 実行形態 | 実行可能 jar + systemd を廃止し、**war を Tomcat へデプロイ**する。Nginx 配下に置く点は変わらない。運用手順（起動・停止・ログ出力先・ヘルスチェック）は Tomcat 前提へ全面的に書き直す |
| APIドキュメント | springdoc-openapi（未実装）を採らない。**`/docs`（Swagger UI）は提供しない**。API仕様の正は `tech_api.md`・[tech_api_common.md](../../tech/basic/tech_api_common.md) の記述だけになる |
| 設定ファイル | `application.yml`（YAML・Spring プロファイル）→ `META-INF/spring/*.properties`。`@ConfigurationProperties` による束ね方も使えないため、設定値の受け取り方を再設計する |
| ログ設定 | `logback-spring.xml` の `<springProfile>` は Boot 拡張のため使えない。`logback.xml` へ移し、環境別の切り替えを別方式にする |
