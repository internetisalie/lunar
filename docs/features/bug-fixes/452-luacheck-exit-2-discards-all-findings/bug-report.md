---
id: "BUG-452"
title: "luacheck's exit code 2 means \"errors were reported\", not \"crashed\" — Lunar discards the whole report and shows a crash banner"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-452: a file with a syntax error loses all luacheck output

Found 2026-08-22 by the [[ANALYSIS-01]] and [[ANALYSIS-04]] retroactive-requirements agents,
independently. Verified against luacheck's own source.

## 1. Reproduction

1. Open a `.lua` file and type an incomplete statement, e.g. `local x =`.
2. Or give a file a malformed inline directive, e.g. `-- luacheck: ignroe foo`.

## 2. Expected vs actual

- **Expected**: luacheck's findings appear, including the syntax error itself as a diagnostic.
- **Actual**: every parsed problem is discarded and the file gets a whole-file banner reading
  `luacheck exited with code 2`. The `E011` / `E021` that luacheck *did* report is not shown.

## 3. Root cause

`analysis/luacheck/LuaCheckInvoker.kt`:

```kotlin
private const val FATAL_EXIT_CODE = 2
if (result.exitCode >= FATAL_EXIT_CODE) { ... LuaCheckOutcome.Failure(FailureKind.CRASHED, detail) }
```

luacheck's own exit table (`test/luacheck/src/luacheck/main.lua`):

```lua
warnings = 1,
errors   = 2,
fatals   = 3,
```

**Exit 2 is a lint result.** It means error-class findings were reported — `E011` syntax error,
`E021`–`E023`, `E033` — and `main.lua` writes the complete report to stdout *before* exiting with
it. Only 3 is a fatal. The threshold should be `3`.

Because a lint run writes nothing to stderr, the banner falls back to the generic
`"luacheck exited with code ${result.exitCode}"`, so the user is told the tool crashed when it
worked correctly.

## 4. Why nothing caught it

`LuaCheckInvokerClassifyTest` TC11 **asserts `exit 2 → CRASHED` as intended behaviour**. Fixing
this requires changing that test, which is the tell that the constant came from a design document's
framing rather than from luacheck's table. See [[BUG-461]].

## 5. Fix strategy

Raise the threshold to `3`, and treat exit 2 as a normal result whose problems are parsed and
displayed. Keep a distinct outcome for exit 3, where luacheck writes its fatal report to **stdout**
— `completedOutcome` currently reads stderr, so the fatal text is dropped too and should be picked
up from the correct stream while here.

## 6. Test strategy

Change TC11 to assert exit 2 yields parsed problems, and add a case for exit 3 as fatal. The
regression test must feed real luacheck output for a file with a syntax error — the current test
hand-writes plain-formatter output that production never requests, which is a second reason it
could not have caught this.
