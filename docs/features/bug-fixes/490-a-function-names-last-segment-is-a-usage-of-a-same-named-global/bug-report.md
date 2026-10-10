---
id: "BUG-490"
title: "A dotted or colon function's own name is counted as a usage of a same-named global function"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-490: `function t:m()`'s `m` is a usage of an unrelated global `function m()`

Found while fixing [[BUG-476]], which closed the **lexical** half of this and left the global half
as it was.

## Reproduction

```lua
function m() end
local t = {}
function t:m() end
t:m()
```

`ReferencesSearch` on the global `m`@9 returns `[41]` — the `m` of `function t:m()`. This is asserted
as correct today by `LuaColonCallUsageWithdrawalTest.aSameNamedGlobalFunctionLosesTheColonCallSite`
(`usageOffsetsAt(9) == [41]`, "only the method declaration's own name remains").

## Expected

No usages. In `function t:m()`, `m` is a table key — the statement is `t.m = function(self) end` —
so it reads no variable, and certainly not the global function `m`.

## Actual

`[41]`. Because `GLOBAL_FUNCTION` renames search the project through the same `ReferencesSearch`,
renaming the global `m` should also rewrite `function t:m()` to `function t:<new>()`, silently
renaming the method. **That consequence is inferred from the search result, not executed.**

## Mechanism — read from the source, not probed

[[BUG-476]] made a segment after the head of a function name skip **Phase 1** (lexical) resolution
in `LuaNameReference` (`lexicalTargetOf`), so a same-named *local* no longer captures it. **Phase 2**
still runs for that segment: `getQualifiedName` returns null for a `LuaFuncNameProperty` /
`LuaFuncNameMethod` parent, so the bare-name lookups run, and `LuaGlobalDeclarationIndex` under
`"m"` returns the global `function m()`, whose `declarationIdentifier` is `m`@9 — so
`isReferenceTo(m@9)` is true.

## Suggested fix sketch

Treat the non-head segment as resolving through nothing at all (or only through its qualified name),
not through the bare-name lookups. The test above then pins the defect and needs its expectation
changed to `[]` — check that Find Usages from a method declaration ([[BUG-478]]) still finds call
sites, since that path targets the leaf rather than resolving it.
