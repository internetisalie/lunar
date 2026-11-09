---
id: "BUG-52"
title: "Every .rockspec that fails to evaluate, test fixtures included, logs a WARN with its stack trace"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-52: Every .rockspec that fails to evaluate, test fixtures included, logs a WARN with its stack trace

## 1. Reproduction
Open `~/Documents/src/lua/test`. `idea.log` gets one WARN per fixture, for example:
`Rockspec bridge failed for …/luacheck/spec/projects/default_stds/default_stds-scm-1.rockspec (exit=1): … attempt to call global 'it' (a nil value)`,
followed by its stack trace. The same happens for `spec/samples/bad.rockspec`.

## 2. Expected vs Actual Behavior
- **Expected**: a file that is not a valid rockspec (a test fixture or a deliberately broken sample)
  is skipped quietly. Where a broken rockspec matters to the user, it is reported on that file in the
  editor, not in the log.
- **Actual**: `RockspecBridge.kt:62` logs every failure at WARN. BUG-380 already moved the
  "no runtime" case to debug for the same flooding reason.

## 3. Context / Environment
- Discovery has no default excludes (`rockspecIncludeGlobs`/`rockspecExcludeGlobs` start empty), so
  vendored test trees are evaluated too. Whether to add default excludes (`spec/`, `test/`) is a
  question for plan-bug.
- Related: [[BUG-50]] decides whether these files are evaluated at all.

## 4. Fix
`RockspecBridge.read` logs an evaluation failure at debug, with only the first line of the
interpreter's error rather than its traceback. This matches how BUG-380 handled the "no runtime" case.

Default excludes for `spec/` and `test/` were not added. [[BUG-50]] already stops evaluation in an
untrusted project and removes the standard library, so fixtures no longer run. [[BUG-53]] keeps one
rockspec per package, and a broken fixture just contributes nothing.

## 5. Test
No regression test: the change is a log level, which the light-fixture logger does not expose
without asserting on the logging framework itself.
