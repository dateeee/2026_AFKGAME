# AFK GAME — バックエンドコーディング規約（Java）索引

> `backend/` の Java 実装が従う規約の**正**。本書は**索引だけ**を持ち、規約の本体はすべて `coding_standards_backend/` の分冊にある。
> ベースは [TERASOLUNA Server Framework for Spring 開発ガイドライン 5.11.0.RELEASE 日本語版](https://terasolunaorg.github.io/guideline/current/ja/)。**規約はそこからの差分だけを持つ**（適用範囲・準拠元・原則・逸脱一覧・検証手段は [basis.md](coding_standards_backend/basis.md) が正）。
> フロントエンド（Vue 3 / TypeScript）は別書 `coding_standards_frontend.md`（未整備）。
> 位置づけ・改訂手順は [phases.md](phases.md) §3.2.2、遵守の判定は [development_process.md](development_process.md) §4「製造完了ゲート」。
> 技術スタック一覧は [profile.md](../../.claude/project/profile.md) §3、エージェント向けの要約は [.claude/references/coding-standards-backend.md](../../.claude/references/coding-standards-backend.md)（本書からの派生）。

---

## 1. 分冊索引

| 分冊 | 内容 | 読むとき |
|------|------|---------|
| [basis.md](coding_standards_backend/basis.md) | §1 適用範囲と準拠元 / §2 原則 / §3 ガイドラインからの逸脱一覧 / §4 適用と検証 | 規約に無い判断をするとき・規約を改訂するとき |
| [layering.md](coding_standards_backend/layering.md) | §1 3レイヤの定義 / §2 コンポーネントの担当 / §3 Repository を作らない構成と呼び出し可否 / §4 モジュール構成の対応 | 新しいクラスの置き場・呼び出し方向に迷ったとき |
| [common.md](coding_standards_backend/common.md) | §2 モジュールとパッケージ / §3 命名 / §4 全層共通のルール / §5 Java 記述規約 / §6 例外 / §7 ログ / §8 Javadoc / §9 禁止事項 | **常に最初に読む** |
| [domain.md](coding_standards_backend/domain.md) | `afkgame-domain`: §1 責務 / §2 Entity / §3 Mapper / §4 マスターデータ・乱数 / §5 命名 | Entity・Mapper を書くとき |
| [domain_service.md](coding_standards_backend/domain_service.md) | `afkgame-domain`: §1 Service の役割 / §2 クラス構成 / §3 作り方 / §4 トランザクション / §5 作成単位 / §6 例外とメッセージ | Service を書くとき |
| [web.md](coding_standards_backend/web.md) | `afkgame-web`: §1 責務 / §2 コントローラ / §3 Resource / §4 設定・フィルタ / §5 エラー応答 / §6 命名 | Web層を書くとき |
| [test.md](coding_standards_backend/test.md) | §1 配置と分離 / §2 記述規約 / §3 再現性 / §4 分担 | テストを書くとき |

## 2. 読む順

`common.md` →（置き場・呼び出し方向に迷えば `layering.md`）→ いま書く層の分冊。分冊にもガイドラインにも無い判断は近傍の既存コードに倣う（[basis.md](coding_standards_backend/basis.md) §2 #1）。
