---
id: "BUG-134-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "BUG-134"
folders:
  - "[[features/bug-fixes/134-return-comma-parsing/requirements|requirements]]"
---

# Technical Design: BUG-134 — @return Comma Parsing

## 1. Architecture Overview

### Current State
Currently, `luacats.bnf` defines `returnTag` to accept exactly one `type`, an optional name, and an optional description. If a user tries to document multiple return values on a single line using a comma (e.g., `---@return number count, string error`), the parser encounters the `,` token, fails to match, and creates a `PsiErrorElement`. As a result, `LuaCatsReturnTag` directly exposes `getArgType()`, which limits it to exactly one type.

### Target State
The Grammar-Kit definition for `returnTag` will be updated to accept a comma-separated list of return types. This will introduce a new PSI element, `LuaCatsReturnTypeDescriptor`, which encapsulates a single return type, optional name, and optional description. `LuaCatsReturnTag` will be updated to contain a list of these descriptors.

## 2. Core Components

### 2.1 `LuaCatsParser` (Generated from `luacats.bnf`)
- **Responsibility**: Parse comma-separated `@return` tags without errors.
- **Key API**:
  The grammar in `src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf` will be modified:
  ```bnf
  returnTag ::= '@return' returnTypeDescriptor { ',' returnTypeDescriptor }*
  returnTypeDescriptor ::= <<ArgType type>> [(<<ArgName NAME>>? '#' description? ) | ( <<ArgName NAME>> description ? )]
  ```
  This creates the `LuaCatsReturnTypeDescriptor` PSI class and changes `LuaCatsReturnTag` to expose `List<LuaCatsReturnTypeDescriptor> getReturnTypeDescriptorList()`.

### 2.2 `LuaTypeGraphBridge`
- **Responsibility**: Propagate parsed return types into the type engine.
- **Key API**:
  Modifies `cats.getReturnTagList().forEachIndexed` to first flatten the return descriptors:
  ```kotlin
  val allReturnDescriptors = cats.getReturnTagList().flatMap { it.returnTypeDescriptorList }
  allReturnDescriptors.forEachIndexed { i, desc ->
      val typeName = desc.argType.text.trim()
      // Existing injection logic
  }
  ```

### 2.3 `LuaTypesVisitor`
- **Responsibility**: Compute the total number of expected return nodes for scope creation.
- **Key API**:
  Updates `returnCount` to count all descriptors across all return tags:
  ```kotlin
  val returnDescriptors = allCats.flatMap { it.getReturnTagList() }.flatMap { it.returnTypeDescriptorList }
  val returnCount = returnDescriptors.size
  ```

### 2.4 Insight Providers and Stubs
- **Responsibility**: Ensure existing visual hints and stub indexers don't break due to the removed `getArgType()` method.
- **Collaborators**: `LuaMethodChainInlayHintProvider`, `LuaTypeInlayHintProvider`, `LuaFuncStubElementType`, `LuaLocalFuncStubElementType`, `LuaTypeManagerImpl`.
- **Key API**:
  All direct `argType` accessors on `LuaCatsReturnTag` will be safely mapped through the descriptor list:
  ```kotlin
  // Inlay Hints
  val tags = cats.getReturnTagList().flatMap { it.returnTypeDescriptorList }
  return tags.map { it.argType.text.trim() }

  // Stub Element Types
  val returnType = catsComment?.getReturnTagList()?.flatMap { it.returnTypeDescriptorList }?.firstOrNull()?.argType?.text
  ```

## 3. Algorithms
No complex new algorithms are introduced; the logic is entirely structural (flattening lists).

## 4. External Data & Parsing
N/A. This feature affects internal PSI parsing via Grammar-Kit.

## 5. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| BUG-134-01 | M | §2.1 (luacats.bnf modification) |
| BUG-134-02 | M | §2.1 (luacats.bnf modification preserves names/descriptions) |
| BUG-134-03 | M | §2.2 (LuaTypeGraphBridge flattening) |

## 6. Open Questions
_None — feature has cleared the planning bar._
