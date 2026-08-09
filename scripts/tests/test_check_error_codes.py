"""check_error_codes.py の回帰テスト。

実行: `python -m pytest scripts/tests -q`（リポジトリルートから）

仕様（tech_error_handling.md 相当）と対応表（ErrorCatalog.java 相当）を `tmp_path` に
最小構成で作り、`SPEC` / `CATALOG` を差し替えて検証する。検査ごとに
「一致した状態を通す（緑パス）」と「1か所だけ崩すと検出する（変異）」を対で置く。
"""

import pytest

import check_error_codes as mod


SPEC_DOC = """# エラーハンドリング

| コード | HTTP | 発生条件 |
|--------|------|---------|
| `AUTH_TOKEN_EXPIRED` | 401 | 期限切れ |
| `AUTH_EMAIL_TAKEN` | 409 | 使用済み |

サーバー内部エラーは `INTERNAL_UNEXPECTED_ERROR` を返す。
"""

CATALOG_DOC = """package com.afkgame.web.filter;

public final class ErrorCatalog {

    static final String INTERNAL_ERROR = "INTERNAL_UNEXPECTED_ERROR";

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            Map.entry("AUTH_TOKEN_EXPIRED", new Entry(401, "期限切れです")),
            Map.entry("AUTH_EMAIL_TAKEN", new Entry(409, "使用済みです")),
            Map.entry(INTERNAL_ERROR, new Entry(500, "サーバー内部エラーが発生しました")));
}
"""


@pytest.fixture
def repo(tmp_path, monkeypatch):
    """仕様と対応表が一致した最小構成を作り、書き換え用のハンドルを返す。"""
    spec = tmp_path / "tech_error_handling.md"
    catalog = tmp_path / "ErrorCatalog.java"
    monkeypatch.setattr(mod, "ROOT", tmp_path)
    monkeypatch.setattr(mod, "SPEC", spec)
    monkeypatch.setattr(mod, "CATALOG", catalog)

    spec.write_text(SPEC_DOC, encoding="utf-8")
    catalog.write_text(CATALOG_DOC, encoding="utf-8")
    return type("Repo", (), {"spec": spec, "catalog": catalog})


def run(capsys) -> tuple[int, str]:
    code = mod.main()
    return code, capsys.readouterr().out


def test_一致していれば通る(repo, capsys):
    code, out = run(capsys)

    assert code == 0
    # 定数参照のキー（INTERNAL_ERROR）も解決して数に入る
    assert "3件が仕様と一致" in out
    assert "ステータス照合 2件" in out


def test_対応表に無いコードを欠落として検出する(repo, capsys):
    repo.catalog.write_text(
        CATALOG_DOC.replace('            Map.entry("AUTH_EMAIL_TAKEN", new Entry(409, "使用済みです")),\n', ""),
        encoding="utf-8")

    code, out = run(capsys)

    assert code == 1
    assert "欠落: AUTH_EMAIL_TAKEN" in out


def test_ステータスの不一致を検出する(repo, capsys):
    repo.catalog.write_text(CATALOG_DOC.replace("new Entry(409,", "new Entry(400,"), encoding="utf-8")

    code, out = run(capsys)

    assert code == 1
    assert "ステータス不一致: AUTH_EMAIL_TAKEN 仕様=409 対応表=400" in out


def test_仕様に無いコードを余剰として検出する(repo, capsys):
    repo.catalog.write_text(
        CATALOG_DOC.replace('"AUTH_EMAIL_TAKEN"', '"AUTH_UNKNOWN_CODE"'), encoding="utf-8")

    code, out = run(capsys)

    assert code == 1
    assert "余剰: AUTH_UNKNOWN_CODE" in out


def test_ステータス列を持たない出現だけのコードは余剰にしない(repo, capsys):
    """`INTERNAL_UNEXPECTED_ERROR` は本文にあるだけでステータス表を持たないが、余剰ではない。"""
    code, out = run(capsys)

    assert code == 0
    assert "余剰" not in out


def test_解析できなければ落ちる(repo, capsys):
    repo.catalog.write_text("public final class ErrorCatalog {}\n", encoding="utf-8")

    code, out = run(capsys)

    assert code == 1
    assert "解析できなかった" in out


def test_解決できない定数キーを報告する(repo, capsys):
    repo.catalog.write_text(
        CATALOG_DOC.replace("Map.entry(INTERNAL_ERROR,", "Map.entry(UNDEFINED_NAME,"), encoding="utf-8")

    code, out = run(capsys)

    assert code == 1
    assert "UNDEFINED_NAME を解決できない" in out
