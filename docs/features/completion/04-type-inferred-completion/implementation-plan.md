---
id: COMP-04-PLAN
title: "Implementation Plan"
type: plan
parent_id: COMP-04
priority: "high"
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
---

# COMP-04: Type-Inferred Completion Implementation Plan

Enhances the **existing** member-completion path in `LuaCompletionContributor` plus two
type-engine additions in `LuaTypesVisitor`. Phases map to requirement IDs and design sections.

## Phase 1: Type-engine additions [Must] — COMP-04-08/09
- [ ] `self` typing in `LuaTypesVisitor` (§3.2): for method defs
      (`funcName.funcNameMethod != null`), edge receiver-type → `self` node.
- [ ] `setmetatable __index` in `LuaTypesVisitor` (§3.3): on `setmetatable(t, mt)`, add
      `mt.__index` table (or function-return table) to `t`'s `superTypes`.
- [ ] Unit tests against the type graph: `self` resolves to the class (TC-06 precondition);
      `setmetatable({}, {__index={x=1}})` exposes `x` (TC-05 precondition).

## Phase 2: Member provider presentation & filtering [Must/Should] — COMP-04-01/02/06/07/10
- [ ] In `LuaCompletionContributor`'s member provider, enumerate via `gt.getMembers()` (§3.1);
      keep the existing colon→Function filter (COMP-04-02).
- [ ] Visibility filter (§3.1 step 4) using `snap.graphTypeToLuaType(gt).resolveMember(name)
      ?.visibility` + enclosing-method scope check.
- [ ] Union-partial marking (§3.1 step 3) and overload expansion (§3.1 step 6).
- [ ] `LuaMemberLookup.create` (§2.4): Field/Method icon, type/signature tail text, `(partial)`.
- [ ] Integration tests: TC-01 (literal), TC-02 (inherited `@field`), TC-03 (colon method),
      TC-04 (union), TC-05 (`__index`), TC-06 (`self`).

## Phase 3: Generics & polish [Should/Could] — COMP-04 generics, presentation
- [ ] Confirm `LuaParameterizedType.getMembers()` substitution flows through
      `graphTypeToLuaType` (§3.4); add a `List<string>` member test or record `COMP-04-DR-02`.
- [ ] Tail-text/icon refinements; prioritise exact-prefix members.

## Verification Tasks
- Unit: `LuaTypesVisitor` self + setmetatable; member presentation helper.
- Integration (`BasePlatformTestCase` + `myFixture.completeBasic()` /
  `myFixture.lookupElementStrings`): TC-01…06 assert the expected member names appear (and colon
  hides fields, partial members are marked).
- Manual: see `human-verification-checklists.md`.
