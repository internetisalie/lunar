---
id: "BUG-478"
title: "Find Usages is refused at a colon-method declaration although its usage set is computable"
type: "bug"
parent_id: "BUG"
status: "done"
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
[[NAV-13]]'s own `human-verification-checklists.md` **HV-2** specifies. HV-2 therefore **failed as
written** on the 2026-09-03 run, and now passes on the 2026-09-05 re-run recorded there.

It also blocked HV-9 steps 2 and 4 (Find Usages and Safe Delete driven at a declaration); the Find
Usages half is unblocked by the fix, the Safe Delete half has not been re-driven.

`ReferencesSearch` is correct, so [[REFACT-09]] — which consumes the search API, not the action — is
unaffected.

## The cause, as measured

The hypothesis below was right about the *layer* and had to be executed to name the element. A probe
instrumented the editor's own data context at four carets — with the global-function control in every
run — and read what the platform actually hands the provider:

| caret | `findReference().resolve()` | `TargetElementUtil.findTargetElement` | `USAGE_TARGETS_KEY` | action |
| :-- | :-- | :-- | :-- | :-- |
| `function gfun()` declaration (control) | the `gfun` leaf — **itself** | the leaf | 1 | works |
| `t:m()` call site | the `m` declaration leaf | the leaf | 1 | works |
| `function t:m()` declaration | **null** | **`LuaNameRefImpl`** | **null** | refused |
| `function M.run()` declaration | **null** | **`LuaNameRefImpl`** | **null** | refused |

**The provider is never asked about the leaf.** `TargetElementUtilBase.doFindTargetElement` tries the
reference branch first; a colon or dotted declaration name resolves to nothing, so it falls through to
the `PsiNamedElement`-parent branch, which answers with the enclosing `LuaNameRef` **composite**.
`LuaFindUsagesProvider.canFindUsagesFor` is `LuaDeclarationSite.kindOf(...) != null`, and `kindOf`
classifies IDENTIFIER **leaves** only — so it returns false for that composite,
`DefaultUsageTargetProvider` contributes no `UsageTarget`, `FindUsagesAction`'s `targetVariants` comes
back empty, and `resolver.kt` paints *"Cannot search for usages from this location."*

A global function declaration escaped only because its own name resolves to **itself**, which keeps it
on the reference branch and delivers the leaf. The two carets that work and the two that do not differ
in exactly one measured value.

## The fix

`LuaTargetElementEvaluator.getNamedElement` now returns the declaration's own IDENTIFIER leaf for
`METHOD_FUNCTION` and `DOTTED_FUNCTION`, joining `NUMERIC_FOR_VARIABLE` (BUG-469) on the same hook and
for the same reason: the declaration caret and the usage caret then target **one** element — the one
`ReferencesSearch` already answers correctly. Rename is unaffected in either direction, because
`LuaRenameProcessor.canProcessElement` admits a `LuaNameRef` and a classified leaf alike and
`substituteElementToRename` normalises both to this same leaf.

Pinned by `LuaDeclarationFindUsagesActionTest`, which drives `IdeActions.ACTION_FIND_USAGES` through the
editor's data context rather than calling `ReferencesSearch` — the search API was already correct, so a
test at that layer would have passed with the defect present. Reverting the fix reddens both declaration
cases with *"UsageView wasn't shown"* and leaves the global-function control green.

Re-driven live on 2026-09-05 in sandbox GoLand 2026.1.3, caret column verified at `2:12` before
invoking: <kbd>Alt+F7</kbd> opens the Find window with *Global function → m*, **1 result**, `t:m()` on
line 3; the `function gfun()` control in the same session gives *Global function → gfun*, **2 results**.

## A note on method, because this report was wrong once

The first version of this report claimed the action was **silent** — no window, no hint. That was a
caret artifact: the caret sat one character past the identifier, where the platform legitimately has
no element. The defect is real but its signature is a **visible refusal**, and the difference matters
because "silent" and "refuses" point at different code.

**Verify the caret's column from the status bar before believing any negative result from a
keyboard-driven IDE probe.** A control elsewhere in the file proves the *action* works; it says
nothing about where the caret is. `2:16` and `2:17` are one keystroke apart and mean opposite things.
