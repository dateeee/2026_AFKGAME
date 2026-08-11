# AFK GAME — フロントエンドコーディング規約（Vue 3 / TypeScript）索引

> `frontend/` の Vue 3 / TypeScript 実装が従う規約の**正**。本書は**索引だけ**を持ち、規約の本体はすべて `coding_standards_frontend/` の分冊にある。
> ベースは [Vue 3 公式ガイド](https://ja.vuejs.org/guide/introduction.html)と[公式スタイルガイド](https://ja.vuejs.org/style-guide/)。**規約はそこからの差分だけを持つ**（適用範囲・準拠元・スタイルガイド優先度別の採否は [basis.md](coding_standards_frontend/basis.md) が正。個々の差分は各分冊が正）。
> 記述方式は **`<script setup lang="ts">` + Composition API のみ**（Options API 禁止。[basis.md](coding_standards_frontend/basis.md) §2）。**規約と異なる実装は修正対象**として [known_issues.md](../backlog/known_issues.md) へ記録し解消する（basis.md §2 #6）。
> バックエンド（Java / Terasoluna）は別書 [coding_standards_backend.md](coding_standards_backend.md)。
> 位置づけ・改訂手順は [phases.md](phases.md) §3.2.2、遵守の判定は [development_process.md](development_process.md) §4「製造完了ゲート」。
> 技術スタック一覧は [profile.md](../../.claude/project/profile.md) §3、エージェント向けの要約は [.claude/references/coding-standards-frontend.md](../../.claude/references/coding-standards-frontend.md)（本書からの派生）。

---

## 1. 分冊索引

| 分冊 | 内容 | 読むとき |
|------|------|---------|
| [basis.md](coding_standards_frontend/basis.md) | §1 適用範囲と準拠元 / §2 原則 / §3 適用と検証 | 規約に無い判断をするとき・規約を改訂するとき |
| [layering.md](coding_standards_frontend/layering.md) | §1 層の定義 / §2 呼び出し方向 / §3 ルーティング / §4 開発時フォールバック / §5 ディレクトリの追加 | 新しいファイルの置き場・依存の向きに迷ったとき |
| [common.md](coding_standards_frontend/common.md) | §1 配置と置き場 / §2 命名 / §3 TypeScript 記述規約 / §4 import / §5 コメント・JSDoc / §6 禁止事項 | **常に最初に読む** |
| [component.md](coding_standards_frontend/component.md) | §1 SFC の構成 / §2 Props・Emits / §3 テンプレート / §4 分類と命名 / §5 スロット・Teleport・テンプレート参照 / §6 表示状態 / §7 アクセシビリティ | コンポーネント・画面を書くとき |
| [composition.md](coding_standards_frontend/composition.md) | §1 リアクティビティ / §2 watch / §3 ライフサイクルとクリーンアップ / §4 composable | リアクティブな状態・composable を書くとき |
| [store.md](coding_standards_frontend/store.md) | §1 責務と作成単位 / §2 Setup Store 形式 / §3 ストア間参照 / §4 API 通信と状態 / §5 リセットと保持上限 / §6 命名 | Pinia ストアを書くとき |
| [api.md](coding_standards_frontend/api.md) | §1 責務と一元化 / §2 型 / §3 認証トークン / §4 リトライとフォールバック / §5 エラー分類 / §6 表示への変換と想定外エラー | API 呼び出し・エラー処理を書くとき |
| [styling.md](coding_standards_frontend/styling.md) | §1 役割分担（Tailwind の使い方）/ §2 scoped CSS の書式 / §3 E2E ロケータとの連動 | スタイルを書くとき（ビジュアルの正は [tech_design_system.md](../tech/detail/tech_design_system.md)） |
| [test.md](coding_standards_frontend/test.md) | §1 構成と分担 / §2 配置と実行環境 / §3 記述規約 / §4 再現性 / §5 分担 | E2E テストを書くとき |

## 2. 読む順

`common.md` →（置き場・依存の向きに迷えば `layering.md`）→ いま書く対象の分冊（コンポーネント・画面なら `component.md` + `composition.md`、ストアなら `store.md`、API 呼び出し・エラー処理を書くなら層を問わず `api.md`、スタイルなら `styling.md`、E2E なら `test.md`）。分冊にも準拠元にも無い判断は近傍の既存コードに倣う（[basis.md](coding_standards_frontend/basis.md) §2 #4）。
