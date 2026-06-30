---
id: "NAV-06-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-06"
priority: "medium"
folders:
  - "[[features/navigation/06-hierarchy-view/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-06 Hierarchy View

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-06-R-01` | **Subtype scan cost** | Medium | Could-priority; a linear scan over `LuaClassNameIndex` keys is acceptable initially, cached per hierarchy session. Reverse index if needed. |
| `NAV-06-R-02` | **Inheritance cycles** | Low | `visited` class-name guard in both directions. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-06-G-01` | **Reverse subtype index** | No `superType → subclasses` index exists; subtype lookup scans all classes. | `NAV-06-DR-01` |

## De-risking Tasks (DR)

- [ ] `NAV-06-DR-01`: If the subtype scan is slow on large projects, add a stub index keyed on
      each class's supertype name(s) for O(1) subtype lookup.
