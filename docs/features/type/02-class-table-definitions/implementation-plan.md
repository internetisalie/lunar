---
id: TYPE-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: TYPE-02
status: "planned"
priority: "high"
folders:
  - "[[features/type/02-class-table-definitions/requirements|requirements]]"
---

# Implementation Plan: TYPE-02 Class/Table Definitions

TYPE-02-01..04 are already implemented in `LuaTypeManagerImpl`; this plan adds TYPE-02-05.

## Phase 0: Regression coverage [Must] — TYPE-02-01/02/03/04
- [ ] Add/confirm tests for the existing behaviour: `resolveType` materializes `@class`
      members, `@alias` targets, and inherited members (requirements §5.1/§5.2).

## Phase 1: Implicit field discovery [Should] — TYPE-02-05
- [ ] `LuaImplicitFields.collect` (§3.1): scan the class's defining files for
      `ClassName.field = …` and `self.field = …` (in `ClassName` methods); add members, explicit
      `@field` winning.
- [ ] Call it from `materializeClass` after the `@field` loop (§2.1).
- [ ] Tests: TC-TYPE-02-05 (direct + self implicit fields appear; explicit `@field` not
      overwritten).

## Verification Tasks
- Unit: `resolveType("Player").getMembers()` includes inherited + implicit fields; alias
  resolves; explicit `@field` precedence.
- Manual: completion/hover on a class table shows implicitly-assigned fields.
