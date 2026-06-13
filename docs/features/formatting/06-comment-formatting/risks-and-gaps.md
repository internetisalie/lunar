---
id: "FORMAT-06-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "FORMAT-06"
status: "planned"
priority: "medium"
folders:
  - "[[features/formatting/06-comment-formatting/requirements|requirements]]"
---

# Risks & Design Gaps: FORMAT-06 Comment Formatting

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `FORMAT-06-R-01` | **Doc-comment safety** | Medium | The wrap processor must exclude `LUACATS_COMMENT`; covered by TC-FORMAT-06-03. |
| `FORMAT-06-R-02` | **Semantic comments** | Low | Some `--` comments are directives (e.g. `-- luacheck: ignore`); wrap is opt-in and off by default to avoid breaking them. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `FORMAT-06-G-01` | **Directive detection** | Whether to skip known tool directives when wrapping. | `FORMAT-06-DR-01` |

## De-risking Tasks (DR)

- [ ] `FORMAT-06-DR-01`: Exclude `-- luacheck:`/`-- selene:` directive comments from hard-wrap.
