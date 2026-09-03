---
id: "REFACT-08"
title: "08: Rename a LuaCATS type name"
type: "feature"
status: "done"
priority: "medium"
parent_id: "REFACT/INTENT"
folders:
  - "[[features/refactoring/requirements|requirements]]"
---

# REFACT-08: Rename a LuaCATS type name

## Overview

Rename a `---@class` / `---@alias` type name and carry **every** spelling of it with the rename —
every other declaration of the same name, and every use of it in every LuaCATS tag in the project —
or refuse with a reason. This is the half of `REFACT-01-16` that did not ship: renaming a parameter
already moves its `---@param` tag, while a type name has no renameable symbol at all.

`REFACT-01-00-DR-04` established the premise and killed the cheap route. Of 70 running PSI elements
spelling a type name, **zero** answered `getReference()` or `getReferences()`, and neither the
hand-written nor the generated LuaCATS PSI holds a `PsiNamedElement`. Rewriting the name through
`LuaCatsTypeNameIndex` — the only index that knows type names — moves the `@class` and leaves every
use byte-identical, because that index maps **declaration sites only**.

## The reach, measured before the design (`REFACT-08-00-DR-01`)

The corpus cannot size this feature: **0 of its 734 files carry a `---@` tag**
([`build.gradle.kts:322-326`](../../../../build.gradle.kts), [[MAINT-39]]). The in-repo annotated
fixture (`src/test/resources/corpus/annotated/`, [[BUG-473]] DR-6) is two synthetic files. So DR-01
measured against **`lua-language-server` 3.10.6** (`66141703`), the reference implementation of the
dialect, over the 195 annotated `.lua` files under `meta/`, `script/` and `tools/`. Its own `test/`
tree is excluded deliberately: it holds intentionally malformed Lua for diagnostics tests, which
would pollute the parse-error count. The tree was staged into the fixture and the census run through
Lunar's own PSI on the gce builder; the full transcript is in `risks-and-gaps.md` under "DR-01
result".

| | |
| :--- | ---: |
| annotated files scanned | 195 |
| files with at least one `PsiErrorElement` | 19 (33 errors) |
| `@class` tags reached through PSI | 188 (raw text scan finds 191) |
| `@alias` tags reached through PSI | 46 (raw text scan finds 46) |
| distinct declared type names | 188 (142 `@class` + 46 `@alias`, disjoint) |
| …of those, colliding with a LuaCATS **builtin keyword** | 11 |
| …the one parameterized head (`table<K, V>`) | 1 |
| distinct declared names in scope for this feature | **176** |
| …of those, also declared by a **bundled runtime stub**, so newly refused by `REFACT-08-16` | **`_G`, and nothing else** |
| **use occurrences a rename must rewrite** | **1 126** |
| …as `LuaCatsNamedType` | 1 082 |
| …as `LuaCatsTypeParam` | 44 — re-derived by `REFACT-08-00-DR-04` as **44 uses and 0 declarations** on this corpus |
| declared names with 0 / 1-5 / 6-20 / 21-100 / >100 uses | 57 / 93 / 16 / 8 / 2 |
| the busiest name, `parser.object` | 324 uses; the word index narrows to 46 files and `ReferencesSearch` returns them from 45 |
| declared names that are plain Lua identifiers | 85 |
| declared names containing `.`, `-` or `*` | **91** |

**Each row below is a requirement rather than colour:**

- **1 126 uses in three spellings, and all three are reachable.** Every use occurrence sits under
  `LuaCatsNamedType`, `LuaCatsTypeParam` or `LuaCatsGenericType` — one NAME leaf each, by the
  grammar (`namedType ::= NAME`, `typeParam ::= NAME`, `genericType ::= NAME`,
  [`luacats.bnf:201-207`](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/parser/luacats.bnf)).
  `REFACT-08-00-DR-02` then rewrote all of them, cross-file, from both a declaration caret and a use
  caret.
- **91 of the 176 in-scope names are not Lua identifiers.** `LuaNamesValidator.isIdentifier` matches
  `^[A-Za-z_][A-Za-z0-9_]*$`
  ([LuaNamesValidator.kt:24](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/LuaNamesValidator.kt)),
  and `RenameUtil.isValidName` falls through to it for any element with no
  `renameInputValidator`. Without one, the majority of real type names could not be renamed **to
  their natural new spelling**. `REFACT-08-08` is that validator.
- **The library-collision refusal fires, and on one name.** `REFACT-08-16` refuses a name any
  attached read-only library also declares, so the question is how much of the reach it removes.
  Measured 2026-09-02 at `a25cf998` by intersecting two name sets: every `---@class` / `---@alias`
  name in the reach corpus, and every one in the plugin's bundled runtime stubs
  (`src/main/resources/runtime` — the union across all bundled runtimes, which is an upper bound,
  since a project attaches one). The intersection is
  `_G any boolean function nil number string table thread userdata`; all of it except `_G` is a
  builtin keyword `REFACT-08-07` already refuses on other grounds, so `_G` is what `-16` adds.
  Reproduce:
  ```bash
  grep -rhoE '@(class|alias)\s+(\(exact\)\s+)?[A-Za-z_*][A-Za-z0-9_.*-]*' \
      ~/Documents/src/lua/lua-language-server/{meta,script,tools} \
    | sed -E 's/.*@(class|alias)\s+(\(exact\)\s+)?//' | sort -u > /tmp/lls.names
  grep -rhoE '@(class|alias)\s+(\(exact\)\s+)?[A-Za-z_*][A-Za-z0-9_.*-]*' src/main/resources/runtime \
    | sed -E 's/.*@(class|alias)\s+(\(exact\)\s+)?//' | sort -u > /tmp/stub.names
  comm -12 /tmp/lls.names /tmp/stub.names
  ```
  So `-16` is not the sound-but-empty predicate [[REFACT-09]] has to argue for: on this corpus it
  fires, and it costs exactly the one name above. Two cautions on reading it further: the stub set is
  the union of every bundled runtime rather than the one a project attaches, and a project that also
  attaches rocks or a fetched definitions tree will collide on more; and the name set here is a text
  scan whose declared-name total (188) is the pre-exclusion figure, while 176 is the same set after
  the 11 builtin keywords and the one parameterized head are removed — the intersection is unmoved
  either way, since every member of it is present in both readings.
- **11 of the 188 declared names collide with a builtin keyword and are unreachable.** `builtinType` is a fixed keyword
  alternative tried before `namedType` (`luacats.bnf:205-207`), so every use of a `---@class table`
  parses as `LuaCatsBuiltinType` and no `LuaCatsNamedType` exists to carry a reference. Measured
  directly (DR-02 P9): `builtinTypes=[table] namedTypes=[] references=0`. `REFACT-08-07` refuses
  them rather than half-applying.

**What is out of reach, and how much of it there is.** 87 occurrences of a declared name (82 under
`DUMMY_BLOCK`, 5 directly under `LAZY_COMMENT`) sit inside comment regions the LuaCATS parser did
not structure, concentrated in five files (`guide.lua` 56, `export.lua` 16, `debug.lua` 5,
`ffi.lua` 2, `template.lua` 2). No reference can attach there, so those spellings survive a rename.
`risks-and-gaps.md` Gap 2.3 owns it.

**87 is a floor, not a ceiling, and the exclusion that makes it one biases it downward.** The residue
tracks the 19 of 195 files carrying a `PsiErrorElement`, not the dialect — and DR-01 deliberately
excluded `lua-language-server`'s own `test/` tree, which is where its *intentionally malformed* Lua
lives. Excluding it removes exactly the files that generate this residue class, so a census over the
whole checkout would report more than 87, not fewer. The exclusion is right for the parse-error count
it was made for; it is the wrong direction for this number, and the number is quoted as a lower bound
accordingly.

## What DR-02 executed, and the one thing it corrected

`REFACT-08-00-DR-02` built the whole design as a throwaway prototype and ran it. Every row of "Test
Cases" below is transcribed from that run. It corrected one claim `REFACT-01-00-DR-04` recorded as
read rather than run:

> *"cats PSI reports `LuaLanguage`, so a `psi.referenceContributor language="Lua"` patterned on
> `LuaCatsNamedType` is a legal registration."*

It is a legal registration and it is **inert**. `LuaCatsBaseElement`
([LuaCatsBaseElements.kt:11-15](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsBaseElements.kt))
does not override `getReferences()`, so it inherits `PsiElementBase.getReferences()` →
`SharedPsiElementImplUtil.getReferences(this)`, which returns `getReference()` and **never consults
`ReferenceProvidersRegistry`**. With the contributor registered and its pattern matching, DR-02
measured `references=0` and a rename that moved the `@class` and left all eleven uses stale — the
index route's half-apply reproduced by the reference route. The Lua side already solves this:
`LuaBaseElement.getReferences()` calls `ReferenceProvidersRegistry.getReferencesFromProviders(this)`
explicitly
([LuaBaseElements.kt:36-48](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaBaseElements.kt)).
`REFACT-08-14` is the symmetric change on `LuaCatsBaseElement`, and without it every other
requirement here is unreachable.

## Scope

### In Scope

- Renaming a `---@class <Name>` or `---@alias <Name>` type name from the declaration caret or from
  any use caret, rewriting **every** declaration slot spelling that name and **every** use site in
  the project, in one undoable write.
- The three use spellings `LuaCatsNamedType`, `LuaCatsTypeParam` and `LuaCatsGenericType`, which by
  the grammar cover `@type`, `@param`, `@return`, `@field`, `@cast`, `@vararg`, `@operator`, a
  parent type in `@class X : Y`, a union member, an array element, a `fun(...)` argument or return,
  and a type argument inside `<...>` — **in a use position only**. Two of the three are also reached
  in *declaration* positions, which `REFACT-08-17` excludes.
- New-name validation against the **LuaCATS** `NAME` grammar rather than the Lua identifier grammar.
- A conflict report when the new name already names a type.
- A refusal for each shape whose uses cannot be reached (`REFACT-08-07`, `REFACT-08-09`) and for
  each name whose declarations cannot all be **written** (`REFACT-08-16`).
- Go to Declaration and Find Usages on a type name, which fall out of the same reference.

### Out of Scope — each measured, each refused or excluded rather than half-applied

- **The Lua-side host declaration.** `---@class Widget` above `local Widget = {}` is two symbols.
  Renaming the tag leaves the local alone and the class still resolves under the new name (DR-02 P8:
  `before=Widget afterOld=null afterNew=Gadget`, host line unchanged), because
  `LuaLocalVarStubElementType.createStub` re-derives `className` from the rewritten tag. Renaming
  the local remains `LuaRenameProcessor`'s job and is unchanged. `risks-and-gaps.md` Gap 2.1 records
  the alternative and why it is not taken.
- **`---@enum` names.** `LuaCatsEnumTag` has exactly one consumer in `src/main/kotlin` — the
  delegating accessor `LuaCatsLazyCommentImpl.getEnumTagList` — so nothing in Lunar reads an enum
  name as a type. Reproduce: `grep -rn "LuaCatsEnumTag" --include=*.kt src/main/kotlin`.
- **Renaming a `---@generic T` type parameter, or a `<T>` of `---@class Box<T>`.** These are
  declarations of a function- or class-local type parameter, not of a project type, and this feature
  offers no rename for them. They are *in* scope in one direction only: a rename of a project type
  that happens to share the spelling must **not** rewrite them, nor the tags they shadow
  (`REFACT-08-17`).

  Both sit in a `LuaCatsTypeParam`, which `typeParam ::= NAME` (`luacats.bnf:203`) reaches from **two**
  rules — `parameterizedName` (`:201`) and `genericTypeParam` (`:117`) — so the NAME leaf's parent is
  `TYPE_PARAM` in both, and it is the grandparent that separates a declaration from a use.
  Executed (`REFACT-08-00-DR-04` P-A):
  `---@class Box<T>` gives `TYPE_PARAM > PARAMETERIZED_NAME > ARG_TYPE > CLASS_TAG`, `---@generic T`
  gives `TYPE_PARAM > ARG_NAME > GENERIC_TYPE_PARAM > GENERIC_TYPE_PARAMS > GENERIC_TAG`, and a
  genuine use (`---@type Box<Widget>`) gives
  `TYPE_PARAM > PARAMETERIZED_NAME > DISTINCT_TYPE > …`. `design.md` §3.4 is the discrimination.
- **Parameterized class heads** `---@class Foo<T>` — refused (`REFACT-08-09`); 2 of 188 class tags
  in the reach corpus.
- **Builtin-keyword type names** — refused (`REFACT-08-07`); 11 of the 188 declared names.
- **A type name a read-only library also declares** — refused (`REFACT-08-16`). The bundled runtime
  stubs declare non-builtin class names (`File`, `io`, `os`, `math`, …), and a rename cannot write
  them. `design.md` §3.11 states the rule and what it gives up.
- **Prose mentions** of a type name inside a tag's description (`LuaCatsDescription`, 50
  occurrences) and names that coincide with a parameter or field name (`LuaCatsArgName` 154,
  `LuaCatsFieldNameDescriptor` 16). These are correctly *not* type uses and must not be rewritten.
- **Un-parsed comment regions** (`DUMMY_BLOCK` / bare `LAZY_COMMENT`, 87 occurrences) — no PSI to
  attach a reference to. Gap 2.3.
- **In-place (inline) rename of a type name.** `LuaInplaceRenameHandler` gates on
  `LuaElementTypes.IDENTIFIER`
  ([LuaInplaceRenameHandler.kt](../../../../src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaInplaceRenameHandler.kt));
  a cats NAME leaf is `LuaCatsElementTypes.NAME`. Deferred, `risks-and-gaps.md` TBD-1.
- **Stubbing the LuaCATS comment.** Not on the critical path — `design.md` Alternative A states the
  cost and what it would and would not buy.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
|----|-------------|----------|--------|-------------|
| `REFACT-08-01` | **Rename from the declaration caret** | M | Full | Caret on the name in `---@class Widget` or `---@alias Handle string` renames it and every use of it in the project. Delivered Phase 4: `LuaCatsTypeRenameProcessor`, verified by `LuaCatsTypeRenameTest` (TC-1, TC-5, TC-25). |
| `REFACT-08-02` | **Rename from a use caret** | M | Full | Caret on `Widget` in `---@param p Widget` substitutes to the declaration and does the same. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-4). |
| `REFACT-08-03` | **Every declaration slot moves** | M | Full | A name declared by more than one `@class` tag (LuaCATS allows re-opening) has every one of its declaration slots rewritten, in every file. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-6) and mutation-proved (mutation A). |
| `REFACT-08-04` | **Every use spelling moves, cross-file** | M | Full | `LuaCatsNamedType`, `LuaCatsTypeParam` and `LuaCatsGenericType` are all rewritten, in every file in the project scope. A `LuaCatsGenericType` that is the **head of a parameterized class declaration** (`---@class Box<T>`) is not a use and is not rewritten; one in any other position — `---@type Box<string>`, `---@class Panel : Box<string>` — is. `REFACT-08-17` states the same exclusion for `LuaCatsTypeParam`, which has two declaration positions of its own. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-1, TC-21, TC-22, TC-23). |
| `REFACT-08-05` | **`@alias` names rename like `@class` names** | M | Full | The declaration slot differs (`LuaCatsArgName` vs `LuaCatsArgType`); everything downstream is identical. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-20). |
| `REFACT-08-06` | **The Lua-side host declaration is untouched** | M | Full | Renaming the tag above `local Widget = {}` leaves `local Widget` spelled as it was, and the class resolves under the new name. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-7). |
| `REFACT-08-07` | **A builtin-keyword type name is refused** | M | Full | Renaming a `---@class table` is declined with a message naming the reason; the file is byte-identical. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-10, TC-11) and mutation-proved (mutation F, isolated with `integer`, a builtin no bundled stub also declares). |
| `REFACT-08-08` | **The new name is validated against the LuaCATS name grammar** | M | Full | `parser.node` and `ffi.cdata*` are accepted; `has space` and a builtin keyword are rejected. The Lua identifier grammar still governs Lua renames. Delivered Phase 5: `LuaCatsTypeNameInputValidator`, verified by `LuaCatsTypeNameInputValidatorTest` (TC-8, TC-9) and mutation-proved (mutation E, plus the pattern-narrowing regression pinned in `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel`). |
| `REFACT-08-09` | **A parameterized class head is refused** | M | Full | A caret on the head of `---@class Box<T>` is offered no rename target at all, and the file is byte-identical. Unlike `-07` this refusal carries no message: the platform finds nothing to rename before any Lunar code is asked. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-13). |
| `REFACT-08-10` | **Atomic** | M | Full | The rename is one undoable write action; a refusal writes nothing. Delivered Phase 4: `renameElement`'s single `ProgressManager.executeNonCancelableSection`, verified by `LuaCatsTypeRenameTest` (TC-15, TC-24). |
| `REFACT-08-11` | **Renaming onto an existing type name is reported** | S | Full | A conflict is raised through the existing `LuaRenameCollisionUsageInfo` carrier rather than silently merging two types. Delivered Phase 6: `LuaCatsTypeRenameProcessor.findCollisions`, verified by `LuaCatsTypeRenameConflictTest` (TC-14, plus its negative control — renaming to an undeclared name raises no conflict) and mutation-proved (mutation H). |
| `REFACT-08-12` | **Go to Declaration from a use site** | S | Full | Ctrl+Click on `Widget` in `---@param p Widget` navigates to its `---@class Widget`. Delivered Phase 2: `LuaCatsTypeReference.resolve()`, verified by `LuaCatsTypeReferenceTest.testUseResolvesToTheDeclarationInAnotherFile` (TC-16). |
| `REFACT-08-13` | **Find Usages on a type name** | S | Full | Find Usages on a `@class` name lists every use site. Delivered Phase 3: `LuaCatsTypeReferenceSearcher` plus the `LuaFindUsagesProvider` clause, verified by `LuaCatsTypeReferenceSearchTest` (TC-2, TC-17). |
| `REFACT-08-14` | **LuaCATS PSI consults the reference registry** | M | Full | `LuaCatsBaseElement` answers `getReferences()`/`getReference()` from `ReferenceProvidersRegistry`, as `LuaBaseElement` already does. Without it every requirement above is unreachable — measured, not argued. Delivered Phase 1: `LuaCatsBaseElements.kt`, verified by `LuaCatsTypeDeclarationsTest.testGetReferencesReachesTheRegistryAndGetReferenceReadsIt` (TC-3, both halves) plus the full-suite gate. |
| `REFACT-08-15` | **No regression to the routes that work today** | M | Full | Lua rename, label rename, Find Usages, Go to Class/Symbol, quick doc, type resolution and the type hierarchy behave exactly as they do at `154f26f3`. Closed Phase 7: `tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache"` reports **2 993 tests, 0 failures, 0 errors, 1 skipped across 475 files** — `154f26f3`'s baseline (2 989 tests, 0 failures, 0 errors, 1 skipped, 473 files) plus exactly the four new tests across the two new files Phases 5 and 6 added (`LuaCatsTypeNameInputValidatorTest`, `LuaCatsTypeRenameConflictTest`); `LuaRenameTest`, `LuaRenameConflictTest` and `LuaCatsParamRenameTest` are unchanged and green in the same run. |
| `REFACT-08-17` | **A type-parameter declaration is not a use** | M | Full | Renaming a project type whose name is also spelled as a type parameter leaves both declaration positions of `LuaCatsTypeParam` alone — the `<T>` of `---@class Box<T>` (`parameterizedName`, `luacats.bnf:201`) and the `T` of `---@generic T` (`genericTypeParam`, `:117`) — and leaves the tags those parameters shadow inside the same `LuaCatsComment` alone with them. A `T` in a comment that declares no type parameter is an ordinary use and is still rewritten. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-26, TC-27, TC-28) and mutation-proved (mutations T, U, V). |
| `REFACT-08-16` | **A type any read-only library also declares is refused** | M | Full | Resolution sees `GlobalSearchScope.allScope`; the rewrite may write only `GlobalSearchScope.projectScope`. When a declaration slot for the name lies outside the project — a bundled runtime stub, a rock, a fetched definitions tree — the rename is declined with a message naming the file, and nothing is written. `design.md` §3.11 states the rule and what it gives up. Delivered Phase 4, verified by `LuaCatsTypeRenameTest` (TC-24, TC-25) and mutation-proved (mutations R and R2, by hand). |

## Behaviour Rules

- **A rename that cannot reach every use is refused, not attempted.** Both measured half-applies —
  the index route (`REFACT-01-00-DR-04` P4) and the reference route without `REFACT-08-14`
  (`REFACT-08-00-DR-02` P3) — are silent: `LuaTypeManager.resolveType` and
  `LuaCatsDeclaredType.isType` both flip to false and nothing is reported to the user. Refusal is
  strictly better than either.
- **The declaration slot and the use slot are asymmetric, and that asymmetry is the grammar's.**
  A class name is its tag's `LuaCatsArgType`; an alias name its `LuaCatsArgName`
  (`luacats.bnf:82, 91`). `LuaCatsTypeNameIndex` and `LuaCatsTypeNavigation` already encode exactly
  this pair; this feature reuses them and defines **no second map from a type name to its
  declaration**. It does add a leaf-level reader of the same pair (`design.md` §3.2), because a
  rename needs the PSI leaf to write and neither of those two hands one out; that reader is the only
  place the pair is spelled again.
- **The scope that resolves a name is not the scope that may rewrite it.** Resolution reads
  `GlobalSearchScope.allScope` because `LuaTypeManagerImpl` does, and disagreeing with it would make
  navigation and type resolution answer different questions. The rewrite writes only
  `GlobalSearchScope.projectScope`, which is the scope the platform's own `RenameProcessor` searches
  usages in by default (`RenameProcessor.java:104`). Where the two disagree — a declaration slot in a
  library — the rename is refused rather than narrowed, because narrowing is a silent split
  (`REFACT-08-16`, measured under mutation R).
- **Nothing here is a text search.** The non-code ("search in comments and strings") route is
  disabled for this element (`design.md` §3.9): the type name already lives in a comment, and a text
  pass over the same comment would rewrite the prose mentions this feature deliberately excludes.

## Test Cases

Every row was executed on the gce builder against a throwaway prototype of `design.md` §2-§3, plus
throwaway production files and the `plugin.xml` registrations of §4 — all reverted, with
`git status --porcelain` empty on both trees afterwards. Rows 1-20 come from `REFACT-08-00-DR-02`
(`LuaCatsTypeRenameProtoTest`) and rows 21-25 from `REFACT-08-00-DR-03`
(`LuaCatsTypeWriteScopeProbeTest`). The "Then" column is transcribed output. Every mutation named
below was applied to the prototype and the whole probe class re-run; `risks-and-gaps.md` "DR-02
mutation results" and "DR-03 mutation results" carry each one's full observation.

**A probe number indexes an observation, not a test method** — one method can report more than one,
so the DR-02 probe table's row count and its harness note's method count measure different things.
Where a mutation's effect is quoted as a fraction below, the denominator is always **observations**,
never methods.

**Fixture isolation.** Each row is one test method with one `configureByText` plus its own
`addFileToProject` siblings: `LuaTypeManagerImpl.resolveType` searches
`GlobalSearchScope.allScope(project)`
([LuaTypeManagerImpl.kt:322](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypeManagerImpl.kt)),
so a stray sibling binds a type name to the wrong file.

| # | Requirement | Given (fixture, caret marked) | When | Then | Mutation that turns it red (executed) |
|---|-------------|-------------------------------|------|------|---------------------------|
| 1 | `REFACT-08-01`, `-04`, `-14` | `types.lua` = `--- @class Wid<caret>get` + `local Widget = {}`; `uses.lua` spelling `Widget` in eleven slots — `@type`, `@param`, `@return`, `@class Panel : Widget`, `@field`, `Widget[]`, `table<string, Widget>`, `Widget\|nil` in an `@alias`, `fun(a: Widget): Widget` (twice) and `@cast` | rename to `Gadget` | `staleWidgetSpellings=0 newGadgetSpellings=11`; `types.lua` reads `--- @class Gadget` | **revert `design.md` §2.2** (leave `LuaCatsBaseElement` inheriting `PsiElementBase.getReferences`) → `staleWidgetSpellings=11 newGadgetSpellings=0`: the declaration moved and every use stayed |
| 2 | `REFACT-08-04`, `-14` | row 1's fixture | `ReferencesSearch.search(declarationLeaf, allScope)` | `references=11 byFile={uses.lua=11} byHolder={NAMED_TYPE=10, TYPE_PARAM=1}` | **mutation K** — drop `LuaCatsTypeParam` from the contributor's pattern list → `references=10 byHolder={NAMED_TYPE=10}`, and row 1 leaves `staleWidgetSpellings=1`. At real-tree scale the same mutation moves `parser.object` from 324 references to 320 |
| 3 | `REFACT-08-14` | row 1's fixture | keep §2.2's `getReferences()` and drop only its `getReference()` override | *(negative control)* `firstNamedType.reference=null getReferences=1 references=0` | this row **is** a mutation, of the intermediate state a reviewer would pass on inspection: the contributed reference is present and every consumer reading `element.reference` sees null |
| 4 | `REFACT-08-02` | `types.lua` = `--- @class Widget` + `local Widget = {}`; `uses.lua` = `--- @param p Wid<caret>get` + `local function f(p) return p end` | rename to `Gadget` | `uses.lua` = `--- @param p Gadget`, `types.lua` = `--- @class Gadget` | **mutation J** — `LuaCatsTypeReference.resolve` returns null → `testP4RenameFromAUseCaret FAILED`. Note the mutation that does **not** work: emptying `substituteElementToRename`'s use branch leaves this row green, because `TargetElementUtilBase` follows the reference first (`risks-and-gaps.md` Gap 2.7) |
| 5 | `REFACT-08-01` | `types.lua` = `--- @class Wid<caret>get` + `local Widget = {}` | `TargetElementUtil.findTargetElement(editor, allAccepted)` | `PsiElement(NAME) type=NAME text='Widget'` | **mutation D** — delete the `isDeclarationLeaf` clause from `LuaTargetElementEvaluator.getNamedElement` → `findTargetElement=null`, and five observations of that run go red: the declaration caret has no reference and no `PsiNamedElement` parent, so nothing else supplies a target |
| 6 | `REFACT-08-03` | `types.lua` = `--- @class Wid<caret>get` + `local Widget = {}`; `more.lua` = a second `--- @class Widget` + `--- @field extra string`; `uses.lua` = `--- @type Widget` | rename to `Gadget` | all three files on `Gadget`: `types` and `more` both re-declare it, `uses` refers to it | **mutation A** — replace `declarationLeaves(...)` with `listOf(element)` in `renameElement` → `more=<<<--- @class Widget`, so the project holds two types where it held one |
| 7 | `REFACT-08-06` | `types.lua` = `--- @class Wid<caret>get` + `--- @field w string` + `local Widget = {}`; `uses.lua` = `--- @type Widget` | rename to `Gadget`, then `LuaTypeManager.resolveType` for both names | `before=Widget afterOld=null afterNew=Gadget`, and the host line still reads `local Widget = {}` | none exists, deliberately: the host is never in the rewrite set, because `design.md` §3.7 writes only cats slots. Row 1's mutation is the falsifier for the rewrite set as a whole, and `risks-and-gaps.md` Gap 2.1 records why the two symbols stay independent |
| 8 | `REFACT-08-08` | `types.lua` = `--- @class parser.object` + `local M = {}` | `RenameUtil.isValidName(project, declarationLeaf, …)` for seven candidates, plus the Lua-side `M` leaf as a control | `parser.node=true Gadget=true table=false 'has space'=false 9bad=true ffi.cdata*=true goto=true`; control `M`: `parser.node=false` | **mutation E** — delete `newName in BUILTIN_KEYWORDS` → `table=true`, admitting a new name whose every future use would parse as `LuaCatsBuiltinType`. The control column is its own gate: with an over-broad `getPattern()` it reads `true` and `LuaNamesValidatorTest.testRenameUtilReachesValidatorForLabel` goes red |
| 9 | `REFACT-08-08` | as row 8, plus `uses.lua` = `--- @param p parser.object` / `--- @return parser.object` | rename `parser.object` to `parser.node` | `types.lua` = `--- @class parser.node`; both use lines rewritten | **mutation B** — narrow the searcher's `CacheManager.getFilesWithWord` context from `IN_COMMENTS` to `IN_CODE` → `references=0`, both use lines stale, and at real-tree scale 10 references in 1 file instead of 324 in 45 |
| 10 | `REFACT-08-07` | `types.lua` = `--- @class table` + `local T = {}`; `uses.lua` = `--- @param p table` | inspect the use PSI and search references | `builtinTypes=[table] namedTypes=[] references=0` | none is possible from this fixture, and that is the finding: `builtinType` is a fixed keyword alternative tried before `namedType` (`luacats.bnf:205-207`), so no `LuaCatsNamedType` exists for a provider to match. Row 11 is where the refusal is falsified |
| 11 | `REFACT-08-07` | as row 10, caret on `--- @class ta<caret>ble` | rename to `Gadget` | refused; both files byte-identical | **mutation F** — delete `substituteElementToRename` step 2 → the rename proceeds and reaches the plugin's own bundled stub, failing with `Cannot modify a read-only file '…/runtime/standard/lua-5.4/table.lua'`. Read-only is the only thing that stopped it |
| 12 | `REFACT-08-09` | `types.lua` = `--- @class Box<T>` + `local Box = {}` | `LuaCatsTypeDeclarations.classDeclarationLeaf(tag)` | `null` | **mutation G3** — relax `classDeclarationLeaf`'s `firstChildNode` element-type test to `firstChildNode?.psi` → `PARAMETERIZED_NAME`. Note the mutation that does **not** work: deleting the explicit `LuaCatsParameterizedName` guard leaves this row green, which is why `design.md` §3.2 does not carry that guard |
| 13 | `REFACT-08-09` | as row 12, caret on `--- @class Bo<caret>x<T>`, with `uses.lua` = `--- @type Box` | rename to `Crate` | no rename target is offered; both files byte-identical | falsified by row 12's mutation together with mutation G: with both applied the head becomes a declaration leaf, `declarationLeaves("Box")` returns nothing because the index holds `Box<T>`, and the rewrite covers the caret's own slot alone |
| 14 | `REFACT-08-11` | `types.lua` = `--- @class Wid<caret>get`; `other.lua` = `--- @class Gadget` | rename to `Gadget` | `ConflictsInTestsException: A LuaCATS type named 'Gadget' is already declared in this project` | **mutation H** — return from `findCollisions` before its lookup → `outcome=RENAMED`: two declarations merge into one type, with the members of both, and nothing is reported |
| 15 | `REFACT-08-10` | `types.lua` = `--- @class Wid<caret>get` + `local Widget = {}`; `uses.lua` = `--- @type Widget` | rename to `Gadget`, then `UndoManager.undo(selectedEditor as? TextEditor)` — the idiom `LuaRenameUndoTest.undoAfterRenameRestoresTheDocument` uses ([LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt)) | `renamed=true typesRestoredDoc=true usesRestoredDoc=true` | inherited from the single `executeNonCancelableSection` of `design.md` §3.7; `LuaRenameUndoTest` is the existing gate for the mechanism and this row extends it to two files. **The harness facts below are load-bearing**: a two-file rename makes the platform ask `Undo Renaming type Widget to Gadget?`, so `TestDialogManager.setTestDialog(TestDialog.OK)` is required, and the assertion must read `Document` text — read from PSI, the same restored document reported `typesRestored=false` |
| 16 | `REFACT-08-12` | `types.lua` = `--- @class Widget`; `uses.lua` = `--- @param p Wid<caret>get` | `LuaCatsTypeReference.resolve()` on the use holder | the `NAME` leaf of `types.lua`'s `--- @class Widget` | **mutation J**, as row 4 |
| 17 | `REFACT-08-13` | row 1's fixture | `LuaFindUsagesProvider.canFindUsagesFor(declarationLeaf)` and `getType` | `canFindUsagesFor=true type='type'` | **mutation I** — delete the cats clause from `canFindUsagesFor` → `false`, while `getType` still answers `'type'` and every rename probe stays green: the Find Usages gate and the rename are independent |
| 18 | `REFACT-08-04` | the 195 annotated files of `REFACT-08-00-DR-01`, staged into the fixture | `ReferencesSearch.search` for `parser.object` | `references=324 files=45 elapsedMs=1457`, `byHolder={NAMED_TYPE=320, TYPE_PARAM=4}` — matching DR-01's independent census of 324 uses | **mutations B and K** both move it: `10 files=1` and `320` respectively. This row is what makes rows 1-2 more than a statement about an eleven-line fixture |
| 19 | `REFACT-08-15` | the whole unit suite | `test --rerun --no-build-cache` with the prototype in place | see `risks-and-gaps.md` "DR-02 result" and "DR-03 result" for the measured counts and the one failure they found | not a mutation row: it is the regression gate for `REFACT-08-14`, whose blast radius is every LuaCATS PSI element, and it is what caught the input validator's over-broad pattern |
| 20 | `REFACT-08-05` | `types.lua` = `--- @alias Han<caret>dle string`; `uses.lua` = `--- @param p Handle` / `--- @return Handle` | inspect the slot, then rename to `Token` | `aliasDeclarationLeaf=Handle isDeclarationLeaf=true references=2`, then `--- @alias Token string` with both use lines rewritten | **mutation L** — `aliasDeclarationLeaf` returns null → `aliasDeclarationLeaf=null isDeclarationLeaf=false references=null` and the rename is refused, while every `@class` row stays green: the two declaration slots are independent |
| 21 | `REFACT-08-04` | `types.lua` = `--- @class Bo<caret>x` + `local B = {}`; `uses.lua` = `--- @type Box<string>` + `--- @param p Box` | search references, then rename to `Crate` | `references=2 byHolder={GENERIC_TYPE=1, NAMED_TYPE=1}`, and `uses.lua` reads `--- @type Crate<string>` / `--- @param p Crate` | **mutation M** — drop `LuaCatsGenericType` from the contributor's pattern list → `references=1 byHolder={NAMED_TYPE=1}` and `uses.lua` keeps `--- @type Box<string>`. This is `REFACT-08-04`'s third spelling, `LuaCatsGenericType`, in a use position |
| 22 | `REFACT-08-04` | as row 21, plus `params.lua` = `--- @class Box<T>` + `--- @field item T` | rename `Box` to `Crate` | `references=1 files={uses.lua=1}`; `params.lua` is byte-identical — the parameterized **declaration head** is not a use | **mutation N** — make `isDeclarationSlotHolder` return false → `references=2 files={params.lua=1, uses.lua=1}` and `params.lua` becomes `--- @class Crate<T>`: a second, differently-keyed type renamed without being asked. This settles `risks-and-gaps.md` Gap 2.5 by execution |
| 23 | `REFACT-08-04` | `types.lua` = `--- @class Bo<caret>x`; `uses.lua` = `--- @class Panel : Box<string>` | rename to `Crate` | `references=1 byHolder={GENERIC_TYPE=1}`; `uses.lua` reads `--- @class Panel : Crate<string>` | *(negative control for row 22's guard, so the guard cannot be widened into a blanket exclusion of `GENERIC_TYPE`)* — under **mutation M** this row reads `references=0` and the parent type stays `Box<string>` |
| 24 | `REFACT-08-16`, `-10` | `types.lua` = `--- @class Fi<caret>le` + `local F = {}`; `uses.lua` = `--- @param p File` / `--- @return File`. The plugin's own bundled stub declares `---@class File` in `runtime/standard/lua-5.4/io.lua` | rename to `Handle` | `REFUSED RefactoringErrorHintException` carrying `catsLibraryType:'File' in [ …/lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua ]`; `typesUnchanged=true usesUnchanged=true` | **mutation R** — delete the out-of-project refusal from `substituteElementToRename` → `outcome=RENAMED typesUnchanged=false usesUnchanged=false` with the library's `File` left behind: **no exception at all**, one type silently split in two. **Mutation R2** — R plus the write scope widened back to `allScope` → `IncorrectOperationException: Cannot modify a read-only file '…/io.lua'` **after** `uses.lua` was already rewritten (`usesUnchanged=false`): a half-applied rename |
| 25 | `REFACT-08-16`, `-01` | `types.lua` = `--- @class Wid<caret>get` + `local W = {}`; `uses.lua` = `--- @param p Widget` / `--- @return Widget`. No library declares `Widget` | rename to `Gadget` | `outcome=RENAMED staleWidget=0 newGadget=2` | *(control for row 24, so its refusal cannot be a blanket one)*, and separately the falsifier for the searcher itself: **mutation O** — remove the `referencesSearch` registration of `LuaCatsTypeReferenceSearcher` → `outcome=RENAMED staleWidget=2 newGadget=0`, the declaration moved and both uses left stale, with rows 21-23 all reporting `references=0`. This is what `design.md` §2.5's necessity argument rests on, and it corrects `REFACT-01`'s `risks-and-gaps.md` claim that no searcher is needed |

**Rows 26-28 are specified, not transcribed.** They came out of `REFACT-08-00-DR-04`, which measured
the *predicate* rather than an end-to-end rename: no prototype of §2-§3 exists any more, so the
"Then" column for these three is the expected outcome, and what is executed is the discrimination
underneath it (P-A, P-B and P-E, quoted in `design.md` §3.1 and §3.4). Each named mutation is
reachable from its own fixture because P-B and P-E show the two predicates disagreeing on exactly
these shapes; none of the three has an executed rename transcript, and `risks-and-gaps.md` DR-04
records that as the limit.

| # | Requirement | Given (fixture, caret marked) | When | Then | Mutation that turns it red |
|---|-------------|-------------------------------|------|------|---------------------------|
| 26 | `REFACT-08-17`, `-04` | `types.lua` = `--- @class <caret>T` + `local T = {}`; `boxes.lua` = `--- @class Box<T>` + `local Box = {}`; `uses.lua` = `--- @param p T` + `local function f(p) return p end` | rename `T` to `Elem` | `types.lua` = `--- @class Elem`, `uses.lua` = `--- @param p Elem`, and `boxes.lua` is **byte-identical** — `Box`'s type parameter is a differently-scoped declaration | **mutation T** — restore `holder is LuaCatsGenericType` as a conjunct of `isDeclarationSlotHolder` clause 2, so the clause reaches the head and not the parameters → `boxes.lua` becomes `--- @class Box<Elem>` and a second, differently-keyed type is renamed unasked. Reachability is executed: DR-04 P-B reports `holder=TYPE_PARAM text='T' line=1 SPEC=DECLARATION SHIPPED=use <== DISAGREE` on this exact shape |
| 27 | `REFACT-08-17` | `types.lua` = `--- @class <caret>T` + `local T = {}`; `gen.lua` = `--- @generic T` + `--- @param v T` + `--- @return T` + `local function id(v) return v end` | rename `T` to `Elem` | `types.lua` = `--- @class Elem`; `gen.lua` is **byte-identical** — the `@generic` line is a declaration, and the two tags it shadows bind to that parameter, not to the project class | **mutation U** — delete clause 1 of `isDeclarationSlotHolder` (`holder is LuaCatsTypeParam && holder.parent is LuaCatsArgName`) → `--- @generic Elem`, a function-local parameter renamed unasked; DR-04 P-B reports the same `DISAGREE` at line 5. **Mutation V** — delete the `shadowedTypeParameterNames` clause from `useHolderOf`/`useLeafOf` → `--- @param v Elem` / `--- @return Elem` under an unchanged `--- @generic T`, which is a generic function silently broken; DR-04 P-E reports `commentShadows=[T] SHADOWED=true` for both tags |
| 28 | `REFACT-08-17`, `-04` | `types.lua` = `--- @class <caret>T` + `local T = {}`; `plain.lua` = `--- @param w T` + `local function h(w) return w end` — a comment declaring **no** type parameter | rename `T` to `Elem` | `plain.lua` = `--- @param w Elem` | *(control for row 27's shadowing clause, so it cannot be widened into a blanket exclusion of the name)*: under **mutation V** this row stays green, and under a name-based rather than comment-scoped exclusion it goes red. DR-04 P-E's line 10 is the executed half — `commentShadows=[] SHADOWED=false` for a `T` in an unrelated comment |

## Acceptance Criteria

- [x] Every `REFACT-08-00-DR-*` de-risking action listed below has run and its result is recorded in
      `risks-and-gaps.md`. All four (DR-01…DR-04) are marked done with results transcribed.
- [x] Every `M` requirement has an executed test with a named, reachable mutation — `REFACT-08-08`
      (Phase 5, mutation E) and `REFACT-08-15` (Phase 7, the full-suite count delta) are the two this
      phase closed; every other `M` row's mutation is recorded against its own requirement row above.
- [x] The rename is complete or refused: no fixture in the suite ends with a file holding both the
      old and the new spelling of a type name. Confirmed again live (2026-09-03): every refusal
      (builtin keyword, library-declared name) left both files byte-identical, and the one completed
      rename left zero stale spellings.
- [x] `LuaCatsTypeNameIndex`, `LuaCatsTypeNavigation`, `LuaClassNameIndex` and `LuaAliasIndex` are
      **reused**, not duplicated: this feature adds no second map from a type name to its
      declaration. Phases 5-7 added a validator and a `findCollisions` clause, no new index.
- [x] `LuaRenameCollisionUsageInfo` is the only conflict carrier; no new one is defined. Phase 6's
      `findCollisions` constructs the existing carrier; no new type was declared.
- [x] No test in the suite ends with a file outside `GlobalSearchScope.projectScope` modified, and
      every refusal leaves both the caret's file and every use file byte-identical.
- [x] The full unit suite is green, and the delta against `154f26f3` is stated with counts. Phase 7:
      **2 993 tests, 0 failures, 0 errors, 1 skipped across 475 files** — baseline (2 989 / 0 / 0 / 1 /
      473) plus exactly the four tests in the two files Phases 5-6 added. The `-PwithCorpus` gate
      separately reports **3 003 tests, 0 failures, 0 errors, 1 skipped across 479 files**, with
      `LuaCorpusSweepTest`, `LuaTortureCorpusTest`, `LuaInspectionParityTest` and
      `LuaAnnotatedFixtureSweepTest` present with fresh timestamps.
- [x] `REFACT-01-16` moves from `Partial` to `Full` only once this ships. Done in this phase; its
      delegation note now points at this feature's shipped state instead of describing work not yet
      done.

## Non-Functional Requirements

- **Threading.** Every step runs where the platform already puts it: `canProcessElement` and
  `LuaTargetElementEvaluator.getNamedElement` on the EDT as pure PSI shape tests with no index read;
  `substituteElementToRename` on the EDT, where its one index read is the same one quick-doc already
  performs at that caret; `findReferences` and `findCollisions` inside `BaseRefactoringProcessor`'s
  background read action; `renameElement` inside the platform's own write action. No `Project`,
  `Editor`, `PsiFile` or `VirtualFile` is retained in a field.
- **Cancellation.** `LuaCatsTypeReferenceSearcher` opens both of its iteration blocks with
  `ProgressManager.checkCanceled()` — the per-file loop and the per-use-holder loop — because both
  can load PSI. The write loop of `renameElement` carries none, deliberately: it runs inside
  `executeNonCancelableSection`, and a cancellation there is what half-applies a rename
  (REFACT-01 design §3.3).
- **The out-of-project check costs no extra index read.** `substituteElementToRename` already reads
  `LuaCatsTypeNameIndex` once per rename; `REFACT-08-16`'s check reuses that same
  `declarationLeaves(name, …, allScope)` result and compares each leaf's file against
  `GlobalSearchScope.projectScope(project)`, which is an in-memory scope test.
- **Cost.** The search is narrowed by the word index before any PSI is loaded:
  `CacheManager.getFilesWithWord(name, UsageSearchContext.IN_COMMENTS, scope, true)`. Measured over
  the DR-01 corpus, that narrows 195 files to 46 for the busiest name and to 6 for `scope`; the
  numbers are in `risks-and-gaps.md`. This is the same primitive
  [LuaNameReferenceSearcher.kt:92-96](../../../../src/main/kotlin/net/internetisalie/lunar/lang/insight/LuaNameReferenceSearcher.kt)
  uses, with `IN_CODE` swapped for `IN_COMMENTS`.

## De-risking

| ID | Question | Blocks | Status |
|----|----------|--------|--------|
| `REFACT-08-00-DR-01` | Over real annotated Lua, how many type-name uses must a rename rewrite, in how many spellings, and is each one reachable from PSI? | the whole design; the refusal set | **done — see `risks-and-gaps.md` "DR-01 result"** |
| `REFACT-08-00-DR-02` | Does a `psi.referenceContributor` on the LuaCATS use shapes actually produce references, does `ReferencesSearch` find them cross-file, and does the resulting rename leave the type resolving? | `REFACT-08-01`…`-14` | **done — see `risks-and-gaps.md` "DR-02 result". The contributor alone is inert; `REFACT-08-14` is what makes it work.** |
| `REFACT-08-00-DR-04` | `LuaCatsTypeParam` is reached from two grammar rules — which occurrences are uses, which are declarations, and does the design's guard reach both declaration positions? | `REFACT-08-17`, `REFACT-08-04`; Gap 2.5; the Out-of-Scope claim about `---@generic` | **done — see `risks-and-gaps.md` "DR-04 result". The shipped guard reached neither; `design.md` §3.4 is the corrected predicate, and the corpus share of the hazard is zero.** |
| `REFACT-08-00-DR-03` | Where may the rewrite **write**? Does a declaration slot outside the project reach the write set, does `LuaCatsGenericType` ever fire, and does the searcher earn its registration? | `REFACT-08-04`, `-16`; Gap 2.5; Risk 1.4 | **done — see `risks-and-gaps.md` "DR-03 result". `allScope` reaches the bundled stubs; `GenericType` fires and needs a declaration-head guard; without the searcher `ReferencesSearch` returns nothing.** |

## Dependencies

- **[[REFACT-01]]** supplies `LuaRenameCollisionUsageInfo`, the leaf-rename idiom and
  `LuaTargetElementEvaluator`; this feature extends them and replaces nothing.
- **[[NAV-03]]** supplies `LuaCatsTypeNameIndex` and `LuaCatsTypeNavigation`, which already answer
  "name → declaration site". This feature reuses that answer for resolution instead of building a
  second index.
- No dependency on [[MAINT-39]]: DR-01 measured reach against an out-of-repo checkout rather than
  waiting for a pinned annotated corpus, and `risks-and-gaps.md` Gap 2.4 states what that costs.
