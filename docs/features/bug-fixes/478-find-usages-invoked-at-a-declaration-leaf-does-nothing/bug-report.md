---
id: "BUG-478"
title: "Find Usages invoked at a declaration leaf does nothing, silently"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-478: Find Usages invoked at a declaration leaf does nothing, silently

<kbd>Alt+F7</kbd> with the caret on a colon-method declaration's own name opens no tool window and
shows no hint. Nothing is dispatched — a previously-open results tab stays on screen unchanged. The
same action **from a call site** returns the correct usage set.

Silence is the defect. A user cannot tell "this symbol has no usages" from "the action did not run",
and the platform's own failure mode here is a visible hint, not nothing.

## Found by

[[NAV-13]]'s human verification (`human-verification-checklists.md`, HV-2). NAV-13 is not the cause:
it changes what a *call site* resolves to, and this is the declaration end, reached without any
reference. It is what blocks HV-9 steps 2 and 4 from being driven.

## Reproduction

Sandbox GoLand 2026.1.3 on the builder VM, driven live 2026-09-03, all in one session.

| # | Fixture | Caret | Observed |
| :-- | :-- | :-- | :-- |
| 1 | `local t = {}` / `function t:m() end` / `t:m()` | the **declaration**'s `m` (`2:12`) | **nothing** — no window, no hint |
| 2 | as #1 | the **call site**'s `m` (`3:4`) | **works** — "Global function → m", 1 result, `hv1.lua:3` |
| 3 | **control** — `function gfun() end` / `gfun()` / `gfun()` | the declaration's `gfun` | **works** — "Global function → gfun", 2 results |
| 4 | as #1 | `local t` on line 1 (`1:7`) | visible hint: *"Cannot search for usages from this location. Place the caret on the element to find usages for and try again."* |

Row 3 proves the tool window and the driving are sound. Row 4 shows what a *refusal* looks like — a
hint — which is what makes row 1's silence the anomaly rather than the expected refusal.

Row 4 is itself the known shape recorded in `.agents/AGENTS.md`: Lua locals have declaration PSI but
no `PsiNameIdentifierOwner`, so the platform cannot build a target from them. Whether row 1 is the
same cause presenting differently, or a distinct one, is **not established**.

## What is known about the cause

`LuaFindUsagesProvider.canFindUsagesFor` is
`LuaDeclarationSite.kindOf(element) != null || LuaCatsTypeDeclarations.isDeclarationLeaf(element)`
([:41](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaFindUsagesProvider.kt)),
and `kindOf` of a colon-method name leaf is `METHOD_FUNCTION`, i.e. **non-null** — so the provider
should accept it. That it accepts is consistent with row 1 showing no "cannot search" hint.

`ReferencesSearch` itself is correct and pinned green: `LuaColonCallFindUsagesTest` (NAV-13
requirements cases 7 and 8) drives `ReferencesSearch.search(<declaration leaf>, allScope)` and gets
the call site back, with `isReferenceTo` true. **So the search API works and the action does not**,
which places the fault between `FindUsagesAction`/`FindUsagesManager` and the provider — most likely
in what `TargetElementUtil` hands the action for a declaration leaf.

**Do not start from that hypothesis.** Instrument the action and record what it actually receives;
NAV-13's own DR-05 exists because reading a gate and asking whether it admits something is not a
sound way to draw this kind of conclusion.

## Impact

`ReferencesSearch` returning the right answer is what [[REFACT-09]] consumes, and that is unaffected.
What is affected is the user-visible half of `NAV-13-03`: Find Usages on a colon method is reachable
from the call site and not from the declaration, which is the more natural gesture.

## Fix strategy

Not decided — locate first, by execution. Whatever the cause, the fix must not make the silent case
merely *hint*: the declaration is a legitimate target whose usage set is known to be computable, so
the correct outcome is the results window, matching row 3.

## Surface

User-visible IDE surface (the Find Usages tool window), so the fix needs a `verify-in-ide` pass as
well as a unit test.
