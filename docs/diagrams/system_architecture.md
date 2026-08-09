# システム構成図

> 技術仕様: [tech_spec.md](../tech/tech_spec.md) / 運用設計: [tech_operations.md](../tech/nonfunctional/tech_operations.md)
> 本書は索引。各図は [system_architecture/](system_architecture/) 配下の個別ファイルに分割している。

## 索引

| 図 | 内容 | ファイル |
|----|------|---------|
| 全体アーキテクチャ | フロント（Vue 3 SPA）・バックエンド（Terasoluna(Spring MVC)）・DB のモジュール構成と依存関係 | [system_architecture/application.md](system_architecture/application.md) |
| tick処理のデータフロー | `POST /api/battle/tick` がルーター→サービス→モデル→DBを通る経路 | [system_architecture/tick_flow.md](system_architecture/tick_flow.md) |
| サーバー権威モデル / エラーハンドリング | 処理ごとの実行場所（サーバー/クライアント）と通信失敗時のリトライ方針 | [system_architecture/authority.md](system_architecture/authority.md) |
| 本番構成（AWS） | S3/CloudFront + EC2（Nginx・Tomcat + war・EBS・cron）のデプロイ構成とバックアップ経路 | [system_architecture/deployment.md](system_architecture/deployment.md) |
