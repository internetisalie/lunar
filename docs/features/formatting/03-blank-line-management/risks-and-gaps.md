---
id: "FORMAT-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "FORMAT-03"
priority: "medium"
folders:
  - "[[features/formatting/03-blank-line-management/requirements|requirements]]"
---

# Risks & Design Gaps: FORMAT-03 Blank Line Management

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `FORMAT-03-R-01` | **Spacing regressions** | Medium | Changing `STANZA_SPACING` affects existing function-separation tests; covered by TC-FORMAT-03-01/02 and the existing formatter suite. |
| `FORMAT-03-R-02` | **EOF processor scope** | Low | The post-processor must only adjust the trailing range, not reflow the file. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `FORMAT-03-G-01` | **Around-class blanks** | Whether `@class` table groupings get blank-line rules. | `FORMAT-03-DR-01` |

## De-risking Tasks (DR)

- [ ] `FORMAT-03-DR-01`: Decide whether blank-line management extends to `@class`/table groups.
