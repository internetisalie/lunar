---
id: "BUG-359"
title: "False positive 'nil value is not assignable to string' on package.path concat assignment (reported twice)"
type: "bug"
status: "todo"
priority: "medium"
folders:
  - "[[features]]"
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
