---
id: FORMAT-05-PLAN
title: "Implementation Plan"
type: plan
parent_id: FORMAT-05
priority: "medium"
folders:
  - "[[features/formatting/05-alignment-logic/requirements|requirements]]"
---

# Implementation Plan: FORMAT-05 Alignment Logic

## Phase 1: Settings [Should] — FORMAT-05-03
- [ ] Add `ALIGN_CONSECUTIVE_ASSIGNMENTS` / `ALIGN_TABLE_FIELDS` to `LuaCodeStyleSettings` +
      `showCustomOption` (§2.1/§3.3).

## Phase 2: Assignment alignment [Should] — FORMAT-05-01
- [ ] In `LuaFormatBlock`, group adjacent assignment/local statements and share one
      `Alignment.createAlignment(true)` on their `ASSIGN` leaves (§3.1).
- [ ] Test: TC-FORMAT-05-01, TC-FORMAT-05-03 (off).

## Phase 3: Table-field alignment [Should] — FORMAT-05-02
- [ ] Share one alignment across a constructor's `FIELD` `ASSIGN` leaves (§3.2).
- [ ] Test: TC-FORMAT-05-02.

## Verification Tasks
- Unit (`reformat`): aligned `=` columns when on; no padding when off.
- Manual: toggle the option → reformat reflects it.
