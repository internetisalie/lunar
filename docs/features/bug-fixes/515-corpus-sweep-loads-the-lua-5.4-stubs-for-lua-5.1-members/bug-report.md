---
id: "BUG-515"
title: "The corpus sweep and the inspection-parity test load the lua-5.4 stdlib stubs for members pinned to Lua 5.1"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-515: the corpus measures Lua 5.1 members against the Lua 5.4 standard library

Found while delivering [[BUG-513]]. Its fix to the `lua-5.1` stubs did not move any corpus baseline,
and this is the reason.

The prototype is commit `aea7b101` on branch `bug-515` (based on `224a2f3f`), labelled "prototype,
not for merge". Every measurement below was executed on the libvirt builder. Run logs and test
XML are in `~/.cache/claude-scratch/lunar/bug-515/`, which is scratch and will not survive.

## 1. Reproduction (executed)

`LuaCorpusSweepTest.sweepAndRatchet` (`LuaCorpusSweepTest.kt:119` at `224a2f3f`) and
`LuaInspectionParityTest.testUndeclaredCountsAreIndependentOfTheTypeInspection`
(`LuaInspectionParityTest.kt:69`) pin the language level from the manifest, and do nothing else:

```kotlin
LuaProjectSettings.getInstance(myFixture.project).state.languageLevel = entry.luaLevel
```

All four members of `tooling/corpus/corpus.json` are `"luaLevel": "LUA51"`. A temporary probe was
added around that line in both tests. It printed `explicitTarget`, `languageLevel`, the stored
`state.target` (read before anything calls `getTarget()`), and
`PlatformLibraryIndex.getPlatformLibraryFolder(project)`. Run:
`test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*' --tests '*InspectionParityTest'` on
`224a2f3f`:

```
sweep:luacheck  before explicit=false level=Lua 5.4 rawTarget=Standard/5.4 root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
sweep:luacheck  after  explicit=false level=Lua 5.1 rawTarget=Standard/5.4 root=…/lunar-0.18.0.jar!/runtime/standard/lua-5.4
sweep:luarocks  before explicit=false level=Lua 5.1 rawTarget=Standard/5.4 root=…/runtime/standard/lua-5.4
sweep:luarocks  after  explicit=false level=Lua 5.1 rawTarget=Standard/5.4 root=…/runtime/standard/lua-5.4
(penlight and zerobrane: identical to luarocks)
parity          before explicit=false level=Lua 5.1 rawTarget=Standard/5.4 root=…/runtime/standard/lua-5.4
parity          after  explicit=false level=Lua 5.1 rawTarget=Standard/5.4 root=…/runtime/standard/lua-5.4
```

The parity test is affected in the same way as the sweep. The run also shows a second leak. Main
never restores the level, so every member after the first, and the parity test, starts at
`Lua 5.1`, the previous class's value.

## 2. Expected vs Actual
- **Expected**: a member pinned to `LUA51` is swept with the Standard 5.1 target, so its stdlib
  names resolve against `runtime/standard/lua-5.1/`. MAINT-33's design says the pin does this
  (`docs/features/maint/33-corpus-sweep/design.md:333-334`): "the level selects which stdlib
  globals are known, so `LuaUndeclaredVariable` moves with it too". A member's settings must not
  reach the next test class.
- **Actual**: the pin moves the parser, `LuaLanguageLevelInspection` and the
  `LuaStandardGlobals` allowlist, which all read `languageLevel`. The stdlib root does not move.
  The level then leaks into later classes.

## 3. Root Cause

- **Files**: `src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt:125-131`
  (`State.getTarget`). The test-side flaw is at `LuaCorpusSweepTest.kt:119` and
  `LuaInspectionParityTest.kt:69`.
- **Flaw**: the stdlib root follows the target. Writing the level leaves the target unchanged.
  1. `PlatformLibraryIndex.getPlatformLibrary` (`PlatformLibraryProvider.kt:124`) calls
     `settings.getTarget()`. It then calls `RuntimeLibraryProvider(project).getLibraryRoot(target)`.
  2. `getTarget()` derives a target from `languageLevel` (`buildDefaultTarget`) only when
     `target == null`. Otherwise it returns the stored target.
  3. The probe shows `rawTarget=Standard/5.4` is already stored before the first member. The
     first writer can be `State.getTarget()` itself: on a read with no stored target it builds the
     default from `languageLevel` and saves it (`LuaProjectSettings.kt:126-128`), so any earlier
     read in the shared light project stores one. Which read came first is not established, and
     does not matter: any stored target defeats a level-only pin.
  4. The tests write `state.languageLevel` directly. That is a plain field, so it does not touch
     `target`, does not bump `targetModificationTracker`, and publishes no
     `LuaSettingsChangedListener.TOPIC`. As a result, `LuaSettingsChangeListener` never calls
     `PlatformLibraryIndex.reload()`.
- **Why `rawlen` and `loadstring` behaved as they did on main**: `LuaUndeclaredNames.isExemptGlobal`
  (`LuaUndeclaredNames.kt:31`) reads `languageLevel`, which was `LUA51`. Through
  `LuaStandardGlobals.DELTA_51`, the 5.1-only names (`loadstring`, `setfenv`, `newproxy`, `unpack`
  and so on) were exempt and were never reported. The `lua-5.4` stub index still declared
  `rawlen`, so it resolved. The result was a hybrid: the 5.1 allowlist with 5.4 declarations,
  signatures and deprecations.

## 4. Fix Strategy

The fix is test code only. The prototype `aea7b101` is the fix: take its four Kotlin files
verbatim and its four re-recorded baselines (see step 4).

1. **New file `src/test/kotlin/net/internetisalie/lunar/corpus/CorpusTarget.kt`.** It contains a
   top-level `data class SavedTarget(val target: Target?, val languageLevel: LuaLanguageLevel,
   val explicitTarget: Boolean)` and an `object CorpusTarget` with these members:
   - `fun pin(project: Project, level: LuaLanguageLevel): SavedTarget`:
     1. Capture `SavedTarget(state.target?.toTarget(), state.languageLevel, state.explicitTarget)`.
     2. Set `state.explicitTarget = true`. This makes `LuaTargetSynchronizer.recompute`
        (`LuaTargetSynchronizer.kt:79`) return early, so it queues no new move to the builder's
        `~/bin/lua` runtime. It does not cancel a move already queued: `applyTarget` runs later
        through `invokeLater` and does not re-check `explicitTarget`. That was never observed, and
        a move partway through a sweep would revert every 5.1 hit, so it would not pass silently.
     3. Call `apply(project, standardTarget(level))`.
     4. Return the capture.
   - `fun restore(project: Project, saved: SavedTarget)`:
     1. Call `apply(project, saved.target ?: standardTarget(saved.languageLevel))`.
     2. In a `finally`, write back `state.languageLevel` and `state.explicitTarget`, so a throwing
        `apply` still clears the explicit pin.
   - `fun standardTarget(level: LuaLanguageLevel): Target` returns
     `Target(LuaPlatform.STANDARD, requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD,
     level.version)) { … })`. `LuaLanguageLevel.version` is `"$major.$minor"`. `LUA50` therefore
     fails loudly, because there is no Standard `5.0` entry. No member uses `LUA50`.
   - `private fun apply(project: Project, target: Target)` runs these four calls:
     1. `CorpusDefinitionLibraries.requireSettingsSubscriber(project)`. Without the subscriber,
        the broadcast reloads nothing.
     2. `LuaProjectSettings.getInstance(project).setTargetAndNotify(target)`. This sets the target
        and the implicit level, and its broadcast runs `PlatformLibraryIndex.reload()` and restarts
        the daemon.
     3. `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()`.
     4. `IndexingTestUtil.waitUntilIndexesAreReady(project)`.

   All of these run on the test's EDT. `reload()` takes a `WriteAction`, which is why the
   `BasePlatformTestCase` EDT default is needed. The VFS warm-up that `Lua51StdlibStubsTest.useStandardTarget` performs
   (`collectChildrenRecursively` / `markDirtyAndRefresh`) is **not** needed. The
   regression test passes without it.
2. **`LuaCorpusSweepTest`.**
   - Add `private var savedTarget: SavedTarget? = null`.
   - In `sweepAndRatchet`, replace the level write with
     `savedTarget = CorpusTarget.pin(project, entry.luaLevel)` and update the comment above it.
     The pin runs before `refreshFor`, so its roots change stays outside the MAINT-37 refresh
     observation window: `observeRefresh` settles first, then attaches the probe.
   - In `tearDown`, wrap the existing restore/release/refresh steps in an inner `try` and put
     `savedTarget?.let { CorpusTarget.restore(project, it) }` in its `finally`, ahead of the outer
     `finally { super.tearDown() }`. A throw from `CorpusSourcePath.restore`, `release` or
     `refreshRoots` then still restores the target, instead of leaking `explicitTarget=true` and
     the 5.1 target into later classes.
3. **`LuaInspectionParityTest`.**
   - Add the same field.
   - Replace the level write with `savedTarget = CorpusTarget.pin(project, entry.luaLevel)`.
   - Add a `tearDown` override: `try { savedTarget?.let { CorpusTarget.restore(project, it) } }
     finally { super.tearDown() }`.
   - Drop the now-unused `LuaProjectSettings` import.
4. **Baselines.** Re-record `luacheck`, `luarocks`, `penlight` and `zerobrane` with
   `-PrecordCorpusBaseline`. `torture-fuzzing-lua.baseline` does not move. The prototype commit
   carries a recording made at `224a2f3f`, and it can be used as-is if main has not moved the
   corpus lane since. **Commit a recording whose zerobrane `inspection.LuaSuspiciousConcatenation`
   is 19, not 17** (§7, flicker). A 19 baseline reads a 17 run as `IMPROVED`, which passes. A 17
   baseline fails every 19 run.
5. **CHANGELOG**: no entry. This is test infrastructure, with no user-visible change.

Contract check: no function exceeds 30 logic lines or 3 arguments, there is no `!!` and there are
no wildcard imports. No `Project` is retained: `SavedTarget` holds only value types.

## 5. Test Strategy

**`src/test/kotlin/net/internetisalie/lunar/corpus/MemberTargetPinTest.kt`** (`BasePlatformTestCase`,
JUnit4) is in the prototype. It runs in the routine suite: it reads no corpus, and its name stays
outside `*Corpus*`, so `SweepNamingGuardTest` passes. Its `setUp` reproduces the sweep's starting
state, a **stored** Standard 5.4 target in Auto mode. It calls
`initial = CorpusTarget.pin(project, LUA54)`, sets `state.explicitTarget = false`, and asserts that
the root is `lua-5.4` as a precondition. `tearDown` restores `initial`, so nothing leaks into the
routine suite. The three cases:

| Case | Input | Expected |
| :-- | :-- | :-- |
| `testPinMovesTheStdlibRootOffAStoredTarget` | `pin(project, LUA51)` | `getPlatformLibraryFolder(project)?.name == "lua-5.1"`, `languageLevel == LUA51` |
| `testPinnedMemberSeesTheLua51Globals` | `pin(LUA51)`, then highlight `print(rawlen, setfenv, unpack, newproxy)` with `LuaUndeclaredVariableInspection` | undeclared names `== ["rawlen"]` |
| `testRestorePutsTheStoredTargetBack` | `restore(project, pin(project, LUA51))` | root `lua-5.4`, `languageLevel == LUA54`, `explicitTarget == false` |

**It fails on main for the stated reason.** On main the test does not compile, because
`CorpusTarget` is absent. Mutation M1 puts main's code path inside the helper. Each mutation was
applied to the prototype with the temporary-edits loop, the class was run once
(`test --rerun --no-build-cache --tests '*MemberTargetPinTest'`), and the file was restored:

| Mutation | Result |
| :-- | :-- |
| none | 3/3 green |
| **M1**, main's behaviour: in `pin`, replace `state.explicitTarget = true` + `apply(project, standardTarget(level))` with `state.languageLevel = level` | **red**: `testPinMovesTheStdlibRootOffAStoredTarget` `expected:<lua-5.[1]> but was:<lua-5.[4]>`; `testPinnedMemberSeesTheLua51Globals` `expected:<[rawlen]> but was:<[]>` (rawlen resolves through `lua-5.4`, and the 5.1 names are exempt through the level allowlist, which is the §3 hybrid) |
| M2: in `apply`, replace `setTargetAndNotify(target)` with `state.setTarget(target)`, so there is no broadcast and no reload | **red**: `testPinnedMemberSeesTheLua51Globals` `expected:<[rawlen]> but was:<[]>`. The root path moves but the stub index does not, which is why the behavioural case is needed |
| M3: in `restore`, delete `state.explicitTarget = saved.explicitTarget` | **red**: `testRestorePutsTheStoredTargetBack` "explicitTarget must be restored" |

M1 was run twice: before and after a formatting-only edit to `pin`, which hoisted `val state`.
It was red both times.

## 6. Baseline movement (measured)

The movement has two sources, so it was split with a control. **Control:** main's two test files
(`git show HEAD:…`) were recorded with the prototype's new files present but unused (`-PrecordCorpusBaseline`).
**Fix:** the prototype was recorded the same way. For attribution, both runs printed every
highlight (`file:line toolId description`) from a temporary probe in
`CorpusSweep.accumulateHits`, and the two hit lists were diffed.

### 6.1 Pre-existing drift on main (HEAD baselines → control recording; not BUG-515)

On main the ratchet passes. These moves are improvements or keys that are not gated. Any
re-record absorbs them:
- **Ballast `unclaimed` → `claimed`** for 23 groups:
  - luacheck: `.gitignore Dockerfile Makefile md sh yaml yml`
  - luarocks: `.editorconfig .gitignore GNUmakefile Makefile md sh yml`
  - penlight: `.editorconfig .gitignore md yml`
  - zerobrane: `icns ico md png sh`

  `isClaimed` is `FileTypeManager.getFileTypeByFileName(name) != UnknownFileType`, so the test IDE
  now recognises these types. Which change caused it is not established.
- **luacheck `parseErrorFile` wording** for `spec/samples/compound_operators.lua:2`:
  `<expr> expected, got '='` becomes `<expr>, LuaTokenType.&, … expected, got '='`. This is
  [[BUG-517]] (`81e5ad35`), whose report accepts exactly this message change.
- **zerobrane `LuaSuspiciousConcatenation` 21 → 19** (`nil` 4 → 2). Main's ratchet already prints
  `IMPROVED (… baseline 21 → observed 19)`. The value was stable at 19 on all three main runs.

### 6.2 Movement caused by the fix (control recording → fix recording)

| Member | Key | Control → fix | Attribution |
| :-- | :-- | --: | :-- |
| luacheck | `LuaUndeclaredVariable` | 32 → 35 | +3 `rawlen`: `spec/samples/globals.lua:1` ×1 and `spec/samples/compat.lua:1` ×2. `lua-5.1` does not declare it, and `DELTA_51` does not exempt it |
| luacheck | `symbol.LuaUndeclaredVariable.c=1` | removed | Not a hit change. The hit (`spec/configs/project/nested/nested/abc.lua:1`) is in both runs. `rawlen=3` entered the `SYMBOLS_PER_INSPECTION = 10` cap, and `c` lost the tie-break by name |
| luacheck | `LuaDeprecatedApi` | 0 → 8 | 7 `newproxy` (`---@deprecated` in `lua-5.1/builtin.lua` since BUG-513), at line 1 of the seven `spec/projects/default_stds/**` files; 1 `loadstring` (`---@deprecated`), at `src/luacheck/utils.lua:50` |
| luacheck | `LuaSuspiciousConcatenation` | 7 → 6 | `src/luacheck/fs.lua:69` (`ensure_dir_sep(base) .. path`): a `{ ... }`-operand false positive produced by the 5.4 stub types is gone |
| luarocks | `LuaDeprecatedApi` | 0 → 1 | `loadstring`, at `src/compat53/module.lua:428` |
| luarocks | `LuaSuspiciousConcatenation` | 115 → 110 | 5 `{ ... }` false positives gone: `compat53/file_mt.lua:9`, `compat53/module.lua:203`, `luarocks/fetch.lua:181`, `:206`, `luarocks/fs/win32.lua:338` |
| luarocks | `LuaTypeAssignability` | 5 → 9 | `spec/unit/fs_spec.lua:282` `os.remove(intdir)` "nil value is not assignable to string" (5.1 `---@param filename string`); `fs/unix/tools.lua:249-250` ×3 "string is not assignable to number" (5.1 `os.date` `---@return string`, assigned to a `number`-narrowed `time`) |
| penlight | `LuaDeprecatedApi` | 0 → 2 | `loadstring`, at `lua/pl/compat.lua:24` and `:101` |
| penlight | `LuaReturnTypeMismatch` | 1 → 2 | `lua/pl/pretty.lua:59` (`local function tostring(value)`): "string is not assignable to number" |
| zerobrane | `LuaDeprecatedApi` | 0 → 6 | `local loadstring = loadstring or load`, at `editor/commands.lua:457`, `editor/debugger.lua:356`, `:466`, `editor/shellbox.lua:321`, `util.lua:517`, `:538` |
| zerobrane | `LuaTypeAssignability` | 0 → 18 | `editor/commands.lua:894-898` ×4 "boolean \| number is not assignable to number" (5.1 `collectgarbage` `---@return number\|boolean`; 5.4 returns `any`); `editor/findreplace.lua` ×8 (lines 22, 377 ×2, 490 ×2, 494, 495, 499) "nil value is not assignable to string"; `editor/outline.lua` ×6 (387, 391, 400, 694 ×2, 696) "nil value is not assignable to any[] \| string \| { ... }" |
| zerobrane | `LuaReturnTypeMismatch` | 0 → 1 | `editor/outline.lua:388` |
| zerobrane | `LuaSuspiciousConcatenation` | 19 → 19 (nil 2 → 3, `{ ... }` 17 → 16) | Gone: `editor/shellbox.lua:262` (`nil`) and `main.lua:310` (`{ ... }`). Added in 2 of 3 fix runs: `editor/menu_project.lua:96`, `:136` (`nil`, `ide.config.default.interpreter`). See the flicker below |
| zerobrane | `LuaUndeclaredVariable` | 1954 → 1954 | Unchanged. The parity anchor holds |

Check against the old prediction:
- **`rawlen`**: as predicted.
- **`newproxy`**: deprecation hits appeared. The predicted undeclared hits never existed: on main,
  `DELTA_51` already exempted the name.
- **`package.config`**: no hit, as predicted.
- **Not predicted**: the ten `loadstring` deprecations, the type-inspection moves and the
  concatenation false positives.

The fix recording matches the control on every key the table does not list.

**Flicker (determinism), measured.** Across four fix runs (three recordings and one verify),
zerobrane `LuaSuspiciousConcatenation` read 19, 17, 19 and 19. The difference is the
`menu_project.lua:96`/`:136` pair. On main, three runs read 19 every time. luacheck, luarocks and
penlight recorded byte-identically in all three fix recordings. Whether the fix introduces the
flicker or only exposes it is not established (§8). The per-member target reload is the visible
new ingredient: `StubIndex.forceRebuild` runs twice per member. Filed as [[BUG-541]].

## 7. Verification

Run on the prototype:

| Gate | Command | Result |
| :-- | :-- | :-- |
| Corpus ratchet | `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` | green: `LuaCorpusSweepTest` 4/4, `LuaTortureCorpusTest` 1/1, no `IMPROVED` lines (zerobrane read 19) |
| Parity, by name | `test --rerun --no-build-cache -PwithCorpus --tests '*InspectionParityTest'` | green: `files=72 withTypes=1954 withoutTypes=1954 baseline=1954`, 72/72 at exact parity |
| Routine suite | `test --rerun --no-build-cache` | 3,293 tests, 0 failures, 0 errors, 4 skipped. `MemberTargetPinTest` ran (3/3) |
| Pin test, final text | `test --rerun --no-build-cache --tests '*MemberTargetPinTest'` | green, 3/3 |
| ktlint, final tree | `ktlintCheck` alone, on the delivered tree | `BUILD SUCCESSFUL` |
| Mutations | §5 | M1, M2 and M3 red |

The sweep's cost is unchanged within noise. Per-member `elapsedMs` on main against the prototype:
- luacheck: 42,855 against 43,030
- luarocks: 103,249 against 93,836
- penlight: 26,120 against 31,976
- zerobrane: 77,659 against 72,320

**For the implementer**, after landing §4:
1. Run the corpus ratchet. If the committed baseline was re-recorded, confirm zerobrane
   `LuaSuspiciousConcatenation=19`.
2. Run the parity test by name. It must never run inside the `*Corpus*` filter (BUG-418).
3. Run the routine suite, `test --rerun --no-build-cache`. Known unrelated intermittents:
   [[BUG-529]] (`LuaRenameConflictTest` cost test) and a one-off `DefinitionPinProvisioningTest`
   hang. Re-run the affected class alone before treating either as a regression.
4. Run `ktlintCheck` on its own, never paired with `ktlintFormat`.
5. Re-run mutation M1 once against the landed `CorpusTarget.pin`. It must turn
   `testPinMovesTheStdlibRootOffAStoredTarget` red with `expected:<lua-5.[1]> but was:<lua-5.[4]>`.

## Verification of the delivered fix

Run on the delivered tree: the prototype, merged with main at `7a79a2ba`, plus the tear-down
hardening of §4 steps 1–2. Each gate ran once on the libvirt builder:

| Gate | Result |
| :-- | :-- |
| Routine suite, `test --rerun --no-build-cache` | 3,293 tests, 0 failures, 0 errors, 4 skipped; `MemberTargetPinTest` 3/3 |
| Corpus ratchet, `test --rerun --no-build-cache -PwithCorpus --tests '*Corpus*'` | green: `LuaCorpusSweepTest` 4/4, `LuaTortureCorpusTest` 1/1. One `IMPROVED` line: zerobrane `LuaSuspiciousConcatenation` baseline 19 → observed 17, the [[BUG-541]] flicker that §4 step 4 anticipates |
| Parity, by name, `-PwithCorpus --tests '*InspectionParityTest'` | green: `files=72 withTypes=1954 withoutTypes=1954 baseline=1954` |
| `ktlintCheck` alone | `BUILD SUCCESSFUL` |
| M1 against the delivered `pin` | red: `testPinMovesTheStdlibRootOffAStoredTarget` `expected:<lua-5.[1]> but was:<lua-5.[4]>`, `testPinnedMemberSeesTheLua51Globals` `expected:<[rawlen]> but was:<[]>`; file restored |

The baselines are the prototype's recording, used as-is: main's moves since `224a2f3f` do not touch
the corpus lane. No test makes a tear-down step throw, so the `finally` path of the hardened
tear-down was not run.

## 8. Not established, and findings for whoever triages

- **Zerobrane concatenation flicker** (§6.2). The `menu_project.lua:96`/`:136` `nil`-operand hits
  appear in some fix runs and not others. The cause is not established. The type of
  `ide.config.default.interpreter`, a global table assembled across files, is the candidate. This
  contradicts BUG-418's "byte-deterministic per invocation shape" for this key. The §4 step 4
  recording rule keeps the ratchet green, but it does not fix the flicker. Filed as [[BUG-541]].
- **New type hits that look like stub-signature false positives**, all from `lua-5.1` stubs.
  They are out of scope here, and the baselines record them as they are:
  - `collectgarbage("count")` is typed `number|boolean` rather than `number` (no per-option
    overload). This produces the 4 hits at `editor/commands.lua:894-898`.
  - `os.date` is typed `---@return string` even for `"*t"`, which returns a table. This produces
    the 3 hits at `fs/unix/tools.lua:249-250`.
  - `findreplace.lua` and `outline.lua` assign a nil-able iterator result to fields typed as
    strings. Whether those 14 hits are true or false positives was not judged.
- **Which writer first stores the shared project's target** (§3, step 3) is not established. The
  fix does not depend on it.
- **Ballast `claimed` drift** (§6.1). The cause is not established. It is not BUG-515.
