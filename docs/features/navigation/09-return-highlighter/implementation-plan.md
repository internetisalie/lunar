---
id: NAV-09-PLAN
title: "Implementation Plan"
type: plan
parent_id: NAV-09
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/09-return-highlighter/requirements|requirements]]"
---

# Implementation Plan: NAV-09 Return Highlighter

## Phase 1: Handler [Could] — NAV-09-01/03
- [ ] `LuaReturnHighlightUsagesHandlerFactory` + `LuaReturnHighlightHandler` (§2.1/§3.1) +
      `<highlightUsagesHandlerFactory>`.
- [ ] `enclosingFunction` helper + same-scope `RETURN` collection (excludes nested functions).
- [ ] Tests: TC-NAV-09-01 (same-scope returns highlighted), TC-NAV-09-02 (nested excluded).

## Phase 2: Function keyword [Could] — NAV-09-02
- [ ] Optionally add the enclosing `FUNCTION` keyword to the highlight set.

## Verification Tasks
- Unit (`myFixture` highlight-usages): caret on a `return` highlights only same-scope returns.
- Manual: Next/Previous highlighted usage cycles the returns.
