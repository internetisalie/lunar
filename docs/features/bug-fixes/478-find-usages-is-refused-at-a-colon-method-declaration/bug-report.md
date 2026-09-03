---
id: "BUG-478"
title: "Find Usages is refused at a colon-method declaration although its usage set is computable"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-478: Find Usages is refused at a colon-method declaration

<kbd>Alt+F7</kbd> with the caret on the `m` of `function t:m()` declines with

> Cannot search for usages from this location.
> Place the caret on the element to find usages for and try again.

**The usage set exists and the platform is refusing to compute it.** All three preconditions hold:

| | |
| :-- | :-- |
| `LuaFindUsagesProvider.canFindUsagesFor` | **true** — it is `LuaDeclarationSite.kindOf(element) != null` ([:41](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaFindUsagesProvider.kt)), and `kindOf` of a colon-method name leaf is `METHOD_FUNCTION` |
| `ReferencesSearch.search(<that leaf>, allScope(project))` | **returns the call site**, with `isReferenceTo` true — pinned green by `LuaColonCallFindUsagesTest` ([[NAV-13]] requirements cases 7 and 8) |
| the Find Usages action | **declines** |

## This is not the plain-local limitation

`.agents/AGENTS.md` records that Lua locals have declaration PSI but no `PsiNameIdentifierOwner`, and
a plain `local t` refuses with the same hint. **For a plain local that refusal is correct** — there
is no computable usage set behind it. Here there is, and it is under test. The two cases share a
message and nothing else, and an earlier version of this report was withdrawn on exactly that
conflation.

## Reproduction

Sandbox GoLand 2026.1.3 on the builder VM, plugin loaded, driven live 2026-09-03. **Caret column
verified from the status bar before each invocation** — see the note at the end.

```lua
local t = {}
function t:m() end
t:m()
```

| # | Caret | Verified column | Observed |
| :-- | :-- | :-- | :-- |
| 1 | the `m` of `function t:m()` | `3:12` / `2:12` | **"Cannot search for usages from this location."** |
| 2 | the `m` of `t:m()` (call site) | `3:4` | **works** — "Global function → m", 1 result, line 3, `t:m()` |
| 3 | **control** — `function gfun() end` / `gfun()` / `gfun()`, caret on the declaration's `gfun` | — | **works** — "Global function → gfun", 2 results |

Row 3 is the one that makes this a defect rather than a general property of declarations: a *global
function* declaration searches fine in the same session. Row 2 shows the same underlying reference
set being found from the other end.

## Impact

This is the user-visible half of `NAV-13-03`. Find Usages on a colon method is reachable from the
call site and **not** from the declaration, which is the more natural gesture — and it is the gesture
[[NAV-13]]'s own `human-verification-checklists.md` **HV-2** specifies. HV-2 therefore **fails as
written**, and is recorded there as failing rather than as a pass reached by a different route.

It also blocks HV-9 steps 2 and 4 (Find Usages and Safe Delete driven at a declaration).

`ReferencesSearch` is correct, so [[REFACT-09]] — which consumes the search API, not the action — is
unaffected.

## Where the fault is not

Not in `LuaFindUsagesProvider` (its gate returns true) and not in `LuaNameReferenceSearcher` or
`LuaNameReference.isReferenceTo` (the search returns the right answer under test). That places it
between `FindUsagesAction`/`FindUsagesManager` and the provider — most plausibly in what
`TargetElementUtil` yields for a declaration leaf, since the platform builds its handler from that
element rather than from the leaf directly.

**That is a hypothesis, not a finding. Execute it — instrument the action and record what it actually
receives — rather than reading the gate and asking whether it admits the element.** [[NAV-13]]'s
DR-05 exists because the reading approach is unsound here, and this bug is one layer from where that
was learned.

## A note on method, because this report was wrong once

The first version of this report claimed the action was **silent** — no window, no hint. That was a
caret artifact: the caret sat one character past the identifier, where the platform legitimately has
no element. The defect is real but its signature is a **visible refusal**, and the difference matters
because "silent" and "refuses" point at different code.

**Verify the caret's column from the status bar before believing any negative result from a
keyboard-driven IDE probe.** A control elsewhere in the file proves the *action* works; it says
nothing about where the caret is. `2:16` and `2:17` are one keystroke apart and mean opposite things.
