# AFK GAME — 未確定仕様一覧

> 全Phaseの仕様を確定してから段階的に実装する方針。
> 各項目が確定し仕様書（design/systems/ / tech/ / data/master/。索引は game_spec.md・tech_spec.md・master_data.md）に反映されたらこのファイルから削除する。
> すべての項目が解消されたらこのファイル自体を削除する。
> 確定済み項目の記録は git 履歴および [changelog.md](changelog.md) を参照。
>
> **仕様は確定済みで数値だけ調整待ち**の項目は本書ではなく [balance_backlog.md](balance_backlog.md) で管理する。

---

## 技術・インフラ（Phase横断）

- [ ] デプロイ先の選定（Vercel + Render / Railway / VPS など）
- [ ] アセット調達方針（キャラ絵・BGM・SE: 自作 / フリー素材 / AI生成 / 外注）→ 必要になった時点で決定
- [ ] 退会（アカウント削除）機能 — 要件は non_functional_requirements §5 で定義済み、実装・API未定義。削除処理は tech_operations §12.6 のゲスト削除ジョブと共通化する想定
- [ ] ゲーム内お知らせ機能 — 未実装。告知手段がないため下方修正を伴うバランス改定・計画メンテナンス告知（operation_requirements §3）が実施できない
- [ ] 定期ジョブの実行基盤 — cron 相当をデプロイ先が提供するか未定（tech_operations §12.6）。デプロイ先選定と併せて決定
- [ ] イベントダンジョンの入退場API・データ構造 — 機能仕様は systems/endgame §2.13 で確定済みだが tech_api・tech_data に定義がない。深淵の塔と同様に `/api/tower/*` へ難易度パラメータを足して再利用するか、`/api/event/*` を新設するかを基本設計で決定する

---

## コンテンツ・キャラクター定義（Phase横断）

- [ ] イベントダンジョン3種（試練の迷宮/宝物庫/修練場）×3難易度の敵構成・報酬テーブル — game_spec §2.13 に「master_data.md で後日定義」と明記。Phase 5実装前までに master_data へ追加
- [ ] 酒場ガチャ専用キャラの正式名称 — master_data §7.3 は仮置きID管理。Phase 4（酒場）実装時に定義
