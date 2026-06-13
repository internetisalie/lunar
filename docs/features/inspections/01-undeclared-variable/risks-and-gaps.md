---
id: "INSP-01-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "INSP-01"
status: "planned"
priority: "medium"
folders:
  - "[[features/inspections/01-undeclared-variable/requirements|requirements]]"
---

# Risks & Design Gaps: INSP-01 Undeclared Variable

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `INSP-R-01` | **Resolution Performance** | High | `resolve()` can be expensive. Annotators run on the EDT. Use caching if necessary, though `resolve()` results are usually cached by the platform. |
| `INSP-R-02` | **Global Pollution** | Medium | Large Lua projects often have many implicit globals. Too many warnings can lead to "warning fatigue". |
| `INSP-R-03` | **Complex Assignments** | Low | Multi-assignments like `x, y = f()` need careful context detection to ensure `x` and `y` are seen as write contexts. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `INSP-G-01` | **LuaCheck Compatibility** | Should we support `.luacheckrc` or similar for global lists? | `INSP-DR-01`: Evaluate effort to parse basic `.luacheckrc` globals. |

## De-risking Tasks (DR)

- [ ] `INSP-DR-01`: Research existing parser support for Luacheck config in the project.
