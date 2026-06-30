---
id: "FORMAT-05-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "FORMAT-05"
priority: "medium"
folders:
  - "[[features/formatting/05-alignment-logic/requirements|requirements]]"
---

# Risks & Design Gaps: FORMAT-05 Alignment Logic

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `FORMAT-05-R-01` | **Run-boundary detection** | Medium | Line-gap/non-assignment boundary must be precise; covered by TC-FORMAT-05-01 + a mixed-statement test. |
| `FORMAT-05-R-02` | **Alignment + wrapping interaction** | Low | Keep FORMAT-04 wrapping and FORMAT-05 alignment independent; default both off. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `FORMAT-05-G-01` | **Comment alignment** | Trailing-comment alignment across a run is deferred to FORMAT-06. | `FORMAT-05-DR-01` |

## De-risking Tasks (DR)

- [ ] `FORMAT-05-DR-01`: Decide whether trailing comments participate in assignment alignment.
