---
id: "BUG-9"
title: "A bare @class tag binds to a local declared after a blank line"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-9: A bare `---@class` block binds to the next local even across a blank line

Found while triaging [[BUG-566]]'s corpus run. It blocks [[BUG-566]].

## 1. Reproduction

lazynvim, `lua/lazy/manage/process.lua:7-16`:

```lua
---@class ProcessOpts
---@field args string[]
---@field cwd? string
-- … more optional fields

local M = {}               -- the module table, not a ProcessOpts
M.running = setmetatable({}, { __mode = "k" })
```

Callers then pass a plain options table where `ProcessOpts` is declared, e.g. `manage/task/plugin.lua:36 task:spawn(shell, { args = …, cwd = … })`.

## 2. Expected vs Actual
- **Expected**: the blank line ends the annotation block, so `ProcessOpts` is a type-level class with only its `@field`s, and `local M` is not annotated.
- **Actual**: `ProcessOpts` takes `M`'s members (`running`, `signals`, `spawn`, `abort`, `exec`). A table with `args` and `cwd` is reported as missing them: 8 *Missing required field* hits on `main`, and 11 *missing fields 'running', 'signals', …* hits with [[BUG-566]]'s fix (`pkg/rockspec.lua:40, :169, :197`; `manage/process.lua:219, :232-234`).

## 3. Context
- The binding comes from comment attachment, not from type inference: `LuaPsiImplUtil.getCatsComment(local M)` returns the `@class ProcessOpts` comment, so `M` becomes `ProcessOpts`'s host declaration (§4).
- The LuaCATS convention follows LuaLS, which binds a doc block only to the line directly after it. A blank line ends the block and leaves it unbound. In `lua-language-server/script/parser/luadoc.lua`, `isNextLine` (`:1782-1789`) groups doc lines only when `newRow - lastRow == 1`, and `bindDocs` (`:2099-2103`) looks for a binding source only on the row of the last doc line or the row after it.

## 4. Root Cause

### 4.1 Where a comment attaches to a declaration
`LuaPsiImplUtil.getCatsComment` (`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaPsiImplUtil.kt:14-49`) walks back from the owner. If the owner has no previous sibling, it climbs to the owner's parent and tries again. While walking back, the inner loop at `:20-22` skips every `PsiWhiteSpace`, whatever it contains. A whitespace leaf holding `"\n\n"` (a blank line) is skipped exactly like a single `"\n"`, so nothing in the walk can see a blank line.

In the reproduction, the comment is not a sibling of the declaration. A Phase 0 dump (`DebugUtil.psiToString`, §7) shows:

```
Lua(0,231)
  LuaCatsLazyCommentImpl(LAZY_COMMENT)(0,67)     -- ---@class ProcessOpts … ---@field cwd? string
  BLOCK(69,143)                                  -- (67,69) is the "\n\n" whitespace
    LOCAL_VAR_DECL(69,81)                        -- local M = {}
```

`LOCAL_VAR_DECL` is the first child of `BLOCK`, so the walk climbs to `BLOCK`, skips the `"\n\n"` and returns the `@class` comment.

### 4.2 How that makes `M`'s members part of `ProcessOpts`
Every reader of the local's comment goes through this one function:
- **Stub:** `LuaLocalVarStubElementType.createStub` (`lang/psi/stubs/impl/LuaLocalVarStubElementType.kt:23-28`) stores `luacatsClassName = "ProcessOpts"`, and `indexStub` sinks that into `LuaClassNameIndex`.
- **Type manager:** `LuaTypeManagerImpl` (`lang/psi/types/LuaTypeManagerImpl.kt:325`) then materializes `ProcessOpts` as a hosted class with `decls = [local M]` (`materializeClass`, `:344`). `materializeClass` collects members from three places:
  - the `@field`s;
  - `LuaImplicitFields.collect` over the receiver names, which picks up `M.running = …` and `M.signals = …`;
  - `collectMethodMembers`, which picks up `function M.spawn`.
- **Visitor:** `LuaTypesVisitor.visitLocalVarDecl` (`:819-823`) injects `ProcessOpts` as `M`'s declared type.

A table literal passed where `ProcessOpts` is expected is then checked against `M`'s members. `LuaTypeGraph.kt:925` reports `Missing required field 'running'` and `'signals'`.

Without the blank line the attachment is intended. `---@class C` + `local C = {}` is the hosted-class form that `materializeClass` and `classReceiverNames` exist to serve. LuaLS binds it the same way.

### 4.3 Relation to BUG-557 and BUG-558
- **BUG-557 has the same root-cause rule but a different walker.** Its assignment-statement path, `LuaTypesVisitor.getAllCatsComments` (`lang/psi/types/LuaTypesVisitor.kt:296-321`), skips `PsiWhiteSpace` without regard to blank lines (`:313`). It also accumulates every cats comment it passes. The corpus case (`pkg/init.lua:8-30`) and the report's fixture both separate their `@class` blocks with blank lines. A blank-line stop in that walker would leave only the adjacent `---@type`. BUG-9's change to `getCatsComment` does **not** reach that walker. `getAllCatsComments` is used by `statementCats` (`:362-372`), `ambientCatsComments` (`:1478-1486`) and `visitFunctionBody` (`:1496-1498`).
- **BUG-558 does not share the root cause.** Its comment is adjacent, so the binding is correct. Its defect is that the class's own declaring table is checked as an instance. The probe's adjacent case shows a related but separate question: `use({ args, cwd })` against an adjacent hosted class also reports `running`/`signals`, because implicit `M.x = …` members are treated as required instance fields. That is not BUG-9 (see §5.4).

## 5. Fix Strategy

### 5.1 The change
In `LuaPsiImplUtil` (`lang/psi/LuaPsiImplUtil.kt`), add one shared predicate:

```kotlin
/** A whitespace run holding a blank line ends a LuaCATS comment's reach (LuaLS binds row+1 only). */
fun isBlankLineGap(element: PsiElement): Boolean =
    element is PsiWhiteSpace && StringUtil.countNewLines(element.text) >= 2
```

In `getCatsComment`'s skip loop (`:20-22`), stop when the element just skipped or about to be skipped is a blank-line gap, and return `null`. The check applies at every level of the parent climb, because in the reproduction the gap sits between `BLOCK` and the comment, not next to the declaration.

A separate plain comment between the cats comment and the owner, such as `--[[ note ]]`, stays transparent, as now, provided no blank line separates any of them. A `-- note` line comment directly under the block is not a separate comment: the lexer merges it into the block (§8).

Nothing else changes: no grammar, lexer or regeneration work, and no type-engine edits. `ProcessOpts` then has no host. It falls through to `materializeUnhostedClass` (`LuaTypeManagerImpl.kt:333`, `:392`) via `LuaCatsTypeNameIndex`, with only its `@field`s. The probe's control case (§7) shows that path yields no hits.

### 5.2 Index versions
Same text now produces different stub and index content, so bump these versions:
- `LuaFileElementType.getStubVersion()` (`lang/psi/LuaFileElementType.kt:22`, `6` → `7`). This covers the `luacatsClassName`/`luacatsType`/`luacatsFields`/`luacatsParents` fields of `LuaLocalVarStub`, `LuaFuncStub` and `LuaLocalFuncStub`.
- `LuaDescriptionIndex.getVersion()` (`lang/indexing/LuaDescriptionIndex.kt:48`, `5` → `6`). It reads `owner.catsComment` at `:92`.

`LuaCatsTypeNameIndex` reads the tags directly and is unaffected.

### 5.3 Consumers that will see the change
All of these read `getCatsComment`, either directly or through the generated `catsComment` getter (`lua.bnf:191-244`, on every `LuaCommentOwner`). A comment separated by a blank line now reads as absent for all of them, which is the intended reading:

| Area | Sites | What it sees |
|---|---|---|
| Stubs | `LuaLocalVarStubElementType.kt:23`, `LuaFuncStubElementType.kt:25`, `LuaLocalFuncStubElementType.kt:24` | No class/type/alias/field/param/return hoisted from a detached block. |
| Type engine | `LuaTypeManagerImpl.kt:477, :613, :648`; `LuaTypesVisitor.kt:363, :819, :1498`; `TypeParser.kt:22` | No declared type or `@param`/`@return` from a detached block. Bare `@class` goes unhosted. |
| Docs | `LuaDocumentationRenderer.kt:79`, `LuaCatsDocumentationRenderer.kt:52, :657`, `LuaDocumentationTargetProvider.kt:260`, `LuaDocSearchEverywhereContributor.kt:141`, `LuaDescriptionIndex.kt:92` | Hover and description no longer show a detached block. |
| Editing | `LuaGenerateDocIntention.kt:26`, `LuaEnterHandlerDelegate.kt:83` | A declaration after a blank line counts as undocumented, so *Generate doc* is offered. |
| Hints | `LuaParameterInfoHandler.kt:135, :202`, `LuaTypeInlayHintProvider.kt:177, :197`, `LuaMethodChainInlayHintProvider.kt:219` | Detached `@param`/`@return` are ignored. |
| Other | `LuaDeprecatedApiInspection.kt:122, :127`, `LuaHierarchyUtil.kt:25`, `LuaImportNameResolver.kt:52`, `LuaCatsParamRenamer.kt:80` | Detached `@deprecated`/`@class`/`@param` are ignored. |

### 5.4 Out of scope
- **The BUG-557 walker** (`LuaTypesVisitor.getAllCatsComments`) and **`LuaFileElementType.getCatsComment`** (`:115-127`, the module-return walker) have the same unconditional whitespace skip. They should reuse `isBlankLineGap`, but under BUG-557 with its own probe and corpus delta. Fixing them here would mix two corpus movements.
- **`LuaPsiImplUtil.getComment`** (plain comments, via `prevSiblingSkipWhitespaceOnly`, `:84-90`) has the same shape. Untouched.
- **Implicit members as required fields** (§4.3, the adjacent-case hits). Untouched.

### 5.5 Expected corpus effect
`lazynvim.baseline` currently carries `symbol.LuaTypeAssignability.running=5` and `symbol.LuaTypeAssignability.signals=5`. The `process.lua` attachment is the source of the *Missing required field 'running'/'signals'* hits, so they should fall.

The detached-block rule is general, so other symbols in any corpus project can move too. The implementer measures before and after with:

```
tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --stacktrace -PwithCorpus --tests '*Corpus*'"
```

Every moved key must be attributed to this rule (a blank-line-separated block) before re-recording with `-PrecordCorpusBaseline`. A key that moves for any other reason is a finding to report, not to absorb.

## 6. Test Strategy
Change `LuaCatsCommentResolutionTest` (`src/test/kotlin/net/internetisalie/lunar/lang/psi/`) and add `LuaCatsBlankLineBindingTest` (`IndexedBasePlatformTestCase`, `myFixture.enableInspections(LuaTypeAssignabilityInspection())`). The fixtures use the reproduction's shape plus one required scalar field: `---@class ProcessOpts` / `---@field args string[]` / `---@field name string` / `---@field cwd? string`, then `local M = {}`, `M.running = {}`, `M.signals = {}`, `function M.spawn() end`, and a caller `---@param opts ProcessOpts` `local function use(opts) end`.

| # | Case | Assertion |
|---|---|---|
| T1 | Blank line between block and `local M` | `getCatsComment(M)` is `null` |
| T2 | Same, adjacent (control) | `getCatsComment(M)` is the `@class ProcessOpts` comment |
| T3 | `---@class A` / `--[[ note ]]` / `local A = {}`, no blank line | still binds (plain comment stays transparent) |
| T4 | `---@class A` / `--[[ note ]]` / blank / `local A = {}` | `null` |
| T5 | T1 + `use({ args = {}, name = "n", cwd = "x" })` | no `Missing required field 'running'` / `'signals'` |
| T6 | T1 + `use({ cwd = "x" })` | `Missing required field 'name'` and no `'running'`, which proves `ProcessOpts` still resolves, unhosted, with its fields. Fails before the fix, because `'running'` is reported while `M` hosts `ProcessOpts`. `args` cannot be the control (§8) |
| T7 | T1's declaration in `proc.lua` via `addFileToProject`, caller in the open file | first asserts `M`'s stub `luacatsClassName` is `null` (stub arm actually taken), then T5's absence and T6's presence |

`testNearestPrecedingCommentIsChosen` (`LuaCatsCommentResolutionTest.kt:62-82`, MAINT-09-02) currently asserts the opposite of T4: a blank line plus a plain comment between `---@class Animal` and `local Animal`. It pins old behaviour and no requirement backs it (MAINT-09-02 says "nearest preceding comment block"). Drop the blank line from its fixture and write the intervening comment as `--[[ some regular comment ]]`, so it becomes T3. Update the case listed in `docs/features/maint/09-psi-stubs/design.md:104` to match.

Mutation proof: with `isBlankLineGap`'s check removed, T1, T4, T5, T6 and T7 must go red, and T2 and T3 must stay green.

The full unit suite and the corpus run in §5.5 gate the fix.

## 7. Phase 0 probe (executed)
2026-10-29, worktree `bug-9` at `4d82fe19`, libvirt builder, `test --rerun --no-build-cache --tests '*Bug9ProbeTest*'`: BUILD SUCCESSFUL. The probe was a throwaway `IndexedBasePlatformTestCase` with `LuaTypeAssignabilityInspection` and has been removed. The caller was `use({ args = {}, cwd = "x" })`.

| Case | Hits | `getCatsComment(local M)` |
|---|---|---|
| Blank line | `Missing required field 'running'`, `'signals'` | the `@class ProcessOpts` comment |
| Adjacent | same two | same |
| Blank line, declaration in another file (stub arm) | same two | — |
| Control: `local x = 1` between block and `local M` | none | — |

The PSI dump is quoted in §4.1.

## 8. Not established
- The §5.5 corpus run is now measured. Only lazynvim moves: `LuaTypeAssignability` 306 → 256 and `LuaReturnTypeMismatch` 58 → 33. A per-site comparison with and without the blank-line check removes 75 hits and adds none, and every removed hit comes from one of two detached blocks:
  - `manage/process.lua:7-14` `@class ProcessOpts` (blank line, then `local M` at `:16`): the `running`/`signals` hits (5 + 5) at `process.lua:16`, `task/plugin.lua:36` and `pkg/rockspec.lua:40, :169, :197`, plus the `ProcessOpts | { ... }` mismatches at `process.lua:36, :219, :220, :232` and `task/init.lua:193`.
  - `pkg/rockspec.lua:16-17` `@class RockManifest` (blank line, then `local M` at `:19`): the `hererocks`/`python`/`rewrites`/`skip`/`repository` hits at `rockspec.lua:19, :225` and `build.lua:54`, and the `RockManifest`, `RockSpec` and `HereRocks` mismatches elsewhere in `rockspec.lua`.

  The other detached blocks in lazynvim (`view/config.lua:3` `@class LazyViewCommand`, `view/text.lua:98` `@cast`, `minit.lua:1` `@diagnostic`) move nothing. luacheck, luarocks, penlight, zerobrane and the bundled `.lua` resources contain no detached block. zerobrane's `LuaSuspiciousConcatenation` measured 1 on one run and 3 on two others, with the same code. zerobrane has no LuaCATS annotations on declarations, so that count does not depend on this fix.
- Whether IntelliJ's normalized `\n` covers every whitespace leaf the walk meets. Documents are line-separator-normalized, so a `\r\n` file still counts correctly, but this has not been probed.
- Whether implicit `M.x = …` members of an adjacent hosted class should be required on instance literals (§4.3). This is a separate question for the type engine, nearest to BUG-558.
- Whether a missing `string[]` field should be reported. The type engine never reports one as missing, hosted or unhosted, while a scalar field is reported. Probe on the libvirt builder, caller `use({ cwd = "x" })` unless noted:

  | Fixture | Hits |
  |---|---|
  | `---@class P` / `---@field args string[]` / `---@field cwd? string` | none |
  | same, `---@field args string` | `Missing required field 'args'` |
  | `string[]` fields + adjacent `local P = {}` | none |
  | `args string`; `---@type P` `local p = { cwd = "x" }` | `Missing required field 'args'` |

  This is out of scope for BUG-9. T6 and T7 use a required `name string` field as their positive control for this reason.
- Whether the line-comment merge is a defect. The lexer folds `---@class A` followed by `-- note` on the next line into one plain `PsiComment`, so the class is lost and nothing binds. This fix does not change that, and it is out of scope here.
