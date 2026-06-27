---
id: MAINT-10
title: "MAINT-10: Test Coverage - Stub Indexes"
type: feature
parent_id: MAINT
status: todo
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-10: Test Coverage - Stub Indexes

## Overview
Increase unit test coverage for indexing services. This includes checking index keys, value extractors, file filters, and query performance.

## Scope
* **In Scope**:
  * Unit tests verifying `LuaCatsTypeNameIndex` logs all bare `@class` and `@alias` annotations.
  * Unit tests verifying `LuaClassNameIndex` and `LuaAliasIndex` index stubbed declarations.
  * Unit tests verifying `LuaGlobalDeclarationIndex` splits dotted global function targets correctly (e.g. `cjson.decode` indexes `cjson`).
  * Unit tests verifying `LuaDescriptionIndex` tokenizes and stores description keywords from comments.
  * Unit tests verifying `LuaMemberFieldIndex` maps assignments using qualified dotted receiver keys.
  * Unit tests verifying `LuaFileBindingsIndex` builds the project import/require dependency tree.
* **Out of Scope**:
  * Testing core IDE indexing engines.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-10-01 | **Bare Type Indexing** | Must | planned | Verify that `LuaCatsTypeNameIndex` indexes `@class` and `@alias` tags written without adjacent variables. |
| MAINT-10-02 | **Type & Alias Resolution** | Must | planned | Verify that type names index to their stub declarations using `LuaClassNameIndex` and `LuaAliasIndex`. |
| MAINT-10-03 | **Dotted Global Splitting** | Must | planned | Verify that `LuaGlobalDeclarationIndex` maps dotted function names to both the full name and the base module key. |
| MAINT-10-04 | **Documentation Token Indexing** | Must | planned | Verify that `LuaDescriptionIndex` maps keywords inside comments to their parent elements. |
| MAINT-10-05 | **Qualified Member Mapping** | Must | planned | Verify that `LuaMemberFieldIndex` indexes receiver member fields without namespace collisions. |
| MAINT-10-06 | **Require Dependency Tracking** | Must | planned | Verify that `LuaFileBindingsIndex` indexes module dependencies from `require()` calls. |

## Acceptance Criteria
* **AC-10-01**: A test case asserts that a file containing only `---@class MyType` results in `MyType` being returned from `LuaCatsTypeNameIndex`.
* **AC-10-02**: A test case asserts that `@alias MyAlias string` maps to its source stub element via `LuaAliasIndex`.
* **AC-10-03**: A test case asserts that `cjson.decode()` maps to its declaration using the base query key `cjson` in `LuaGlobalDeclarationIndex`.
* **AC-10-04**: A test case asserts that a comment description containing "serializes" indexes that keyword to its function declaration.
* **AC-10-05**: A test case asserts that `self.width` and `self.height` assignments are correctly indexed and queryable via `LuaMemberFieldIndex`.
* **AC-10-06**: A test case asserts that a call to `require("mymodule")` indexes a dependency to `mymodule` in `LuaFileBindingsIndex`.
