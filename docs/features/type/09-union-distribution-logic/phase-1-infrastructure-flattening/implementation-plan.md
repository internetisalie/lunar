---
id: "TYPE-09-P1-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TYPE-09-P1"
priority: "high"
folders:
  - "[[features/type/09-union-distribution-logic/phase-1-infrastructure-flattening/requirements|requirements]]"
---

# Implementation Plan: TYPE-09 P1 — Infrastructure & Flattening

Done: `LuaGraphType.Union` + `flatten`. Remaining: canonicalization.

## Phase 1: Algebra [Should] — TYPE-09-P1-03/04
- [ ] `LuaTypeAlgebra.canonicalize` (§3.1): flatten → simplify (`T|any`, drop `Undefined`) →
      dedupe → sort → collapse.
- [ ] `LuaGraphType.Union.create` factory; route `fromLuaType` / `LuaTypesVisitor` union
      construction through it.
- [ ] Tests: TC-TYPE-09-P1-01 (flatten regression), TC-TYPE-09-P1-02 (simplify any),
      TC-TYPE-09-P1-03 (collapse single).

## Verification Tasks
- Unit: `canonicalize` table (flatten/simplify/dedupe/collapse), including `nil` preservation.
- Equal unions (any member order) produce structurally equal canonical forms.
