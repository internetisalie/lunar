---
id: NAV-02-PLAN
title: "Implementation Plan"
type: plan
parent_id: NAV-02
priority: "medium"
folders:
  - "[[features/navigation/02-find-usages/requirements|requirements]]"
---

# Implementation Plan: NAV-02 Find Usages

Extends the existing `LuaLabelFindUsagesProvider` and adds a usage-type provider. Phases map to
requirement IDs.

## Phase 1: Provider broadening [Must] — NAV-02-01/02/03
- [ ] Rename/extend to `LuaFindUsagesProvider`; `canFindUsagesFor` accepts declaration
      identifiers (§3.1) plus `LuaLabelName`; `getType`/`getDescriptiveName`/`getNodeText`
      per the declaration kind.
- [ ] Repoint `<lang.findUsagesProvider>` to the new class.
- [ ] Tests: TC-NAV-02-01 (local), TC-NAV-02-02 (cross-file global), TC-NAV-02-03 (label),
      TC-NAV-02-04 (scope isolation).

## Phase 2: Read/Write classification [Must] — NAV-10 integration
- [ ] `LuaReadWriteUsageTypeProvider : UsageTypeProvider` (§3.2, write-target test) +
      `<usageTypeProvider>` registration.
- [ ] Test: TC-NAV-02-05.

## Phase 3: Deferred breadth [Could/Should]
- [ ] `NAV-02-DR-01`: table-field text fallback (NAV-02-04).
- [ ] `NAV-02-DR-02`: `@type`→`@class` usages once NAV-07 reference contributors land
      (NAV-02-05).

## Verification Tasks
- Unit/integration (`BasePlatformTestCase` + `myFixture.findUsages`): counts + read/write per
  TC-NAV-02-01…05.
- Manual: Find Usages and Show Usages popup on a global function across two files.
