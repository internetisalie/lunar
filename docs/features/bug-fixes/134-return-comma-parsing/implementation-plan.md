---
id: "BUG-134-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "BUG-134"
folders:
  - "[[features/bug-fixes/134-return-comma-parsing/requirements|requirements]]"
---

# Implementation Plan: BUG-134 — @return Comma Parsing

## Phase 1: Grammar and AST Updates [Must]
**Goal**: Update the parser to successfully parse comma-separated return tags.

1.  **Modify `luacats.bnf`**: 
    - Locate the `returnTag` rule.
    - Extract the type/name/description sequence into a new `returnTypeDescriptor` rule.
    - Update `returnTag` to accept a comma-separated list of `returnTypeDescriptor`s.
2.  **Regenerate Parser**: 
    - Run the Grammar-Kit generation tasks to produce the new `LuaCatsReturnTypeDescriptor` PSI interfaces and update `LuaCatsReturnTag`.

## Phase 2: Kotlin Subsystem Migrations [Must]
**Goal**: Fix compiler errors caused by the removal of `LuaCatsReturnTag.getArgType()` and accurately flatten multiple return values.

1.  **Type Engine Refactoring**:
    - Update `LuaTypeGraphBridge.kt` to flatten `returnTypeDescriptorList` before injecting types positionally.
    - Update `LuaTypesVisitor.kt` to count the flattened descriptors for `returnCount`.
    - Update `LuaTypeManagerImpl.kt` to access the first element from the flattened descriptor list when reading raw returns.
2.  **Insight Providers Refactoring**:
    - Update `LuaMethodChainInlayHintProvider.kt` and `LuaTypeInlayHintProvider.kt`.
3.  **Stub Index Refactoring**:
    - Update `LuaFuncStubElementType.kt` and `LuaLocalFuncStubElementType.kt` to fetch the first `argType` via `returnTypeDescriptorList.firstOrNull()`.

## Phase 3: Verification [Must]
**Goal**: Prove the bug is resolved via unit testing.

1.  **Run Lexer/Parser Tests**: Ensure `LuaCatsParserTest` passes and add a new test case asserting `---@return number, string` creates two `LuaCatsReturnTypeDescriptor` elements without `PsiErrorElement`.
2.  **Run Type Inference Tests**: Add a test asserting `local a, b = fn()` correctly infers `a` as `number` and `b` as `string` when using the comma-separated return tag.
