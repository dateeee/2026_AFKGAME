# 技術仕様 — デザインシステム

> [tech_spec.md](tech_spec.md) 関連詳細仕様。画面構成の要件は [systems/ui.md](../design/systems/ui.md)。
> **画面を追加・改修する前に本書を読むこと。** ここに書かれた層より下に、色・寸法・部品を作らない。

## 設計方針

見た目の一貫性を「気をつける」ではなく**構造**で担保する。3層に分け、上の層は下の層だけを使う。

| 層 | 実体 | 責務 |
|----|------|------|
| 1. トークン | `assets/styles/tokens.css` | 色・書体・寸法・モーションの唯一の定義元 |
| 2. UIプリミティブ | `components/ui/` | ボタン・カード等の見た目と挙動。画面から再実装しない |
| 3. アプリシェル | `components/layout/` | ヘッダ・ナビ・スクロール境界・セーフエリア |
| （画面） | `views/` | 配置と業務ロジックのみ。色や寸法の判断をしない |

## 禁止事項

| 禁止 | 理由 | 代わりに |
|------|------|---------|
| コンポーネントに生の16進数・`rgb()` を書く | テーマ変更が全ファイルに散る | `var(--color-*)` |
| 絵文字をアイコンに使う | 端末ごとに字形・色・サイズが変わる | `AppIcon`（インラインSVG） |
| 入力部品の文字サイズを16px未満にする | iOS Safari がタップ時に画面を自動拡大する | `--text-input`（16px固定） |
| タップ対象を44px未満にする | 指で押せない | `--size-tap-min` / `.tap-target` |
| `<select>` `<input>` を画面ごとに自前スタイルする | 同じ部品が画面数だけ増える | `BaseSelect` / `BaseTextInput` |
| `:hover` を素で書く | タッチ端末でタップ後にホバーが残り、選択中に見える | `@media (hover: hover)` で囲む |
| `background-attachment: fixed` / `-webkit-overflow-scrolling: touch` | iOS 非対応・合成レイヤーの描画崩れ | 使わない |
| Cinzel など欧文書体のフォールバックに `serif` を置く | 和文が明朝に化けて本文と不整合になる | `'Noto Sans JP', sans-serif` を次点に置く |

## 1. トークン（`tokens.css`）

**プリミティブ（`--ramp-*`）**に生の色を置き、**セマンティック（`@theme`）**が役割名で参照する。
UI が使うのはセマンティックだけ。テーマ変更はプリミティブの差し替えで完結する。

| 種別 | トークン | 用途 |
|------|---------|------|
| 面 | `surface-base` / `1` / `2` / `3` | ページ地 / カード / 入れ子 / ホバー・押下 |
| 面 | `surface-inset` / `surface-overlay` | 沈み込み（バーの溝・入力欄）/ モーダル背面 |
| 罫線 | `line-soft` / `line` / `line-strong` | 通常 / 境界 / 強調 |
| 文字 | `content-strong` / `content` / `content-muted` / `content-faint` | 見出し・数値 / 本文 / ラベル / 無効 |
| 強調 | `accent`（古金）系 4段 + `gold` | 主要導線・選択状態・通貨。**多用しない** |
| 状態 | `hp` / `exp` / `danger` / `success`（各 `-bright` あり） | ゲージ・警告 |
| レアリティ | `rarity-common` 〜 `rarity-legendary` | 装備の等級 |

- 文字サイズは役割名（`--text-display` / `title` / `heading` / `body` / `label` / `caption` / `stat` / `input`）。`text-xs` のような場当たりの指定をしない
- 面は隣り合う段で明度差を約4%取る。これ未満だとカードが背景に溶けて階層が消える
- 寸法は `--size-tap-min` / `--size-nav-h` / `--size-header-h` / `--container-content` / `--container-wide`

### 配色の考え方（深緑 × 古金）

- 面は**低彩度の深緑**でまとめ、彩度の高い色は情報にだけ与える
- 古金は「報酬・主要導線・選択中」に限定する。装飾で使うと強調が効かなくなる
- 選択・状態は色だけで示さず、**帯・アイコン・文言**を併用する（[systems/ui.md](../design/systems/ui.md) 色非依存）

## 2. UIプリミティブ（`components/ui/`）

| 部品 | 用途・要点 |
|------|-----------|
| `BaseButton` | `variant`: primary（1画面1つ）/ secondary / ghost / danger、`size`: sm/md/lg。md 以上が44px |
| `BaseCard` | ルートは `.panel`。見出しは「短い古金の目印 + 小さめラベル」。色付きの上辺ボーダーは付けない |
| `BaseModal` | Teleport + スマホは下寄せ（親指が届く）。Esc・背面タップで閉じる。背面スクロールを止める |
| `BaseBadge` | 状態ラベル。`tone`: neutral / gold / danger / success / info |
| `StatBar` | HP・EXP・敵HP。ラベル / バー / 数値の三点セット |
| `BaseSelect` | `options` 配列で渡す（DOM 経由の文字列化を避け、数値のまま返すため） |
| `BaseTextInput` / `BaseField` | 入力とラベルの紐付け（for/id）を一元化 |
| `NumberStepper` | −/＋ 付き数値入力。スマホでは連打の方が速く確実 |
| `AppIcon` | インラインSVG。`icons.ts` に 24x24・線のみで追加する |

数値の表示には `.num`（`tabular-nums`）を付ける。毎tick更新される値の桁揺れを止めるため。

## 3. アプリシェル（`components/layout/`）

`AppShell` が縦持ちの土台を引き受ける。**各画面は高さ・スクロール・セーフエリアを意識しない。**

| 要素 | 役割 |
|------|------|
| `AppShell` | `100dvh` の grid。スクロールするのは `main` だけ（`minmax(0, 1fr)` + `overflow-y: auto`） |
| `AppHeader` | ブランド（ホームへ戻る）・ゴールド・設定・お知らせ（Phase 3〜）。**ゴールドの表示場所はここ1箇所に固定する**。要素の一覧は [systems/ui.md](../design/systems/ui.md) ヘッダ表が正 |
| `AppNav` | 項目は `navItems.ts` の1箇所。モバイルはボトム、PC（768px〜）は左サイドに自動で切り替わる |
| `ConnectionBanner` | 通信エラー。`position: fixed` でレイアウトの流れから外す（フローに置くと本体がずれる） |

- 画面追加は `navItems.ts` に1行追加すれば両ナビに反映される。**5項目を超えたら**モバイルは1項目64px を割るため末尾を「その他」へまとめる（PCは全項目を並べる）。対象項目は [systems/ui.md](../design/systems/ui.md) ナビゲーション構造が正
- セーフエリアは `.pt-safe` / `.pb-safe` / `.px-safe`、`index.html` の `viewport-fit=cover` と対で機能する
- 拡大操作は禁止しない（`maximum-scale` を指定しない）。入力欄の自動ズームは16px指定で回避する

## 4. 画面を追加するときの手順

1. `views/` に画面を作り、外枠は `AppShell` に任せる（`App.vue` が包む）
2. 情報の塊は `BaseCard`、操作は `BaseButton`、入力は `BaseField` + `Base*` で組む
3. 色・寸法が足りないと感じたら、画面に書かずに**トークンを追加**する
4. 部品が足りないと感じたら、画面に書かずに**`components/ui/` に追加**する
5. ナビに載せるなら `navItems.ts` に1行足す
6. 320px 幅で横スクロールが出ないこと、タップ対象が44px以上あることを確認する
