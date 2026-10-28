---
id: "MAINT-39"
title: "39: A LuaCATS-Annotated Corpus"
type: "feature"
parent_id: "MAINT"
status: "done"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-39: A LuaCATS-Annotated Corpus

## Overview

The corpus sweep cannot see Lunar's LuaCATS subsystem. Its pinned projects (`luacheck`, `luarocks`,
`penlight`, `zerobrane`) are pre-LuaCATS-era Lua, so no file the sweep indexes carries a `---@` tag,
and everything under
`src/main/kotlin/net/internetisalie/lunar/luacats/` plus the annotated half of the type engine is
validated by unit fixtures alone. [[BUG-473]] (one `---@class` making `LuaTypesSnapshot.forFile`
superlinear) lived there undetected.

This feature adds **one natively annotated application project, `lazy.nvim`, as a ratcheted corpus
member** named `lazynvim`. It is a Neovim plugin, so it is swept as one: on the LuaJIT 2.1 target
([[TARGET-12]]) with the `neovim` definition library ([[TARGET-11]]). The manifest cannot name a
non-Standard target today, so the feature adds an optional `target` field, in test code only. It is
the first slice of the gap; the other slices are follow-on candidates in
[risks-and-gaps.md](risks-and-gaps.md) → "Follow-on candidates".

**Expect this feature to record defects, not health.** Its prototype already did: the sweep of
`lazy.nvim` reports LuaCATS parse errors on valid LuaLS syntax, false-positive type diagnostics and
require mis-resolution (risks-and-gaps DR-05, DR-06, DR-12). The baseline records them as the floor,
and each defect class is filed (MAINT-39-05).

## Scope

### In Scope
- A `lazynvim` entry in `tooling/corpus/corpus.json`, pinned to the `lazy.nvim` `v11.17.5` tag tip,
  with the `LUAJIT:2.1` target and the `neovim` library.
- An optional manifest field `target`, read by `CorpusManifest` and pinned by `CorpusTarget`, with
  routine-suite tests.
- `LuaCorpusSweepTest.testLazynvimCorpus` and its baseline `src/test/resources/corpus/lazynvim.baseline`,
  recorded and gated in the full `'*Corpus*'` invocation shape like every other member.
- Filing the defect classes the first record surfaces.
- Correcting the statements this feature makes false, and documenting the member-name rule and the
  `target` field (design §2.4).

### Out of Scope
- Fixing any defect the sweep finds, including [[BUG-541]]'s zerobrane flicker.
- Further Neovim plugins, the `LuaCATS/*` definition repositories, `lua-language-server`'s trees,
  new LuaCATS-specific sweep metrics, and a guard that every manifest member is swept — all
  follow-on candidates.
- Any change to `CorpusSweep`, `CorpusMetrics`, `CorpusBaseline`, `CorpusGuards`, `fetch-corpus.py`
  or the CI workflow, and any product code.

## Functional Requirements

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :---: | :--- |
| MAINT-39-01 | **A pinned, natively annotated member** | M | Full | `corpus.json` carries the `lazynvim` entry of design §2.1, and `fetch-corpus.py` provisions it and its library. |
| MAINT-39-02 | **Swept and ratcheted in the full shape** | M | Full | `LuaCorpusSweepTest.testLazynvimCorpus` sweeps the member through the existing `sweepAndRatchet`, against a baseline recorded with the full `'*Corpus*'` filter. |
| MAINT-39-03 | **No other member moves** | M | Full | Adding the member leaves every committed corpus and torture baseline byte-identical in every full-shape record, apart from [[BUG-541]]'s known zerobrane flicker (design §3.1). |
| MAINT-39-04 | **The member's gate is deterministic** | M | Full | Repeated full-shape records produce a byte-identical `lazynvim.baseline`, so the ratchet cannot flake. |
| MAINT-39-05 | **Findings are filed** | S | Full | Each defect class design §3.2 marks **file** has a BUG report and a roadmap row; the other classes there are recorded as covered, genuine or accepted. |
| MAINT-39-06 | **No statement this feature falsifies survives** | S | Full | The statements of design §2.4 are corrected, and the README states the member-name rule and the `target` field. |
| MAINT-39-07 | **Further annotated sources** | W | Future Work | The follow-on candidates. |
| MAINT-39-08 | **A member's target comes from the manifest** | M | Full | An entry's optional `target` (`"<LuaPlatform>:<version label>"`) is the target the sweep pins; an absent one keeps the Standard target of `luaLevel`; an unregistered target, or one whose implied level is not `luaLevel`, is refused (design §2.5). |

## Detailed Specifications

### MAINT-39-01
The entry is fixed field by field in design §2.1, including the rule that the member name is
lowercase letters only.

### MAINT-39-02
The sweep enables the same inspections and runs the same metrics as every other member; nothing in
the sweep is specific to annotations. The annotated path is reached because the member's code is
annotated (DR-03) and highlighting it drives the type engine and the LuaCATS parser (DR-04, DR-05).

### MAINT-39-03
Checked after every full-shape record of Phase 1 (design §3.1 steps 3 and 4). The torture files
count: they are recorded by the same command. The one tolerated difference is [[BUG-541]]'s, fixed
literally in design §3.1; it is never committed. Evidence: DR-08, DR-12.

### MAINT-39-04
Measured on the real configuration by DR-12. Phase 1 re-checks it: every record after the first is
compared with the first (design §3.1 step 4).

### MAINT-39-05
The defect classes and their filing decisions are design §3.2's table. Every `parseErrorFile=`
line of the committed `lazynvim.baseline` names a site DR-05 lists, so no new class was found. The
rows marked **file** are filed as:

| Design §3.2 class | BUG |
| :-- | :-- |
| Optional `name?:` in a table-literal type or `fun` parameter | [[BUG-542]] |
| Numeric key `[1]` | [[BUG-543]] |
| Optional `fun` return `:boolean?` inside a table type | [[BUG-544]] |
| Parenthesised return list `fun():(A?, B?)` | [[BUG-545]] |
| `async fun(…)` | [[BUG-546]] |
| Unnamed `fun` parameters `fun(LazyPlugin)` | [[BUG-547]] |
| Spaced string-literal union `"a" \| "b"` | [[BUG-548]] (any string literal under `@field`) |
| `@see \|text\|` | [[BUG-549]] |
| A string argument of a call chained on `require(…)` counted as a require | [[BUG-550]] |
| `loadstring` reported deprecated on Lua 5.1 and LuaJIT 2.1 | [[BUG-551]] |
| An explicit `nil` for an optional `---@param mode? string` not assignable | [[BUG-552]] |
| A string-valued operand typed `{ ... }` reported by `LuaSuspiciousConcatenation` | [[BUG-553]] |

### MAINT-39-06
The statements are listed in design §2.4.

### MAINT-39-08
The parse and pin rules are design §2.5. The target is not written into the baseline: changing it
moves the member's floors, which the ratchet reports, and the changing commit re-records.

## Test Cases

Every builder run below follows implementation-plan → Gates. "Gate" is the corpus gate command,
"record" the record command and "routine" the routine suite defined there.

| # | Requirement | Given (input) | When (action) | Then (expected) | Mutation that turns it red |
|---|---|---|---|---|---|
| 1 | 01 | The design §2.1 entry in `corpus.json` | `python3 tooling/corpus/fetch-corpus.py` | Exit 0; `test/corpus/.definitions/neovim-779c36969e31080503be2dfccbcc3f999e289606.tar.gz` exists; `test/corpus/lazynvim/.corpus-sha` reads `85c7ff3711b730b4030d03144f6db6375044ae82`; `find test/corpus/lazynvim/lua -name '*.lua' \| wc -l` prints `54` | Change the entry's `commit` to `85c7ff3711b730b4030d03144f6db6375044ae83`: the fetch exits `1` with `not our ref` (executed, DR-02) |
| 2 | 01, 02 | Tree after Phase 1, `.corpus-sha` holding the pin | Edit the entry's `commit` to any other 40-hex value, without refetching; run the gate | `BUILD FAILED`; the `LuaCorpusSweepTest` XML has `Corpus 'lazynvim' is at the wrong commit` | Remove the `CorpusGuards.assertCorpusFetched` call from `sweepAndRatchet`: the XML carries `Baseline was recorded against a different corpus commit` instead, so the expected message is absent |
| 3 | 02 | Tree after Phase 1 | Run the gate | `BUILD SUCCESSFUL`; the XML has `<testcase name="testLazynvimCorpus"` and a `[corpus:lazynvim] files=54 ` line; the design §3.1 step 5 `IMPROVED` filter prints nothing | Delete `testLazynvimCorpus`: the `grep` for its `<testcase` prints nothing. Raise the `inspection.LuaTypeAssignability=` value in `lazynvim.baseline` by 1: the filter prints that key's `IMPROVED` line |
| 4 | 02 | Tree after Phase 1 | Lower the value of the `inspection.LuaTypeAssignability=` line of `lazynvim.baseline` by 1; run the gate | `BUILD FAILED`; the XML failure message contains `inspection.LuaTypeAssignability: baseline` | Delete the `CorpusGuards.assertRatchet` call from `sweepAndRatchet`: `BUILD SUCCESSFUL` |
| 5 | 01 | The fetched checkout | `grep -rlE '^\s*---\s*@' test/corpus/lazynvim/lua --include='*.lua' \| wc -l` | `50` (DR-03) | Point the entry's `url` and `commit` at penlight's (`corpus.json`) and refetch: `test/corpus/penlight/lua` prints `0` today, so the count reads `0` |
| 6 | 03 | Record 1 (design §3.1 step 2), copied back | Design §3.1 step 3 | `git status --porcelain -- src/test/resources/corpus/` prints `?? src/test/resources/corpus/lazynvim.baseline`, plus ` M src/test/resources/corpus/zerobrane.baseline` only when that file's diff is exactly the BUG-541 text (then restored from `HEAD`) | Remove `LuaSuspiciousConcatenationInspection()` from `LuaCorpusSweepTest.setUp`'s `enableInspections` before the record: every member whose baseline has an `inspection.LuaSuspiciousConcatenation=` line is listed ` M`, and zerobrane's diff is not the BUG-541 text |
| 7 | 03, 04 | The tree after record 1 | Design §3.1 step 4: records 2–8, each copied to scratch and compared | The loop prints nothing but `zerobrane.baseline` `differ` lines for copies that differ only by BUG-541 | Before record 2's comparison, raise the `inspection.LuaTypeAssignability=` value in `$SCR/rec/2/lazynvim.baseline` by 1: the loop prints a `differ` line. The detection power for real variation is design §3.1 "Sample size" |
| 8 | 05 | The recorded baseline | Design §3.2 | Every row of §3.2's table marked "file" has a filed BUG with a roadmap row; every `parseErrorFile=` line maps to a row | Untestable by automation: a filing obligation. The Step 9 review of the implementation reads the BUG list against §3.2 |
| 9 | 06 | Tree after Phase 2 | (a) `grep -n '0 of the 734\|their 734 files\|across all 734\|stay blocked until\|as all four below\|every currently-pinned\|Standard target of its manifest level\|must be measured against .runtime/standard' build.gradle.kts tooling/corpus/corpus.json src/test/kotlin/net/internetisalie/lunar/corpus/*.kt src/test/kotlin/net/internetisalie/lunar/lang/types/LuaColonCallInferenceWithdrawalTest.kt`; (b) `grep -c 'lowercase ASCII letters only' tooling/corpus/README.md`; (c) `grep -c '"  target     optional' tooling/corpus/corpus.json` | (a) prints nothing; (b) and (c) each print `1` | Leave any one listed statement in place: (a) prints its line (each is present at `ca108e35`). Leave the README or the `$comment` unedited: (b) or (c) prints `0` |
| 10 | 08 | `BaselineRatchetTest.manifestTargetParses` (design §2.5) | Routine | Passes: `plain`'s `target` is null, `jit`'s `getLibraryRootPath()` is `runtime/luajit/luajit-2.1` | Drop `.let { parsed -> parsed.copy(target = entry.target(parsed)) }` from `parseEntry`: `jit`'s target is null, so the second assertion fails (executed, DR-13) |
| 11 | 08 | `BaselineRatchetTest.manifestTargetMustImplyTheLuaLevel` | Routine | Passes: `load` throws with `implies LUA51, not LUA54` | Delete the `require(target.getImplicitLanguageLevel() == parsed.luaLevel)` call: `load` succeeds and the test fails (executed, DR-13) |
| 12 | 08 | `BaselineRatchetTest.manifestTargetMustBeRegistered` | Routine | Passes: `load` throws with `unknown target 'LUAJIT:9.9'` | Replace the `requireNotNull(…)` with `PlatformVersionRegistry.findVersion(…) ?: PlatformVersionRegistry.getVersions(platform).last()`: `load` succeeds and the test fails (executed, DR-13) |
| 13 | 08 | `MemberTargetPinTest.testManifestTargetPinsTheLuaJitRuntime` | Routine | Passes: stdlib root `luajit-2.1`; undeclared names of `print(jit, bit, setfenv, rawlen)` are `[rawlen]` | Make `CorpusTarget.memberTarget` return `standardTarget(entry.luaLevel)`: the root is `lua-5.1` and the assertion fails (executed, DR-13) |

## Acceptance Criteria
- [x] TC 10–13 pass in the routine suite (Phase 1).
- [x] TC 1–7 pass on the Phase 1 tree, TC 2 and 4 restored afterwards.
- [x] TC 8 and 9 hold (Phase 2).
- [ ] The PR's CI `corpus` job is green.

## Non-Functional Requirements
- **Sweep cost.** The member adds under a minute to a full-shape run on the libvirt builder (DR-12).
  `elapsedMs` stays advisory and ungated, as for every member.
- **No product code.** Every Kotlin change is test code; nothing under `src/main/` changes.
- **Licensing.** The checkout and the `neovim` archive are fetched at test time into the out-of-repo
  `test/` tree and never committed or shipped (DR-01; the archive's licence is TARGET-11 DR-01's).
- **No human-verification checklist.** The feature changes no UI and no user-visible behaviour, and
  every acceptance check above is a command with a literal expected output, so the Step 8 checklist
  has nothing to hold.

## Dependencies
- [[BUG-501]] (done): the member's counts are deterministic only with its fix (DR-07, DR-11).
- [[TARGET-11]] (done): the `neovim` catalog library the entry pins.
- [[TARGET-12]] (done): the `runtime/luajit/luajit-2.1` root the `LUAJIT:2.1` target loads.
- [[BUG-515]] (done): `CorpusTarget`, which this feature extends to non-Standard targets.
- [[BUG-506]] (done): the corpus attribution classifier counts dotted-function roots such as
  `vim.*`'s as library globals, which the `neovim` pin needs.
- [[TYPE-12]] (done), [[MAINT-37]] (record command, definition libraries), [[MAINT-33]] (the sweep).
- Interacts with [[BUG-500]], [[BUG-502]], [[BUG-508]] and [[BUG-541]]: each fix moves this
  member's or zerobrane's counts (design §6).

## See Also
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
