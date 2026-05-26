---
id: "SYNTAX-17-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "SYNTAX-17"
status: "planned"
priority: "low"
folders:
  - "[[features/syntax/17-inferred-type-highlighting/requirements|requirements]]"
---

# SYNTAX-17: Inferred-Type Highlighting Design

## Architecture

This feature acts as a secondary, type-aware annotation pass that complements the primary scope-based highlighting.

### Components

1.  **`LuaTypeHighlightingAnnotator`**:
    *   Subclass of `Annotator` or `HighlightingPass`.
    *   Uses `LuaTypeManager` to fetch the inferred type of `LuaNameRef` and `LuaIndexExpr` elements.
2.  **`LuaHighlightCache`**:
    *   Caches type-based attributes to avoid redundant inference on every editor heartbeat.

### Logic Flow

1.  **Identifier Recognition**: The annotator identifies all identifiers that haven't been highlighted as keywords or literals.
2.  **Type Resolution**: 
    *   Call `PsiReference.resolve()` or `LuaTypeManager.getInferredType()`.
    *   Check the `LuaType` of the result.
3.  **Attribute Selection**:
    *   `LuaFunctionType` -> `LuaHighlight.CALL_LOCAL/GLOBAL`
    *   `LuaClassType` -> `LuaHighlight.CLASS`
    *   `LuaTableType` -> `LuaHighlight.FIELD` (if indexing)
4.  **Application**: Apply the `SilentAnnotation` with the selected `TextAttributesKey`.

### Performance Strategy

- **Lazy Evaluation**: Only resolve types for elements currently visible in the editor viewport.
- **Dumb Mode Awareness**: Disable type-based highlighting during indexing.
- **Transitive Depth Limit**: Limit the depth of type inference for highlighting to 3-5 levels to ensure responsiveness.
