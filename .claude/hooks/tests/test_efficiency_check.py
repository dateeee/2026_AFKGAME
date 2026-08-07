"""efficiency_check.py の回帰テスト。

実行: `python -m pytest .claude/hooks/tests -q`（リポジトリルートから）

`MEMO_PATH` / `ROOT` を `tmp_path` へ差し替え、実際の効率メモを書き換えない。
検出のしきい値は定数なので、**しきい値ちょうど / 1つ手前**を対で置き、
「何も検出しない実装」が通らないようにする。
"""

import io
import json

import pytest

import efficiency_check as mod


# ── トランスクリプトの部品 ───────────────────────────────────

def user(text):
    return {"type": "user", "message": {"content": text}}


def tool_use(name, tool_id="t1", **inp):
    return {"type": "assistant", "message": {"content": [
        {"type": "tool_use", "id": tool_id, "name": name, "input": inp}]}}


def tool_result(tool_id="t1", content="boom", is_error=True):
    return {"type": "user", "message": {"content": [
        {"type": "tool_result", "tool_use_id": tool_id,
         "is_error": is_error, "content": content}]}}


def reads(path, count, tool_id="t"):
    return [tool_use("Read", f"{tool_id}{i}", file_path=path) for i in range(count)]


def commands(cmd, count, tool_id="c"):
    return [tool_use("Bash", f"{tool_id}{i}", command=cmd) for i in range(count)]


@pytest.fixture
def memo(tmp_path, monkeypatch):
    """`MEMO_PATH` / `ROOT` を差し替えた（まだ存在しない）メモのパスを返す。"""
    path = tmp_path / "docs" / "reviews" / "efficiency_memo.md"
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    monkeypatch.setattr(mod, "MEMO_PATH", path)
    return path


def write_transcript(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(json.dumps(e, ensure_ascii=False) for e in entries),
                    encoding="utf-8")
    return path


def signals(entries):
    return mod.analyze(entries)["signals"]


# ── to_local_path / iter_entries ─────────────────────────────

def test_to_local_path_converts_posix_drive_form():
    assert mod.to_local_path("/c/Users/x/a.jsonl").as_posix() == "c:/Users/x/a.jsonl"


def test_iter_entries_skips_blank_and_broken_lines(tmp_path):
    path = tmp_path / "t.jsonl"
    path.write_text('{"type": "user"}\n\n{壊れた\n"文字列"\n', encoding="utf-8")
    assert [e["type"] for e in mod.iter_entries(path)] == ["user"]


# ── text_of ──────────────────────────────────────────────────

def test_text_of_reads_string_and_text_blocks():
    assert mod.text_of("依頼") == "依頼"
    assert mod.text_of([{"type": "text", "text": "一"}, {"type": "text", "text": "二"}]) == "一\n二"


def test_text_of_returns_empty_for_other_shapes():
    assert mod.text_of({"type": "text"}) == ""


# ── find_boundary ────────────────────────────────────────────

def test_find_boundary_returns_last_real_user_input():
    entries = [user("一件目"), tool_use("Read", file_path="a.md"), user("二件目")]
    assert mod.find_boundary(entries) == 2


def test_find_boundary_ignores_tool_result_entries():
    entries = [user("依頼"), tool_use("Read", file_path="a.md"), tool_result(is_error=False)]
    assert mod.find_boundary(entries) == 0


@pytest.mark.parametrize("flag", ["isSidechain", "isMeta"])
def test_find_boundary_ignores_sidechain_and_meta(flag):
    entries = [user("依頼"), user("サブエージェント") | {flag: True}]
    assert mod.find_boundary(entries) == 0


def test_find_boundary_returns_none_without_user_input():
    assert mod.find_boundary([tool_use("Read", file_path="a.md")]) is None


# ── analyze: ターンの切り出し ────────────────────────────────

def test_analyze_returns_none_without_turn():
    assert mod.analyze([tool_use("Read", file_path="a.md")]) is None


def test_analyze_counts_only_calls_after_boundary():
    entries = [user("前のターン"), *reads("a.md", 5, "old"), user("今のターン"),
               *reads("b.md", 1, "new")]
    assert mod.analyze(entries)["calls"] == 1


def test_analyze_reports_no_signal_for_efficient_turn():
    entries = [user("依頼"), tool_use("Read", "t1", file_path="a.md")]
    assert signals(entries) == []


# ── analyze: same-read ───────────────────────────────────────

def test_analyze_detects_repeated_read_at_threshold():
    entries = [user("依頼"), *reads("a.md", mod.SAME_READ_MIN)]
    assert any(s.startswith("same-read") for s in signals(entries))


def test_analyze_allows_read_below_threshold():
    entries = [user("依頼"), *reads("a.md", mod.SAME_READ_MIN - 1)]
    assert signals(entries) == []


def test_analyze_normalizes_path_case_and_separator():
    """`C:\\x\\A.md` と `c:/x/a.md` は同一 Read とみなす。"""
    entries = [user("依頼"),
               tool_use("Read", "t1", file_path=r"C:\x\A.md"),
               tool_use("Read", "t2", file_path="c:/x/a.md")]
    assert any(s.startswith("same-read") for s in signals(entries))


def test_analyze_treats_different_ranges_as_distinct_reads():
    entries = [user("依頼"),
               tool_use("Read", "t1", file_path="a.md", offset=1, limit=10),
               tool_use("Read", "t2", file_path="a.md", offset=50, limit=10)]
    assert signals(entries) == []


def test_analyze_shows_file_name_and_count_in_signal():
    entries = [user("依頼"), *reads("docs/a.md", 3)]
    assert "same-read(a.md×3)" in signals(entries)


# ── analyze: same-command ────────────────────────────────────

def test_analyze_detects_repeated_command_at_threshold():
    entries = [user("依頼"), *commands("npm run build", mod.SAME_CMD_MIN)]
    assert any(s.startswith("same-command") for s in signals(entries))


def test_analyze_allows_command_below_threshold():
    entries = [user("依頼"), *commands("npm run build", mod.SAME_CMD_MIN - 1)]
    assert signals(entries) == []


def test_analyze_ignores_empty_command():
    entries = [user("依頼"), *commands("", 5)]
    assert signals(entries) == []


# ── analyze: errors / denials ────────────────────────────────

def error_turn(count, cmd="git push"):
    entries = [user("依頼")]
    for i in range(count):
        entries.append(tool_use("Bash", f"e{i}", command=cmd))
        entries.append(tool_result(f"e{i}", "fatal: boom"))
    return entries


def test_analyze_detects_tool_errors_at_threshold():
    assert any(s.startswith("errors×") for s in signals(error_turn(mod.ERRORS_MIN)))


def test_analyze_allows_errors_below_threshold():
    assert signals(error_turn(mod.ERRORS_MIN - 1)) == []


def test_analyze_excludes_expected_nonzero_commands():
    """検査・リンタ・テストの「違反あり exit 1」は試行錯誤ではない。"""
    entries = error_turn(mod.ERRORS_MIN + 2, cmd="python scripts/check_docs.py")
    assert mod.analyze(entries)["errors"] == 0


def test_analyze_counts_denials_separately_from_errors():
    entries = [user("依頼")]
    for i in range(mod.DENIALS_MIN):
        entries.append(tool_use("Bash", f"d{i}", command="rm -rf x"))
        entries.append(tool_result(f"d{i}", "The user doesn't want to proceed"))
    stats = mod.analyze(entries)
    assert stats["errors"] == 0 and f"denials×{mod.DENIALS_MIN}" in stats["signals"]


def test_analyze_ignores_successful_tool_results():
    entries = [user("依頼"), tool_use("Bash", "t1", command="ls"),
               tool_result("t1", "ok", is_error=False)]
    assert mod.analyze(entries)["errors"] == 0


# ── analyze: long-turn ───────────────────────────────────────

def test_analyze_detects_long_turn_at_threshold():
    entries = [user("依頼"), *[tool_use("Grep", f"g{i}", pattern=str(i))
                              for i in range(mod.CALLS_MIN)]]
    assert f"long-turn(calls={mod.CALLS_MIN})" in signals(entries)


def test_analyze_allows_turn_below_call_threshold():
    entries = [user("依頼"), *[tool_use("Grep", f"g{i}", pattern=str(i))
                              for i in range(mod.CALLS_MIN - 1)]]
    assert signals(entries) == []


# ── analyze: correction ──────────────────────────────────────

@pytest.mark.parametrize("word", ["違う", "やり直", "間違", "何度も"])
def test_analyze_detects_correction_words_in_prompt(word):
    assert any(s.startswith("correction") for s in signals([user(f"それは{word}よ")]))


def test_analyze_ignores_correction_words_in_system_text():
    """`<` で始まる自動挿入テキストは手戻りの根拠にしない。"""
    assert signals([user("<system-reminder>違う</system-reminder>")]) == []


def test_analyze_truncates_prompt_head_to_60_chars():
    assert mod.analyze([user("あ" * 100 + "\n二行目")])["prompt"] == "あ" * 60


# ── append_memo ──────────────────────────────────────────────

STATS = {"signals": ["errors×3"], "calls": 12, "errors": 3, "denials": 0,
         "prompt": "依頼文"}


def test_append_memo_creates_file_with_header(memo):
    mod.append_memo("abcdef1234", STATS)
    text = memo.read_text(encoding="utf-8")
    assert text.startswith("# 効率メモ") and "session abcdef12" in text


def test_append_memo_records_signals_and_placeholder(memo):
    mod.append_memo("s", STATS)
    text = memo.read_text(encoding="utf-8")
    assert "- シグナル: errors×3" in text
    assert "ツール12回・エラー3回・拒否0回。開始:「依頼文」" in text
    assert mod.PLACEHOLDER in text


def test_append_memo_keeps_existing_entries(memo):
    memo.parent.mkdir(parents=True)
    memo.write_text("# 効率メモ\n\n## 既存エントリ\n", encoding="utf-8")
    mod.append_memo("s", STATS)
    text = memo.read_text(encoding="utf-8")
    assert "## 既存エントリ" in text and text.count("- シグナル:") == 1


def test_append_memo_handles_missing_session_id(memo):
    mod.append_memo("", STATS)
    assert "session unknown" in memo.read_text(encoding="utf-8")


# ── block_reason ─────────────────────────────────────────────

def test_block_reason_points_at_memo_with_repo_relative_path(memo):
    reason = mod.block_reason(STATS)
    assert "docs/reviews/efficiency_memo.md" in reason and "[errors×3]" in reason


def test_block_reason_forbids_new_work(memo):
    assert "それ以外の新しい作業・調査はしない" in mod.block_reason(STATS)


# ── main ─────────────────────────────────────────────────────

def run_main(monkeypatch, payload):
    monkeypatch.setattr(mod.sys, "stdin", io.StringIO(json.dumps(payload)))
    mod.main()


def test_main_blocks_and_appends_memo_on_signal(memo, tmp_path, monkeypatch, capsys):
    path = write_transcript(tmp_path / "t.jsonl", [user("依頼"), *reads("a.md", 3)])
    run_main(monkeypatch, {"transcript_path": str(path), "session_id": "s1"})
    printed = json.loads(capsys.readouterr().out)
    assert printed["decision"] == "block" and "same-read" in printed["reason"]
    assert memo.exists()


def test_main_does_nothing_without_signal(memo, tmp_path, monkeypatch, capsys):
    path = write_transcript(tmp_path / "t.jsonl", [user("依頼"), *reads("a.md", 1)])
    run_main(monkeypatch, {"transcript_path": str(path), "session_id": "s1"})
    assert capsys.readouterr().out == "" and not memo.exists()


def test_main_does_nothing_when_stop_hook_active(memo, tmp_path, monkeypatch, capsys):
    """フック起因の継続では再検出しない（ループ防止）。"""
    path = write_transcript(tmp_path / "t.jsonl", [user("依頼"), *reads("a.md", 3)])
    run_main(monkeypatch, {"transcript_path": str(path), "stop_hook_active": True})
    assert capsys.readouterr().out == "" and not memo.exists()


def test_main_does_nothing_for_missing_transcript(memo, tmp_path, monkeypatch, capsys):
    run_main(monkeypatch, {"transcript_path": str(tmp_path / "none.jsonl")})
    assert capsys.readouterr().out == "" and not memo.exists()


def test_main_does_nothing_when_turn_cannot_be_identified(memo, tmp_path, monkeypatch, capsys):
    path = write_transcript(tmp_path / "t.jsonl", reads("a.md", 3))
    run_main(monkeypatch, {"transcript_path": str(path)})
    assert capsys.readouterr().out == "" and not memo.exists()
