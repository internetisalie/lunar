---
id: "BUG-30"
title: "The JSON-schema tests remove their file-type association before the fixture tears down, which can fail teardown"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-30: The JSON-schema tests remove their file-type association before teardown

## Reproduction

Intermittent. GitHub's `Build Plugin` run 41529681975 on `457212bf` failed one test of 3562:

```
LuaJsonSchemaCompletionTest > testTc1_FindsSchema FAILED
    com.intellij.util.lang.CompoundRuntimeException at LuaJsonSchemaCompletionTest.kt:52
        Caused by: com.intellij.psi.PsiInvalidElementAccessException at PsiDocumentManagerBase.java:191
```

Gitea's build of the same commit, and GitHub's builds of the neighbouring commits, passed.

## Expected vs Actual

- **Expected:** teardown never fails.
- **Actual:** line 52 is `super.tearDown()`, which runs after `tearDown` has removed the `testcfg`
  association.

## Root Cause

`LuaJsonSchemaCompletionTest.tearDown` and `LuaJsonSchemaEngineTest.tearDown` call
`FileTypeManager.removeAssociatedExtension(LuaFileType, …)` before `super.tearDown()`. Removing the
association re-types `test.testcfg`, which the fixture still has open, so its PSI is invalidated; the
fixture's teardown then touches it. Read from the trace, not reproduced: one failure in many runs.

## Fix Strategy

Both tests remove the associations after `super.tearDown()`, in a `finally`.

## Test Strategy

The two classes stay green. The failure is too rare to show its absence directly; a recurrence
reopens this report.
