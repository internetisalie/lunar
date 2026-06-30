---
id: "TYPE-02-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TYPE-02"
priority: "high"
folders:
  - "[[features/type/02-class-table-definitions/requirements|requirements]]"
---

# Risks & Design Gaps: TYPE-02 Class/Table Definitions

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `TYPE-02-R-01` | **Implicit-field scan cost** | Medium | Scan bounded to the class's defining files and cached via `typeCache`; a project-wide index is the optimization. |
| `TYPE-02-R-02` | **self-context resolution** | Low | `self.field` discovery depends on knowing the enclosing method's class (the COMP-04 self-typing); use the method receiver name directly here to stay self-contained. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `TYPE-02-G-01` | **Cross-file implicit fields** | Implicit fields assigned in other modules are not collected. | `TYPE-02-DR-01` |
| `TYPE-02-G-02` | **Nested implicit fields** | `ClassName.x.y = …` (nested tables) not modeled. | `TYPE-02-DR-02` |

## De-risking Tasks (DR)

- [ ] `TYPE-02-DR-01`: If cross-file implicit fields are needed, add a stub/file index keyed on
      `className` → assigned field names.
- [ ] `TYPE-02-DR-02`: Decide whether to model nested implicit field paths.
- [ ] `TYPE-02-DR-03`: **Precise RHS type inference for implicit fields is deferred.**
      Implicit-field collection runs inside `materializeClass`, which itself runs inside
      `resolveType` during `LuaTypesVisitor` graph-building. Calling
      `LuaTypesVisitor.getTypes(file)` / `resolveType` from materialization would re-enter the
      same class (uncached mid-materialization → recursion) and re-enter `getTypes` mid-build,
      so RHS types are derived by **light syntactic inference** (literal/table/function KIND →
      primitive type) only. A `resolvingTypes` ThreadLocal guard in `resolveType` defends
      against any transitive re-entry. When precise implicit-field RHS types are needed, infer
      them outside the materialization path (e.g. a post-pass keyed off the cached class type).
