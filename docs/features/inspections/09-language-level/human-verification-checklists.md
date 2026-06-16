---
id: "INSP-09-CHECKLIST"
title: "Verification Checklists"
type: "qa"
status: "planned"
parent_id: "INSP-09"
folders:
  - "[[features/inspections/09-language-level/requirements|requirements]]"
---

# Verification Checklists: INSP-09 — Language-Level Compliance

Run in a sandbox IDE (`./gradlew runIde`) with a Lua file open. Change the language level via
Settings → project Lua settings (or the inspection's "Upgrade project to …" quick fix).

## 1. Per-level detection
### Scenario 1.1: Attributes (5.4)
- [ ] Level 5.1, type `local x <const> = 1` → ERROR on `<const>` ("Variable attributes … 5.4").
- [ ] Level 5.4, same text → NO error.

### Scenario 1.2: Bitwise & integer division (5.3)
- [ ] Level 5.1, type `local x = 1 & 2` → ERROR on `&` ("Bitwise AND … 5.3+").
- [ ] Level 5.2, type `local x = 10 // 3` → ERROR on `//` ("Integer division … 5.3+").
- [ ] Level 5.2, type `local x = ~5` → ERROR on unary `~` ("Bitwise NOT … 5.3+").
- [ ] Level 5.3, all three above → NO error.

### Scenario 1.3: Goto / label (5.2)
- [ ] Level 5.1, type `goto exit` → ERROR ("Goto statements … 5.2+").
- [ ] Level 5.1, type `::exit::` → ERROR ("Labels … 5.2+").
- [ ] Level 5.2, both → NO error.

## 2. Quick fixes
### Scenario 2.1: Fixes apply
- [ ] On a 5.1 `goto exit` error, invoke "Remove goto statement" → the statement is deleted.
- [ ] On a 5.2 `10 // 3` error, invoke "Replace // with / and math.floor()" → becomes `math.floor(10 / 3)`.
- [ ] On any error, invoke "Upgrade project to Lua 5.x" → the project level changes and the error clears.

## 3. No false positives & no double-reporting
### Scenario 3.1: Allowed constructs
- [ ] Level 5.1, type `local x = 2 ^ 3`, `local y = 10 / 3`, `local s = "1 & 2"`, `-- goto here`
      → NO language-level error on any.

### Scenario 3.2: Single report (migration sanity)
- [ ] Level 5.1, type `local x = 1 & 2` → exactly ONE error on `&` (not two). Confirms the old
      annotator no longer also fires.
- [ ] In Settings → Editor → Inspections → Lua, "Language level compliance" appears and can be
      disabled; disabling removes the errors (proves it is an inspection, not an annotator).

## See Also
- Requirements: [requirements.md](requirements.md)
- Plan: [implementation-plan.md](implementation-plan.md)
