---
id: "SYNTAX-17-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "SYNTAX-17"
status: "planned"
priority: "low"
folders: ["[[features/syntax/17-inferred-type-highlighting/requirements]]"]
---

# SYNTAX-17: Inferred-Type Highlighting Implementation Plan

A single `Annotator` reusing the cached type snapshot. Phases map to requirement IDs and design
sections.

## Phase 1: Keys + Annotator skeleton [Must] — SYNTAX-17-01/04
- [ ] Add the five `TextAttributesKey`s to `LuaHighlight` (§2.2) and register them in
      `LuaColorSettingsPage` (§2.3).
- [ ] `LuaInferredTypeAnnotator : Annotator` (§2.1) + `<annotator language="Lua">`; dumb-mode
      guard; `newSilentAnnotation(...).textAttributes(key)`.
- [ ] `classify` step 3 (call site, local vs global via `resolve()`); helpers ≤30 lines.
- [ ] Tests: TC-01 (local call), TC-04 (global call), TC-05 (dumb mode → null).

## Phase 2: Member field/method [Should] — SYNTAX-17-03
- [ ] `classify` step 2: member of `LuaIndexExpr` → `receiverOf` + `getMembers()[name]`;
      Function → `INFERRED_METHOD`, else `INFERRED_FIELD` (reuse the completion receiver helper).
- [ ] Test: TC-03 (field vs method distinct keys).

## Phase 3: Class references [Should] — SYNTAX-17-02
- [ ] `classify` step 4: `Table.className` match → `INFERRED_CLASS`.
- [ ] Test: TC-02.

## Verification Tasks
- Unit (`BasePlatformTestCase` + `myFixture.doHighlighting()`): assert the `HighlightInfo` at
  each identifier carries the expected `forcedTextAttributesKey` (TC-01…05).
- Manual: recolor a key in *Settings ▸ Color Scheme ▸ Lua* and confirm it applies; profile a
  large file (the platform pass is viewport-limited).
