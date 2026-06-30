---
id: "NAV-10-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-10"
priority: "medium"
folders:
  - "[[features/navigation/10-access-detector/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-10 Access Detector

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-10-R-01` | **Classification drift** | Low | The write-target test is shared with INSP-01/NAV-02; a single helper keeps them consistent. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-10-G-01` | **Compound assignment** | Lua has no `+=`; if future sugar is added, `ReadWrite` handling must be revisited. | `NAV-10-DR-01` |

## De-risking Tasks (DR)

- [ ] `NAV-10-DR-01`: Revisit `Access.ReadWrite` only if compound-assignment sugar is introduced.
