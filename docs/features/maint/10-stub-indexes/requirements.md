---
id: MAINT-10
title: "MAINT-10: Test Coverage - Stub Indexes"
type: feature
parent_id: MAINT
status: done
priority: medium
folders:
  - "[[features/maint/requirements|requirements]]"
---

# MAINT-10: Test Coverage - Stub Indexes

## Overview
Increase unit test coverage for Lunar's stub and file-based indexes under
`lang/indexing/`. This is a **coverage** feature: it adds unit tests only and makes **no
production behavior changes**. The goal is to pin down index keys, value externalizers,
dotted-key splitting, and negative (not-indexed) cases with direct index queries.

## Current Coverage (grounded)
The following already exists and must **not** be duplicated (only extended where a gap is noted):

- `TestLuaStubIndexing` (`src/test/kotlin/net/internetisalie/lunar/lang/TestLuaStubIndexing.kt`)
  covers `LuaGlobalDeclarationIndex` (global func indexed / local func not indexed),
  `LuaClassNameIndex` (`@class` indexed / plain local not indexed), and `LuaAliasIndex`
  (`@alias` indexed / plain local not indexed) via `StubIndex.getElements`.
- `LuaDescriptionIndexTest` (`src/test/kotlin/net/internetisalie/lunar/lang/LuaDescriptionIndexTest.kt`)
  covers `LuaDescriptionIndex` tokenization, dedup, same-file merge, and a size guardrail.
- `MemberFieldIndexTest` (`src/test/kotlin/net/internetisalie/lunar/lang/types/MemberFieldIndexTest.kt`)
  covers `LuaMemberFieldIndex` **indirectly** through `LuaMemberFieldNavigation.find`.

## Gaps this feature closes
1. `LuaCatsTypeNameIndex` — **no direct test**. Bare `--- @class` / `--- @alias` (no host
   `local` decl) indexing is unverified.
2. `LuaGlobalDeclarationIndex` — the **dotted base-key split** (`cjson.decode` also indexing
   `cjson`, `LuaFuncStubElementType.kt:61`) is unverified.
3. `LuaMemberFieldIndex` — the index itself (keys, `dottedMemberName` filtering of bracket/method
   access) is only tested through navigation, never queried directly.
4. `LuaFileBindingsIndex` — **no test at all**; `require("x")` dependency extraction is unverified.

## Scope
* **In Scope**:
  * Direct `FileBasedIndex` queries against `LuaCatsTypeNameIndex`, `LuaMemberFieldIndex`,
    `LuaFileBindingsIndex`.
  * Direct `StubIndex` queries verifying `LuaGlobalDeclarationIndex` dotted base-key splitting.
  * Negative cases: names that must NOT be indexed (bracket/method member access; non-`require`
    calls).
* **Out of Scope**:
  * Testing core IntelliJ indexing engines (persistence, forward index internals).
  * Any production source change to the index classes.
  * Re-testing behaviors already covered by the three existing test classes above.

## Functional Requirements
| ID | Requirement | Priority | Status | Description |
|---|---|---|---|---|
| MAINT-10-01 | **Bare Type Indexing** | Must | Full | `LuaCatsTypeNameIndex` indexes a bare `--- @class Name` and a bare `--- @alias Name type` (no host `local`), reachable via `FileBasedIndex.getContainingFiles`. |
| MAINT-10-02 | **Type Name Key Set** | Must | Full | `LuaCatsTypeNameIndex.getAllKeys` contains both a `@class` name and an `@alias` name from the same file; an un-annotated identifier is absent. |
| MAINT-10-03 | **Dotted Global Splitting** | Must | Full | `LuaGlobalDeclarationIndex` maps `function cjson.decode() end` to both the full key `cjson.decode` and the base key `cjson`. |
| MAINT-10-04 | **Member Field Keys** | Must | Full | `LuaMemberFieldIndex.getAllKeys` contains the qualified key `self.width` for `self.width = 1`, and the deep key `a.b.c` for `a.b.c = 1`. |
| MAINT-10-05 | **Member Field Exclusions** | Must | Full | `LuaMemberFieldIndex` does NOT index a bare assignment (`x = 1`), a bracket access (`t[i] = 1`), or a method-style target — `dottedMemberName` returns null for these. |
| MAINT-10-06 | **Require Dependency Tracking** | Must | Full | `LuaFileBindingsIndex` records `mymodule` in the file's `LuaFileBindingsRecord.requires` for `require("mymodule")`, and records nothing for a non-`require` call. Verified via a heavy real-disk fixture (`TempDirTestFixtureImpl`, `file:`-scheme URLs) because the index's `LuaFileInputFilter` rejects `temp://` light fixtures. No production change. |

## Test Cases
| TC | Requirement | Given | When | Then |
|----|-------------|-------|------|------|
| TC-01 | MAINT-10-01 | A `.lua` file containing only `--- @class MyType` (no `local`) | Query `FileBasedIndex.getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyType", scope)` | The set contains the configured file |
| TC-02 | MAINT-10-01 | A `.lua` file containing only `--- @alias MyAlias string` | Query `getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyAlias", scope)` | The set contains the configured file |
| TC-03 | MAINT-10-02 | A file with `--- @class Foo` and `--- @alias Bar number` and a plain `local baz = 1` | `FileBasedIndex.getInstance().getAllKeys(LuaCatsTypeNameIndex.KEY, project)` | Keys contain `Foo` and `Bar`; keys do NOT contain `baz` |
| TC-04 | MAINT-10-03 | A file with `function cjson.decode() end` | `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "cjson.decode", project, scope, LuaFuncDecl::class.java)` | Exactly one `LuaFuncDecl` returned |
| TC-05 | MAINT-10-03 | Same file as TC-04 | `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "cjson", project, scope, LuaFuncDecl::class.java)` | Exactly one `LuaFuncDecl` returned (the base key was also indexed) |
| TC-06 | MAINT-10-04 | A file with `self.width = 1` and `self.height = 2` | `getAllKeys(LuaMemberFieldIndex.KEY, project)` | Keys contain `self.width` and `self.height` |
| TC-07 | MAINT-10-04 | A file with `a.b.c = 1` | `getAllKeys(LuaMemberFieldIndex.KEY, project)` | Keys contain the deep qualified key `a.b.c` |
| TC-08 | MAINT-10-05 | A file with `x = 1` (bare) and `t[i] = 1` (bracket) | `getAllKeys(LuaMemberFieldIndex.KEY, project)` | Keys contain neither `x` nor any `t`/`t[i]`-derived key |
| TC-09 | MAINT-10-06 | A file with `require("mymodule")` | Read the file's `LuaFileBindingsRecord` via `getValues(LuaFileBindingsIndexName, 0, scope)` filtered to that file | `record.requires` contains `mymodule` |
| TC-10 | MAINT-10-06 | A file with `notrequire("mymodule")` (a non-`require` call) | Read the file's `LuaFileBindingsRecord` as in TC-09 | `record.requires` is empty |

## Acceptance Criteria
* **AC-10-01**: TC-01 and TC-02 pass — bare `@class`/`@alias` tags are reachable via `LuaCatsTypeNameIndex`.
* **AC-10-02**: TC-03 passes — the type-name key set is exactly the annotated names.
* **AC-10-03**: TC-04 and TC-05 pass — dotted globals index both full and base keys.
* **AC-10-04**: TC-06 and TC-07 pass — qualified and deep member keys are present.
* **AC-10-05**: TC-08 passes — bare/bracket targets are excluded.
* **AC-10-06**: TC-09 and TC-10 pass — `require` dependencies are tracked, non-`require` calls ignored.
