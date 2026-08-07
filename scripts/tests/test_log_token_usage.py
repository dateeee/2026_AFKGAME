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
    """`LOG_PATH` を差し替えた（まだ存在しない）CSV パスを返す。"""
    path = tmp_path / "logs" / "token_usage.csv"
    monkeypatch.setattr(mod, "LOG_PATH", path)
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
