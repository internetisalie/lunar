"""MAINT-60-04: `fetch-corpus.py --only` fetches exactly the named members."""
from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
SPEC = importlib.util.spec_from_file_location("fetch_corpus", HERE / "fetch-corpus.py")
fetch_corpus = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(fetch_corpus)

MEMBERS = ["luals", "ldoc", "beequeue", "fallo"]


class FetchOnlyTest(unittest.TestCase):
    def run_main(self, argv: list[str]) -> tuple[list[str], list[list[str]]]:
        """The member names fetch_one received, and the member names fetch_definition_archives received."""
        with tempfile.TemporaryDirectory() as scratch:
            manifest = Path(scratch) / "manifest.json"
            manifest.write_text(json.dumps({"corpora": [self.entry(name) for name in MEMBERS]}))
            fetched: list[str] = []
            archived: list[list[str]] = []
            with mock.patch.object(fetch_corpus, "CORPUS_ROOT", Path(scratch) / "corpus"), \
                    mock.patch.object(fetch_corpus, "fetch_one", lambda entry: fetched.append(entry["name"])), \
                    mock.patch.object(
                        fetch_corpus,
                        "fetch_definition_archives",
                        lambda m: archived.append([e["name"] for e in m["corpora"]]),
                    ):
                self.make_roots(Path(scratch) / "corpus")
                fetch_corpus.main(["--manifest", str(manifest), *argv])
            return fetched, archived

    @staticmethod
    def entry(name: str) -> dict:
        return {"name": name, "url": "u", "commit": "c", "roots": ["src"], "luaLevel": "5.4"}

    @staticmethod
    def make_roots(corpus: Path) -> None:
        for name in MEMBERS:
            (corpus / name / "src").mkdir(parents=True, exist_ok=True)

    def test_only_fetches_exactly_the_named_members(self) -> None:
        fetched, archived = self.run_main(["--only", "ldoc,beequeue"])
        self.assertEqual(["ldoc", "beequeue"], fetched)
        self.assertEqual([["ldoc", "beequeue"]], archived)

    def test_no_only_fetches_every_member(self) -> None:
        fetched, archived = self.run_main([])
        self.assertEqual(MEMBERS, fetched)
        self.assertEqual([MEMBERS], archived)

    def test_unknown_member_exits_naming_it(self) -> None:
        with self.assertRaises(SystemExit) as raised:
            self.run_main(["--only", "nosuch"])
        self.assertNotEqual(0, raised.exception.code)

    def test_unknown_member_message_names_it(self) -> None:
        with self.assertRaises(SystemExit), mock.patch.object(fetch_corpus, "die", wraps=fetch_corpus.die) as die:
            self.run_main(["--only", "nosuch"])
        self.assertIn("nosuch", die.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
