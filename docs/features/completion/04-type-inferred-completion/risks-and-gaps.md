---
id: "COMP-04-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "COMP-04"
priority: "high"
folders:
  - "[[features/completion/04-type-inferred-completion/requirements|requirements]]"
---

# Risks & Design Gaps: Type-Inferred Completion (COMP-04)

## Technical Risks

| ID | Risk | Impact | Mitigation |
| :--- | :--- | :--- | :--- |
| `COMP-04-R-01` | **Type-engine edits** | Medium | `self`/`setmetatable` changes touch `LuaTypesVisitor`/`LuaTypeGraph`; covered by graph unit tests before the completion tests. They also improve hover/inlay (shared engine). |
| `COMP-04-R-02` | **Completion latency** | Medium | Snapshot is cached per document hash (`FileUserData`); `getMembers()` is bounded by the `fromLuaType` `visited` guard. Profile on deep hierarchies. |
| `COMP-04-R-03` | **Visibility metadata sparse** | Low | `LuaTypeMember.visibility` defaults PUBLIC; filter is forward-compatible and harmless until `@private`/`@protected` parsing lands. |

## Design Gaps

| ID | Gap | Description | De-risking Action |
| :--- | :--- | :--- | :--- |
| `COMP-04-G-01` | **Dynamic metatables** | Only literal/locally-inferable `setmetatable(t, mt)` is handled; dynamic `mt` falls back to `Any`. | `COMP-04-DR-01` |
| `COMP-04-G-02` | **Generic substitution path** | Whether `graphTypeToLuaType` preserves generic args so `LuaParameterizedType.getMembers()` substitutes. | `COMP-04-DR-02` |
| `COMP-04-G-03` | **`@private`/`@protected` parsing** | LuaCATS visibility tags are not yet parsed into `LuaTypeMember.visibility`. | `COMP-04-DR-03` |

## De-risking Tasks (DR)

- [ ] `COMP-04-DR-01`: Confirm the graph models `setmetatable(t, mt)` value flow (§3.3) for the
      common literal case; decide whether to support `mt` aliased through a variable.
- [ ] `COMP-04-DR-02`: Verify `snapshot.graphTypeToLuaType(gt)` yields a `LuaParameterizedType`
      with type args for `@type List<string>`; if not, thread generic args through the bridge.
- [ ] `COMP-04-DR-03`: Decide whether to parse `@field` visibility prefixes (LuaCATS
      `private`/`protected`) into `LuaVisibility` to make the §3.1 step-4 filter meaningful.
