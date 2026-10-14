"""MAINT-37 TC 20-26 and TC 32: attribute_delta.py."""
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path

import attribute_delta
from attribute_delta import NotComparable, attribute, exit_code

BARE = {
    "commit": "c", "files": "2", "settingsBroadcasts": "1", "rootsTicks": "1", "physicalPsiChanges": "2",
    "unresolvedRequires": "1",
    "inspection.LuaUndeclaredVariable": "10",
    "allsymbol.LuaUndeclaredVariable.describe": "6",
    "allsymbol.LuaUndeclaredVariable.lfs": "4",
    "inspection.LuaShadowingVariable": "3",
    "allsymbol.LuaShadowingVariable.file": "1",
}
PINNED = {
    "commit": "c", "files": "2", "settingsBroadcasts": "1", "rootsTicks": "1", "physicalPsiChanges": "2",
    "library.busted": "v",
    "unresolvedRequires": "0",
    "inspection.LuaUndeclaredVariable": "4",
    "allsymbol.LuaUndeclaredVariable.lfs": "4",
    "inspection.LuaShadowingVariable": "5",
    "allsymbol.LuaShadowingVariable.file": "3",
}
GLOBALS = {"describe", "file"}


def classes(rows) -> dict[str, tuple[str, str]]:
    return {row.key: (row.cls, row.evidence) for row in rows}


class AttributeTest(unittest.TestCase):
    def test_TC_20_library_global_drop_is_explained_and_collisions_are_triaged(self):
        rows = attribute(BARE, PINNED, GLOBALS)
        self.assertEqual({
            "inspection.LuaUndeclaredVariable": ("EXPLAINED", "describe:-6"),
            "unresolvedRequires": ("EXPECTED", "library modules now resolve"),
            "inspection.LuaShadowingVariable": ("TRIAGE-GLOBAL", "file:+2"),
        }, classes(rows))
        self.assertEqual(3, len(rows))
        self.assertEqual(0, exit_code(rows))

    def test_TC_21_a_drop_on_a_non_library_symbol_is_unexplained(self):
        pinned = {**PINNED, "allsymbol.LuaUndeclaredVariable.lfs": "3", "inspection.LuaUndeclaredVariable": "3"}
        rows = attribute(BARE, pinned, GLOBALS)
        self.assertEqual(("UNEXPLAINED", "lfs:-1"), classes(rows)["inspection.LuaUndeclaredVariable"])
        self.assertEqual(1, exit_code(rows))

    def test_undeclared_drop_beyond_the_symbol_tally_is_unexplained(self):
        pinned = {**PINNED, "inspection.LuaUndeclaredVariable": "3"}
        rows = attribute(BARE, pinned, GLOBALS)
        self.assertEqual(
            ("UNEXPLAINED", "; symbol tally sums to -6, inspection delta -7"),
            classes(rows)["inspection.LuaUndeclaredVariable"],
        )
        self.assertEqual(1, exit_code(rows))

    def test_TC_22_a_moved_structural_key_is_unexplained(self):
        rows = attribute({**BARE, "requires": "5"}, {**PINNED, "requires": "6"}, GLOBALS)
        row = next(r for r in rows if r.key == "requires")
        self.assertEqual(("UNEXPLAINED", 1), (row.cls, row.delta))
        self.assertEqual(1, exit_code(rows))

    def test_TC_23_a_rising_library_global_is_unexplained(self):
        pinned = {**PINNED, "allsymbol.LuaUndeclaredVariable.describe": "7", "inspection.LuaUndeclaredVariable": "11"}
        rows = attribute(BARE, pinned, GLOBALS)
        self.assertEqual(("UNEXPLAINED", "describe:+1"), classes(rows)["inspection.LuaUndeclaredVariable"])
        self.assertEqual(1, exit_code(rows))

    def test_TC_24_incomparable_pairs_raise_with_the_exact_message(self):
        cases = {
            "a": ({**BARE, "library.busted": "v"}, PINNED, "the bare run carried libraries"),
            "b": (BARE, {**PINNED, "commit": "d"}, "not the same corpus commit (bare c, pinned d)"),
            "c": (BARE, {**PINNED, "settingsBroadcasts": "0"},
                  "refresh observable settingsBroadcasts differs (bare 1, pinned 0): the arms did not receive the same refresh"),
            "d": ({k: v for k, v in BARE.items() if k != "settingsBroadcasts"}, PINNED,
                  "the bare tally has no settingsBroadcasts line"),
            "e": (BARE, {**PINNED, "files": "3"}, "file counts differ (bare 2, pinned 3)"),
            "f": (BARE, {**PINNED, "rootsTicks": "0"},
                  "refresh observable rootsTicks differs (bare 1, pinned 0): the arms did not receive the same refresh"),
            "g": (BARE, {**PINNED, "physicalPsiChanges": "1"},
                  "refresh observable physicalPsiChanges differs (bare 2, pinned 1): the arms did not receive the same refresh"),
            "h": (BARE, {k: v for k, v in PINNED.items() if k != "rootsTicks"},
                  "the pinned tally has no rootsTicks line"),
        }
        for name, (bare, pinned, message) in cases.items():
            with self.subTest(case=name), self.assertRaises(NotComparable) as raised:
                attribute(bare, pinned, GLOBALS)
            self.assertEqual(message, str(raised.exception))

    def test_TC_25_highlight_failures_are_not_comparable(self):
        with self.assertRaises(NotComparable) as raised:
            attribute(BARE, {**PINNED, "inspection.highlightFailures": "1"}, GLOBALS)
        self.assertIn("BUG-390", str(raised.exception))

    def test_TC_26_a_rising_non_library_symbol_is_a_contract_triage(self):
        bare = {**BARE, "inspection.LuaTypeAssignability": "0"}
        pinned = {**PINNED, "inspection.LuaTypeAssignability": "3", "allsymbol.LuaTypeAssignability.x": "3"}
        rows = attribute(bare, pinned, GLOBALS)
        self.assertEqual(4, len(rows))
        self.assertEqual(("TRIAGE-CONTRACT", "x:+3"), classes(rows)["inspection.LuaTypeAssignability"])
        self.assertEqual(0, exit_code(rows))


class MainTest(unittest.TestCase):
    def test_TC_32_a_version_mismatch_is_reported_as_exit_2(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            for arm, tally in (("bare", BARE), ("pinned", PINNED)):
                (root / arm).mkdir()
                (root / arm / "a.tally").write_text("".join(f"{k}={v}\n" for k, v in tally.items()))
            (root / "defs").mkdir()
            catalog = root / "catalog.json"
            catalog.write_text(json.dumps({"catalogVersion": 1, "libraries": [{"id": "busted", "version": "w"}]}))
            err = io.StringIO()
            arguments = ["--corpus", "a", "--bare", str(root / "bare"), "--pinned", str(root / "pinned"),
                         "--definitions", str(root / "defs"), "--catalog", str(catalog)]
            with contextlib.redirect_stderr(err), contextlib.redirect_stdout(io.StringIO()):
                code = attribute_delta.main(arguments)
        self.assertEqual(2, code)
        self.assertIn(
            "attribute_delta: not comparable: library busted: the pinned tally carries v, the catalog has w",
            err.getvalue(),
        )


if __name__ == "__main__":
    unittest.main()
