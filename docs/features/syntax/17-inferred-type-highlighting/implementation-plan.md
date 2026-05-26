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

## Phases

### Phase 1: Call Site Highlighting [Must]
- Implement `LuaTypeHighlightingAnnotator` to identify `LuaNameRef` in `LuaFuncCall`.
- Integrate with `LuaTypeManager` to resolve function/method definitions.
- Apply `CALL_LOCAL`, `CALL_GLOBAL`, or `CALL_PLATFORM` attributes based on resolution results.
- **Verification**: Unit tests with various call types.

### Phase 2: Class & Enum Highlighting [Should]
- Extend annotator to resolve identifiers pointing to `@class` or `@alias` (enums).
- Implement lookup for user-defined type definitions.
- **Verification**: Test cases for LuaCATS defined classes.

### Phase 3: Member Highlighting [Could]
- Resolve table indexing (`t.field`) and method calls (`t:method()`).
- Differentiate between functions stored in tables vs data fields.
- **Verification**: Complex table structure tests.

### Phase 4: Performance & Caching [Must]
- Implement viewport-only analysis.
- Add `DumbMode` checks.
- Integrate with `CachedValuesManager` to prevent re-inference on every keystroke.

## Verification Tasks

- [ ] [Must] Implement `LuaTypeHighlightingAnnotatorTests`.
- [ ] [Must] Verify "Silent" annotations don't interfere with error reporting.
- [ ] [Should] Manual verification of color customization in Settings.
- [ ] [Must] Profile editor performance with large files.
