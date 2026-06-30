---
id: "NAV-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-02"
priority: "medium"
folders:
  - "[[features/navigation/02-find-usages/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-02 Find Usages

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-02-R-01` | **Target recognition** | Medium | `canFindUsagesFor` must accept exactly the identifier leaves `resolve()` returns; covered by TC-NAV-02-01/02 and shared with INSP-01's declaration-site set. |
| `NAV-02-R-02` | **Global search cost** | Medium | Search is index-driven (word index + `LuaGlobalDeclarationIndex`); no un-opened-file parsing. |
| `NAV-02-R-03` | **Plain cross-file globals** | Low | Plain global assignments (`X = 1`) resolve cross-file only if indexed; shares INSP-R-03. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-02-G-01` | **Table-field usages** | `t.x` fields are not uniquely resolvable; only a text fallback is planned. | `NAV-02-DR-01` |
| `NAV-02-G-02` | **LuaCATS type usages** | Needs a reference from `@type` names to `@class` decls. | `NAV-02-DR-02` |

## De-risking Tasks (DR)

- [ ] `NAV-02-DR-01`: Decide the table-field find-usages strategy (text search scoped by
      receiver type, or defer until field references are modeled).
- [ ] `NAV-02-DR-02`: Wire `@type` → `@class` references (NAV-07) so `@class` find-usages lists
      annotation sites (NAV-02-05).
