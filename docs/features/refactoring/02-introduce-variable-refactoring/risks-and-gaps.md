---
id: "REFACT-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-02"
status: "planned"
priority: "medium"
folders:
  - "[[features/refactoring/02-introduce-variable-refactoring/requirements|requirements]]"
---

# Risks & Design Gaps: REFACT-02 Introduce Variable

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `REFACT-02-R-01` | **Anchor/insertion correctness** | Medium | Inserting before the right statement and indenting correctly is the crux; covered by TC-REFACT-02-01/03 incl. a nested-block case. |
| `REFACT-02-R-02` | **Scope safety of replace-all** | Medium | Replace only occurrences in the same block whose evaluation can't differ (no intervening reassignment); conservative `PsiEquivalenceUtil` match. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `REFACT-02-G-01` | **Multi-value expressions** | `local x = f()` truncates multi-return; whether to support `local a, b = f()`. | `REFACT-02-DR-01` |
| `REFACT-02-G-02` | **Side-effect duplication** | Replace-all must avoid changing evaluation order/side effects. | `REFACT-02-DR-02` |

## De-risking Tasks (DR)

- [ ] `REFACT-02-DR-01`: Decide multi-value introduce handling.
- [ ] `REFACT-02-DR-02`: Define the safety rule for replace-all with side-effecting expressions.
