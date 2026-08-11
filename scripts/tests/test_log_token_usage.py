"""log_token_usage.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

`LOG_PATH` を `tmp_path` へ差し替え、実際の `logs/token_usage.csv` を書き換えない。
トランスクリプト JSONL は Claude Code の内部仕様のため、**壊れた入力を与えても
落ちない**ことを変異テストとして押さえる（フックはセッションを妨げてはならない）。
"""

import csv
import io
import json
import re

import pytest

import log_token_usage as mod


@pytest.fixture
def log_path(tmp_path, monkeypatch):
    """`LOG_PATH`/`DELTA_PATH` を差し替えた（まだ存在しない）CSV パスを返す。"""
    path = tmp_path / "logs" / "token_usage.csv"
    monkeypatch.setattr(mod, "LOG_PATH", path)
    monkeypatch.setattr(mod, "DELTA_PATH", tmp_path / "logs" / "token_usage_deltas.csv")
    return path


def write_transcript(path, entries) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(json.dumps(e, ensure_ascii=False) for e in entries),
                    encoding="utf-8")


def assistant(model="claude-opus-5", msg_id="m1", **usage):
    base = {"input_tokens": 0, "output_tokens": 0,
            "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}
    base.update(usage)
    return {"type": "assistant", "message": {"id": msg_id, "model": model, "usage": base}}


def user(text):
    return {"type": "user", "message": {"content": text}}


def read_rows(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


# ── to_local_path ────────────────────────────────────────────

def test_to_local_path_converts_posix_drive_form():
    assert mod.to_local_path("/c/Users/x/a.jsonl").as_posix() == "c:/Users/x/a.jsonl"


def test_to_local_path_keeps_plain_path():
    assert mod.to_local_path("logs/a.jsonl").as_posix() == "logs/a.jsonl"


def test_to_local_path_keeps_existing_posix_path(tmp_path):
    """変換前のパスが実在するならそのまま使う（POSIX 環境の誤変換を防ぐ）。"""
    real = tmp_path / "a.jsonl"
    real.write_text("", encoding="utf-8")
    assert mod.to_local_path(str(real)) == real


# ── iter_entries（壊れた入力への耐性）────────────────────────

def test_iter_entries_skips_blank_and_broken_lines(tmp_path):
    path = tmp_path / "t.jsonl"
    path.write_text('{"type": "user"}\n\n{壊れたJSON\n"文字列"\n{"type": "assistant"}\n',
                    encoding="utf-8")
    assert [e["type"] for e in mod.iter_entries(path)] == ["user", "assistant"]


def test_iter_entries_tolerates_invalid_encoding(tmp_path):
    path = tmp_path / "t.jsonl"
    path.write_bytes(b'{"type": "user"}\n\xff\xfe\n')
    assert len(list(mod.iter_entries(path))) == 1


# ── user_text ────────────────────────────────────────────────

def test_user_text_reads_plain_string_content():
    assert mod.user_text(user("こんにちは")) == "こんにちは"


def test_user_text_joins_text_blocks():
    entry = {"type": "user", "message": {"content": [
        {"type": "text", "text": "一"}, {"type": "image"}, {"type": "text", "text": "二"}]}}
    assert mod.user_text(entry) == "一\n二"


def test_user_text_ignores_assistant_entry():
    assert mod.user_text(assistant()) == ""


@pytest.mark.parametrize("flag", ["isSidechain", "isMeta"])
def test_user_text_ignores_sidechain_and_meta(flag):
    entry = user("x") | {flag: True}
    assert mod.user_text(entry) == ""


def test_user_text_returns_empty_for_unknown_content_shape():
    assert mod.user_text({"type": "user", "message": {"content": 42}}) == ""


# ── task_label ───────────────────────────────────────────────

def test_task_label_prefers_slash_command():
    entries = [user("先に書いた依頼文"), user("<command-name>/dev</command-name>")]
    assert mod.task_label(entries) == "/dev"


def test_task_label_skips_builtin_commands():
    """`/clear` 等の組み込みはタスク名にしない。"""
    entries = [user("<command-name>/clear</command-name>"),
               user("<command-name>/unit-test</command-name>")]
    assert mod.task_label(entries) == "/unit-test"


def test_task_label_strips_plugin_prefix_when_skipping():
    entries = [user("<command-name>/plugin:clear</command-name>"), user("素の依頼文")]
    assert mod.task_label(entries) == "素の依頼文"


def test_task_label_falls_back_to_skill_tool_use():
    entries = [{"type": "assistant", "message": {"content": [
        {"type": "tool_use", "name": "Skill", "input": {"skill": "doc-review"}}]}}]
    assert mod.task_label(entries) == "/doc-review"


def test_task_label_prefers_skill_tool_use_over_first_prompt():
    """優先順位は スラッシュコマンド > Skillツール呼び出し > 最初のプロンプト。"""
    entries = [user("依頼文"),
               {"type": "assistant", "message": {"content": [
                   {"type": "tool_use", "name": "Skill", "input": {"skill": "dev"}}]}}]
    assert mod.task_label(entries) == "/dev"


def test_task_label_uses_first_prompt_when_no_command_or_skill():
    entries = [user("依頼文"),
               {"type": "assistant", "message": {"content": [
                   {"type": "tool_use", "name": "Read", "input": {"file_path": "a.md"}}]}}]
    assert mod.task_label(entries) == "依頼文"


@pytest.mark.parametrize("text", ["<system-reminder>x", "Caveat: y", "[注記]",
                                  "This session is being continued from"])
def test_task_label_ignores_boilerplate_prompts(text):
    assert mod.task_label([user(text)]) == "(不明)"


def test_task_label_truncates_long_prompt_to_60_chars():
    assert mod.task_label([user("あ" * 100)]) == "あ" * 60


def test_task_label_returns_unknown_when_nothing_found():
    assert mod.task_label([]) == "(不明)"


# ── collect_usage ────────────────────────────────────────────

def test_collect_usage_sums_all_token_kinds():
    entries = [assistant(input_tokens=1, output_tokens=2,
                         cache_read_input_tokens=3, cache_creation_input_tokens=4)]
    assert mod.collect_usage(entries)["claude-opus-5"] == {
        "input": 1, "output": 2, "cache_read": 3, "cache_creation": 4, "calls": 1}


def test_collect_usage_deduplicates_by_message_id():
    """同一 API 応答が複数行に現れても1回だけ数える。"""
    entries = [assistant(msg_id="m1", output_tokens=5), assistant(msg_id="m1", output_tokens=5)]
    assert mod.collect_usage(entries)["claude-opus-5"]["calls"] == 1


def test_collect_usage_separates_models():
    entries = [assistant(model="opus", msg_id="a", output_tokens=1),
               assistant(model="sonnet", msg_id="b", output_tokens=2)]
    assert set(mod.collect_usage(entries)) == {"opus", "sonnet"}


def test_collect_usage_drops_models_with_zero_tokens():
    assert mod.collect_usage([assistant()]) == {}


def test_collect_usage_ignores_entries_without_usage():
    entries = [{"type": "assistant", "message": {"id": "m1", "model": "opus"}},
               user("x")]
    assert mod.collect_usage(entries) == {}


def test_collect_usage_labels_missing_model_as_unknown():
    entry = {"type": "assistant",
             "message": {"id": "m1", "usage": {"output_tokens": 3}}}
    assert list(mod.collect_usage([entry])) == ["unknown"]


# ── upsert_rows ──────────────────────────────────────────────

def test_upsert_rows_creates_csv_with_fixed_columns(log_path):
    mod.upsert_rows([{"session_id": "s1", "model": "opus"}], "s1")
    with open(log_path, encoding="utf-8-sig", newline="") as f:
        assert next(csv.reader(f)) == mod.COLUMNS


def test_upsert_rows_replaces_same_session_and_keeps_others(log_path):
    mod.upsert_rows([{"session_id": "s1", "total_tokens": 10}], "s1")
    mod.upsert_rows([{"session_id": "s2", "total_tokens": 20}], "s2")
    mod.upsert_rows([{"session_id": "s1", "total_tokens": 99}], "s1")
    rows = {r["session_id"]: r["total_tokens"] for r in read_rows(log_path)}
    assert rows == {"s1": "99", "s2": "20"} and len(read_rows(log_path)) == 2


def test_upsert_rows_ignores_unknown_keys(log_path):
    mod.upsert_rows([{"session_id": "s1", "未知の列": "x"}], "s1")
    assert list(read_rows(log_path)[0]) == mod.COLUMNS


# ── log_session ──────────────────────────────────────────────

def test_log_session_writes_one_row_per_model(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [user("<command-name>/dev</command-name>"),
                            assistant(model="opus", msg_id="a", output_tokens=5),
                            assistant(model="sonnet", msg_id="b", input_tokens=7)])
    assert mod.log_session(str(path), "s1", "stop") is True
    rows = read_rows(log_path)
    assert [r["model"] for r in rows] == ["opus", "sonnet"]
    assert {r["task"] for r in rows} == {"/dev"} and {r["reason"] for r in rows} == {"stop"}


def test_log_session_totals_all_token_kinds(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(input_tokens=1, output_tokens=2,
                                      cache_read_input_tokens=3,
                                      cache_creation_input_tokens=4)])
    mod.log_session(str(path), "s1", "stop")
    assert read_rows(log_path)[0]["total_tokens"] == "10"


def test_log_session_includes_subagent_transcripts(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(msg_id="a", output_tokens=5)])
    write_transcript(tmp_path / "s1" / "subagents" / "sub.jsonl",
                     [assistant(msg_id="b", output_tokens=7)])
    mod.log_session(str(path), "s1", "stop")
    assert read_rows(log_path)[0]["output_tokens"] == "12"


def test_log_session_returns_false_without_session_id(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=5)])
    assert mod.log_session(str(path), "", "stop") is False
    assert not log_path.exists()


def test_log_session_returns_false_for_missing_transcript(log_path, tmp_path):
    assert mod.log_session(str(tmp_path / "none.jsonl"), "s1", "stop") is False


def test_log_session_returns_false_when_no_usage(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [user("依頼文だけ")])
    assert mod.log_session(str(path), "s1", "stop") is False
    assert not log_path.exists()


def test_log_session_records_end_time_in_minute_precision(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=1)])
    mod.log_session(str(path), "s1", "stop")
    assert re.fullmatch(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}", read_rows(log_path)[0]["end_time"])


# ── logged_session_ids ───────────────────────────────────────

def test_logged_session_ids_is_empty_without_log(log_path):
    assert mod.logged_session_ids() == set()


def test_logged_session_ids_collects_recorded_sessions(log_path):
    mod.upsert_rows([{"session_id": "s1"}], "s1")
    mod.upsert_rows([{"session_id": "s2"}], "s2")
    assert mod.logged_session_ids() == {"s1", "s2"}


# ── backfill ─────────────────────────────────────────────────

@pytest.fixture
def projects(tmp_path, monkeypatch):
    """`~/.claude/projects/<cwd slug>/` を差し替えて返す。"""
    home = tmp_path / "home"
    work = tmp_path / "work"
    work.mkdir()
    monkeypatch.chdir(work)
    monkeypatch.setattr(mod.Path, "home", classmethod(lambda cls: home))
    monkeypatch.setattr(mod, "worktree_roots", lambda: [])  # git 起動を避ける
    slug = re.sub(r"[^0-9A-Za-z-]", "-", str(work))
    directory = home / ".claude" / "projects" / slug
    directory.mkdir(parents=True)
    return directory


UUID = "12345678-1234-1234-1234-123456789abc"
UUID2 = "abcdef01-1234-1234-1234-123456789abc"


def test_backfill_records_unlogged_sessions(log_path, projects, capsys):
    write_transcript(projects / f"{UUID}.jsonl", [assistant(output_tokens=5)])
    mod.backfill()
    assert [r["session_id"] for r in read_rows(log_path)] == [UUID]
    assert "1 セッションを記録しました" in capsys.readouterr().out


def test_backfill_skips_already_logged_sessions(log_path, projects, capsys):
    write_transcript(projects / f"{UUID}.jsonl", [assistant(output_tokens=5)])
    mod.upsert_rows([{"session_id": UUID, "total_tokens": 1}], UUID)
    mod.backfill()
    assert "0 セッションを記録しました" in capsys.readouterr().out
    assert read_rows(log_path)[0]["total_tokens"] == "1"


def test_backfill_skips_non_uuid_filenames(log_path, projects, capsys):
    write_transcript(projects / "notes.jsonl", [assistant(output_tokens=5)])
    write_transcript(projects / f"{UUID2}.jsonl", [assistant(output_tokens=5)])
    mod.backfill()
    assert [r["session_id"] for r in read_rows(log_path)] == [UUID2]


def test_backfill_reports_missing_project_directory(log_path, tmp_path, monkeypatch, capsys):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(mod.Path, "home", classmethod(lambda cls: tmp_path / "none"))
    monkeypatch.setattr(mod, "worktree_roots", lambda: [])
    mod.backfill()
    assert "トランスクリプトが見つかりません" in capsys.readouterr().err


# ── main ─────────────────────────────────────────────────────

def test_main_dispatches_to_backfill_with_all_flag(log_path, projects, monkeypatch, capsys):
    write_transcript(projects / f"{UUID}.jsonl", [assistant(output_tokens=5)])
    monkeypatch.setattr(mod.sys, "argv", ["log_token_usage.py", "--all"])
    mod.main()
    assert [r["session_id"] for r in read_rows(log_path)] == [UUID]


def test_main_logs_session_from_stdin_json(log_path, tmp_path, monkeypatch):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=5)])
    payload = json.dumps({"transcript_path": str(path), "session_id": "s1"})
    monkeypatch.setattr(mod.sys, "argv", ["log_token_usage.py"])
    monkeypatch.setattr(mod.sys, "stdin", io.StringIO(payload))
    mod.main()
    assert read_rows(log_path)[0]["session_id"] == "s1"


def test_main_defaults_reason_to_stop(log_path, tmp_path, monkeypatch):
    """Stop フックの stdin に reason は無い。"""
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=5)])
    payload = json.dumps({"transcript_path": str(path), "session_id": "s1"})
    monkeypatch.setattr(mod.sys, "argv", ["log_token_usage.py"])
    monkeypatch.setattr(mod.sys, "stdin", io.StringIO(payload))
    mod.main()
    assert read_rows(log_path)[0]["reason"] == "stop"


def test_main_keeps_explicit_reason(log_path, tmp_path, monkeypatch):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=5)])
    payload = json.dumps({"transcript_path": str(path), "session_id": "s1",
                          "reason": "clear"})
    monkeypatch.setattr(mod.sys, "argv", ["log_token_usage.py"])
    monkeypatch.setattr(mod.sys, "stdin", io.StringIO(payload))
    mod.main()
    assert read_rows(log_path)[0]["reason"] == "clear"


# ── cost_usd（削減計画⑤-2）──────────────────────────────────

def test_cost_usd_applies_input_output_prices():
    assert mod.cost_usd("claude-opus-5", 1_000_000, 0, 0, 0) == 5.0
    assert mod.cost_usd("claude-opus-5", 0, 1_000_000, 0, 0) == 25.0


def test_cost_usd_applies_cache_rates():
    """cache_read=入力単価×0.1、cache_creation=×2.0（1時間TTL運用）。"""
    assert mod.cost_usd("claude-opus-5", 0, 0, 1_000_000, 0) == 0.5
    assert mod.cost_usd("claude-opus-5", 0, 0, 0, 1_000_000) == 10.0


def test_cost_usd_matches_model_by_prefix():
    assert mod.cost_usd("claude-haiku-4-5-20251001", 1_000_000, 0, 0, 0) == 1.0


def test_cost_usd_falls_back_to_opus_price_for_unknown_model():
    assert mod.cost_usd("unknown", 1_000_000, 0, 0, 0) == 5.0


def test_log_session_writes_cost_usd_column(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=1_000_000)])
    mod.log_session(str(path), "s1", "stop")
    assert read_rows(log_path)[0]["cost_usd"] == "25.0"


def test_upsert_rows_fills_cost_usd_for_legacy_rows(log_path):
    """旧形式（cost_usd 列なし）の既存行は書き直し時に再計算して埋める。"""
    legacy_cols = [c for c in mod.COLUMNS if c != "cost_usd"]
    log_path.parent.mkdir(parents=True)
    with open(log_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=legacy_cols, restval="")
        writer.writeheader()
        writer.writerow({"session_id": "old", "model": "claude-opus-5",
                         "input_tokens": "0", "output_tokens": "1000000",
                         "cache_read_tokens": "0", "cache_creation_tokens": "0"})
    mod.upsert_rows([{"session_id": "s2", "model": "claude-opus-5"}], "s2")
    rows = {r["session_id"]: r for r in read_rows(log_path)}
    assert rows["old"]["cost_usd"] == "25.0"


# ── last_prompt_label（削減計画⑤-1）─────────────────────────

def test_last_prompt_label_uses_last_prompt():
    entries = [user("最初の依頼"), assistant(), user("直近の依頼")]
    assert mod.last_prompt_label(entries) == "直近の依頼"


def test_last_prompt_label_uses_command_name():
    entries = [user("依頼文"), user("<command-name>/doc-review</command-name>")]
    assert mod.last_prompt_label(entries) == "/doc-review"


def test_last_prompt_label_skips_builtin_command_and_boilerplate():
    entries = [user("実際の依頼"),
               user("Stop hook feedback:\n[bash ...] コミットしてください"),
               user("<command-name>/clear</command-name>")]
    assert mod.last_prompt_label(entries) == "実際の依頼"


def test_last_prompt_label_returns_unknown_when_nothing_found():
    assert mod.last_prompt_label([user("<system-reminder>x")]) == "(不明)"


def test_last_prompt_label_truncates_to_60_chars():
    assert mod.last_prompt_label([user("あ" * 100)]) == "あ" * 60


# ── append_deltas / deltas 統合（削減計画⑤-1）───────────────

def read_deltas():
    with open(mod.DELTA_PATH, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def test_deltas_first_stop_records_full_cumulative(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [user("依頼A"), assistant(msg_id="a", output_tokens=5)])
    mod.log_session(str(path), "s1", "stop")
    rows = read_deltas()
    assert len(rows) == 1
    assert rows[0]["output_d"] == "5" and rows[0]["calls_d"] == "1"
    assert rows[0]["prompt"] == "依頼A"


def test_deltas_second_stop_records_difference(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [user("依頼A"), assistant(msg_id="a", output_tokens=5)])
    mod.log_session(str(path), "s1", "stop")
    write_transcript(path, [user("依頼A"), assistant(msg_id="a", output_tokens=5),
                            user("依頼B"), assistant(msg_id="b", input_tokens=3)])
    mod.log_session(str(path), "s1", "stop")
    rows = read_deltas()
    assert len(rows) == 2
    assert rows[1]["input_d"] == "3" and rows[1]["output_d"] == "0"
    assert rows[1]["calls_d"] == "1" and rows[1]["prompt"] == "依頼B"


def test_deltas_negative_difference_records_current_cumulative(log_path, tmp_path):
    """resume 引き継ぎ等で累計が巻き戻ったら今回累計をそのまま記録する。"""
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(msg_id="a", output_tokens=5)])
    mod.log_session(str(path), "s1", "stop")
    write_transcript(path, [assistant(msg_id="c", output_tokens=2)])
    mod.log_session(str(path), "s1", "stop")
    assert read_deltas()[1]["output_d"] == "2"


def test_deltas_skips_unchanged_models(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(msg_id="a", output_tokens=5)])
    mod.log_session(str(path), "s1", "stop")
    mod.log_session(str(path), "s1", "stop")  # 変化なし
    assert len(read_deltas()) == 1


def test_deltas_not_written_for_backfill(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(output_tokens=5)])
    mod.log_session(str(path), "s1", "backfill")
    assert not mod.DELTA_PATH.exists()


def test_deltas_cost_uses_delta_tokens(log_path, tmp_path):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(msg_id="a", output_tokens=1_000_000)])
    mod.log_session(str(path), "s1", "stop")
    assert read_deltas()[0]["cost_usd"] == "25.0"


# ── context_warning（削減計画①-2）───────────────────────────

def test_context_warning_below_thresholds_is_empty():
    entries = [assistant(input_tokens=1_000)]
    assert mod.context_warning(entries, mod.collect_usage(entries)) == ""


def test_context_warning_level1_by_context():
    entries = [assistant(cache_read_input_tokens=130_000)]
    msg = mod.context_warning(entries, mod.collect_usage(entries))
    assert "検討" in msg and "13.0万" in msg


def test_context_warning_level1_by_calls():
    entries = [assistant(msg_id=f"m{i}", output_tokens=1) for i in range(100)]
    msg = mod.context_warning(entries, mod.collect_usage(entries))
    assert "検討" in msg and "100回" in msg


def test_context_warning_level2_strongly_recommends_clear():
    entries = [assistant(cache_read_input_tokens=185_000)]
    msg = mod.context_warning(entries, mod.collect_usage(entries))
    assert "強く推奨" in msg


def test_context_warning_uses_last_assistant_usage():
    """現在コンテキストは「最後の」応答の usage から取る（累計ではない）。"""
    entries = [assistant(msg_id="a", cache_read_input_tokens=190_000),
               assistant(msg_id="b", cache_read_input_tokens=1_000)]
    assert mod.context_warning(entries, mod.collect_usage(entries)) == ""


def test_log_session_emits_system_message_on_large_context(log_path, tmp_path, capsys):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(cache_read_input_tokens=200_000)])
    mod.log_session(str(path), "s1", "stop")
    out = capsys.readouterr().out
    assert '"systemMessage"' in out and "/clear" in out


def test_log_session_backfill_emits_no_warning(log_path, tmp_path, capsys):
    path = tmp_path / "s1.jsonl"
    write_transcript(path, [assistant(cache_read_input_tokens=200_000)])
    mod.log_session(str(path), "s1", "backfill")
    assert capsys.readouterr().out == ""


# ── repo_root（worktree からの起動）──────────────────────────

def test_repo_root_resolves_worktree_to_main(tmp_path, monkeypatch):
    main = tmp_path / "main"
    (main / ".git" / "worktrees" / "x").mkdir(parents=True)
    wt = tmp_path / "wt"
    (wt / "scripts").mkdir(parents=True)
    (wt / ".git").write_text(f"gitdir: {main / '.git' / 'worktrees' / 'x'}\n",
                             encoding="utf-8")
    monkeypatch.setattr(mod, "__file__", str(wt / "scripts" / "log_token_usage.py"))
    assert mod.repo_root() == main


def test_repo_root_returns_own_root_for_normal_repo(tmp_path, monkeypatch):
    repo = tmp_path / "repo"
    (repo / "scripts").mkdir(parents=True)
    (repo / ".git").mkdir()
    monkeypatch.setattr(mod, "__file__", str(repo / "scripts" / "log_token_usage.py"))
    assert mod.repo_root() == repo


def test_backfill_fills_missing_cost_usd_without_new_sessions(log_path, projects, capsys):
    """新規セッションが無くても --all は旧形式行の cost_usd を埋める。"""
    legacy_cols = [c for c in mod.COLUMNS if c != "cost_usd"]
    log_path.parent.mkdir(parents=True)
    with open(log_path, "w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=legacy_cols, restval="")
        writer.writeheader()
        writer.writerow({"session_id": "old", "model": "claude-opus-5",
                         "input_tokens": "0", "output_tokens": "1000000",
                         "cache_read_tokens": "0", "cache_creation_tokens": "0"})
    mod.backfill()
    assert read_rows(log_path)[0]["cost_usd"] == "25.0"


# ── backfill の worktree 走査 ─────────────────────────────────

def test_backfill_scans_worktree_slugs(log_path, projects, tmp_path, monkeypatch):
    wt_root = tmp_path / "wtroot"
    wt_root.mkdir()
    monkeypatch.setattr(mod, "worktree_roots", lambda: [wt_root])
    slug = re.sub(r"[^0-9A-Za-z-]", "-", str(wt_root))
    wt_dir = tmp_path / "home" / ".claude" / "projects" / slug
    wt_dir.mkdir(parents=True)
    write_transcript(projects / f"{UUID}.jsonl", [assistant(output_tokens=5)])
    write_transcript(wt_dir / f"{UUID2}.jsonl", [assistant(msg_id="b", output_tokens=7)])
    mod.backfill()
    assert {r["session_id"] for r in read_rows(log_path)} == {UUID, UUID2}
