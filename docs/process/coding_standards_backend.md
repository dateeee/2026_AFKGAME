# AFK GAME — バックエンドコーディング規約（Java）索引

> `backend/` の Java 実装が従う規約の**正**。本書は**索引だけ**を持ち、規約の本体はすべて `coding_standards_backend/` の分冊にある。
> ベースは [TERASOLUNA Server Framework for Spring 開発ガイドライン 5.11.0.RELEASE 日本語版](https://terasolunaorg.github.io/guideline/current/ja/)。**規約はそこからの差分だけを持つ**（適用範囲・準拠元・原則・検証手段は [basis.md](coding_standards_backend/basis.md) が正。個々の差分は各分冊が正）。
> フロントエンド（Vue 3 / TypeScript）は別書 `coding_standards_frontend.md`（未整備）。
> 位置づけ・改訂手順は [phases.md](phases.md) §3.2.2、遵守の判定は [development_process.md](development_process.md) §4「製造完了ゲート」。
> 技術スタック一覧は [profile.md](../../.claude/project/profile.md) §3、エージェント向けの要約は [.claude/references/coding-standards-backend.md](../../.claude/references/coding-standards-backend.md)（本書からの派生）。

---

## 1. 分冊索引

| 分冊 | 内容 | 読むとき |
|------|------|---------|
| [basis.md](coding_standards_backend/basis.md) | §1 適用範囲と準拠元 / §2 原則 / §3 適用と検証 | 規約に無い判断をするとき・規約を改訂するとき |
| [layering.md](coding_standards_backend/layering.md) | §1 3レイヤの定義 / §2 コンポーネントの担当 / §3 Repository による抽象化と呼び出し可否 / §4 モジュール構成の対応 | 新しいクラスの置き場・呼び出し方向に迷ったとき |
| [common.md](coding_standards_backend/common.md) | §2 モジュールとパッケージ / §3 命名 / §4 全層共通のルール / §5 Java 記述規約 / §6 例外（→ `exception.md`） / §7 ログ / §8 Javadoc / §9 禁止事項 | **常に最初に読む** |
| [exception.md](coding_standards_backend/exception.md) | §1 例外の3分類（ビジネス例外・システム例外・予期しないエラー） / §2 ガイドラインの種類との対応 / §3 送出 / §4 応答への変換 / §5 ログ / §6 分担 | 例外を投げる・捕まえる・応答へ変換するとき |
| [logging.md](coding_standards_backend/logging.md) | §1 ログ3種別と出力先 / §2 通信ログ / §3 AOP による境界ログ / §4 業務ログ / §5 エラーログ / §6 禁止事項 / §7 テストと分担 | ログを書くとき・ログの出力先を触るとき |
| [domain.md](coding_standards_backend/domain.md) | `afkgame-domain`: §1 責務 / §2 Entity / §3 Repository / §4 マスターデータ・乱数 / §5 命名 | Entity・Repository を書くとき |
| [domain_service.md](coding_standards_backend/domain_service.md) | `afkgame-domain`: §1 Service の役割 / §2 クラス構成 / §3 作り方 / §4 トランザクション / §5 作成単位 / §6 例外とメッセージ（→ `exception.md`） | Service を書くとき |
| [web.md](coding_standards_backend/web.md) | `afkgame-web`: §1 責務 / §2 コントローラ / §3 Resource / §4 設定・フィルタ / §5 エラー応答（→ `exception.md`） / §6 命名 / §7 セキュリティ | Web層を書くとき |
| [filter.md](coding_standards_backend/filter.md) | §1 フィルタとインターセプタの**使い分け** / §2 作り方 / §3 登録と順序 / §4 例外と応答 / §5 採らないもの / §6 テスト | 横断処理をどちらで書くか決めるとき・フィルタを触るとき |
| [interceptor.md](coding_standards_backend/interceptor.md) | §1 位置づけ / §2 3つのフックの使い分け / §3 規約 / §4 採らないもの / §5 テスト | `HandlerInterceptor` を作る・登録するとき |
| [test.md](coding_standards_backend/test.md) | §1 配置と分離 / §2 記述規約 / §3 再現性 / §4 分担 / §5 ガイドラインとの差分 | テストを書くとき |

## 2. 読む順

`common.md` →（置き場・呼び出し方向に迷えば `layering.md`）→ いま書く層の分冊（エラー処理を書くなら層を問わず `exception.md`、ログを書くなら層を問わず `logging.md`、横断処理なら `filter.md` → 必要なら `interceptor.md`）。分冊にもガイドラインにも無い判断は近傍の既存コードに倣う（[basis.md](coding_standards_backend/basis.md) §2 #1）。
