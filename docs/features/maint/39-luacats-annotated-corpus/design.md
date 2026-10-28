---
id: "MAINT-39-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-39"
folders:
  - "[[features/maint/39-luacats-annotated-corpus/requirements|requirements]]"
---

# Technical Design: MAINT-39 — A LuaCATS-Annotated Corpus

All Kotlin in this feature is **test code** under `src/test/kotlin/net/internetisalie/lunar/corpus/`.
Nothing under `src/main/` and no `plugin.xml` registration changes. No new class is created.
Line numbers are at `ca108e35`.

## 1. Architecture Overview

### Current State
A corpus member is a `corpus.json` entry, a fetched checkout under the out-of-repo
`test/corpus/<name>/`, a `@Test` in `LuaCorpusSweepTest` that calls `sweepAndRatchet(name)`, and a
recorded `src/test/resources/corpus/<name>.baseline`. No member carries LuaCATS annotations, and
every member is pinned to the Standard target of its `luaLevel` (`CorpusTarget.pin`, BUG-515).

### Prior Art in This Repo

| Component | Location | This design |
| :-- | :-- | :-- |
| Manifest and its reader | `tooling/corpus/corpus.json`; `CorpusManifest.load` (`CorpusManifest.kt:45`), `parseEntry` (`:79`), `CorpusEntry` (`:14`) | **Extended** by one entry and the optional `target` field (§2.5) |
| Target pin | `CorpusTarget.pin` (`CorpusTarget.kt:29`), `standardTarget` (`:55`) | **Extended** by a `Target` overload and `memberTarget` (§2.5). TARGET-12 design §8's private `useTarget` (`LuaJitRuntimeStubsTest.kt:180`) is not reused: `CorpusTarget` already owns the sweep's pin and restore |
| Fetcher | `tooling/corpus/fetch-corpus.py` (`fetch_one`, `main`) | **Unchanged**. It checks only `name`, `url`, `commit`, `roots`, `luaLevel` (`:96`), so it ignores `target`, and it provisions the `neovim` archive the entry pins (DR-12) |
| Definition libraries | `CorpusDefinitionLibraries` (`stage`, `enable`, `release`) | **Unchanged**; the entry's `definitionLibraries` drives them as for `busted` |
| Sweep and metrics | `CorpusSweep.run` (`CorpusSweep.kt:75`), `CorpusMetrics`, `CorpusBaseline` (`CorpusMetrics.kt:117`) | **Unchanged** |
| Sweep class | `LuaCorpusSweepTest` (`sweepAndRatchet` at `:119`, its pin at `:125`, `testPenlightCorpus` at `:117`) | **Extended** by one `@Test` and the pin call of §2.5 |
| Guards | `CorpusGuards.assertCorpusFetched` (`CorpusGuards.kt:17`), `assertRatchet` (`:37`) | **Unchanged**; they gate the new member as they gate the others, including the `library.neovim=` identity check |
| Record command | `-PrecordCorpusBaseline` → `recordBaseline` (`LuaCorpusSweepTest.kt:222`) | **Reused** as the determinism instrument: full-shape records compared byte-for-byte (§3.1) |
| Synthetic annotated fixture | `LuaAnnotatedFixtureSweepTest` | **Kept**; it gates BUG-473's root-resolution budget on one sized file, which a whole-project sweep does not. Its KDoc is corrected (§2.4) |
| Other `CorpusTarget` callers | `LuaInspectionParityTest` (`:77`, member `zerobrane`) | **Unchanged**; keeps `pin(project, level)` |
| `MemberTargetPinTest` | **Extended** by `testManifestTargetPinsTheLuaJitRuntime` and its imports (§2.5); its existing `pin(project, level)` calls are unchanged |
| CI corpus job | `.gitea/workflows/build-plugin.yml`, job `corpus` | **Unchanged**. Its fetch step runs `fetch-corpus.py` (`:388`), its ratchet step runs `--tests '*Corpus*'` (`:406`, runs the new method), and its diagnostics grep `\[corpus:[a-z-]*\]` (`:408`) matches the member name (§2.1, DR-09) |

### Target State
`lazynvim` is one more member, on its own target and library, gated by the same ratchet in the same
invocation shape.

## 2. Core Components

### 2.1 The manifest entry (`tooling/corpus/corpus.json`)
Appended as the last element of `corpora`, exactly:

```json
    {
      "name": "lazynvim",
      "url": "https://github.com/folke/lazy.nvim.git",
      "commit": "85c7ff3711b730b4030d03144f6db6375044ae82",
      "roots": ["lua"],
      "luaLevel": "LUA51",
      "target": "LUAJIT:2.1",
      "moduleRoot": "lua",
      "definitionLibraries": ["neovim"]
    }
```

- `name` is lowercase ASCII letters only. Existing consumers constrain it: the sweep method is named
  `"test" + name.replaceFirstChar { it.uppercaseChar() } + "Corpus"` (§2.2; MAINT-37's member-alone
  arm derives it the same way), which must be a Kotlin identifier, and CI's diagnostics grep matches
  `[a-z-]*` only (DR-09). `lazy.nvim` or `lazy-nvim` would break one or the other.
- `commit` is the tip of tag `v11.17.5` (DR-02). No `prune`: the checkout carries no binaries
  (2.9 MB).
- `roots` is `lua` only. `tests/` holds `*_spec.lua` specs bootstrapped by `tests/minit.lua`; DR-03
  measured that it adds no annotation tag kind `lua/` lacks.
- `target` is `LUAJIT:2.1` because Neovim embeds LuaJIT 2.1; it declares `jit` and `bit`
  (TARGET-12). `luaLevel` is `LUA51`, the level that target implies, which §2.5 enforces; it also
  selects the 5.1 `luac` oracle (`test/luac/5.1.5`), as before.
- `moduleRoot` is `lua` because `lazy.nvim` names its own modules relative to `lua/`
  (`require("lazy.core.config")` → `lua/lazy/core/config.lua`; DR-06).
- `definitionLibraries` is `neovim` (TARGET-11), which declares `vim`. Its pinned version is
  identity-checked as the baseline's `library.neovim=` line.

### 2.2 `LuaCorpusSweepTest.testLazynvimCorpus`
Inserted directly after `testPenlightCorpus`:

```kotlin
    /**
     * MAINT-39: the one member written in LuaCATS — `lazy.nvim`, annotated natively and densely
     * (`@class` with inheritance, `@alias`, `@generic`, `@overload`, `@cast`), so a green sweep says
     * something about the luacats subsystem and the annotated type engine.
     */
    @Test
    fun testLazynvimCorpus() = sweepAndRatchet("lazynvim")
```

The name matches `build.gradle.kts`'s `*Corpus*` filter (`:423`), so the routine run excludes it and
`-PwithCorpus` includes it, like its siblings. `SweepNamingGuardTest` exempts `LuaCorpusSweepTest`
by name, and `UncollectedTestMethodGuardTest` is satisfied by the `@Test` annotation.

### 2.3 The baseline `src/test/resources/corpus/lazynvim.baseline`
Produced only by the record command (implementation-plan → Gates) and copied back. Its format is
`CorpusBaseline.render`'s (`CorpusMetrics.kt:144`); nothing about it is hand-written.

### 2.4 Corrected statements

| File | Statement | Replacement |
| :-- | :-- | :-- |
| `src/test/kotlin/net/internetisalie/lunar/corpus/LuaAnnotatedFixtureSweepTest.kt:28-33` | "Measured 2026-08-29: **0 of the 734 pinned corpus files** …" through "… stay blocked until BUG-473's growth exponent moves." | "The pinned pre-LuaCATS projects cannot trigger BUG-473, and a whole-project sweep cannot gate a root-resolution budget on one sized file, so this fixture does. Real annotated code is swept by `LuaCorpusSweepTest.testLazynvimCorpus` (MAINT-39)." |
| `build.gradle.kts:430-431` | "the annotated fixture the pinned corpora cannot supply — 0 of their 734 files carry a `---@` tag." | "the sized annotated fixture whose root-resolution budget a whole-project sweep cannot gate." |
| `tooling/corpus/corpus.json:32-33` (`$comment`, `moduleRoot`) | "Omit when requires are relative to the checkout root, as all four below are." | "Omit when requires are relative to the checkout root." |
| `tooling/corpus/corpus.json` `$comment`, the `luaLevel` lines | "the level selects which stdlib globals are known" | "the level selects which stdlib globals are known, unless `target` is set (below)" (keep the line wrapping) |
| `tooling/corpus/corpus.json` `$comment`, after the `definitionLibraries` lines | (absent) | Three lines: `"  target     optional; \"<LuaPlatform>:<version label>\" from PlatformVersionRegistry, e.g.",` / `"             \"LUAJIT:2.1\". The sweep pins that target instead of the Standard one of luaLevel,",` / `"             which must be the level the target implies; CorpusManifest refuses a mismatch."` (the comma after the previous last line is added) |
| `tooling/corpus/README.md:41-45` (§ Adding a project) | "Append an entry to `corpus.json` …" through "… and record a baseline." | "Append an entry to `corpus.json` — the fields are documented in its `$comment`: `name`, `url`, **a commit SHA resolved from a release tag**, the `roots` to index, `luaLevel`, and optionally `prune`, `moduleRoot`, `definitionLibraries` and `target` (a non-Standard target such as `LUAJIT:2.1`). Never pin a branch — the ratchet needs a byte-identical tree across runs. `name` is lowercase ASCII letters only: the sweep method is `test` + `name` with its first letter upper-cased + `Corpus` (`lazynvim` → `testLazynvimCorpus`), which must be a Kotlin identifier, and CI's ratchet diagnostics match `\[corpus:[a-z-]*\]` only. Add that method to `LuaCorpusSweepTest` and record a baseline with the full `'*Corpus*'` filter (above)." Wrapped at the README's width, with `lowercase ASCII letters only` kept on one line |
| `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaColonCallInferenceWithdrawalTest.kt:29-30` | "the ratchet structurally cannot see it: the pinned corpus carries 0 `---@` tags across all 734 files, so no expected-callback type exists to seed from." | "the ratchet cannot gate it: it needs a hand-built colon-call receiver, which no corpus member supplies, so no expected-callback type exists to seed from." (keep the line wrapping) |
| `src/test/kotlin/net/internetisalie/lunar/corpus/MemberTargetPinTest.kt:13` | "BUG-515: a corpus member pinned to `LUA51` must be measured against `runtime/standard/lua-5.1`." | "BUG-515: a corpus member pinned to `LUA51` must be measured against its target's runtime — `runtime/standard/lua-5.1`, or the manifest's `target` (MAINT-39)." |
| `src/test/kotlin/net/internetisalie/lunar/corpus/CorpusManifest.kt:22-23` | "which is what every currently-pinned project does." | "which is what most pinned projects do." |
| `src/test/kotlin/net/internetisalie/lunar/corpus/BaselineRatchetTest.kt:365-366` | "which is what every currently-pinned project does — so the default must stay null rather than \"\"." | "which is what most pinned projects do — so the default must stay null rather than \"\"." |
| `src/test/kotlin/net/internetisalie/lunar/corpus/CorpusTarget.kt:20` (KDoc first line) | "Pins a corpus member to the Standard target of its manifest level, not just the level (BUG-515)." | "Pins a corpus member to its target — the manifest's `target`, else the Standard target of its level — not just the level (BUG-515, MAINT-39)." |

`README.md` § Usage already names `corpus.json` and `fetch-corpus.py`; nothing else in it is
falsified by this member.

### 2.5 The `target` field (MAINT-39-08)
`CorpusEntry` gains a last property, so every existing constructor call compiles unchanged:

```kotlin
    /** The member's non-Standard target (`"<LuaPlatform>:<version label>"`); null pins Standard [luaLevel]. */
    val target: Target? = null,
```

`CorpusManifest.parseEntry` ends `).let { parsed -> parsed.copy(target = entry.target(parsed)) }`
instead of `)`, and gains, with imports `LuaPlatform`, `PlatformVersionRegistry` and `Target`:

```kotlin
    /** Absent means Standard at `luaLevel`; a target whose implicit level differs is refused. */
    private fun JsonObject.target(parsed: CorpusEntry): Target? {
        val spec = get("target")?.asString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val platform = LuaPlatform.valueOf(spec.substringBefore(':'))
        val version =
            requireNotNull(PlatformVersionRegistry.findVersion(platform, spec.substringAfter(':', ""))) {
                "Corpus entry '${parsed.name}' names unknown target '$spec' in $MANIFEST_PATH"
            }
        val target = Target(platform, version)
        require(target.getImplicitLanguageLevel() == parsed.luaLevel) {
            "Corpus entry '${parsed.name}': target '$spec' implies ${target.getImplicitLanguageLevel().name}, not ${parsed.luaLevel.name}"
        }
        return target
    }
```

The level check keeps the `luac` oracle's level (`entry.luaLevel`, `CorpusSweep.kt:86`) equal to the
level `setTarget` derives (`LuaProjectSettings.kt:136`). An unknown platform name throws
`LuaPlatform.valueOf`'s `IllegalArgumentException`.

`CorpusTarget`: the body of `pin(project, level)` moves unchanged into a new overload, with
`apply(project, target)` in place of `apply(project, standardTarget(level))`; the old signature
delegates; and `memberTarget` is added:

```kotlin
    fun pin(project: Project, level: LuaLanguageLevel): SavedTarget = pin(project, standardTarget(level))

    fun pin(project: Project, target: Target): SavedTarget { /* the former body */ }

    /** The manifest's `target` when it names one, else the Standard target of its `luaLevel`. */
    fun memberTarget(entry: CorpusEntry): Target = entry.target ?: standardTarget(entry.luaLevel)
```

`LuaCorpusSweepTest.kt:125` becomes `savedTarget = CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))`.
`restore` is unchanged: it puts back the stored target, whatever its platform. The code is as
executed in DR-12 and DR-13; `ktlintFormat` may rewrap it.

**Tests** (routine suite: neither class name contains `Corpus`). Appended to `BaselineRatchetTest`,
which already has `manifestRoot` and the `assertNull`/`assertTrue` imports:

```kotlin
    /** MAINT-39: `target` names a non-Standard target; absent leaves the Standard target of `luaLevel`. */
    @Test
    fun manifestTargetParses() {
        val repoRoot =
            manifestRoot(
                """{"name":"plain","url":"https://example.invalid/x.git","commit":"deadbeef","roots":["src"],"luaLevel":"LUA51"}""",
                """{"name":"jit","url":"https://example.invalid/y.git","commit":"cafebabe","roots":["src"],""" +
                    """"luaLevel":"LUA51","target":"LUAJIT:2.1"}""",
            )
        assertNull(CorpusManifest.entry(repoRoot, "plain").target)
        assertEquals("runtime/luajit/luajit-2.1", CorpusManifest.entry(repoRoot, "jit").target?.getLibraryRootPath())
    }

    /** A target implying another level would split the oracle's level from the sweep's. */
    @Test
    fun manifestTargetMustImplyTheLuaLevel() {
        val repoRoot =
            manifestRoot(
                """{"name":"skew","url":"https://example.invalid/x.git","commit":"deadbeef","roots":["src"],""" +
                    """"luaLevel":"LUA54","target":"LUAJIT:2.1"}""",
            )
        val failure = runCatching { CorpusManifest.load(repoRoot) }.exceptionOrNull()
        assertTrue("got: ${failure?.message}", failure?.message.orEmpty().contains("implies LUA51, not LUA54"))
    }

    @Test
    fun manifestTargetMustBeRegistered() {
        val repoRoot =
            manifestRoot(
                """{"name":"nine","url":"https://example.invalid/x.git","commit":"deadbeef","roots":["src"],""" +
                    """"luaLevel":"LUA51","target":"LUAJIT:9.9"}""",
            )
        val failure = runCatching { CorpusManifest.load(repoRoot) }.exceptionOrNull()
        assertTrue("got: ${failure?.message}", failure?.message.orEmpty().contains("unknown target 'LUAJIT:9.9'"))
    }
```

Appended to `MemberTargetPinTest`, with imports `LuaPlatform`, `PlatformVersionRegistry` and `Target`
(its `setUp` and `tearDown` already restore the initial target):

```kotlin
    /** MAINT-39: a manifest `target` reaches the pin, so a LuaJIT member sees `jit` and `bit`. */
    @Test
    fun testManifestTargetPinsTheLuaJitRuntime() {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.LUAJIT, "2.1"))
        val entry =
            CorpusEntry(
                name = "jit",
                commit = "cafebabe",
                roots = listOf("src"),
                luaLevel = LuaLanguageLevel.LUA51,
                moduleRoot = null,
                target = Target(LuaPlatform.LUAJIT, version),
            )
        CorpusTarget.pin(project, CorpusTarget.memberTarget(entry))
        assertEquals("luajit-2.1", stdlibRootName())
        assertEquals(listOf("rawlen"), undeclaredNames("print(jit, bit, setfenv, rawlen)\n"))
    }
```

## 3. Algorithms

### 3.1 Admission procedure (implementation-plan Phase 1)
Every corpus run is a full `'*Corpus*'` record or gate: the shape the CI ratchet runs (BUG-418).
`$SCR` and the commands are implementation-plan → Gates.

**The BUG-541 rule.** zerobrane's `LuaSuspiciousConcatenation` count reads 19 or 17 from run to run
([[BUG-541]]; DR-08). A recorded `zerobrane.baseline` file `F` *differs only by BUG-541* when both
hold:
- `diff <(git show HEAD:src/test/resources/corpus/zerobrane.baseline) F | grep '^[<>]' | grep -vE '^[<>] (inspection\.LuaSuspiciousConcatenation|symbol\.LuaSuspiciousConcatenation\.nil)='`
  prints nothing, and
- `grep -cx 'inspection.LuaSuspiciousConcatenation=17' F` prints `1`.

Such a file is never committed: the committed value stays 19, so a 17 run passes the gate as
`IMPROVED` while a committed 17 would fail every 19 run.

1. **Fetch** (TC 1, TC 5).
2. **Record 1** with the record command, copied back into the tree.
3. **Other members (TC 6):** `git status --porcelain -- src/test/resources/corpus/` prints
   `?? src/test/resources/corpus/lazynvim.baseline` and nothing else, except
   ` M src/test/resources/corpus/zerobrane.baseline` when that file differs only by BUG-541; then
   `git show HEAD:src/test/resources/corpus/zerobrane.baseline > src/test/resources/corpus/zerobrane.baseline`.
4. **Repeat (TC 7).** Records 2 to 8, each copied back to `$SCR/rec/<i>/`, never to the tree. After
   each, every `src/test/resources/corpus/*.baseline` and `*.expected-accepts` file is `cmp`-equal
   to its copy in `$SCR/rec/<i>/`, except that `$SCR/rec/<i>/zerobrane.baseline` may differ only by
   BUG-541. This checks MAINT-39-04 (`lazynvim.baseline` against record 1) and MAINT-39-03 (every
   other file) on every record.
5. **Gate** run (TC 3). Its `IMPROVED` filter is
   `grep -o '\[corpus\] IMPROVED ([^)]*)' <XML> | grep -vxF '[corpus] IMPROVED (inspection.LuaSuspiciousConcatenation: baseline 19 → observed 17)'`,
   which prints nothing. Then TC 4 and TC 2 with their restores.

Any failed check stops (implementation-plan → Gates "Stop"). The stop's BUG names the cause the step
isolates: at step 3, or at step 4 on a file other than `lazynvim.baseline`, the member perturbing
another (DR-08); at step 4 on `lazynvim.baseline`, nondeterminism on the member.

**Sample size.** Eight full-shape records: record 1 and the seven repeats, each repeat compared
with record 1.
- **What BUG-501's fix removed.** Before it, `lazynvim`'s block differed in one of four full-shape
  records, beside a modal block the other three shared (DR-07, DR-11). A variation like that is
  detected with probability at least 1 − (3/4)⁷ ≈ 87 %: when record 1 is the modal block each repeat
  differs with probability 1/4, and when it is the minority block each differs with probability
  3/4. The rate rests on four records, so it is an estimate, not a bound.
- **The smallest per-record rate detected with 95 % confidence** is 1 − 0.05^(1/7) ≈ 35 % for the
  repeat alone. DR-12's eight records on the real configuration found no variation, which excludes
  a rate above 1 − 0.05^(1/8) ≈ 31 % on its tree at 95 % confidence; DR-11's sixteen probe records
  bound the same rate below 17.1 % for the probe configuration.
- **Cost.** A full-shape record takes about six minutes on the libvirt builder (DR-12), so the
  repeat is about 45 minutes.

### 3.2 Reading the findings out of the baseline (MAINT-39-05)
The classes are fixed here; coverage was decided against the bug reports at `ca108e35`. Each row
marked **file** gets one BUG (skill `report-bug`) with its roadmap row, IDs claimed at commit time.
A report filed later does not change a row.

| Class, as DR-05 / DR-06 / DR-12 names it (sites listed there) | Decision |
| :-- | :-- |
| Optional `name?:` in a table-literal type or `fun` parameter (DR-05) | **file**; links [[BUG-502]], whose relation to these error-producing sites is not established |
| Numeric key `[1]` (DR-05) | **file** |
| Optional `fun` return `:boolean?` inside a table type (DR-05) | **file** |
| Parenthesised return list `fun():(A?, B?)` (DR-05) | **file** |
| `async fun(…)` (DR-05) | **file** |
| Unnamed `fun` parameters `fun(LazyPlugin)` (DR-05) | **file** |
| Spaced string-literal union `"a" \| "b"` (DR-05) | **file** |
| `@see \|text\|` (DR-05) | **file** |
| An absent module resolves to the first `init.lua` (DR-06) | covered by [[BUG-500]] |
| A string argument of a call chained on `require(…)` counted as a require (DR-06) | **file** |
| `@param` with a `{k?: T}` table type silently becomes LDoc (`view/render.lua:482`; no `parseErrorFile` line, part of the `LuaTypeAssignability` floor) | covered by [[BUG-502]] |
| `loadstring` reported deprecated on the Lua 5.1 and LuaJIT 2.1 targets, where it is current API (`core/plugin.lua:308`; the `---@deprecated` tag in both targets' `builtin.lua`; DR-12) | **file** |
| An explicit `nil` argument for an optional `---@param mode? string` reported as not assignable (`pkg/rockspec.lua:228`, `loadfile(file, nil, ret)`; DR-12) | **file** |
| A project `M.clear()` / `M.save()` resolving to Neovim's deprecated functions (`manage/init.lua`, `docs.lua`; DR-12) | covered by [[BUG-508]] |
| `tbl_islist`, `termopen`, `nvim_get_hl_by_name` (DR-12) | genuine deprecations; not filed |
| `inspection.LuaSuspiciousConcatenation=14`, every site `symbol.LuaSuspiciousConcatenation.{ ... }=14`, identical in DR-12's arms B, C and D (the operand's type is `{ ... }`; sampled: `terminal.lua:63` `"\r" .. nextChar`, a `gsub` callback parameter; `core/util.lua:80` `home .. path:sub(2)`, `home = vim.uv.os_homedir()`; `core/cache.lua:451` `name` from `uv.fs_scandir_next`) | **file** as one BUG, "a string-valued operand typed `{ ... }` is reported by `LuaSuspiciousConcatenation`"; the 14 sites are not triaged individually |
| `inspection.LuaShadowingVariable=1` (`health.lua`, `ok`) and `inspection.LuaUnusedLocal=1` (`view/float.lua`, `normal`) | genuine findings in the project; not filed |

1. Every `parseErrorFile=<path>:<line>:<message>` line of the committed `lazynvim.baseline` must
   name a site DR-05 lists. A line that names no listed site is a new class: open `<path>` at
   `<line>`, name the construct, and **file** it.
2. Beyond the rows above, `inspection.LuaTypeAssignability` and `inspection.LuaReturnTypeMismatch`
   are **not** triaged further (follow-on candidate "triage the type-inspection floors").

## 4. External Data & Parsing
- **`corpus.json`**: the existing schema plus `target` (§2.5), parsed by Gson (`CorpusManifest`) and
  Python `json` (`fetch-corpus.py`, which ignores `target`). The `target` value is
  `<LuaPlatform enum name>:<VersionEntry label>`, split at the first `:`.
- **The `lazy.nvim` checkout and the `neovim` archive**: read only by the sweep and
  `CorpusDefinitionLibraries`. No format is parsed by this feature's code.
- **Baselines**: `CorpusBaseline.render` output, compared byte-for-byte or by the BUG-541 rule
  (§3.1), never parsed by new code.

## 5. Data Flow
1. `fetch-corpus.py` → `test/corpus/lazynvim/` with `.corpus-sha`, and the `neovim` archive in
   `test/corpus/.definitions/`.
2. `--tests '*Corpus*'` → JUnit runs `testLazynvimCorpus` among the members →
   `sweepAndRatchet("lazynvim")` → `CorpusGuards.assertCorpusFetched` →
   `CorpusTarget.pin(project, memberTarget(entry))` (LuaJIT 2.1 root) → `refreshFor` (stages and
   enables `neovim`) → `CorpusSweep.run` (copies `lua/` into the light project, puts the on-disk
   `lua/` on `sourcePath`, tallies, highlights with the inspections `LuaCorpusSweepTest.setUp`
   enables, `:67-78`) → `CorpusGuards.assertRatchet` against `lazynvim.baseline` → `tearDown`
   restores the source path, the library and the target.

Threading is unchanged: the sweep and the pin run on the test's EDT, as for every member.

## 6. Edge Cases
- **The baseline records defects as its floor.** LuaCATS parse errors raise both `parseErrors` and
  `oracleDisagreements` (`CorpusSweep.kt:55-60`: any `PsiErrorElement`, including one inside a
  LuaCATS comment, makes Lunar reject a file `luac` accepts; DR-05). Each fix lowers both, prints
  `IMPROVED`, and is re-recorded by the fixing change — the ratchet's normal path.
- **A require fix moves `requires`.** BUG-500's fix, or one for the chained-call recognition DR-06
  found, changes this member's `requires`/`unresolvedRequires`. `requires` is identity-checked
  (`CorpusGuards.kt:82-87`), so that fix re-records `lazynvim.baseline` in its own change.
- **BUG-502's fix can move the type floor.** It restores a dropped `@param`. Whether the member's
  recorded `LuaTypeAssignability` count includes the resulting false *Too many arguments* on
  `view/render.lua`'s `self:markdown(…)` is not measured. Any movement prints `IMPROVED` or fails
  the gate, and that fix re-records.
- **A catalog re-pin of `neovim`** fails the `library.neovim=` identity check, and the re-pinning
  change re-records this member, as MAINT-37 requires for `busted`.
- **[[BUG-541]]** is zerobrane's, not this member's; §3.1's rule keeps its flicker from stopping
  Phase 1 or reaching a committed baseline. Its fix lowers or fixes zerobrane's count in its own
  change.

## 7. Integration Points
- No `plugin.xml`, extension point, setting or index change.
- `build.gradle.kts`: comment text only (§2.4); no filter change (§2.2).
- CI: no workflow change (§1 table).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by |
| :-- | :-- | :-- |
| MAINT-39-01 | M | §2.1 |
| MAINT-39-02 | M | §2.2, §2.3, §5 |
| MAINT-39-03 | M | §3.1 steps 3 and 4 |
| MAINT-39-04 | M | §3.1 step 4 |
| MAINT-39-05 | S | §3.2 |
| MAINT-39-06 | S | §2.4 |
| MAINT-39-07 | W | risks-and-gaps → Follow-on candidates |
| MAINT-39-08 | M | §2.5 |

## 9. Alternatives Considered
- **A separate sweep class outside the `'*Corpus*'` shape** (as `LuaAnnotatedFixtureSweepTest` is).
  Rejected: a second gate command and a second CI step for one member, and it would not run under the
  record command that every other member's procedure uses. Its only advantage — isolating the other
  members from this one's JVM state — is measured by DR-08, DR-11 and DR-12 and checked by §3.1
  steps 3 and 4 instead.
- **Sweep on Standard 5.1 without the library**, as DR-01 to DR-11 did. Rejected: `jit` and
  `vim` then fire `LuaUndeclaredVariable` on every reference, a floor that measures missing
  definitions rather than the plugin (DR-12 quantifies it).
- **Record `target` in the baseline** as an identity line, like `library.*`. Rejected: it changes
  `CorpusBaseline.render` and so every committed baseline's bytes, which MAINT-39-03 forbids; a
  target change already moves the floors the ratchet gates.
- **Record the baseline at the maximum over several runs**, so a one-sided ratchet never fails on a
  varying count. Rejected: it changes the record mechanism for one member, hides the variance it
  tolerates, and any later ordinary re-record that lands below the maximum makes the gate flaky.
- **`telescope.nvim`, `plenary.nvim` or `nvim-lspconfig` instead of `lazy.nvim`.** DR-03: a smaller
  share of their files is annotated, with no or little `@class` inheritance, or (`nvim-lspconfig`'s
  `lsp/`) the annotations are almost all `@type` on configuration tables.
- **Narrowing `roots` to skip the file whose count varied** (`lua/lazy/view/`). Rejected: it removes
  the densest annotated code to hide the defect the member exists to expose.

## 10. Open Questions

_None._
