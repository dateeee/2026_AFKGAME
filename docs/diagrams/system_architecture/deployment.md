# システム構成図 — 本番構成（AWS）

> 親: [system_architecture.md](../system_architecture.md)。構成・数値・設定値は [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12.1〜§12.3 と [tech_operations_procedure.md](../../tech/nonfunctional/tech_operations_procedure.md) §12.4〜§12.7 が正（本図には再掲しない）。

## 本番構成（AWS）

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TB
    Browser["ブラウザ (SPA)"]

    subgraph AWS["AWS"]
        subgraph Front["フロント配信"]
            CloudFront["CloudFront\nHTTPS終端"]
            S3spa["S3\nSPA静的ホスティング"]
        end

        subgraph EC2["EC2 (1台)"]
            Nginx["Nginx\nリバースプロキシ"]
            AppWar["Tomcat 11.0 (systemd)\nafkgame-web.war"]
            Cron["OS cron\n日次バッチ (§12.6)"]
            EBS["PostgreSQL\nデータディレクトリは EBS 上"]
        end

        Snapshot["EBS 日次スナップショット\n(§12.5)"]
        S3backup["S3\n論理バックアップ保管\n(§12.5)"]
    end

    Browser -->|"HTTPS"| CloudFront
    CloudFront -->|"SPA配信"| S3spa
    Browser -->|"REST API (JSON)\n別オリジン・CORS"| Nginx
    Nginx --> AppWar
    AppWar -->|"DB読み書き"| EBS
    Cron -->|"日次バッチ・論理バックアップ"| EBS
    EBS -->|"日次スナップショット"| Snapshot
    EBS -->|"論理バックアップを転送"| S3backup

    style Front fill:#e8f5e9
    style EC2 fill:#e3f2fd
```

- フロント（CloudFront）と API（EC2）は**別オリジン**。許可オリジンは [tech_operations.md](../../tech/nonfunctional/tech_operations.md) §12.2 の `CORS_ORIGINS` が正
- 復旧は論理バックアップを第一手段とし、ボリューム障害時にスナップショットから復元する（§12.5）
- マネージドコンテナ（App Runner・ECS Fargate）は採用しない（§12.1）
