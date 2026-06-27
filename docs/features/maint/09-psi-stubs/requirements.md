---
id: MAINT-09
title: "MAINT-09: Test Coverage - PSI & Stubs"
type: feature
parent_id: MAINT
status: done
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-09: Test Coverage - PSI & Stubs

## Overview
Increase test coverage for the core Lua Program Structure Interface (PSI) tree walking, scope evaluation, AST node builders, and stub element serialization.

## Scope
* **In Scope**:
  * Unit tests for `LuaElementFactory` node creation.
  * Unit tests for comment-finding and backwards traversal logic in `LuaPsiImplUtil.getCatsComment()`.
  * Unit tests for the sequential scope crawling algorithm in `LuaResolveUtil.scopeCrawlUp()` and `LuaBlockExt`.
  * Unit tests for method parameters (implicit `self`) and loop variables scope checks in `LuaFunctionExt` and `LuaForStatementExt`.
  * Serialization and type-tag hoisting tests for `LuaLocalVarStubElementType`, `LuaFuncStubElementType`, and `LuaLocalFuncStubElementType`.
* **Out of Scope**:
  * Testing editor UI features, which are covered by syntax/completion epics.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-09-01 | **PSI Factory Node Creation** | Must | done | Verify that `LuaElementFactory` correctly constructs valid AST/PSI nodes (identifiers, labels, expressions) without syntax errors. |
| MAINT-09-02 | **LuaCATS Lazy Comment Resolution** | Must | done | Verify that `LuaPsiImplUtil.getCatsComment()` resolves the nearest preceding comment block for local variables and function declarations. |
| MAINT-09-03 | **Scope Crawling and Sequence Checks** | Must | done | Verify that `LuaResolveUtil.scopeCrawlUp()` climbs the PSI tree correctly and `LuaBlockExt` rejects forward variable resolution. |
| MAINT-09-04 | **Implicit Self and Loop Variables** | Must | done | Verify that `LuaFunctionExt` injects implicit `self` for method calls and `LuaForStatementExt` scopes loop control variables to loop blocks. |
| MAINT-09-05 | **Stub Type Annotation Serialization** | Must | done | Verify that stub element types successfully serialize and deserialize hoisted type annotations (`@class`, `@alias`, etc.). |

## Acceptance Criteria
* **AC-09-01**: A test case asserts that calling `LuaElementFactory.createIdentifier` produces a valid named PSI element (and `createLabelRef` a `LuaLabelRef`). See `design.md` Gap 2.1 — the factory returns the identifier leaf, not a `LuaNameRef`.
* **AC-09-02**: A test case asserts that `getCatsComment()` correctly locates `---@type string` preceding a local variable definition.
* **AC-09-03**: A test case asserts that variables referenced before their definitions do not resolve under sequential scope crawling checks.
* **AC-09-04**: A test case asserts that looking up `self` inside a method block resolves to the method receiver (`function obj:m()` → `obj`). See `design.md` Gap 2.2 — the real predicate is `funcName.funcNameMethod != null`; there is no `isMethod` accessor.
* **AC-09-05**: A test case asserts that saving and reading local variable and function stubs preserves custom type annotation state fields.

## Test Cases
Concrete input → expected output per `Must` requirement. Fixtures are inline Lua source
passed to `myFixture.configureByText("test.lua", …)`; `<caret>` marks the resolution site.

| TC | Req | Input (fixture) | Action | Expected output |
|----|-----|-----------------|--------|-----------------|
| TC-09-01-a | 01 | `"foo"` | `LuaElementFactory.createIdentifier(project, "foo")` | non-null `PsiElement`, `text == "foo"` |
| TC-09-01-b | 01 | `"lbl"` | `LuaElementFactory.createLabelRef(project, "lbl")` | non-null `LuaLabelRef` containing `lbl` |
| TC-09-01-c | 01 | `"1 + 2"` | `LuaElementFactory.createExpression(project, "1 + 2")` | non-null `LuaExpr`; `createFile` text has no `PsiErrorElement` |
| TC-09-02-a | 02 | `---@type string`\\n`local s = ""` | `getCatsComment(localVarDecl)` | non-null; `getTypeTagList().first().argType.text == "string"` |
| TC-09-02-b | 02 | `---@return string`\\n`function f() end` | `getCatsComment(funcDecl)` | non-null; first `@return` argType text == `"string"` |
| TC-09-02-c | 02 | `local a = 1`\\n`local b = 2` (no comment above `b`) | `getCatsComment(bDecl)` | `null` (intervening statement breaks association) |
| TC-09-03-a | 03 | `print(<caret>x)`\\n`local x = 1` | `resolveAtCaret` | `null` (forward reference rejected) |
| TC-09-03-b | 03 | `local x = 1`\\n`do print(<caret>x) end` | `resolveAtCaret` | non-null; resolves to the `x` in `local x` |
| TC-09-04-a | 04 | `for i=1,3 do print(<caret>i) end` | `resolveAtCaret` | non-null; target text == `"i"` |
| TC-09-04-b | 04 | `for i=1,3 do end`\\n`print(<caret>i)` | `resolveAtCaret` | `null` (loop var not visible after loop) |
| TC-09-04-c | 04 | `function obj:m() return <caret>self end` | `resolveAtCaret` | non-null; target text == `"obj"` (method receiver) |
| TC-09-05-a | 05 | `---@class Builder: Base`\\n`local Builder = {}` | `calcStubTree` → serialize → deserialize → `collect<LuaLocalVarStub>().first()` | `luacatsClassName == "Builder"`, `luacatsExtends == "Base"` |
| TC-09-05-b | 05 | `---@return string`\\n`function f() end` | round-trip → `collect<LuaFuncStub>().first()` | `name == "f"`, `luacatsReturnType == "string"` |
| TC-09-05-c | 05 | `local function g() end` | round-trip → `collect<LuaLocalFuncStub>().first()` | `name == "g"`; survives round-trip (no exception) |
