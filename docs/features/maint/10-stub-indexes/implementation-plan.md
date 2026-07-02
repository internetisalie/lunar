---
id: "MAINT-10-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-10"
folders:
  - "[[features/maint/10-stub-indexes/requirements|requirements]]"
---

# MAINT-10: Implementation Plan

Test-only feature. Each phase adds one test file under
`src/test/kotlin/net/internetisalie/lunar/lang/indexing/` extending `BasePlatformTestCase`,
using `myFixture.configureByText` and the query patterns in design §3. No production changes.

## Phases

### Phase 1: LuaCatsTypeNameIndex coverage [Must]
- **Goal**: Cover bare-tag type-name indexing (MAINT-10-01, MAINT-10-02).
- **File**: `src/test/kotlin/net/internetisalie/lunar/lang/indexing/LuaCatsTypeNameIndexTest.kt`
  (`class LuaCatsTypeNameIndexTest : BasePlatformTestCase()`), imports `LuaCatsTypeNameIndex`,
  `FileBasedIndex`, `GlobalSearchScope`.
- **Test methods**:
  - [x] `testBareClassTagIsIndexed` — TC-01. `configureByText("t.lua", "--- @class MyType")`;
        assert `FileBasedIndex.getInstance().getContainingFiles(LuaCatsTypeNameIndex.KEY, "MyType", scope)`
        contains `file.virtualFile`.
  - [x] `testBareAliasTagIsIndexed` — TC-02. `--- @alias MyAlias string`; assert containing files
        contain the file for key `MyAlias`.
  - [x] `testKeySetHasAnnotatedNamesOnly` — TC-03. File with `--- @class Foo`, `--- @alias Bar number`,
        and `local baz = 1`; assert `getAllKeys(...)` contains `Foo` and `Bar` and does NOT contain `baz`.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaCatsTypeNameIndexTest*"` green.

### Phase 2: Dotted global base-key coverage [Must]
- **Goal**: Cover `LuaGlobalDeclarationIndex` full-and-base dotted split (MAINT-10-03).
- **File**: `src/test/kotlin/net/internetisalie/lunar/lang/indexing/LuaGlobalDottedIndexTest.kt`
  (`class LuaGlobalDottedIndexTest : BasePlatformTestCase()`), imports `LuaGlobalDeclarationIndex`,
  `StubIndex`, `LuaFuncDecl`, `GlobalSearchScope`.
- **Test methods**:
  - [x] `testDottedFunctionIndexesFullKey` — TC-04. `configureByText("t.lua", "function cjson.decode() end")`;
        assert `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "cjson.decode", project, scope, LuaFuncDecl::class.java).size == 1`.
  - [x] `testDottedFunctionIndexesBaseKey` — TC-05. Same fixture; assert the query for key `cjson`
        also returns exactly one `LuaFuncDecl`.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaGlobalDottedIndexTest*"` green.

### Phase 3: LuaMemberFieldIndex direct-query coverage [Must]
- **Goal**: Cover qualified/deep keys and exclusions directly on the index (MAINT-10-04, MAINT-10-05).
- **File**: `src/test/kotlin/net/internetisalie/lunar/lang/indexing/LuaMemberFieldIndexTest.kt`
  (`class LuaMemberFieldIndexTest : BasePlatformTestCase()`), imports `LuaMemberFieldIndex`,
  `FileBasedIndex`. (Distinct from the existing navigation-level
  `net.internetisalie.lunar.lang.types.MemberFieldIndexTest`.)
- **Test methods**:
  - [x] `testQualifiedFieldKeysPresent` — TC-06. `self.width = 1\nself.height = 2`; assert
        `getAllKeys(LuaMemberFieldIndex.KEY, project)` contains `self.width` and `self.height`.
  - [x] `testDeepQualifiedKeyPresent` — TC-07. `a.b.c = 1`; assert keys contain `a.b.c`.
  - [x] `testBareAndBracketTargetsExcluded` — TC-08. `x = 1\nt[i] = 1`; assert keys contain neither
        `x` nor the bracket-target keys `t` / `t[i]` / `t.i` (`getAllKeys` is project-wide, so assert
        the exact forbidden keys are absent rather than a broad `t`-prefix, which stdlib keys share).
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --tests *lang.indexing.LuaMemberFieldIndexTest*"` green.

### Phase 4: LuaFileBindingsIndex require coverage [Must]
> **Note (test-only, no production change):** `LuaFileBindingsIndex` composes
> `LuaFileInputFilter` (`LuaIndex.kt:13-15`), whose `acceptInput` requires
> `file.url.startsWith("file:")`. Light `BasePlatformTestCase` fixtures (`configureByText`,
> `tempDirFixture.createFile`) are served from the in-memory temp filesystem with a `temp://` URL and
> are rejected. This phase therefore uses the proven **heavy, real-disk** fixture pattern from
> `LuaCrossFileCompletionHeavyTest` — an `IdeaTestFixtureFactory` project fixture backed by
> `TempDirTestFixtureImpl` (files on the real `LocalFileSystem`, `file:`-scheme URLs) with the temp
> dir registered as a source content root — so the index accepts and indexes the file. No production
> source was modified.

- **Goal**: Cover `require()` dependency extraction (MAINT-10-06).
- **File**: `src/test/kotlin/net/internetisalie/lunar/lang/indexing/LuaFileBindingsIndexTest.kt`
  (`class LuaFileBindingsIndexTest : BasePlatformTestCase()`), imports `LuaFileBindingsIndexName`,
  `ForwardIndexer`, `LuaFileBindingsRecord`, `FileBasedIndex`, `GlobalSearchScope`.
- **Test methods**:
  - [x] `testRequireIsTracked` — TC-09. Heavy real-disk fixture file `m.lua` = `require("mymodule")`;
        `getValues(LuaFileBindingsIndexName, ForwardIndexer.KEY, GlobalSearchScope.fileScope(project, file))`;
        assert the single `LuaFileBindingsRecord.requires` contains `mymodule`.
  - [x] `testNonRequireCallIgnored` — TC-10. `notrequire("mymodule")`; assert the record's
        `requires` is empty.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run "test --tests *LuaFileBindingsIndexTest*"` green.

## Requirement → Phase Coverage
| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-10-01 | M | Phase 1 |
| MAINT-10-02 | M | Phase 1 |
| MAINT-10-03 | M | Phase 2 |
| MAINT-10-04 | M | Phase 3 |
| MAINT-10-05 | M | Phase 3 |
| MAINT-10-06 | M | Phase 4 |

## Verification Tasks
- [ ] Run each phase's `--tests` pattern above.
- [ ] Full run: `tooling/gce-builder/gce-builder.sh run "test --tests *Index*"` to confirm no
      regression against existing `TestLuaStubIndexing` / `LuaDescriptionIndexTest` /
      `MemberFieldIndexTest`.
- [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` on the four new files.

## Task Summary
| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: LuaCatsTypeNameIndex coverage | done | Must |
| Phase 2: Dotted global base-key coverage | done | Must |
| Phase 3: LuaMemberFieldIndex direct-query coverage | done | Must |
| Phase 4: LuaFileBindingsIndex require coverage | done | Must |
