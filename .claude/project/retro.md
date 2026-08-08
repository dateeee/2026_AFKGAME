# retro プロファイル — AFK GAME 固有値

`/retro`（振り返り）スキルが読む固有値。一般手順は [SKILL.md](../skills/retro/SKILL.md)。

## 効率メモ

| 項目 | 値 |
|------|----|
| メモのパス | `docs/backlog/efficiency_memo.md`（作業用の台帳。区分C 8,000字上限・doc検査の対象。超過は `/retro` を回す合図） |
| 自動追記フック | `.claude/hooks/efficiency_check.py`（Stop フック。`stop-chain.sh` 経由で登録済み） |
| 実行順 | `stop-chain.sh` が efficiency_check → stop-commit の順で実行。メモ記入をコミットより先に済ませ、メモの変更も同じコミットへ含める（メモだけの追いコミットを作らない） |
| エントリの寿命 | 反映済み・対応不要になったら削除（`docs/backlog/open_specs.md` と同じ運用） |

## 検出シグナルとしきい値

しきい値は efficiency_check.py 冒頭の定数。誤検出が続くシグナルはそこを調整する。

| シグナル | 意味 | 既定 |
|---------|------|------|
| same-read | 同一パラメータの Read の繰り返し（再Read禁止の違反） | 2回 |
| same-command | 同一コマンドの繰り返し実行 | 3回 |
| errors | ツールエラーの多発（ユーザー拒否・検査系の「違反あり exit 1」は除く） | 3件 |
| denials | ユーザーによる許可拒否 | 2件 |
| long-turn | ターン内ツール呼び出し総数 | 30回 |
| correction | ターン冒頭の発話の手戻り語（「違う」「やり直し」等） | 1語 |

`errors` から除外するコマンドは同ファイルの `EXPECTED_NONZERO`（既定: `check_*.py` /
pytest / mvn / ruff / mypy / eslint / vue-tsc / `--noEmit` / type-check / lint）。
違反検出時の exit 1 は正常な報告であり試行錯誤ではないため数えない。
本プロジェクトで追加が要る場合は環境変数 `EFFICIENCY_EXPECTED_NONZERO`（正規表現）で上書きする。

検出時はフックが仮エントリを追記し、Claude が「原因と改善案」を記入して完成させる。
一時的に止めたい場合は `stop-chain.sh` の efficiency_check 呼び出しを外す
（settings.json の Stop フックごと外すとコミット確認も止まる。`.claude/.no-auto-commit` 方式の無効化フラグは未実装）。

## 反映先マップ（AFK GAME での例）

| 原因の型 | 反映先の例 |
|---------|----------|
| 手順の欠落 | `.claude/skills/<スキル>/SKILL.md` |
| 固有値の欠落 | `.claude/project/<プロファイル>.md`（索引は [INDEX.md](INDEX.md)） |
| 仕様書の欠陥 | 該当工程スキル（`doc-review` → `fix-specs` 等）で修正 |
| コードの欠陥 | `dev` / `unit-test` 等の工程スキルで修正 |

## 改稿時の後始末

`docs/changelog.md` の先頭へ1行追記し、`python scripts/check_doc_size.py` と
`python scripts/check_docs.py` を実行する。
