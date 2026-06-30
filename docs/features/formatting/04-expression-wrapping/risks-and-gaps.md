---
id: "FORMAT-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "FORMAT-04"
priority: "medium"
folders:
  - "[[features/formatting/04-expression-wrapping/requirements|requirements]]"
---

# Risks & Design Gaps: FORMAT-04 Expression Wrapping

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `FORMAT-04-R-01` | **Indent interaction** | Medium | Wrapping relies on the existing continuation indents; verify wrapped items align correctly (TC-FORMAT-04-01/02). |
| `FORMAT-04-R-02` | **Closing-brace placement** | Low | Confirm the `}`/`)` lands on its own line when chopped; add spacing rule if needed. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `FORMAT-04-G-01` | **Binary-expression wrapping** | Long `a and b and c …` chains are not wrapped in v1. | `FORMAT-04-DR-01` |

## De-risking Tasks (DR)

- [ ] `FORMAT-04-DR-01`: Decide whether to wrap long binary/boolean expression chains.
