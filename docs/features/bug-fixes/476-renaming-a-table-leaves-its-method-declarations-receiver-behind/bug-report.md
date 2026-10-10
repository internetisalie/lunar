---
id: "BUG-476"
title: "Renaming a table binding leaves the receiver segment of its function declarations behind"
type: "bug"
parent_id: "BUG"
status: "done"
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
- A caret on an **unbound** receiver segment (no binding in scope) is still refused by
  `receiverSegmentRefusal`. A caret on a **bound** receiver segment renames the binding it reads,
  as a caret on any other usage of that binding does.
- `LuaRenameTest`, `LuaRenameCrossFileTest` and `LuaRenameUndoTest` stay green.

## Resolution

**Where the segment was lost — executed, not read.** A probe over the reproduction printed every
name's resolve target and `ReferencesSearch` on the `local t`: the head `t`@22 of `function t:m()`
resolved to **itself**, and the search returned only the call site `[32]`. The rename processor
never saw the segment; resolution had already given it away. The suspected branch was the right
one — `LuaScopeProcessor`'s `is LuaFuncDecl` arm matched the head by text and stopped the walk —
and the same probe found three more answers of the same kind:

| Fixture | Name | Resolved to, before |
| :-- | :-- | :-- |
| `local M = {}` / `function M.run() M.x() end` | `M` in the body | the head `M` of its own declaration, so a rename of `M` missed it too |
| `local t = {}` / `function t:m(t) end` | the head `t` | the function's own **parameter** `t` |
| `local m = {}` / `function m:m() end` | the method name `m` | the head `m` |
| `local run = 1` / `function M.run() end` | the function name `run` | the `local run`, so renaming that local rewrote `M.run` |

**Fix — three rules, all in resolution:**

1. Only a bare `function f()` binds its own name. The head of a dotted or colon name is kept as a
   fallback and the walk continues, so a binding further out wins; when there is none, the head
   still answers for itself, exactly as before (`LuaScopeProcessor.offerFunctionName`).
2. A function's name sits outside its parameter scope (`LuaFuncDecl.processDeclarations` skips
   the parameter list when the walk comes up from the `LuaFuncName`).
3. A segment after the head is a table key, so it takes no lexical binding at all
   (`LuaNameReference.lexicalTargetOf`).

Rule 1's fallback is what keeps an **unbound** receiver unchanged: `function M.run() end` with no
`M` in scope still resolves `M` to itself, so its caret is still refused with the receiver-segment
reason, and nothing that reads that resolution sees a difference.

**A bound receiver caret renames its binding.** For a **bound** receiver (`local t = {}` /
`function <caret>t:m()`), the segment is a usage of `t`, so the platform hands the rename the
`local t` and <kbd>Shift+F6</kbd> renames the binding, every site included — the same thing a caret
on any other usage of `t` does. An unbound receiver is still refused. `LuaReceiverSegmentRenameTest`
pins both.

**Diagnostics that moved.** The deprecation inspection stops warning on a declaration's own method
name where that warning came from rules 1 and 3's wrong bindings — `function t:m()` beside a
deprecated `local function m`, and `function m:m()` reaching its own deprecated declaration through
its receiver. Call-site warnings are unchanged.

**Left open:** rule 3 is lexical only. The same segment can still be a usage of a same-named
*global* function through `LuaNameReference`'s bare-name index lookups — filed as [[BUG-490]].

## Live verification

GoLand 2026.1.3 sandbox on the libvirt builder, plugin built from `517e686d`
(`Loaded custom plugins: lunar`, 2026-10-10 17:34:08). Caret columns read from the status bar.

| Check | Caret | Result |
| :-- | :-- | :-- |
| <kbd>Shift+F6</kbd> on the bound receiver `t` of `function t:m()`, renamed to `renamedTable` | `2:10` | All three occurrences rewritten, including the head; one undo restores all three. |
| Find Usages on `t` | `3:1` and `2:10` | *Local variable `t`*, 2 results, both *Value read*: line 2 (the head) and line 3. |
| <kbd>Shift+F6</kbd> on the unbound receiver `M` of `function M.run()` | `1:10` | Refused: *"'M' is the receiver part of a function name, not the function's own name…"*; file unchanged. |

Find Usages invoked on the `local t` declaration itself is refused with *"Cannot search for usages
from this location"*. That is the existing behaviour for every plain local, recorded under
[[BUG-478]] and in REFACT-07's checklist, and is unchanged by this fix.

