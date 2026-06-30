---
id: "NAV-09-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-09"
priority: "medium"
folders:
  - "[[features/navigation/09-return-highlighter/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-09 Return Highlighter

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-09-R-01` | **Nested-scope correctness** | Low | The `enclosingFunction(t) === fn` identity test cleanly excludes nested-function returns; covered by TC-NAV-09-02. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-09-G-01` | **Implicit exit points** | Whether to also mark implicit end-of-function fall-through as an exit point. | `NAV-09-DR-01` |

## De-risking Tasks (DR)

- [ ] `NAV-09-DR-01`: Decide whether implicit function exits (no explicit `return`) are
      highlighted alongside `return` keywords.
