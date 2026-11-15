---
id: "BUG-102"
title: "Findings in a file inside a require cycle depend on which file of the cycle was analysed first"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-102: Findings inside a `require` cycle depend on build order

The tnt* cycles behind MAINT-65 DR-06 and MAINT-63 DR-20 are not in the corpus code. The module
resolver creates them: it resolves a bare `require('checks')` (or `log`, `fiber`, `json`) to any
`init.lua` in the project. Fixing that one acceptance rule brings the measured order dependence to
0 under base and under MAINT-63's V3. The type engine is also order-dependent inside a *genuine*
cycle (§4.2). That is a design change, not a surgical one, and is proposed as a separate feature
(§6).

## 1. Reproduction

### 1.1 Corpus

| Measure | tntcrud | tntmetrics | tntqueue |
|---|---|---|---|
| MAINT-65 DR-06: 1 s after-edit digest vs cold digest, base | 20 | 2 | 8 |
| Cold sweep-order digest vs a self-rooted rebuild (`dropPsiCaches` + target tick, then read the file first), base | **20 / 78** | **2 / 42** | **8 / 14** |
| MAINT-63 oracle (kept snapshot vs forced rebuild), V3 | 21 / 78 | 1 / 42 | 0 / 14 |

The second row is a new probe that reproduces DR-06 file for file. It needs no edit, so it isolates
the build root as the only variable.

`crud/stats/registry_utils.lua`, the clean-frame file from MAINT-63 DR-21, differs as follows:

| Read | Type of `require('crud.common.dev_checks')` | Finding at `dev_checks('string')` (offset 99) |
|---|---|---|
| cold, sweep order | `any \| fun()` | `HYPOTHESIS: Too many arguments: expected at most 0, got 1` |
| self-rooted | `fun() \| { ... }` | `HYPOTHESIS: fun() \| { ... } is not assignable to fun(p)` |

`crud/common/dev_checks.lua` is `local checks = require('checks') … return dev_checks`. `checks` is
a Tarantool runtime module and has no file in the project. With the tntcrud fixture loaded,
`resolveModuleCandidates(project, "checks")` returns
`[/src/crud/common/sharding/init.lua, /src/crud/stats/init.lua]`. `log`, `fiber`, `metrics` and
`json` return the same list. In tntmetrics every bare name resolves to `/src/metrics/init.lua`; in
tntqueue to `/src/queue/init.lua`.

### 1.2 Light fixture: a cycle made by the resolver

`BasePlatformTestCase` (`IndexedBasePlatformTestCase`), `myFixture.addFileToProject`, then
`LuaTypesSnapshot.forFile(...)` on the files in the order given, and `getErrors()` on each.

`sub/init.lua`:

```lua
local u = require("user")
local S = { name = "s" }
---@type number
local un = u.name
return S
```

`user.lua`:

```lua
local checks = require("checks")
local U = { name = "s" }
---@type number
local cn = checks.name
return U
```

No file is named `checks.lua`, so `require("checks")` should resolve to nothing. Base resolves it to
`sub/init.lua`, which makes `user.lua ↔ sub/init.lua` a cycle.

| Order | `user.lua` findings | `sub/init.lua` findings |
|---|---|---|
| `user` first, base | 3 × ERROR `string is not assignable to number` at `cn` (it reads `sub/init.lua`'s `name`) | none: `u` is `any` |
| `sub/init` first, base | none: `checks` is `any` | 3 × ERROR `string is not assignable to number` at `un` |
| either order, with the §5 fix | none | 3 × ERROR `string is not assignable to number` at `un` |

### 1.3 Light fixture: a genuine cycle (not fixed here)

`a.lua` requires `b`, `b.lua` requires `a`. Each assigns the other's `name = "s"` field to a
`---@type number` local, as in §1.2. Base, and base with the §5 fix:

| Order | `a.lua` | `b.lua` |
|---|---|---|
| `a` first | 3 × `string is not assignable to number` | none: `require("a")` is `any` |
| `b` first | none: `require("b")` is `any` | 3 × `string is not assignable to number` |

A non-cycle file `e.lua` (`local a = require("a")`, same check) gets the 3 findings when read
alone, and none when it is read after `a.lua`. A non-cycle control (`c.lua` requires `d.lua`) gives
the same findings in both orders.

## 2. Expected
A file's findings are a function of the project's contents. They do not change with the order in
which files are opened, analysed or rebuilt, and do not change across an IDE restart.

`require("name")` for a dotless `name` resolves to a file whose path ends in `name.lua` or
`name/init.lua`, or to nothing. It never resolves to an unrelated directory's `init.lua`.

## 3. Actual
- A bare `require` of any module that is not in the project resolves to an arbitrary `init.lua`. In
  tntcrud that is `crud/common/sharding/init.lua` or `crud/stats/init.lua`, whichever
  `FilenameIndex` lists first. Navigation from such a `require` (`LuaRequireReference.resolve`)
  lands in the same wrong file.
- Every module that requires a runtime library joins one large cycle through that `init.lua`, and
  inside that cycle each file's findings depend on the build root (§4.2).

## 4. Root cause

### 4.1 The corpus case: dotless names accept every `init.lua` (fixed by this bug)

`LuaModuleFileResolver.kt:39-58`, `resolveModuleCandidates`, `FilenameIndex` fallback:

```kotlin
val byName =
    FilenameIndex.getVirtualFilesByName(moduleName.substringAfterLast('.') + ".lua", scope) +
        FilenameIndex.getVirtualFilesByName("init.lua", scope)
…
            ?: (
                path.endsWith(expectedPathPart) ||
                    path.endsWith(expectedInitPathPart) ||
                    !moduleName.contains('.')          // line 53
            )
```

The lenient `!moduleName.contains('.')` term is meant to let a bare name match a `<name>.lua`
anywhere. It is applied to the whole `byName` list, and that list includes every `init.lua` in
`allScope`. So for a dotless name, every project `init.lua` outside a library root is accepted.
Library roots are not affected, because `libraryRootVerdict` (`:49`, `:76`) answers first for them.
The rule dates from before MAINT-30, which moved it unchanged into this function (138eec62).

All three callers take the fabricated edge:
- `LuaTypeManagerImpl.doResolveModule` (`LuaTypeManagerImpl.kt:314`) types the module from the wrong
  file.
- `LuaMemberResolution.requiredModuleType` (`LuaMemberResolution.kt:171`) resolves members through
  it, including the under-construction guard at `:172` named in the original report.
- `LuaRequireReference.resolve` (`LuaRequireReference.kt:22`) navigates to it.

In tntcrud the edge closes a cycle through most of `crud/`. This is the only reason
`registry_utils.lua` takes part in a cycle. In the cold sweep, its `dev_checks` dependency was built
nested inside a build that already had `sharding/init.lua` in progress, so `checks` read as `any`.
In the self-rooted read, `checks` read as `sharding/init.lua`'s export, `{ ... }`.

### 4.2 The engine case: a nested build that read an in-progress file is cached as final (split out, §6)

Rooted at `a.lua` (§1.3):
1. `LuaTypes.forFile(a)` builds `a` (`LuaTypesVisitor.buildSnapshot`, `LuaTypesVisitor.kt:1685`,
   which registers `a` in `inProgressBuilds`).
2. `require("b")` → `LuaTypeManagerImpl.resolveModule("b")` (`:172`) → `getModuleType(b)` →
   `forFile(b)` builds `b` nested.
3. `b`'s `require("a")` → `resolveModule("a")` → `getModuleType(a)` → `forFile(a)`. `a` is under
   construction, so `LuaTypes.kt:312` returns `a`'s partial snapshot. `a`'s `return A` is not yet
   visited, so `getFileReturnType()` (`LuaTypeManagerImpl.kt:281`) is `Undefined`. `getModuleType`
   returns null, and `doResolveModule` answers `ANY` (`:316`).
4. Two stores then outlive `a`'s build:
   - `b`'s nested snapshot is stored as `b`'s `CachedValue` (`LuaTypes.kt:317`), so a later
     `forFile(b)` returns the `a`-rooted view.
   - `ANY` is stored for `"a"` in the project-wide `moduleCache` (`recordInto`, `:172`). That is
     why `e.lua`, outside the cycle, loses `a`'s type when it is read after `a`.

`resolveModule`'s re-entrancy guard (`:164-169`, `ANY` for a module already on the stack) truncates
the same way when the cycle is entered through `resolveModule`, as it is for `e.lua` read alone.
The frame records the hit (`inProgressHits`, an absence), but only `isPinnable` (`LuaTypes.kt:381`)
reads it. Under `MODIFICATION_COUNT`, nothing stops the truncated answer from being cached.

With §4.1 fixed, genuine cycles are still present in the corpus: 63 of tntcrud's 78 self-rooted
frames still record an in-progress hit. On the measured members, though, the findings no longer
depend on the root. §4.2's dependence is real, but none of the files measured here shows it.

## 5. Fix strategy (this bug: §4.1 only)

`LuaModuleFileResolver.kt:53`: the lenient bare-name match covers `<name>.lua` only. An `init.lua`
candidate is accepted only through `expectedInitPathPart` (`<name>/init.lua`):

```kotlin
path.endsWith(expectedPathPart) ||
    path.endsWith(expectedInitPathPart) ||
    (!moduleName.contains('.') && virtualFile.name != "init.lua")
```

No other production change. A bare `require("crud")` still resolves to `crud/init.lua` through
`expectedInitPathPart`. A bare `require("json")` still matches `metrics/plugins/json.lua`, which is
the existing lenient rule for `<name>.lua` and is not changed here.

Update the KDoc of `resolveModuleCandidates` (`:15-26`) to say that the fallback accepts `init.lua`
only as `<name>/init.lua`.

**Prototype results** (the line above applied to base `4035acf4`, and to MAINT-63's V3 prototype on
`d070cee9`):

| Check | Base | Base + fix | V3 | V3 + fix |
|---|---|---|---|---|
| §1.2 fake cycle: same findings in both orders | no | **yes** | — | — |
| §1.3 genuine cycle (expected to stay red) | no | no | — | — |
| §1.3 non-cycle control `c`/`d` | same | same | — | — |
| Root probe tntcrud / tntmetrics / tntqueue (differing files) | 20 / 2 / 8 | **0 / 0 / 0** | 20 / 2 / 8 | **0 / 0 / 0** |
| MAINT-63 oracle tntcrud / tntmetrics / tntqueue | 0 / 0 / 0 | — | 21 / 1 / 0 | **0 / 0 / 0** |
| Root probe and MAINT-63 oracle, gitsigns / telescope | — | — | 0 / 0 | 0 / 0 |
| Full unit suite (`test --rerun --no-build-cache`) | — | 3 791 tests, 0 failures | — | — |

**Cost** (root probe, sum of per-file self-rooted reads after `dropPsiCaches`, ms):

| Member | Base | Base + fix |
|---|---|---|
| tntcrud | 8 138 | 5 519 |
| tntmetrics | 4 791 | 3 531 |
| tntqueue | 917 | 362 |

Base's MAINT-63 oracle is 0 because base rebuilds every unpinned snapshot from the edited file, so
both of its reads share a root. MAINT-63 edit misses under V3 + fix (tntcrud, tntmetrics, tntqueue 0;
gitsigns 12, telescope 8) compare with 10 and 5 for V3 alone in the same session. Edit misses vary from run to run
(MAINT-65 DR-09), and the fix does not target them.

Cold sweep-order passes change little (tntcrud 1 688 → 1 545, tntmetrics 479 → 394, tntqueue 541 →
462 ms). Removing the fabricated edges means a self-rooted read builds fewer modules. The fix can
change findings on any corpus member that has an `init.lua` and a bare `require` of a module outside
the project. 28 type-corpus members have an `init.lua`, among them kong (70), luals (40), blinkcmp
(26), apisix (23) and awesome (22). Expect sweep baselines to move (§7).

## 6. Split: root-independent answers inside a genuine cycle (proposed feature)

§4.2 is not surgical, so it is not planned in this report. Making a nested build that read an
in-progress file (or hit a re-entrancy guard) *provisional* requires:
- a cut marker that knows which open build it cut, so the root's own snapshot stays cacheable while
  a nested one does not;
- the same rule at four stores (the snapshot `CachedValue`, and `moduleCache`, `globalCache` and
  `typeCache` in `LuaTypeManagerImpl`), and at the `ResolveCache`-backed guard sites
  (`LuaMemberResolution.kt:172`, `LuaColonCallResolution.kt:56`, `LuaNameReference.kt:62`, `:74`);
- a per-root memo so that a large cycle does not rebuild its members exponentially;
- a semantic decision about which view of a cycle member is the answer, for example self-rooted or
  a fixpoint.

Each of these is a design decision with a cost to measure on the sweep. Propose a MAINT feature,
"Root-independent inference inside `require` cycles", planned with `plan-feature`, with §1.3 as its
first acceptance fixture. MAINT-63 does not need it to resume if V3 + fix measures 0 (§5). The
residual risk is that MAINT-63 keeps a snapshot rooted inside a genuine cycle, where §1.3 shows the
answers do differ. MAINT-63's oracle remains the gate for that.

## 7. Test strategy
- **Resolver unit test.** Add it to `src/test/kotlin/net/internetisalie/lunar/lang/path/SearchPathRequireTest.kt`,
  or create a new `LuaModuleFileResolverInitLuaTest` beside it (light fixture):

  | Test | Fixture | Base | After fix |
  |---|---|---|---|
  | `testBareNameDoesNotResolveToForeignInitLua` | `sub/init.lua`; `resolveModuleCandidates(project, "checks")` | `[sub/init.lua]` | empty |
  | `testBareNameResolvesToOwnInitLua` | `checks/init.lua` | `[checks/init.lua]` | `[checks/init.lua]` |
  | `testBareNameStillMatchesNamedFileAnywhere` | `plugins/json.lua`; `"json"` | `[plugins/json.lua]` | unchanged |
  | `testRequireNavigationSkipsForeignInitLua` | `sub/init.lua`, `use.lua` with `require("checks")`; `LuaRequireReference.resolve()` | `sub/init.lua` | null |
- **Root-independence regression.** Create `src/test/kotlin/net/internetisalie/lunar/lang/types/RequireRootIndependenceTest.kt`
  using the §1.2 fixture. It reads `user` then `sub/init` in one test, and `sub/init` then `user` in
  another, then asserts both files' `is not assignable` findings equal the "with fix" row of §1.2.
  Before the fix the `user`-first test is red. The §1.3 non-cycle control is a third test, green
  before and after. §1.3's genuine cycle is **not** asserted here; it belongs to §6.
- **Corpus gate (one-off, not committed).** Rerun the cold vs self-rooted probe on tntcrud,
  tntmetrics and tntqueue and expect 0 differing files.
- **Baselines.** Run `test -PwithCorpus --rerun --no-build-cache`. Review the moved rows, which
  should be findings at a bare `require` of a runtime module that no longer read a project
  `init.lua`, then re-record per the corpus ratchet's procedure.
- Full unit suite green, then `ktlintCheck`.
