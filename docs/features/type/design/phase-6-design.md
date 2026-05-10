# Design: Type Engine Phase 6 (Cross-file Inference & Inlay Hints)

**Status**: Ready for Implementation  
**Related Requirements**: [`TYPE-07-03`](../07-external-api-stubs.md), `Navigation / Inlay Hints`

---

## 1. Overview

Phase 6 connects the intra-file type inference engine to the broader project context by enabling **cross-file `require` resolution** and surfaces the inferred types to the user through **Inlay Hints**. 

Prior to this phase, the type graph was strictly isolated to a single file. This phase de-risks and details the architecture for crossing file boundaries safely and efficiently.

---

## 2. Module Export Resolution (Cross-file Inference)

### 2.1 The Problem
When the engine encounters `local mod = require("my_module")`, it needs to know what `my_module` exports. Dynamically triggering full type inference on `my_module.lua` during the inference of the current file would cause massive latency spikes and potentially freeze the IDE due to cascading file analyses.

### 2.2 Stub-based Export Resolution
Instead of deep inference, we will rely on **Layer 1 (Stub Indexing)** to provide the exported type of a file.

1. **`LuaFileStub` Enhancement**: 
   Update `LuaFileElementType` and `LuaFileStub` to compute and store an `exportedTypeString`.
2. **Extraction Logic**: 
   During stub creation for a file, find the last `LuaReturnStatement` at the file's root scope. 
   - If the returned expression is a variable, find its declaration and extract its `@type` or `@class` annotation.
   - If the returned expression is a table with an explicit `@type`, use that.
   - Store the extracted LuaCATS type string (e.g., `"MyModuleClass"`) in the `LuaFileStub`.
3. **`LuaTypeManager.resolveModule`**:
   Add a new method to `LuaTypeManager`:
   ```kotlin
   fun resolveModule(moduleName: String, context: PsiElement): LuaType?
   ```
   This method will:
   - Use `PathConfiguration` to locate the target `VirtualFile` or `PsiFile`.
   - Retrieve the `LuaFileStub` for the file.
   - Parse the `exportedTypeString` into a `LuaTypeReference`.
   - Fall back to `LuaPrimitiveType.ANY` if no explicit export type is annotated.

### 2.3 Graph Injection
In `LuaTypesVisitor.visitFuncCall`:
```kotlin
if (isRequireCall(o)) {
    val moduleName = extractModuleName(o)
    val moduleType = LuaTypeManager.getInstance(project).resolveModule(moduleName, o)
    val moduleGraphType = LuaGraphType.fromLuaType(moduleType, graph)
    
    // Flow the imported type into the require() call's result nodes
    graph.addEdge(graph.value(o, moduleGraphType), callResultNodes.first())
}
```

---

## 3. Circular Dependency Handling

### 3.1 The Risk
If `A.lua` requires `B.lua`, and `B.lua` requires `A.lua`, resolving the module export could trigger an infinite loop if `resolveModule` attempts to eagerly resolve the inner type references.

### 3.2 Recursion Guard
Since we are using Stub Indexing to extract the `exportedTypeString`, we avoid most cyclic PSI resolution issues. However, if `LuaTypeManager` attempts to fully materialize the `LuaType` (e.g., resolving aliases or classes), it could loop.

**Mitigation**: 
Introduce a thread-local recursion guard in `LuaTypeManagerImpl`:
```kotlin
private val resolvingModules = ThreadLocal.withInitial { mutableSetOf<String>() }

fun resolveModule(moduleName: String, context: PsiElement): LuaType? {
    val active = resolvingModules.get()
    if (!active.add(moduleName)) {
        return LuaPrimitiveType.ANY // Cycle detected, break the loop
    }
    try {
        // ... perform stub lookup and type resolution ...
    } finally {
        active.remove(moduleName)
    }
}
```

---

## 4. Inlay Hints API

### 4.1 Objective
Surface the inferred types of local variables and function parameters directly in the editor, without requiring the user to hover over the symbols.

### 4.2 API Selection: `DeclarativeInlayHintsProvider`
IntelliJ 2024.1+ heavily favors the newer **Declarative Inlay Hints API** over the legacy `InlayHintsProvider`. The declarative API is significantly more performant and easier to implement.

**Implementation Details**:
1. **Registration**: Register `<declarativeInlayProvider>` in `plugin.xml`.
2. **Provider**: Implement `DeclarativeInlayHintsProvider`.
3. **Collector**: Iterate through `LuaLocalVarDecl` and `LuaParam` elements.
4. **Trigger Condition**:
   - Only show a hint if the variable does **not** have an explicit `@type` or `@param` annotation.
   - Query the type: `val type = LuaTypes.getValueType(element)`.
   - Filter out unhelpful types: Do not show hints for `Any`, `Undefined`, or trivial literal assignments (e.g., `local x = 1` shouldn't necessarily need a `: number` hint if it clutters the UI).
5. **Presentation**: Render the hint at the end of the identifier as `: <Type>`.

---

## 5. Implementation Tasks

| Task | Description | Priority |
| :--- | :--- | :--- |
| **1. File Stub Export** | Update `LuaFileStub` to compute and store `exportedTypeString`. | High |
| **2. Module Resolver** | Implement `LuaTypeManager.resolveModule` with circular dependency guarding. | High |
| **3. Require Graph Injection**| Update `LuaTypesVisitor` to intercept `require()` and inject the resolved `LuaType`. | High |
| **4. Declarative Inlay Hints**| Implement `DeclarativeInlayHintsProvider` to surface inferred types. | Medium |
| **5. Multi-Return Polish** | Ensure Phase 4's `last-element expansion` correctly handles `require()` returning multiple values (if applicable). | Low |
