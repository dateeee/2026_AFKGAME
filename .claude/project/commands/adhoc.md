# AFK GAME — 使い捨て調査の作法

> [commands.md](../commands.md) の子ファイル（索引側が正）。担当: §4。

## 4. 使い捨て調査の作法

その場限りの検証スクリプト・外部問い合わせで空振りを繰り返さないための作法。

| # | 作法 |
|---|------|
| 1 | **1件で検算してから全量へ回す**。中間件数が期待の桁と合うか必ず確かめる。**`0件`・極端に少ない件数は「異常なし」ではなく解析失敗を疑う**（`unzip` の glob が入れ子に当たらない・`awk` が想定外の行を飲む・引数が多すぎてコマンドラインが溢れる等は、もっともらしい件数を返して黙って失敗する） |
| 2 | **worktree 作業中は context-mode 系ツールへ `cwd` を明示する**。既定はプロジェクトルートで worktree を指さないため、1バッチ丸ごと `No such file or directory` になる |
| 3 | **生成と読み取りは同じ `language` で完結させる**（`ctx_execute` の shell が `/tmp` へ書いたファイルは python の実行環境から見えない） |
| 4 | **`Grep` の `glob` に否定（`!...`）は使えない**。除外を伴う横断調査は `ctx_execute` 内でフィルタする |
| 5 | **API の実在確認（クラス名・コンストラクタ・既定値の有無）は着手前に項目を列挙し、`javap` / `unzip` を1バッチで出す**。前の答えが次の問いを生む形で投げると往復が芋づる式に増える |
| 6 | **Maven Central の版は `https://repo1.maven.org/maven2/<groupId のスラッシュ表記>/<artifactId>/maven-metadata.xml` の `<release>`** を見る（`search.maven.org` の solrsearch API は遅く落ちやすい）。`mvn dependency:tree` に **`-q` を付けない**（ツリーは INFO 出力なので消える） |
| 7 | **外部問い合わせ（`curl`）とビルド（`mvn`・`docker --version` 等の版確認）は最初から `ctx_execute` で叩く**。Bash で出すとフックが context-mode へリダイレクトし、1往復まるごと無駄になる（3セッション連続で踏んだ） |
| 8 | **`Edit` する予定のファイルは必ず `Read` で読む**。`cat`・`ctx_execute_file` で読んでも Edit は「File has not been read yet」で通らない（`profile.md` §6 規律4「Read の全文読みは Edit 前提のときのみ」の裏返し） |
| 9 | **PowerShell ツールへは `-D` 付き引数と複数行スクリプトを直接渡さない**。`-Dxxx=yyy` は `"` で括る（括らないと別トークンとして解釈され `Unknown lifecycle phase`）、`python -c` は使わずスクラッチパッドへ `.py` を書いて実行する（here-string で `"` が崩れる）、native exe のパイプに `Select-Object -First` を付けない（早期終了で exit 255） |
| 10 | **使い捨て Java を Maven のクラスパスで動かす**: `mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=cp.txt` → `-cp` / `-d` に渡すパスは `cygpath -w` で Windows 形式へ直す（Git Bash の `/c/...` を `javac` / `java` は解釈できない）。クラスパスの区切りは `;` |
