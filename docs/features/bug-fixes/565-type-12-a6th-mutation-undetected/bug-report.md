---
id: "BUG-565"
title: "No test detects TYPE-12's A6TH mutation: the false positives that exposed it are gone"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-565: No test detects TYPE-12's A6TH mutation

Found while fixing [[BUG-554]]. A test-coverage gap, not a product defect. Root cause of the gap is stated below because it was measured. A replacement fixture has not been designed.

## 1. Reproduction

On the `bug-554` branch, after [[BUG-554]]'s fix and its golden re-record, apply TYPE-12's A6TH mutation in `LuaTypesVisitor.methodSiteMember` (`LuaTypesVisitor.kt:74`):

```kotlin
val tableConstraint = LuaGraphType.Table(localMembers = mapOf(methodName to (hub ?: siteMember)))
```

Then run `test --rerun --no-build-cache --tests '*LuaTypeGolden*' --tests '*LuaMemberDemandHubTest*'`.

## 2. Expected vs Actual
- **Expected**: red. [[TYPE-12]]'s `implementation-plan.md` lists A6TH as caught by requirements cases 5, 9 and 10, through `pipeUnion`, `fileUnionStdlib` and `luacheck-cache`.
- **Actual**: `BUILD SUCCESSFUL`. All 10 tests pass: `LuaTypeGoldenShapesTest` 2, `LuaTypeGoldenMemberFilesTest` 1, `LuaMemberDemandHubTest` 7. Measured 2026-10-28.

## 3. Context
- **Why the coverage disappeared**: every red signal A6TH produced was one more `File | nil` or `Pipe | nil is not assignable to { ... }` hypothesis, added to the ones already there. Those hypotheses came from a no-argument `fh:read()` failing the arity of `read(fmt?)`, which ignored the `?` until [[BUG-554]]. With the arity correct, none of the three fixtures reports a receiver hypothesis, with or without the mutation.
- **Not established**: whether a fixture with a real receiver mismatch (for example, a site whose member is genuinely missing on one union arm) brings back A6TH's extra per-site entry.
