---
id: "BUG-529"
title: "LuaRenameConflictTest's cancellation-count test fails intermittently on the 2026.2 platform"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-529: the rename cancellation-count test is flaky on 2026.2

Filed under MAINT-21's Risk 1.3, which says to file a BUG if this test fails after the platform bump.

## 1. Reproduction
On `main` after MAINT-21, run the full unit suite on the libvirt builder:

```bash
~/.cache/claude-scratch/lunar/bin/builder-run libvirt "test --rerun"
```

## 2. Expected vs Actual Behavior
- **Expected**: `LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall`
  passes on every run.
- **Actual**: it fails intermittently with `AssertionFailedError` at `LuaRenameConflictTest.kt:359`,
  on code where it otherwise passes.
  - BUG-524's first full-suite run read a cost of 9 against a required ≥10. Its second run, on the
    same code, passed.
  - MAINT-21 DR-04 saw it read 8 in one configuration and pass in the next.

## 3. Context / Environment
- **Relevant Files**: `src/test/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameConflictTest.kt:352`.
- **Other Notes**: per MAINT-21 Risk 1.3, the test counts `checkCanceled` calls attributed by a
  stack walk, so changes in platform frames can move the count. Logs:
  `~/.cache/claude-scratch/lunar/bug-524/unit.log` (failed) and `unit2.log` (passed).
  Whether the test or the code under test is at fault has not been established.
