---
id: "NAV-03-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "NAV-03"
status: "planned"
priority: "medium"
folders:
  - "[[features/navigation/03-go-to-class-file-symbol/requirements|requirements]]"
---

# Risks & Design Gaps: NAV-03 Go to Class / File / Symbol

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `NAV-03-R-01` | **Index value types** | Low | `LuaAliasIndex`/`LuaClassNameIndex` index `LuaLocalVarDecl`, `LuaGlobalDeclarationIndex` indexes `LuaFuncDecl`; `getElements` must pass the matching class — pinned in §2.1/§2.2. |
| `NAV-03-R-02` | **Icon/presentation gaps** | Low | If an indexed element lacks an icon, add it to that element's `ItemPresentation`; cosmetic only. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `NAV-03-G-01` | **Plain global variables** | `LuaGlobalDeclarationIndex` indexes function-style globals; plain `X = 1` globals may not appear in Go to Symbol. | `NAV-03-DR-01` |

## De-risking Tasks (DR)

- [ ] `NAV-03-DR-01`: Decide whether plain global assignments should be indexed for Go to
      Symbol (shares the INSP-01/NAV-02 plain-global-indexing question).
