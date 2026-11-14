---
id: "BUG-98"
title: "A local holding a `require`d module whose table is a `@class` is reported as `Class | { ... } is not assignable to { ... }` on its member accesses"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-98: A local holding a `require`d module whose table is a `@class` is reported as `Class | { ... } is not assignable to { ... }` on its member accesses

Found by MAINT-65's DR-08. The findings are HYPOTHESIS severity, which `LuaTypeHypothesisAnnotator`
shows in the editor. Whether a file gets them depends on build order. A file read on its own gets
them. The same file built inside another file's snapshot build (a dependent's `require`) usually
does not.

## 1. Reproduction
Type corpus members gitsigns and blinkcmp. Open the file first, in a fresh project, so it is
built on its own:

| File | Local | Findings |
|---|---|---|
| blinkcmp `lua/blink/cmp/completion/list.lua` | `local cmp = require('blink.cmp')` | 3 × `blink.cmp.API \| { ... } is not assignable to { ... }` (the declaration, `cmp.is_menu_visible`, `cmp.is_ghost_text_visible`) |
| blinkcmp `lua/blink/cmp/fuzzy/init.lua` | `local cmp = require('blink.cmp')` | 3 × `blink.cmp.API \| { ... }` (the declaration, `cmp.get_repo_root`, `cmp.get_library_name`) |
| gitsigns `lua/gitsigns/debug.lua` and three siblings | `log`, `manager` | `Gitsigns.log \| { ... }` × 6 and `gitsigns.manager \| { ... }` × 4 in total |

Per-file digests from the opened-first build and the sweep-order build, plus the finding rows, are
in MAINT-65's `risks-and-gaps.md` (DR-08).

## 2. Expected
No finding. The module's `@class` table defines every member the file uses on that local, so each
access succeeds at run time.

## 3. Actual
The member-access sites, and the local's declaration, get the HYPOTHESIS finding when the file is built
on its own. Built inside a dependent's build they usually do not, and `fuzzy/init.lua` changes
between identical sweeps depending on which file builds it. The same message shape for other
classes (`Gitsigns.Util`, `blink.cmp.Fuzzy`) is already in the base sweep's findings. Related
message shape, different trigger: BUG-94.

## 4. Why it blocks MAINT-65
MAINT-65's change stops building a callee's snapshot inside the caller's build for calls with no lambda
argument. More files are then built on their own, which surfaces this report in five files, and in `fuzzy/init.lua` depending on build order (MAINT-65 DR-05).

## 5. Minimal reproduction
Two files in a `BasePlatformTestCase`, both added with `myFixture.addFileToProject`, then
`LuaTypesSnapshot.forFile(use.lua).getErrors()`. No extra shape is needed: a `@class` local
returned by its module is enough.

`mod.lua`:
```lua
--- @class Mod
local M = {
  messages = {},
}

function M.show() end

return M
```

`use.lua`:
```lua
local log = require('mod')
log.show()
```

Result on base (2265debe), executed on the builder:
```
HYPOTHESIS|Mod | { ... } is not assignable to { ... }|log
HYPOTHESIS|Mod | { ... } is not assignable to { ... }|.show
```
`log`'s value type is
`Union(Table(className=Mod, {show}, isExact=true), Table(className=null, {messages}, isExact=true))`.

Variants, all executed:

| Variant | Findings |
|---|---|
| As above, `use.lua` read first | 2 |
| `mod.lua` read first, then `use.lua` | 2. Order between these two files does not matter |
| `local M = {}` (empty constructor) | 2 |
| `--- @field messages table` added (the gitsigns shape) | 2 |
| `local log = require('mod')` with no member use | 0 |
| `local n = log.messages` (a member read that is not called) | 0 |
| No `@class` on `M` | 0. `log` is `{ ... }` |
| `M.show()` inside `mod.lua` itself | 0 |
| `mod.lua` also contains `function M.peer() return require('use') end` and is read first | 0. `log` is `any` |

The six corpus files, each read alone in a fresh fixture on base, reproduce DR-08's alone rows:
`fuzzy/init.lua` 3, `debug.lua` 2, `hunks.lua` 6, `deleted_preview.lua` 4 (plus one
`{ ... } | { ... }`, which is not this bug), `completion/list.lua` 13, `system.lua` 2. That is 30
`<Class> | { ... }` findings.

## 6. Root cause
**A `@class` local has two value sources, and the module export turns them into one closed union
whose arms each hold half of the class.**

1. **Two sources in the module file.** `LuaTypeGraphBridge.injectTypeAnnotation`
   (`LuaTypeGraphBridge.kt:109`) adds the declared class as a value on the local
   (`Table(className=Mod)`, which carries the methods). The constructor `{ messages = {} }` is a
   second value on the same local (`Table(className=null)`, which carries the constructor fields).
   The file's return type is their union. `mod.lua` read first gives
   `Union(Table(null, {messages}, isExact=false), Table(Mod, {show}, isExact=true))`.
2. **The export keeps the union.** `LuaTypeManagerImpl.getModuleType`
   (`LuaTypeManagerImpl.kt:280-283`) converts the return type with `graphTypeToLuaType`. A
   `Union` becomes a `LuaUnionType` arm by arm (`LuaTypes.kt:157-159`), so the export is
   `LuaUnionType({ messages } literal, Mod)`.
3. **The consumer makes the anonymous arm exact.** `LuaTypesVisitor.visitFuncCall` gives the
   `require` call that type as one value (`LuaTypesVisitor.kt:923`).
   `LuaGraphType.fromLuaType` rebuilds the literal arm as `Table(null, members, isExact = true)`
   (`LuaGraphType.kt:324`).
4. **The union rule needs every arm to hold the member.** `log.show()` puts a member demand
   `Table({show})` on `log`. `LuaTypeGraph.checkCompatibility` takes the union-value branch and
   reports unless every informative arm passes `isCompatible` (`LuaTypeGraph.kt:521`). For two
   tables, `isCompatible` uses `isStructurallyCompatible` (`LuaTypeGraph.kt:786`). That check fails
   whenever a demanded member is absent and the use is not optional (`LuaTypeGraph.kt:795`). The
   constructor arm has no `show`, so the check fails. The finding is anchored on the local's
   declaration and on the member access.

**The logical flaw.** The two arms are not alternatives. They describe the same runtime table: the
class annotation and the constructor that builds it. The union rule is correct for real
alternatives, and it is wrong here because the export split one table into two arms. Inside
`mod.lua` the same two sources reach a member demand as separate values, which is why `M.show()`
in the module itself gets no finding (see the table in §5). When one table is checked on its own,
a missing member against an inferred demand is not an error (`checkTableCompatibility`,
`LuaTypeGraph.kt:958`). Only the materialized union reaches the strict rule.

**Why build order changes it.** It is a require cycle, not read order between the two files.
Reading `mod.lua` first still reproduces it. The finding goes away only when the consumer is built
*nested inside the module's own build*. In that case `LuaTypesSnapshot.forFile` returns the module's
in-progress snapshot (`LuaTypes.kt:312`). Its `fileReturnType` still has its initial value `Any`
(`LuaTypesVisitor.kt:28`), so `getModuleType` returns null and `doResolveModule` falls back to
`ANY`. `log` becomes `any`, and `checkCompatibility` returns early on `Any`. The last row of the
table in §5 shows this, with a cycle that runs back through `use.lua`. In the corpus, a file built
under `gitsigns.lua` or `blink-cmp.lua` at depth 4 to 11 is reached through such a chain, and which
chain reaches it first decides whether the module is still in progress. This is what DR-08's sweep
rows show. Which corpus cycle produces it in each file was not traced.

**Adjacent, not the cause.** `LuaFileElementType.extractExportedType` would export `"Mod"` for
this shape. Its `getCatsComment` only walks the declaration's previous siblings, while the local's
own stub finds the comment through `LuaPsiImplUtil.getCatsComment`. So `exportedTypeString` is null
in every run above, and `getModuleType` never takes its stub fast path for these modules. Fixing
that extractor is not the fix. A stub-backed module would then export the class without its
constructor-only fields, and an AST-backed one would not. That is the stub-or-AST dependence
BUG-398 removed.

## 7. Fix strategy
One change, in `LuaTypeManagerImpl.getModuleType`. Before conversion, fold a union of one class
table and anonymous tables into the class table, and keep the constructor arms' members on it:

```kotlin
        if (graphType == LuaGraphType.Any || graphType == LuaGraphType.Undefined) return null
        return snapshot.graphTypeToLuaType(classExportOf(graphType))
    }

    private fun classExportOf(type: LuaGraphType): LuaGraphType {
        val union = type as? LuaGraphType.Union ?: return type
        val classArm = union.types.filterIsInstance<LuaGraphType.Table>().singleOrNull { it.className != null } ?: return type
        val others = union.types - classArm
        if (others.any { it !is LuaGraphType.Table || it.className != null }) return type
        val constructorMembers = others.filterIsInstance<LuaGraphType.Table>().flatMap { it.localMembers.entries }
        return classArm.copy(localMembers = constructorMembers.associate { it.key to it.value } + classArm.localMembers)
    }
```

- The class arm's own members win on a name clash. `tableToLuaType` then enriches the result with
  the nominal class members (`@field` tags and methods) as it already does.
  `resolveModule("mod")` becomes `LuaClassType Mod` with members `[show, messages]`. Before the
  fix it was `LuaUnionType` with `[messages, show]`.
- The fold applies only when exactly one arm is nominal and every other arm is an anonymous table.
  A union of two anonymous tables, a `nil` arm or a second class arm leaves the type unchanged.
- The stub fast path, the in-file inference and the union rule in `checkCompatibility` do not
  change.
- Contract: a private helper of at most 30 lines with one argument. It runs where
  `getModuleType` runs, in the caller's read action. It holds no PSI references.
- KDoc for the helper must say that it folds the class annotation and the constructor of one
  table, and must cite BUG-98.

**Accepted narrowing.** `return cond and M or {}`, with `M` a `@class` local, would also be folded.
That shape did not appear in the probes, and folding it hides only a HYPOTHESIS-tier finding.

**Expected movements, to record in the implementing commit:**
- `MemberEnumerationGoldenTest` (`src/test/resources/comp09/member-enumeration.golden`) moves on
  the `OM` receiver only (`OM = require("luassert")` plus `function OM.extra()`). It gains
  `OM|global|extra:fun()`, `OM|completion|extra` and `OM|completion:|extra`, which replaces
  `<none>`. That is exactly the row shape the `M` receiver already has
  (`R = { a = 1 } + function R.f()`). Before the fix, `extra` was lost because the extension's member
  demand hit the same split union. Classify this as a declared expectation and re-record the
  golden. The golden test is green on base and red with the fix. No other test in the suite moves.
- Corpus sweep baselines. The fix also removes the base sweep's existing `<Class> | { ... }`
  findings, such as `Gitsigns.Util`, `blink.cmp.Fuzzy`, `blink.cmp.Context` and
  `blink.cmp.EventEmitter<T>`. Run `test -PwithCorpus --rerun --no-build-cache` and re-record the
  baselines that move. Every movement should be a removal of this message shape.

Out of scope: `LuaMemberDemandHubTest`'s pinned in-file `{ ... } | Builder is not assignable to
{ ... }` rows (`ANNOTATED3_ERRORS`). They come from method chains inside one file, not from a
module export, and this fix leaves them unchanged.

## 8. Test strategy
Add `src/test/kotlin/net/internetisalie/lunar/lang/types/RequiredClassModuleAssignabilityTest.kt`
(`BasePlatformTestCase`). Each case adds `mod.lua` and `use.lua` with `addFileToProject`. It reads
`LuaTypesSnapshot.forFile(use).getErrors()` inside `EdtTestUtil.runInEdtAndWait { runReadAction { … } }`
and keeps only messages that contain `is not assignable`.

| Case | `mod.lua` | `use.lua` | Assertion | Base | Fix |
|---|---|---|---|---|---|
| regression 1 | the §5 module | §5 consumer | no findings | **fails**: 2 findings | passes |
| regression 2 | §5 module plus `--- @field messages table` | §5 consumer | no findings | **fails**: 2 findings | passes |
| control 1 (genuine union) | `local M = os.time() > 0 and { show = function() end } or { messages = {} }` then `return M` | §5 consumer | findings not empty (`{ ... } \| { ... } is not assignable to { ... }` on `log` and `.show`) | passes | passes |
| control 2 (no class) | §5 module without the `@class` line | §5 consumer | no findings | passes | passes |

Also assert, under the fix, that `LuaTypeManager.getInstance(project).resolveModule("mod", use)` is
a `LuaClassType` named `Mod` whose `getMembers()` keys contain both `show` and `messages`. This
pins that the fold keeps the constructor fields and does not just drop the anonymous arm.

Executed on the builder (`builder-run libvirt`, `--rerun --no-build-cache`), with these cases as a
probe class:
- Base: 4 tests, 2 failed (the two regressions), both controls passed.
- With the §7 change: 4 tests, 4 passed. `resolveModule` returned `LuaClassType Mod members=[show, messages]`.
- Six corpus files read alone, with the §7 change: 0 `<Class> | { ... }` findings, down from 30
  on base. The one genuine `{ ... } | { ... }` finding on `deleted_preview.lua` `.lines` stays.
- Full unit suite with the §7 change: 3 780 tests, 1 failed, which was
  `MemberEnumerationGoldenTest` with the `OM` movement in §7. On base that test passes.

Gate for the implementation: the full suite, after the golden is re-recorded. Then the corpus sweep
(`-PwithCorpus`), because this changes cross-file types. MAINT-65-02's alone-read digests for the six
DR-05 files should then equal the gate's digests with these findings removed.
