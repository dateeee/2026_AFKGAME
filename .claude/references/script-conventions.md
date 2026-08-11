# スクリプトの配置規約

`.claude/` 配下でスクリプトを新設・改修するときの共通規約。**プロジェクト非依存**（`.claude/skills/` `.claude/references/` と同じく無改造でコピーする）。
プロファイルの記述スキーマは [.claude/project/_TEMPLATE.md](../project/_TEMPLATE.md)。

判定・採番・雛形生成・一括置換など**決定的に決まる手順は LLM の手作業にせず**スクリプトへ寄せる。

## 1. 置き場所

| 置き場所 | 用途 | 回帰テスト |
|---------|------|----------|
| `.claude/scripts/` | 複数スキルが共有するもの（例: `review_prep.py` — レビュー系5スキルのモード判定・差分特定・採番・雛形生成） | `.claude/scripts/tests/` |
| `.claude/skills/<スキル名>/scripts/` | そのスキル専用のもの | 同ディレクトリの `tests/` |
| `.claude/hooks/` | フック本体（stdin の JSON を受けて判定するもの） | `.claude/hooks/tests/` |
| `<リポジトリ>/scripts/` | プロジェクト固有の検証（`.claude/` の外。パス・対象を内蔵してよい） | `<リポジトリ>/scripts/tests/` |

## 2. 回帰テスト

- 判定ロジックを持つスクリプトには `<配置先>/tests/` へ pytest を置き、`python -m pytest <配置先>/tests -q` で回す。プロダクト側のテスト設定（カバレッジ閾値等）と混ざらないよう**別ルートで回す**
- テストは**緑パス + 変異テスト**（検出対象を1項目ずつ壊して検出されることを確認）をセットにする。緑パスだけでは「何も検出しない実装」が通ってしまう
- `tests/conftest.py` で `sys.path` へ親ディレクトリを足し、通常の import 経路で読む（`importlib` の直読みは `sys.modules` 未登録で dataclass の型解決が落ちる）

## 3. 書き方

- `.claude/` 配下のスクリプトは**プロジェクト非依存**にする。保存先・対象パス・タイトル等の固有値は**すべて引数で受け取り**、呼び出し側（スキル + プロファイル）が渡す
- 出力は `KEY  値` 形式など**そのまま取り込める形**にする。LLM に再計算させない
- 許可プロンプトを省くには `.claude/settings.json` の `permissions.allow` へ `Bash(python .claude/scripts/*)` を追加する（SKILL.md の `allowed-tools` は「そのスキルが使えるツールの制限」であり、書くと列挙外のツールが使えなくなる。許可の追加用途には使わない）
