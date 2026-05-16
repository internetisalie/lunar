---
folders:
  - "[[features/syntax/07-inlay-hints/07-06-return-type-hints/requirements|requirements]]"
title: Technical Design
---
# Technical Design: SYNTAX-07-06 Return Type Hints

## 1. Overview
This feature extends the `LuaTypeInlayHintProvider` to provide inline hints for function return types. It utilizes the IntelliJ Platform's Declarative Inlay Hints API and the Lunar plugin's internal type inference engine.

## 2. Architecture Integration

### 2.1 Provider Extension
The existing `LuaTypeInlayHintProvider` (registered in `plugin.xml`) will be expanded. Its internal `SharedBypassCollector` visitor will be updated to handle function definition nodes.

### 2.2 Target Nodes
The following PSI elements (all implementing `LuaFuncBodyOwner`) are targets for return type hints:
- `LuaFuncDecl` (Global function definitions)
- `LuaLocalFuncDecl` (Local function definitions)
- `LuaFuncDef` (Anonymous function expressions / assignments)

## 3. Implementation Details

### 3.1 Hint Positioning
The hint must be rendered immediately after the closing parenthesis `)` of the function's parameter list.
- **Logic**: Find the `LuaParameters` element within the function definition. Locating the last child token of type `LuaTokenTypes.RPAREN` or the end offset of the `LuaParameters` element.
- **Fallthrough**: If no parameters are present (e.g., malformed code), no hint is shown.

### 3.2 Type Resolution Strategy
Return types will be resolved using the `LuaTypesVisitor.getTypes(element)` utility.
- **Process**:
    1. Resolve the `LuaGraphType` of the function definition itself.
    2. Extract the `returnTypes` list from the function's type signature.
    3. Filter out `unknown` or `nil` if they are the only types and the function body is empty.
- **Multi-return Handling**: If the function signature contains multiple return types, they will be joined by a comma and a space (e.g., `: string, integer`).

### 3.3 Suppression Logic
Hints will be suppressed in the following scenarios to minimize noise:
1. **Explicit Annotation**: Use `LuaCommentOwner.catsComment` to check if a `---@return` tag is already present.
2. **Standard Library**: If the function is a well-known standard library function whose signature is already documented/evident (though most stdlib functions will be annotated anyway).
3. **Void Functions**: Functions that explicitly return nothing (`return` with no values) or have no return statements and infer to `void`.
4. **Indeterminate Types**: If all return paths lead to `unknown` or `any`.

## 4. UI/UX Standards

- **Prefix**: The hint text must always start with `: ` to distinguish it as a type annotation.
- **Coloring**: Use `DefaultLanguageHighlighterColors.INLAY_DEFAULT` for consistency with variable type hints.
- **Formatting**: Type names should match the display names used in code completion and hover documentation (e.g., `table<string, number>`).

## 5. Performance Considerations

- **Background Execution**: Collection occurs via `SharedBypassCollector`, ensuring hint computation does not block the Event Dispatch Thread (EDT).
- **Type Caching**: Leverage `LuaTypeManager` or `CachedValuesManager` within `LuaTypesVisitor` to avoid redundant type inference cycles across multiple inlay passes.
- **Shallow Analysis**: When inferring return types for large functions, the inference engine should ideally use cached results for called functions rather than re-traversing the entire project.

## 6. Integration Points

| Component | Interaction |
| :--- | :--- |
| `LuaTypeInlayHintProvider` | Primary collector implementation. |
| `LuaTypesVisitor` | Source of truth for inferred return types. |
| `LuaFuncBodyOwner` | Common PSI interface for all target function types. |
| `LuaCatsComment` | Used to detect and respect explicit `@return` annotations. |
