# Design Document: Stub Indexing for LuaCATS Data (DOC-06)

## 1. Overview

This document outlines the proposed design for implementing Stub Indexing (`DOC-06-01`) and Type Map Construction (`DOC-06-02`) to support efficient project-wide resolution of Lua symbols and their associated LuaCATS metadata.

Currently, the Lunar plugin relies on a custom `FileBasedIndex` (`LuaFileBindingsIndex`) which acts as a forward index and lacks the ability to store or retrieve LuaCATS types without re-parsing files. Migrating to the standard IntelliJ `StubIndex` infrastructure will provide significant performance improvements and enable full LuaCATS type integration.

## 2. Current Architecture & Limitations

### 2.1 In-File Resolution (`LuaBindingsVisitor`)
The `LuaBindingsVisitor` traverses the entire PSI tree of a file upfront to build a complete map of declarations, scopes, and references. This map is cached using `CachedValuesManager`. `LuaNameReference.multiResolve()` looks up offsets in this map for local resolution.

### 2.2 Cross-File Resolution (`LuaFileBindingsIndex`)
For global symbols, resolution falls back to `LuaFileBindingsIndex`. 
- It is a `FileBasedIndex` acting as a **Forward Index** (using a constant key `0`).
- It stores `LuaFileBindingsRecord` containing:
  - `bindings`: A list of exported global names, their text offsets, and a `Kind` enum (Package, Function, Variable, Label).
  - `requires`: A list of required packages.
- **Limitations**:
  - Requires iterating over files in the scope to find matching bindings.
  - Contains **no LuaCATS data** (types, parameters, return values). To get type information, the resolver must find the file, load the PSI, locate the element by offset, find the preceding comment, and parse it.

## 3. Proposed Solution: Stub Index Integration

To maintain a project-wide index of LuaCATS data tied to its targets, we must transition to `StubBasedPsiElement`s and `StubIndex`.

### 3.1 Implement Stub-Based PSI Elements
Major declaration elements must be converted to `StubBasedPsiElement`.
- **Targets**: `LuaFuncDef`, `LuaLocalFuncDecl`, `LuaVar` (or its underlying declaration component).
- **Stub Classes**: Create corresponding stub classes (e.g., `LuaFunctionStub`) that serialize the element's name and its associated LuaCATS metadata.

```kotlin
interface LuaFunctionStub : StubElement<LuaFuncDef> {
    val name: String?
    val isGlobal: Boolean
    val luacatsReturnType: String? 
    val luacatsParamTypes: Map<String, String>
}
```

### 3.2 Association During Indexing
The connection between LuaCATS data and the target element is established during the **Stub Building** phase.
- Inside the `IStubElementType.createStub()` implementation, scan preceding tokens for a `LUACATS_COMMENT`.
- Extract tags (`@type`, `@param`, `@return`).
- Store these extracted values as serialized data within the stub.

### 3.3 Define Specialized Stub Indices
Create inverted `StubIndex` keys for fast, O(1) global lookups by name.
- `LuaClassNameIndex`: For names defined via `@class`.
- `LuaAliasIndex`: For names defined via `@alias`.
- `LuaGlobalDeclarationIndex`: For indexing global variables and functions.

```kotlin
object LuaGlobalDeclarationIndex : StringStubIndexExtension<LuaFuncDef>() {
    val KEY = StubIndexKey.createIndexKey<String, LuaFuncDef>("lua.global.declaration")
    override fun getKey(): StubIndexKey<String, LuaFuncDef> = KEY
}
```

### 3.4 Resolver Integration
Update `LuaNameReference.multiResolve()` to query the new `StubIndex` for global lookups. This bypasses the need to iterate through files and provides direct access to type data via the element's stub without parsing the comment AST.

## 4. Expected Benefits

1. **Performance**: Changes cross-file lookups from `O(N)` (iterating over all files) to `O(1)` (direct map lookup by symbol name).
2. **Memory Efficiency**: Avoids loading full PSI trees and parsing comments for type resolution across the project.
3. **Feature Completeness**: Directly addresses the unimplemented requirements `DOC-06-01` (Stub Indexing) and `DOC-06-02` (Type Map Construction), laying the groundwork for `DOC-07` (Parameter Info).
