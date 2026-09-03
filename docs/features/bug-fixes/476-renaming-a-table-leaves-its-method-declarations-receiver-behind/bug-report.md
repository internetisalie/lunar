---
id: "BUG-476"
title: "Renaming a table binding leaves the receiver segment of its function declarations behind"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-476: renaming a table leaves `function t:m()`'s receiver on the old name

Found by [[REFACT-09]]'s `REFACT-09-00-DR-02` while measuring something else, and re-measured
directly for this report. **Not caused by REFACT-09 or by [[TYPE-13]]** — it reproduces on the
unmodified rename path and in both function-name spellings, so it belongs to [[REFACT-01]]'s
declaration-collection rules.

## Reproduction

```lua
local t = {}
function t:m() end
<caret>t:m()
```

<kbd>Shift+F6</kbd> on the receiver `t`, new name `renamedTable`.

## Expected

Every occurrence of the local `t` is rewritten, including the receiver segment of
`function t:m()`, which reads the same binding:

```lua
local renamedTable = {}
function renamedTable:m() end
renamedTable:m()
```

## Actual

The declaration's receiver segment is left bound to a name that no longer exists, so the file no
longer parses to the same program: `function t:m()` now declares a method on a fresh global `t`.

Executed on `128ba091` with **no plugin change applied**, through
`myFixture.renameElementAtCaret("renamedTable")`:

```
BUG476[colon] RENAMED
BUG476[colon]   1| local renamedTable = {}
BUG476[colon]   2| function t:m() end          <- left behind
BUG476[colon]   3| renamedTable:m()
```

The same run drove two controls, and both reproduce it:

```
BUG476[declCaret] RENAMED          -- caret on the `local <caret>t` declaration instead
BUG476[declCaret]   1| local renamedTable = {}
BUG476[declCaret]   2| function t:m() end      <- left behind
BUG476[declCaret]   3| renamedTable:m()

BUG476[dotted] RENAMED             -- the DOTTED spelling, `function M.run()`
BUG476[dotted]   1| local renamedTable = {}
BUG476[dotted]   2| function M.run() end       <- left behind
BUG476[dotted]   3| renamedTable.run()
```

So the defect is **independent of the caret position** (a declaration caret and a usage caret both
produce it) and **independent of the colon/dot spelling**. It is not a colon-method defect.

## Suspected mechanism — not yet confirmed by a probe

`LuaNameReference.isReferenceTo` resolves the `t` of `function t:m()` through `LuaScopeProcessor`'s
`is LuaFuncDecl` branch, which matches a function-name head against the name being sought and
returns **that same leaf** so that a function can call itself
([LuaScopeProcessor.kt:79-84](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)):

```kotlin
is LuaFuncDecl -> {
    // Check function name (for recursion)
    if (element.funcName.nameRef.identifier.text == name) {
        result = element.funcName.nameRef.identifier
```

If that is the route, the identity test against the `local t` leaf is false, the segment is never
collected as a usage, and no usage rewrite is prepared for it. **This section is a hypothesis read
from the source; the measurements above are of the symptom only.** Confirm the resolve target
before fixing — a fix aimed at the wrong branch would break recursion resolution, which the same
code delivers.

## Why it is not [[REFACT-09]]'s

[[REFACT-09]] renames a **method**, not its receiver. Its `REFACT-09-05` (caret on the receiver
renames the receiver) still holds: `m` is untouched in every row above. Fixing this inside
REFACT-09 would widen a colon-method rename into a receiver rename, which is a different
refactoring with a different usage set. [[REFACT-09]] `risks-and-gaps.md` Gap 2.1 records the
measurement and points here.

## Suggested fix sketch

Whatever collects a local's usages must treat a function-name **head** whose resolution reaches the
same binding as a usage of that binding, while leaving the function's own **last** segment alone —
the distinction `LuaDeclarationSite.functionNameLeafOf`
([LuaDeclarationSite.kt:111](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaDeclarationSite.kt))
already draws and that `LuaRenameProcessor.receiverSegmentRefusal`
([LuaRenameProcessor.kt:399-403](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt))
already relies on in the other direction.

## Acceptance

- The three fixtures above rewrite the receiver segment as well, in one undoable command.
- Recursion still resolves: `local function f() f() end` renames both occurrences.
- A caret on the receiver segment itself is still refused by `receiverSegmentRefusal`.
- `LuaRenameTest`, `LuaRenameCrossFileTest` and `LuaRenameUndoTest` stay green.
