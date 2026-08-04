---
id: "BUG-359"
title: "False positive 'nil value is not assignable to string' on package.path concat assignment (reported twice)"
type: "bug"
parent_id: "BUG"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-359: False positive "nil value is not assignable to string" on `package.path` concat assignment

This is the dedicated reproduction for **BUG-353 Problem 1**, which was left open with the note
"*Did not reproduce in the containerized GoLand … needs its own reproduction before planning.*"
It reproduces in the local IDE on the test project. See [[353-package-path-member-resolution]].

## 1. Reproduction

Open `~/Documents/src/lua/test/package2.lua` in the plugin environment. The first lines are:

```lua
require "os"
require [[package]]

package.path = "..."..package.path
```

1. Observe the assignment on **line 4**: `package.path = "..."..package.path`.
2. Look at the editor inline error highlight and the **Problems → File** tool window — initially the
   false positive is reported **once**.
3. Make any edit to the document (e.g. press **Enter** elsewhere in the file to insert a newline).
4. Re-check the Problems tool window — the same error is now reported **twice**.

## 2. Expected vs Actual Behavior

- **Expected**: `package.path` is a stdlib `string` field. The concatenation `"..."..package.path`
  yields a `string`, which is assignable to the `string` field `package.path`. No error should be shown.
- **Actual**:
  - Line 4 is flagged with the error **`nil value is not assignable to string`** — a false positive.
  - The error starts as a **single** entry on a fresh open, but becomes reported **twice** after an
    in-file edit (inserting a newline with Enter). The Problems tool window then shows "File 2" with
    two identical entries: `nil value is not assignable to string :4` and
    `nil value is not assignable to string :4`.

## 3. Context / Environment

- **Lua Version**: project default (Lua 5.4 stdlib stubs).
- **IDE**: GoLand 2026.1.1 (local, not the container).
- **Reproduction file**: `~/Documents/src/lua/test/package2.lua` (line 4).
- **Relevant Files (where the symptom surfaces — not a root-cause analysis)**:
  - `src/main/kotlin/net/internetisalie/lunar/analysis/LuaTypeAssignabilityInspection.kt` — registers the
    error from `LuaTypesSnapshot.getErrors()`.
  - `src/main/resources/runtime/standard/lua-5.4/package.lua` — declares `package.path = ""` (`string`).
- **Other Notes**:
  - **Two distinct observations:**
    1. The **false positive** itself — the RHS `"..."..package.path` (string concat) is judged
       `nil` and reported as not assignable to `string`. This is BUG-353 Problem 1, previously
       non-reproducing.
    2. The **duplicate reporting** — the same diagnostic appears twice, but only **after an edit**
       (pressing Enter to insert a newline); on a fresh open it is reported once. This edit-triggered
       timing suggests a stale highlight surviving an incremental re-analysis rather than two
       always-on surfacers. There is an existing regression test,
       `src/test/kotlin/net/internetisalie/lunar/lang/types/DuplicateNilAssignabilityTest.kt`,
       asserting this message is surfaced exactly once via `myFixture.doHighlighting()` on a freshly
       configured file — it would not catch a duplicate that only appears after a document change.
  - This reproduction has the **string literal on the left** of the concat (`"..."..package.path`),
    whereas BUG-353 had `package.path` on the left (`package.path .. ";…"`). Both forms flag.

## 4. Root cause (established 2026-08-04) — this is BUG-397, not its own defect

Still reproduces after the TARGET-08 scope work. Probed directly:

```
highlights = [nil value is not assignable to string]
rhs  '"..."..package.path'  -> String     ← the concat is inferred correctly
lhs  'package.path'         -> Undefined  ← the assignment target has no type at all
resolveGlobal("package")    -> [loaded, searchers, path, cpath, preloaded, loadlib, searchpath]
```

So the RHS is fine and the **LHS is `Undefined`**: assigning a `String` into a slot the engine
believes is nothing produces "nil value is not assignable to string". The information needed is
already reachable — `resolveGlobal("package")` returns the members, `path` among them — but
`LuaTypesVisitor.visitNameRef` deliberately does **not** consult it, so the type engine stays
file-local for free globals. That restraint is [[397-free-globals-untyped-for-the-engine|BUG-397]],
recorded in `visitNameRef`'s KDoc. **Fixing BUG-397 fixes this; there is nothing separate to fix.**

Two things fall out of that and should not be lost:

- **`DuplicateNilAssignabilityTest` currently pins the false positive as expected behaviour.** It
  asserts the message is surfaced *exactly once* — the duplicate-reporting concern — which requires
  it to be surfaced at all. When BUG-397 lands, that assertion must be inverted to zero, or it will
  fail and read as a regression when it is the fix.
- **The earlier BUG-397 evidence was wrong on one of its four items.** The reverted experiment was
  recorded as regressing four suites, one being "a genuine nil-assignability error stopped being
  reported". It was not genuine — it was *this* false positive disappearing. BUG-397's real
  regression budget is three suites, and one of its effects is closing this bug.

**Secondary finding — filed separately as BUG-400, and NOT part of this bug:** `resolveType("package")` returns
null, and so do `math` and `io`. `LuaClassNameIndex` is a stub index over `LuaLocalVarDecl`, but the
stdlib stubs declare their classes on a bare **global assignment** (`---@class package` above
`package = {}`), which is not a stubbed PSI type — so no stdlib `---@class` is nominally resolvable.
`string` and `table` *appear* to resolve only because `resolveType` checks `LuaPrimitiveType.PRIMITIVES`
first and those names collide with primitives. **Nothing in this bug's chain uses that path** — the
member types come through the graph, which is why BUG-397 alone closes this one. Measured separately:
`resolveType` is null for `package`, `io`, `os`, `debug`, `coroutine` and `utf8`, and `---@type package`
completes nothing. Filed as **BUG-400**.
