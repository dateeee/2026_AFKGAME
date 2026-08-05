# 画面遷移図 — モーダル・ダイアログ・通知

> 親: [screen_transition.md](../screen_transition.md)。UI仕様は [systems/ui.md](../../docs/design/systems/ui.md) §3「通知システム」。

## モーダル・ダイアログ一覧

```mermaid
%%{init: {'theme': 'default', 'themeVariables': {'fontSize': '16px'}} }%%
flowchart TD
    subgraph モーダル["モーダル (手動閉じ, 同時1件)"]
        OfflineModal["オフライン報酬モーダル\n経過時間・tick数\n獲得Gold/EXP\nドロップ一覧\n消費ポーション数"]
        BossModal["ボス撃破モーダル\n塔名・ボス名\n特別報酬\n次の塔解放通知"]
        NewCharModal["新キャラ加入モーダル\nキャラ名・タイプ\nステータス表示"]
        WipeModal["全滅結果モーダル\nペナルティ詳細\nEXPロスト量\nGold/Itemロスト"]
        ScoutModal["スカウト結果モーダル\nキャラ名・レアリティ\n重複時: 限界突破案内"]
    end

    subgraph トースト["トースト (3秒自動消去, 最大3件)"]
        LvUpToast["レベルアップ\nLV表示・ステータス上昇"]
        DropToast["装備ドロップ獲得\n装備名・レアリティ"]
        FloorToast["階層クリア\n次の階へ"]
    end

    subgraph ダイアログ["確認ダイアログ"]
        BuyConfirm["購入確認\n商品名・価格\n所持Gold表示"]
        SellConfirm["売却確認\n装備名・売却価格"]
        BulkSellConfirm["一括売却確認\n対象件数・合計売却額"]
        LimitBreakConfirm["限界突破確認\n素材キャラ消費の警告"]
        PrestigeConfirm["転生確認\nリセット内容の最終確認"]
        SkillResetConfirm["スキルリセット確認\nコスト表示 (LV x 50G)"]
    end

    subgraph チュートリアル["チュートリアルヒント (初回1回のみ)"]
        Hint1["初回ログイン時:\n冒険者が自動で探索します"]
        Hint2["初回LVアップ時:\nステータスが上昇しました"]
        Hint3["設定閾値以下初到達時:\nポーションは自動使用されます"]
        Hint4["初回装備ドロップ時 Phase2:\n装備画面で装着できます"]
    end
```

- 退会は確認ダイアログではなく**画面遷移を伴うフロー**（再認証 → 削除確認）。[main_nav.md](main_nav.md) を参照

## 通知システム

| 種別 | 表示方法 | 消去 | 最大同時 |
|------|---------|------|---------|
| ログ内通知 | 戦闘ログ内にインライン | 自動スクロール | 制限なし |
| トースト | 画面上部に一時表示 | 3秒で自動消去 | 3件 |
| モーダル | PC: 画面中央 / モバイル: 下寄せ | 手動で閉じる | 1件 |

- モーダル・ダイアログの実体は `components/ui/BaseModal` 1点。表示位置・閉じ方（Esc・背面タップ）・背面スクロール抑止は [tech_design_system.md](../../docs/tech/detail/tech_design_system.md) §2 が正。画面ごとに自前で組まない
- 待ちキューのルール（FIFO・上限10件・エラー通知の割り込み）は [systems/ui.md](../../docs/design/systems/ui.md)「通知キューのルール」が正
