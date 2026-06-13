---
id: "REFACT-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-03"
status: "planned"
priority: "medium"
folders:
  - "[[features/refactoring/03-safe-delete-refactoring/requirements|requirements]]"
---

# Risks & Design Gaps: REFACT-03 Safe Delete

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `REFACT-03-R-01` | **Find-usages dependency** | Medium | Correctness rests on NAV-02's `isReferenceTo`/declaration recognition; sequence NAV-02 first. |
| `REFACT-03-R-02` | **Partial-statement delete** | Medium | Multi-name `local x, y` must delete only the targeted name; covered by an explicit test. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `REFACT-03-G-01` | **Cascading delete** | Deleting a symbol whose initializer has side effects, or dependent declarations, is not cascaded. | `REFACT-03-DR-01` |

## De-risking Tasks (DR)

- [ ] `REFACT-03-DR-01`: Decide whether to offer cascading delete of declarations that become
      unused after the primary deletion.
