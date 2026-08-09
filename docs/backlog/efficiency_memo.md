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

（現在エントリなし。直近の消化は 2026-08-09 の `/retro`。反映内容は [changelog.md](../changelog.md) 2026-08-09）
（現在エントリなし）

## 2026-08-08 22:00 | session c431fb65 | 自動検出
- シグナル: same-command('python "C:\Users\tubas\AppData'×4) / long-turn(calls=90)
- ターン概要: ツール90回・エラー2回・拒否0回。開始:「<command-message>retro</command-message>」
- 原因と改善案: **大半は誤検出**（`/retro` でメモ11件 + 統合中に届いた2件を消化し、10ファイル改稿 + フック改修 + テスト追加 + 競合解消2件を1ターンで行った正当な分量。same-command は残量測定スクリプトの再実行4回で、profile.md §7 規約7 の「書く前に実測」を守るための編集案ごとの測り直し＝規約どおりの動作。エラー2件はいずれも並行セッション由来の統合停止で、今回追記した `worktree_guide.md` §5.3 手順3 で解消済み）。ただし**測定スクリプトの再実行4回は `doc-size.md` §3.1-1「塊ごとの字数を先に一括計測する（試すたびに測り直さない）」の対象**であり、本ターンで新設したその規約自体が是正になっている（profile.md §7 規約7 からも参照済み）。追加の是正は不要。

## 2026-08-08 22:01 | session ebeebd52 | 自動検出
- シグナル: same-read(next_session.md×2) / long-turn(calls=94)
- ターン概要: ツール94回・エラー2回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **same-read は誤検出**（1回目は `/next` のタスク特定時、2回目は統合後の更新前。その間に別セッションが `8dcf490` で本ファイルを全面改稿しており、同じ内容の再読ではない。むしろ再読しなければ消えた行を書き戻していた）。**long-turn は実在の手戻り1件**: `tech_forge.md` を1ファイルで書き切ってから区分C上限を約4,000字超過していると分かり、索引+操作別3ファイルへ書き直した。`profile.md` §7 規約7 は「既存ファイルへ**追記**する前に残量を測る」しか言っておらず、**新規ファイルを書く前に完成後の字数を見積もる**規約が無い。→ `detail-design.md` §4 へ「分岐一覧が合計50件を超える見込み、または対象APIが3つ以上なら、書き始める前に索引 + 操作別ファイルへ割る」を追記して是正する（次にこの工程へ入るセッションで反映）。

## 2026-08-08 22:13 | session c0907b10 | 自動検出
- シグナル: long-turn(calls=110)
- ターン概要: ツール110回・エラー0回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **long-turn 自体は誤検出**（`/next` → `test-list`（Red）→ `dev`（Green）→ 統合 → 引き継ぎ更新の3工程を1ターンで完走した正当な分量。エラー0）。ただし**Java テストの実行コマンドの試行錯誤で4往復**した: ①`-Dtest=<クラス>` は対象テストの無いモジュールで surefire が落ちるため `-Dsurefire.failIfNoSpecifiedTests=false` が要る ②出力が CP932 で `grep` が "Binary file matches" を返す（ファイルへ落として `iconv -f CP932 -t UTF-8`）③別モジュールのテストだけを回すには先に親 POM を `-N install` してからでないと依存解決に失敗する。→ `commands.md` のバックエンド節へこの3点を「モジュールを絞ってテストする」レシピとして追記する（`dev.md` §5 の注意表からも参照）。

## 2026-08-08 22:21 | session 5fb9f14d | 自動検出
- シグナル: long-turn(calls=86)
- ターン概要: ツール86回・エラー0回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: **long-turn 自体は誤検出**（依頼の前提が未達と判明 → 方針をユーザーへ確認 → 調査で当初計画が不可能と判明して設計変更 → 実装+テスト34件 → ドキュメント6件 → 統合 → 引き継ぎ更新、を1ターンで完走した正当な分量。エラー0・拒否0）。ただし**`carryover_notes.md` を着手前に読まなかった**ため、同ファイルが既に記録していた「`java_migration.md` は残り12字。次の追記は圧縮では吸収できない」を知らずに編集へ入り、残量測定→圧縮案の検討→再測定で3往復した。`next_session.md` §2 末尾と `next.md` §1 は「着手前にそちらも見る」と書いているが、**`/next` SKILL §0「最初に読む」表は profile.md と next.md の2件しか挙げておらず、carryover_notes.md が手順に入っていない**。→ `.claude/skills/next/SKILL.md` §0 の表へ3行目「`docs/backlog/carryover_notes.md`（プロファイル §1 が指す申し送り。既知の制約・環境・前セッションの発見）」を追加し、§1 の「この時点では他の仕様書・コードを読まない」の例外として明記する。

## 2026-08-08 22:31 | session ff3f9a45 | 自動検出
- シグナル: long-turn(calls=74)
- ターン概要: ツール74回・エラー0回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **long-turn の大半は誤検出**（前回レビューが 2026-08-04 の Python 全量で、以後の Java 実装51ファイル・約3,300行が丸ごと未レビュー。差分レビューでも読む量が全量相当になる正当な分量。エラー0）。ただし**実在の無駄が1件**: `review_prep.py` が返した差分175件のうち Python 61件が `A`（追加）扱いになっている理由を3往復かけて追ったが、**対象外と決めたファイルの差分ステータスであり結論に一切影響しなかった**。`review-procedure.md` §3 は「CHANGED を起点に対象を確定する」としか言っておらず、**依頼スコープが CHANGED より狭いときに残りをどう捌くか**（＝対象外と決めた時点でそれ以上調べない、レポートに除外理由だけ書く）が手順に無い。→ `.claude/references/review-procedure.md` §3 へ「依頼で対象が絞られている場合、スコープ外と判断したファイルは**差分の中身を調べずに**除外し、除外範囲と理由をレポート冒頭に1段落で記す」を追記する。

## 2026-08-08 23:0x | session 555f538d | 手動追記
- シグナル: なし（`/next` → `/dev` セグメント1完走）
- ターン概要: `/next` でタスク特定 → ISSUE-601・605 の決着をユーザーへ確認 → worktree → 6件適用（テスト131件 PASS・branch 100%）→ 統合 → 引き継ぎ更新。
- 原因と改善案: **実在の無駄が1件**: `-pl afkgame-web` を `-am` なしで回し、Maven が `~/.m2` の**古い成果物**を解決したため「`APP_ENV` の既定値を外しても起動が止まらない」という**誤った結論**を1回出した（再実行で覆した）。ビルド1回ぶんの空振りに加え、実測を根拠にしたはずの判断が誤りうる点が問題。`dev.md` §5 の注意表は版の推測・テスト件数の数え方は挙げているが、**モジュールを絞った実行が古い成果物を拾う**件が無い。→ `commands.md` のバックエンド節（前セッションが「モジュールを絞ってテストする」レシピの追記を提案済み）へ「`-pl <module>` には必ず `-am` を付ける。付けないと変更前の成果物を解決し、変更が効いていない結果を実測値として読んでしまう」を同じレシピの4点目として足す。
- あわせて**レビュー本文の事実誤り1件**を実測で検出: ISSUE-601 が「既存テストは `@SpringBootTest` が明示的にプロファイルを与えているため影響を受けない」としていたが、`@ActiveProfiles` はリポジトリ全体で0件だった。→ `review-procedure.md` へ「修正案の『既存テストへの影響なし』は、**その根拠（アノテーション・設定の実在）を grep で確かめてから書く**」を足す候補（次の retro で判断）。

## 2026-08-08 23:11 | session 555f538d | 自動検出
- シグナル: errors×5 / long-turn(calls=184)
- ターン概要: ツール184回・エラー5回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **long-turn は誤検出**（`/next` → 決着確認 → worktree → 6件を TDD で適用 → `mvn verify` → 統合 → 引き継ぎ更新まで1ターンで完走。実質的な無駄は1つ上のエントリの `-am` 件のみ）。**errors×5 の内訳は3種**: ①**worktree 分離セッションで複合 Bash が3回拒否された**（`&&` 連結 + `>` リダイレクト + 複数パスを1コマンドに詰めると "too complex to verify that it stays inside the worktree"）②`python -c` に日本語を直接書いて CP932 で壊れ SyntaxError（`profile.md` §7 規約7 の `len()` 実測をワンライナーでやろうとしたため）③`-Dtest=<クラス>` の `failIfNoSpecifiedTests` 未指定（前セッションの効率メモが既に指摘済みで、`commands.md` へのレシピ追記が**未反映のまま再発**）。→ ①は `worktree_guide.md` §5 へ「worktree 内の Bash は1コマンド1目的に割る（`&&` とリダイレクトの併用を避ける）」、②は `profile.md` §7 規約7 へ「実測用スクリプトはスクラッチパッドへ書いて `python <file>` で実行する（`python -c` に日本語を埋めない）」を追記する。③は**前セッション提案の `commands.md` レシピを次の `retro` で確実に反映する**（2セッション連続で同じ往復をしている）。

## 2026-08-08 23:27 | session d27c1b8c | 自動検出
- シグナル: long-turn(calls=55)
- ターン概要: ツール55回・エラー1回・拒否0回。開始:「<ide_selection>The user selected the lines 49 to 49 from c:\」
- 原因と改善案: **long-turn は誤検出**（Terasoluna 単体テストガイドライン6ページの精読 → テスト28ファイルの実装監査 → 改善方針の確定 → pom/プロファイル/テストの是正 → `mvn verify` で分離動作の実測 → 統合まで1ターンで完走）。ただし**`mvn verify` の空振りが2回**: ①failsafe を新規に pom へ足したのに `-o`（オフライン）のまま回してプラグイン解決に失敗 ②spring-boot-starter-parent が failsafe の実行（id: `default`）を pluginManagement に持つことを知らず自前 `<executions>` を足し、結合テストが二重実行になった。→ ②は `backend/pom.xml` のコメントへ記録済み。①は `commands.md` のバックエンド節（直近2セッションが「モジュールを絞ってテストする」レシピの追記を提案済み）へ「pom へプラグインを新規追加した直後の初回だけ `-o` を外す」を同レシピの5点目として足す。

## 2026-08-08 23:41 | session ab970af4 | 自動検出
- シグナル: long-turn(calls=43)
- ターン概要: ツール43回・エラー1回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: **残量WARN（残り16字）のファイルへ追記しようとして、分割を検討する前に圧縮から入った**。§9 の表を散文へ潰す・行を統合するなど約350字ぶんの圧縮に7〜8ツール分を費やした直後、ユーザーから「最低4分割すべき」の指摘が入り、その圧縮作業はほぼ無駄になった。→ `.claude/project/doc-size.md`（と `profile.md` §7）へ「**上限90%超（残量WARN）のファイルへ新規の節・行を足す必要が出たら、圧縮の前に分割を提案する**。圧縮で捻出するのは既存記述の改稿に限る」を追記する。long-turn 自体は誤検出（索引化 + 4分冊の新規作成 + 参照元8ファイルの追随 + 2種の検証まで1ターンで完走）。

## 2026-08-08 23:45 | session 6fda05da | 自動検出
- シグナル: same-command('cd "C:/GIT/2026_AFKGAME" && py'×3) / errors×5 / long-turn(calls=66)
- ターン概要: ツール66回・エラー5回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: **統合中にユーザーが main で同じファイル（コーディング規約）を編集し続けたため、`worktree.py merge` が5回連続で弾かれた**（未コミット拒否3回 → 競合2回）。毎回「status 確認 → 相手の作業をコミット → 再実行 → 競合解消」を回し、errors×5 と再測定コマンドの重複はほぼこれ。さらに**相手が同ファイルを索引+4分冊へ分割していたため、字数相殺のために worktree 側で作り込んだ圧縮（§1・§7・§10）が全部無駄になり、取り下げ→別セクションへ付け替え→再度取り下げと3往復した**。→ ① [worktree_guide.md](../process/worktree_guide.md) §5.3 と [.claude/project/next.md](../../.claude/project/next.md) §4 へ「**統合を始める前に main の `git status --short` を見る。dirty なら統合に入らずユーザーへ確認する**（着手時に clean でも、作業中に main が動くことがある）」を追記する。② 同じ2ファイルへ「**自分の変更と同じファイルを main 側が触っている間は、圧縮・再配置など可逆でない調整を worktree 側で作り込まない**。本体の変更（今回なら SecureRandom の例外1行）だけを入れて統合し、字数調整は統合後に測り直す」を足す。前セッションの「圧縮より分割を先に」（23:41 のエントリ）と同じ根で、**残量が逼迫したファイルは触る前に main 側の進行中作業を確認する**のが共通の対策。long-turn 自体は誤検出（指摘5件の適用 → 検証 → 5回の統合リトライ → 引き継ぎ更新まで1ターンで完走）。

## 2026-08-09 00:18 | session fc22a96c | 自動検出
- シグナル: same-command('cd "C:/GIT/2026_AFKGAME.worktr'×3) / errors×4 / long-turn(calls=209)
- ターン概要: ツール209回・エラー4回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: **23:41・23:45 の改善案が未適用のまま同じ壁に3回目** — 統合直前に main が dirty（別セッションが STEP 2R のドキュメント改訂中）で merge が弾かれ、同じ7ファイルを双方が編集していて手動競合が確定した。→ **`retro` を回して 23:45 の①②を反映する**のが第一で、あわせて `worktree_guide.md` §5.2 へ「ホットスポット（`java_migration.md`・`known_issues.md`・`README.md`）を worktree 側で触ると決めた時点で main の進行を確認する」を追記。same-command×3 は `EnterWorktree` 後の cwd が worktree なのに `cd <worktree> &&` を前置していたもの（複合コマンドは分離ガードで弾かれる）→ §5.4 へ1行。errors×4 の2件と long-turn は誤検出。
- 本ファイル自体が上限超過（8,7xx字 / 8,000字）。未消化3エントリが積んだ結果で、**`retro` の実行が是正そのもの**。

## 2026-08-09 00:24 | session c8b8ca48 | 自動検出
- シグナル: same-read(java_migration.md×2) / long-turn(calls=59)
- ターン概要: ツール59回・エラー0回・拒否0回。開始:「<ide_opened_file>The user opened the file c:\GIT\2026_AFKGAM」
- 原因と改善案: **`carryover_notes.md` が既に記録していた「`java_migration.md` は残り12字・次の追記は圧縮では吸収できない」を読まずに追記へ入った**ため、10編集を入れてから超過（11,532字）に気づき、分割のために同ファイルを再Readした（same-read の実体）。00:11 の同種指摘への対策は `/next` SKILL §0 へ「着手前に carryover_notes.md を読む」を足すことだったが、**本セッションは質問から始まって編集タスクへ変わった**ため `/next` を経由せず効かなかった。→ スキル非経由でも効く場所として [.claude/project/doc-size.md](../../.claude/project/doc-size.md) §3.1 の判断表へ #0「**追記前に**対象の残量（`--sections`）と `carryover_notes.md` の該当行を確認する。残量が上限の5%未満なら追記せず先に分割する」を追加する。long-turn(calls=59) は誤検出（ガイドライン2ページの精読 → リポジトリ実態調査 → 方針3案の提示 → ユーザー決定2件 → 計画改訂 → 分割 → 参照元11箇所の張り直し → 検証 → コミットを1ターンで完走。エラー0・拒否0）。ただし先に分割していれば約10コールは不要だった。

## 2026-08-09 00:41 | session 41a85d7f | 自動検出
- シグナル: long-turn(calls=78)
- ターン概要: ツール78回・エラー0回・拒否0回。開始:「<command-message>next</command-message>」
- 原因と改善案: **統合〜引き継ぎ更新までを1ターンで完走した正当な分量**（鮮度確認 → merge → 競合3ファイルの解消 → 検証 → 統合 → 統合後3タスク → 引き継ぎ書き換え → コミット2件。エラー0）だが、**引き継ぎを書き換える段になって `next_session.md` が既に上限超過（9,164字）だと気づいた**のが無駄で、`carryover_notes.md` への移設と同ファイルの H2 分割・節番号繰り下げが後追いになった（約8コール）。`check_doc_size.py` の台帳には「統合直後に再測定し、超えるなら冒頭の『決まった流儀』を carryover_notes.md へ移す」と**手順まで書いてあったのに読んでいない**（00:11・00:24 と同じ「先行セッションの申し送りを読まずに編集へ入る」パターンの3回目）。→ [.claude/project/next.md](../../.claude/project/next.md) §4 の冒頭へ「**書き換える前に** `check_doc_size.py --sections docs/backlog/next_session.md` を実行し、台帳（`KNOWN_OVERSIZED`）に本ファイルの行があれば**その是正方針をそのまま実施してから**書く」を追加する。
- 別件: Python 残骸の物理削除（`backend/{app,tests,alembic,.pytest_cache,...}`・`__pycache__`）が Bash・PowerShell とも権限分類器に拒否され**未実施**。再帰削除が必要な後片付けは、引き継ぎの「統合後」へ書くだけでなく**承認が要る前提でユーザーへ提示する**手順にしておくと1往復で済む。

## 2026-08-09 08:33 | session 43461a8b | 自動検出
- シグナル: long-turn(calls=85)
- ターン概要: ツール85回・エラー2回・拒否0回。開始:「<ide_selection>The user selected the lines 37 to 38 from c:\」
- 原因と改善案: **H2 の残量を数えずに表へ行を足し、`basis.md` §3 の圧縮を3周した**（2,319 → 2,070 → 1,984 → 1,854字。上限2,000）。着手前に `--sections` で測ってはいたが、見たのは**ファイル残量だけで H2 残量 × 追記予定行数を見積もっていない**。→ [.claude/project/doc-size.md](../../.claude/project/doc-size.md) §3.1 の #0 へ「表へ行を足すときは**該当 H2 の残量 ÷ 既存1行の字数**で入る行数を先に出す。入らないなら圧縮ではなく分割を提案する」を足す。long-turn 自体は誤検出（ガイドライン6章の精読 → 分冊3件との突き合わせ → 逸脱9件の確定 → 4ファイル改訂 + 所有権の移管 → 検証 → 統合まで1ターンで完走）。
- 別件（errors×2、いずれも同種のツール取り違え）: ①Bash ツールへ PowerShell の here-string（`@'...'@`）を書き、コミット本文に `@` が混入して amend ②`worktree.py merge` を worktree の中から実行してパス解決に失敗（`ExitWorktree` して main から実行し直し）。→ ②は [worktree_guide.md](../process/worktree_guide.md) §5 へ「**merge は main で実行する**（worktree 内からは相対パス解決に失敗する）」を1行。①は Stop フックの指示文が既に警告しており、追加の対策は不要。

## 2026-08-09 08:41 | session d25625d5 | 自動検出
- シグナル: long-turn(calls=58)
- ターン概要: ツール58回・エラー0回・拒否0回。開始:「<ide_selection>The user selected the lines 37 to 38 from c:\」
- 原因と改善案: **前ターンで作った「逸脱の通し番号 #1〜#17」を6ファイルへ張った構造が、削除時に18か所の追随修正を強いた**（横断一覧＝二重管理のツケ。本ターンで `basis.md` 原則 #5 へ「差分の正は各分冊が持ち、横断の一覧をどこにも二重に持たない」を明記して再発は塞いだ）。→ [.claude/project/basic-design.md](../../.claude/project/basic-design.md) の規約改訂手順へ「**索引に一覧表を新設するときは、番号で参照させず「どの分冊が正か」だけを持たせる**（番号は分冊内の連番にする）」を1行足す。long-turn 自体は概ね妥当（8ファイル18か所の追随 + 2チェッカー + 変更履歴）。
- 別件: `Grep` が長行を `[Omitted long matching line]` に落とすため、分冊の該当行を掴むのに `Read` を小刻みに7回叩いた。→ 横断置換の下調べでは `Grep` の結果を当てにせず、**対象ファイルを1回ずつ全文 Read する**（分冊は上限8,000字で全文でも安い）。

## 2026-08-09 09:15 | session 25119a5c | 自動検出
- シグナル: long-turn(calls=225)
- ターン概要: ツール225回・エラー0回・拒否0回。開始:「<ide_selection>The user selected the lines 38 to 39 from c:\」
- 原因と改善案: 依頼が規約改訂＝編集確定だったのに main で `layering.md`・`domain.md`・`basis.md` を先に読み、worktree 移動後に Edit 用の再 Read が発生した（worktree_guide §5.2 #4 が禁じる二重読み）。→ [worktree_guide.md](../process/worktree_guide.md) §5.1 に「依頼が仕様・規約・コードの**変更**なら調査の前に worktree を作る（main のまま読んでよいのは質問・レビューだけ）」を明記する。call 数自体は規約25ファイルの追随 + Mapper 8件→Repository 5件の実装・テスト改修という作業量によるもので誤検出。

## 2026-08-09 09:30 | session 951b38b5 | 自動検出
- シグナル: long-turn(calls=67)
- ターン概要: ツール67回・エラー0回・拒否0回。開始:「<ide_selection>The user selected the lines 38 to 39 from c:\」
- 原因と改善案: **直前エントリ（09:15）とまったく同じ原因の再発** — 規約改定＝編集確定の依頼なのに main で `common.md`・`basis.md`・`web.md`・`domain_service.md` を先に読み、worktree 移動後に Edit 用の再 Read を4ファイル分やり直した。前回提案した [worktree_guide.md](../process/worktree_guide.md) §5.1 への1行（「依頼が仕様・規約・コードの**変更**なら調査の前に worktree を作る」）が**未適用のまま**なので、次の `retro` で最優先に反映する。call 数自体は誤検出（新分冊の作成 + 6ファイルの追随 + 派生2件 + 2チェッカー + 統合を1ターンで完走）。
