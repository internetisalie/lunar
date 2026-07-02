---
id: "MAINT-10-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-10"
folders:
  - "[[features/maint/10-stub-indexes/requirements|requirements]]"
---

# Technical Design (Test Map): MAINT-10 — Test Coverage: Stub Indexes

This is a **test map**, not a production design. No production source changes. Every target
class/method below was grep-verified in this repo with the cited `path:line`.

## 1. Target Classes & Behaviors Under Test

### 1.1 `LuaCatsTypeNameIndex` (`lang/indexing/LuaCatsTypeNameIndex.kt`)
- **Type**: `FileBasedIndexExtension<String, String>` (`:37`). Key = type name, value = `""` (unused).
- **Key ID**: `LuaCatsTypeNameIndex.KEY` (`:79`), backed by `ID.create("lunar.luacats.typename")` (`:22`).
- **Indexer** (`:61-76`): walks `LuaCatsClassTag` (name = child `LuaCatsArgType`, `:66-67`) and
  `LuaCatsAliasTag` (name = child `LuaCatsArgName`, `:70-71`) via `PsiTreeUtil.findChildrenOfType`.
  Reads tags directly from PSI, so **bare** tags with no host `LuaLocalVarDecl` are indexed.
- **Input filter**: `.lua` extension (`:53`).
- **Behavior to cover**: MAINT-10-01, MAINT-10-02.
- **Test approach**: `BasePlatformTestCase` + `myFixture.configureByText("t.lua", "--- @class MyType")`.
  Query with `FileBasedIndex.getInstance().getContainingFiles(LuaCatsTypeNameIndex.KEY, name, scope)`
  (returns `Collection<VirtualFile>`) and `getAllKeys(LuaCatsTypeNameIndex.KEY, project)`. This is
  exactly the query surface production uses in `LuaCatsTypeNavigation.processElements`
  (`lang/navigation/LuaCatsTypeNavigation.kt:41`) and `processNames` (`:29`).

### 1.2 `LuaGlobalDeclarationIndex` (`lang/indexing/LuaGlobalDeclarationIndex.kt`)
- **Type**: `StringStubIndexExtension<LuaFuncDecl>` (`:7`); `KEY` (`:11`) = `StubIndexKey.createIndexKey("lunar.global.decl")`.
- **Key-writing logic** lives in `LuaFuncStubElementType.indexStub` (`lang/psi/stubs/impl/LuaFuncStubElementType.kt:56`):
  sinks `stub.name` (`:57-58`) and, when the name contains `'.'`, ALSO sinks
  `it.substringBefore('.')` (`:61-62`) — the **dotted base-key split** that is currently untested.
- **Behavior to cover**: MAINT-10-03 (both full and base keys).
- **Test approach**: `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, key, project, scope, LuaFuncDecl::class.java)`
  — the identical query used by `TestLuaStubIndexing` (`:31`) and `LuaTypeManagerImpl.kt:225`.
  New coverage: the base-key case (`cjson`), not present in `TestLuaStubIndexing`.

### 1.3 `LuaMemberFieldIndex` (`lang/indexing/LuaMemberFieldIndex.kt`)
- **Type**: `FileBasedIndexExtension<String, String>` (`:31`); `KEY` (`:70`) = `ID.create("lunar.member.field")` (`:19`).
- **Indexer** (`:55-67`): for every `LuaAssignmentStatement`, for each `LuaVar` target in
  `stmt.varList.varList`, keys `dottedMemberName(target)` when non-null.
- **`dottedMemberName`** (`lang/indexing/LuaMemberFieldNames.kt:12`): returns the dotted qualified
  name `a.b(.c…)` from `target.nameRef.text` + each `varSuffix.indexExpr.nameRef.text`; returns
  **null** for a bare name (empty `varSuffixList`, `:14`) or any suffix lacking a dotted
  `indexExpr.nameRef` — i.e. bracket `t[i]` / method `a:m` access (`:17`).
- **Behavior to cover**: MAINT-10-04 (qualified + deep keys), MAINT-10-05 (exclusions).
- **Test approach**: `getAllKeys(LuaMemberFieldIndex.KEY, project)` returns the key set directly.
  Existing `MemberFieldIndexTest` only exercises this via `LuaMemberFieldNavigation.find`; direct
  key-set assertions and the exclusion cases are new.

### 1.4 `LuaFileBindingsIndex` (`lang/indexing/LuaFileBindingsIndex.kt`)
- **Type**: `FileBasedIndexExtension<Int, LuaFileBindingsRecord>` (`:31`); single key `0`
  (`ForwardIndexer.map`, `LuaIndex.kt:26-28`); name `LuaFileBindingsIndexName` (`:28`).
- **Indexer** `LuaFileBindingsIndexer.computeValue` (`:286`): extracts `require("x")` string args
  into `requires` via `extractRequires` (`:303`) → `extractRequiresFromStatement` (`:314`), which
  matches only calls whose `luaVar.nameRef.identifier.text == "require"` (`:329`) and reads the
  string arg through `extractLuaString` (`:349`). Produces `LuaFileBindingsRecord(bindings, requires)` (`:402`).
- **Record type**: `LuaFileBindingsRecord(bindings: List<LuaBinding>, requires: List<String>)` (`:402-405`).
- **Behavior to cover**: MAINT-10-06 (require tracked; non-require ignored).
- **Test approach**: query values via
  `FileBasedIndex.getInstance().getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, scope)` where
  `ForwardIndexer.KEY == 0` (`LuaIndex.kt:34`) and `scope = GlobalSearchScope.fileScope(file)` to
  isolate one file's record; assert on `record.requires`. `LuaFileBindingsIndexName` and
  `ForwardIndexer.KEY` are both `public`/top-level and importable from the test.

## 2. Fixtures & Base Classes
- **Base class**: `BasePlatformTestCase` (used by `TestLuaStubIndexing`, `LuaDescriptionIndexTest`).
  All fixtures are in-memory `myFixture.configureByText(...)` / `myFixture.addFileToProject(...)`;
  no vendored project or filesystem I/O needed (unlike `LuaDescriptionIndexTest`'s size test).
- **Index rebuild**: `configureByText` triggers indexing of the light fixture file, so the
  additional `StubIndex.forceRebuild` used by `IndexedBasePlatformTestCase`
  (`src/test/kotlin/net/internetisalie/lunar/lang/types/IndexedBasePlatformTestCase.kt:14`) is **not**
  required for these single-file queries — matching how `TestLuaStubIndexing` queries directly after
  `configureByText`.
- **Scope**: `GlobalSearchScope.allScope(project)` for key-set / element queries;
  `GlobalSearchScope.fileScope(configuredPsiFile)` for the per-file bindings record (TC-09/-10).

## 3. Query Patterns (concrete)
```kotlin
// FileBasedIndex — value-less presence (LuaCatsTypeNameIndex, LuaMemberFieldIndex)
val fbi = FileBasedIndex.getInstance()
fbi.getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyType", scope)   // Collection<VirtualFile>
fbi.getAllKeys(LuaMemberFieldIndex.KEY, project)                    // Collection<String>

// StubIndex — element resolution (LuaGlobalDeclarationIndex)
StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "cjson", project, scope, LuaFuncDecl::class.java)

// FileBasedIndex — forward record (LuaFileBindingsIndex)
val values = fbi.getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, GlobalSearchScope.fileScope(file))
val record = values.single()                                       // LuaFileBindingsRecord
record.requires                                                    // List<String>
```

## 4. Gaps / Untestable Spots
- **`LuaMemberFieldIndex` value** is always `""` (`:63`); only keys are meaningful, so tests assert
  on `getAllKeys`, not `getValues`.
- **`LuaFileBindingsIndex.bindings`** (the `PsiNamedElement` walk, `:360-399`) carries a `TODO`
  and is out of scope for MAINT-10 (require tracking is the requirement); tests assert only on
  `requires`.
- **`@class Dog: Animal`**: `TestLuaStubIndexing` (`:120`) notes the class-name `argType` text may
  include the inheritance suffix; MAINT-10 avoids inheritance syntax in its `@class` fixtures
  (uses bare `--- @class MyType`) so the indexed key is unambiguous.
- **Method target `a:m = ...`** is not valid assignable Lua and is not a realistic `LuaVar`
  assignment target; MAINT-10-05's exclusion coverage therefore uses the bare (`x = 1`) and bracket
  (`t[i] = 1`) cases, both of which `dottedMemberName` provably rejects (`LuaMemberFieldNames.kt:14`/`:17`).

## 5. Requirement Coverage
| Requirement | Priority | Covered by (section / TC) |
|-------------|----------|---------------------------|
| MAINT-10-01 | M | §1.1 / TC-01, TC-02 |
| MAINT-10-02 | M | §1.1 / TC-03 |
| MAINT-10-03 | M | §1.2 / TC-04, TC-05 |
| MAINT-10-04 | M | §1.3 / TC-06, TC-07 |
| MAINT-10-05 | M | §1.3 / TC-08 |
| MAINT-10-06 | M | §1.4 / TC-09, TC-10 |

## 6. Open Questions

_None — feature has cleared the planning bar._
