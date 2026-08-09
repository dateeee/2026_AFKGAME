# 効率メモ（efficiency memo）

非効率だったやり取りの記録。反映したら消す作業用の台帳（[documentation_rules.md](../process/documentation_rules.md) §10）。
運用の正は [.claude/project/retro.md](../../.claude/project/retro.md)。
区分Cの文字数上限（8,000字）の対象。**超過したら `/retro` を回す合図**（溜め込まずに反映して消す）。

- **自動追記**: Stop フック `.claude/hooks/efficiency_check.py` が直近ターンの
  非効率シグナル（同一Readの再読・同一コマンドの連発・エラー多発・許可拒否・
  過大なツール呼び出し・手戻り発話）を検出すると仮エントリを追記し、
  Claude が「原因と改善案」を記入して完成させる
- **手動追記**: ユーザー・Claude が下の書式で直接追記してよい
  （「今のやり取りは非効率だったのでメモして」等）
- **反映**: `/retro` スキルがエントリを読み、スキル・プロファイル・成果物の改善へ
  反映する。**反映済み・対応不要のエントリは削除する**（open_specs と同じ運用。履歴は Git が持つ）

## エントリ書式

    ## YYYY-MM-DD HH:MM | session xxxxxxxx | 自動検出 or 手動
    - シグナル: <検出内容 or 状況の一言>
    - ターン概要: ツールN回・エラーN回・拒否N回。開始:「<プロンプト冒頭60字>」
    - 原因と改善案: <原因 + どのスキル/プロファイル/成果物をどう直すか（1〜2行）>

---

## 2026-08-09 09:42 | session 3543ebbc | 自動検出
- シグナル: long-turn(calls=71)
- ターン概要: ツール71回・エラー1回・拒否0回。開始:「<command-message>retro</command-message>」
- 原因と改善案: **`/retro` でメモ16件を消化し10ファイル改稿 + チェッカー改修 + テスト9件追加 + 統合まで完走した正当な分量**（long-turn は誤検出。errors×1 は測定スクリプトのラベル解析バグで、必要な数値は取得済み）。ただし**実在の欠陥が1件**: 効率メモの**エントリ削除を worktree 内で行ったため、`merge=union`（`.gitattributes`）が両側の行を残し、統合後に全エントリが復活した**。union は追加行の自動統合が目的で**削除を伝播しない**。→ ① [.claude/project/retro.md](../../.claude/project/retro.md) の「エントリの寿命」行へ「**削除は main で行う**（worktree 側の削除は `merge=union` で復活する）」を追記 ② [worktree_guide.md](../process/worktree_guide.md) §3 の「`merge=union` の注意」へ「**削除は伝播しない**。行を消す作業（効率メモの消化・changelog の重複畳み）は統合後に main で行う」を追記。同じ罠は `carryover_notes.md` の行削除にもかかる。

## 2026-08-09 10:17 | session b2f7200f | 自動検出
- シグナル: long-turn(calls=141)
- ターン概要: ツール141回・エラー2回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **2R-0 の6項目を実機検証（雛形生成・Tomcat 2版へ配備・逆アセンブル走査・`mvn verify`）し、文書反映と統合まで完走した正当な分量**（long-turn は概ね誤検出）。ただし**実在の非効率が2件**: ① Servlet API 差分の使い捨てスクリプトを**4回書き直した**（サブシェル内で相対 jar パスが壊れる → `unzip` の glob `jakarta/*.class` が入れ子に当たらない → awk が `;` で終わる `descriptor:` 行を飲む → 引数約1,300件でコマンドラインが溢れ javap が黙って失敗）。いずれも出力が「0件」「51件」と**一見もっともらしく**、最終値しか見ていなかったため1段ずつしか露見しなかった → [.claude/project/basic-design.md](../../.claude/project/basic-design.md) §4 へ「**使い捨ての検証スクリプトは1件で検算してから全量へ回し、中間件数が期待の桁と合うかを必ず確かめる**（0件や極端に少ない件数は"異常なし"ではなく解析失敗を疑う）」を追記 ② worktree へ移った後に `ctx_batch_execute` を**プロジェクトルートの cwd で走らせ1バッチ丸ごと無駄にした**（全コマンドが `No such file or directory`）→ 同 §4 へ「**worktree 作業中は context-mode 系ツールへ `cwd` を明示する**（既定はプロジェクトルートで worktree を指さない）」を併記。

## 2026-08-09 10:43 | session f3ab426d | 自動検出
- シグナル: same-read(tech_structure.md×2) / long-turn(calls=101)
- ターン概要: ツール101回・エラー1回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **`--sections` で「H2 が上限超過（`!`）＋ ファイル残量 329字」と測ったうえで、分割ではなく圧縮を選んで書き始めた**のが原因。フロントのツリーを4回圧縮しても 3字超過にしかならず、結局ユーザー指摘で分割し直したため `tech_structure.md` を再Readして書き戻す往復が発生した（`tech_operations.md` も同じ道をたどり、残り71字になってから分割した）→ [.claude/project/doc-size.md](../../.claude/project/doc-size.md) §3.1 の判断 #0 へ「**`--sections` が `!`（H2 超過）を出しているファイルは、残量が追記予定字数を下回るなら圧縮ではなく分割を既定にする**（圧縮で捻出できるのは数百字で、H2 超過は解消しない）」を追記し、[basic-design.md](../../.claude/project/basic-design.md) §1 の「執筆前の分量見積もり」へ同じ判断への導線を張る。long-turn は分割2件 + 参照29箇所の付け替え + 検証を1ターンで完走した分量で誤検出。

## 2026-08-09 11:10 | session 72f49117 | 自動検出
- シグナル: long-turn(calls=149)
- ターン概要: ツール149回・エラー1回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **雛形生成から war 生成・文書反映・統合まで完走した正当な分量**だが、**実在の非効率が2件**: ① worktree セッションの分離ガードが `&&`・ループ・リダイレクトを含む Bash を「複雑すぎる」と拒否するため、退避と配置を**1コマンド1呼び出しへばらして約20回**費やした（`git mv` 8 + `cp` 13）。移送はファイル単位ではなく**ディレクトリ単位**にまとめれば数回で済んだ → [.claude/project/dev.md](../../.claude/project/dev.md) §5 へ「**worktree 内での一括移送（`git mv`・`cp`）はディレクトリ単位でまとめる**。分離ガードが複合コマンドを拒否するため、ファイル単位に展開すると呼び出し回数が跳ねる」を追記 ② **Boot 依存の有無を調べる前に main コード42件を丸ごと退避**し、`check_schema_triple.py` が7件落ちてから調べ直した（実際に Boot へ依存するのは6件だけで、Entity 7件は非JDK import ゼロ）→ 同 §4 の観点表へ「**既存コードを退避・削除する前に、依存の実体を import で分類する**（"Boot 前提"という前提の粒度を鵜呑みにしない）。常設チェッカーは退避の前後で走らせ、緑→赤の変化で巻き込みを検出する」を追記。

## 2026-08-09 11:21 | session 49abca2c | 自動検出
- シグナル: long-turn(calls=43)
- ターン概要: ツール43回・エラー1回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **引き継ぎ確認 → 鮮度検証 → 仕様4件の該当節読み → 雛形設定10件の把握 → 計画提示まで到達した分量**で long-turn 自体は概ね誤検出。ただし**実在の非効率が2件**: ① 外部ライブラリの実在確認（Jackson 3/2 の同居・`JacksonJsonHttpMessageConverter` のコンストラクタ・`DispatcherServlet` の既定・logback `JsonEncoder`・Flyway 版）を `javap`/`unzip` で**5回の往復に分けて**投げた（前の答えが次の問いを生む形で芋づる式に増えた）→ [.claude/project/dev.md](../../.claude/project/dev.md) §5 の注意3（版調査はまとめて1回）へ「**API の実在確認（クラス名・コンストラクタ・既定値の有無）も同じ扱い**。着手前に確認項目を列挙し `javap`/`unzip` を1バッチで出す」を併記 ② `mvn dependency:tree` に `-q` を付けてツリー出力ごと消し、1回空振りした → 同§5へ「`dependency:tree` は `-q` を付けない（ツリーは INFO で出るため消える）」を追記。
