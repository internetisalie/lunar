# Design: SYNTAX-07-04 Parameter Name Hints

## Architecture

### Collector Expansion
The `LuaTypeInlayHintProvider` will be updated to include a `LuaCallExpr` visitor in its collector.

```kotlin
override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
    when (element) {
        is LuaNameRef -> collectVariableHints(element, sink)
        is LuaFuncCall -> collectParameterHints(element, sink)
    }
}
```

### Symbol Resolution
To extract parameter names, we must resolve the `LuaFuncCall` to its definition:
1.  **Resolve Callee**: Use the `LuaTypeManager` or `LuaTypesVisitor.getTypes()` to find the `LuaGraphType` of the callee.
2.  **Function Type**: Ensure the type is a `LuaGraphType.Function`.
3.  **Source Element**: Map the `LuaGraphType.Function` back to its source PSI element (`LuaFuncDecl`, `LuaLocalFuncDecl`, or `LuaFuncDef`).

### Name Extraction Logic
1.  **PSI Parameters**: Get names from `LuaParList.nameList`.
2.  **LuaCATS Override**: If the function has a `LuaCatsComment` with `@param` tags, prioritize those names as they may be more descriptive than the source code.
3.  **Implicit Self**: For method calls (`:` operator), the first parameter of the definition is mapped to `self` and must be skipped when aligning arguments.

### Suppression Logic
Implement a `ParameterHintSuppression` service with the following checks:
- `isNameMatch(arg, param)`: Returns true if argument expression is a `LuaNameRef` and `text == paramName`.
- `isTrivial(paramName)`: Returns true if length <= 1 or name == `_`.
- `isSingleParam(function)`: Returns true if `params.size` (excluding `self`) == 1.

## Performance
- **Background Resolution**: Resolution must happen within the `SharedBypassCollector` (background thread).
- **Caching**: Leverage the existing `LuaTypesVisitor` snapshot to avoid redundant type inference.
- **Threshold**: Respect the 10,000-line file size threshold.
