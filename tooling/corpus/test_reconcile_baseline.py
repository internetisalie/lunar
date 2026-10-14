"""MAINT-37 TC 39-46: reconcile_baseline.py."""
import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import reconcile_baseline
from attribute_delta import parse_tally
from reconcile_baseline import Residual, StartRow, key_delta, reconcile, residual_exit, start_effect

SHADOW = "inspection.LuaShadowingVariable"
UNDECLARED = "inspection.LuaUndeclaredVariable"


def tally(text: str) -> dict[str, str]:
    return parse_tally("\n".join(text.split()))


def run_main(argv: list[str]) -> tuple[int, str, str]:
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        code = reconcile_baseline.main(argv)
    return code, out.getvalue(), err.getvalue()


class Workspace:
    """A temp dir holding named record files and per-arm tally directories."""

    def __init__(self, test: unittest.TestCase):
        scratch = tempfile.TemporaryDirectory()
        test.addCleanup(scratch.cleanup)
        self.root = Path(scratch.name)

    def record(self, name: str, text: str) -> str:
        path = self.root / name
        path.write_text("\n".join(text.split()) + "\n")
        return str(path)

    def arm(self, arm: str, corpus: str, text: str) -> str:
        directory = self.root / arm
        directory.mkdir(exist_ok=True)
        (directory / f"{corpus}.tally").write_text("\n".join(text.split()) + "\n")
        return str(directory)


class ReconcileTest(unittest.TestCase):
    OLD = tally("requires=5 unresolvedRequires=1 inspection.LuaUndeclaredVariable=10 inspection.LuaShadowingVariable=3")
    NEW = tally("requires=5 unresolvedRequires=0 inspection.LuaUndeclaredVariable=4 inspection.LuaShadowingVariable=6")
    BEFORE = tally(
        "commit=c unresolvedRequires=1 inspection.LuaUndeclaredVariable=10 inspection.LuaShadowingVariable=3"
    )
    PINNED = tally(
        "commit=c unresolvedRequires=0 inspection.LuaUndeclaredVariable=4 inspection.LuaShadowingVariable=5"
    )

    def test_TC_39_attributed_movement_is_subtracted_from_the_committed_delta(self):
        rows = reconcile(key_delta(self.OLD, self.NEW), key_delta(self.BEFORE, self.PINNED))
        self.assertEqual([Residual(SHADOW, 3, 2, 1, False)], rows)

    def test_TC_40_a_moved_requires_count_blocks(self):
        new = {**self.NEW, "requires": "6"}
        rows = reconcile(key_delta(self.OLD, new), key_delta(self.BEFORE, self.PINNED))
        requires = next(row for row in rows if row.key == "requires")
        self.assertEqual(Residual("requires", 1, 0, 1, True), requires)
        self.assertEqual(1, residual_exit(rows))

    def test_TC_41_main_reports_unpaired_modes_and_rejects_bad_argument_sets(self):
        space = Workspace(self)
        old = space.record("old.baseline", "requires=5 inspection.LuaShadowingVariable=3")
        new = space.record("new.baseline", "requires=5 inspection.LuaShadowingVariable=5 crash.parse:NullPointerException=1")
        base = ["--corpus", "zerobrane", "--old", old, "--new", new]

        code, out, _ = run_main(base)
        self.assertEqual(1, code)
        self.assertRegex(out, r"\| crash\.parse:NullPointerException \| \+1 \| 0 \| \+1 \| yes \|")
        self.assertRegex(out, r"\| inspection\.LuaShadowingVariable \| \+2 \| 0 \| \+2 \| no \|")

        code, _, err = run_main([*base, "--bare", space.arm("bare", "zerobrane", "requires=5")])
        self.assertEqual(2, code)
        self.assertIn("reconcile_baseline: --before, --bare and --pinned go together", err)

        code, _, err = run_main([*base, "--offset", "--pinned", space.arm("pinned", "zerobrane", "requires=5")])
        self.assertEqual(2, code)
        self.assertIn("reconcile_baseline: --offset compares two records and takes no --before, --bare or --pinned", err)

    def test_TC_42_blocking_follows_the_residual_bound_and_the_always_blocking_keys(self):
        committed = {
            SHADOW: 4,
            "inspection.LuaTypeAssignability": 5,
            "inspection.LuaUnusedLocal": -5,
            "inspection.highlightFailures": 1,
        }
        rows = reconcile(committed, {})
        self.assertEqual([
            Residual(SHADOW, 4, 0, 4, False),
            Residual("inspection.LuaTypeAssignability", 5, 0, 5, True),
            Residual("inspection.LuaUnusedLocal", -5, 0, -5, True),
            Residual("inspection.highlightFailures", 1, 0, 1, True),
        ], rows)
        self.assertEqual(1, residual_exit(rows))

    def test_TC_43_start_effect_labels_a_first_pin_or_a_previous_pin(self):
        refresh_before = tally("requires=5 unresolvedRequires=1 inspection.LuaShadowingVariable=3")
        bare = tally("requires=5 unresolvedRequires=2 inspection.LuaShadowingVariable=4 settingsBroadcasts=1")
        self.assertEqual(
            ("refresh effect", [StartRow(SHADOW, 3, 4, 1, False), StartRow("unresolvedRequires", 1, 2, 1, True)]),
            start_effect(refresh_before, bare),
        )
        repin_before = {**refresh_before, "library.busted": "u", "requires": "6"}
        self.assertEqual(
            (
                "previous pin effect (busted=u)",
                [
                    StartRow(SHADOW, 3, 4, -1, False),
                    StartRow("requires", 6, 5, 1, True),
                    StartRow("unresolvedRequires", 1, 2, -1, False),
                ],
            ),
            start_effect(repin_before, bare),
        )

    def test_TC_44_a_first_pin_reconciles_to_nothing_and_reports_the_refresh_effect(self):
        space = Workspace(self)
        old = space.record("old.baseline", "requires=5 inspection.LuaShadowingVariable=3")
        new = space.record("new.baseline", "requires=5 library.busted=v inspection.LuaShadowingVariable=6")
        before = space.arm("before", "a", "requires=5 settingsBroadcasts=0 inspection.LuaShadowingVariable=3")
        bare = space.arm("bare", "a", "requires=5 settingsBroadcasts=1 inspection.LuaShadowingVariable=4")
        pinned = space.arm("pinned", "a", "requires=5 settingsBroadcasts=1 library.busted=v inspection.LuaShadowingVariable=6")
        code, out, _ = run_main(
            ["--corpus", "a", "--old", old, "--new", new, "--before", before, "--bare", bare, "--pinned", pinned]
        )
        self.assertEqual(0, code)
        self.assertIn("## a — shape residual\n| key | committed | attributed | residual | blocking |\n"
                      "| :-- | --: | --: | --: | :-- |\n| — | 0 | 0 | 0 | no |", out)
        self.assertIn("## a — refresh effect", out)
        self.assertIn("| inspection.LuaShadowingVariable | 3 | 4 | +1 | no |", out)

    def test_TC_45_a_repin_reports_the_previous_pin_effect_without_blocking(self):
        space = Workspace(self)
        old = space.record("old.baseline", "unresolvedRequires=0 library.busted=u inspection.LuaShadowingVariable=5")
        new = space.record("new.baseline", "unresolvedRequires=0 library.busted=v inspection.LuaShadowingVariable=6")
        before = space.arm(
            "before", "a", "settingsBroadcasts=1 unresolvedRequires=0 library.busted=u inspection.LuaShadowingVariable=5"
        )
        bare = space.arm("bare", "a", "settingsBroadcasts=1 unresolvedRequires=2 inspection.LuaShadowingVariable=4")
        pinned = space.arm(
            "pinned", "a", "settingsBroadcasts=1 unresolvedRequires=0 library.busted=v inspection.LuaShadowingVariable=6"
        )
        code, out, _ = run_main(
            ["--corpus", "a", "--old", old, "--new", new, "--before", before, "--bare", bare, "--pinned", pinned]
        )
        self.assertEqual(0, code)
        self.assertIn("| — | 0 | 0 | 0 | no |", out)
        self.assertIn("## a — previous pin effect (busted=u)", out)
        self.assertIn("| inspection.LuaShadowingVariable | 5 | 4 | +1 | no |", out)
        self.assertIn("| unresolvedRequires | 0 | 2 | -2 | no |", out)

    def test_TC_46_offset_mode_uses_the_offset_bound_and_title(self):
        space = Workspace(self)
        before = space.arm("before", "a", "inspection.LuaShadowingVariable=3 inspection.LuaTypeAssignability=10")
        new = space.record("new.baseline", "inspection.LuaShadowingVariable=5 inspection.LuaTypeAssignability=13")
        code, out, _ = run_main(["--corpus", "a", "--offset", "--old", f"{before}/a.tally", "--new", new])
        self.assertEqual(1, code)
        self.assertIn("## a — shape offset", out)
        self.assertIn("| inspection.LuaShadowingVariable | +2 | 0 | +2 | no |", out)
        self.assertIn("| inspection.LuaTypeAssignability | +3 | 0 | +3 | yes |", out)


if __name__ == "__main__":
    unittest.main()
