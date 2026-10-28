---
id: "MAINT-39-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-39"
folders:
  - "[[features/maint/39-luacats-annotated-corpus/requirements|requirements]]"
---

# MAINT-39: Risks & Gaps

## The split

What the gap statement would ship, and what this feature keeps:

| Part | Shippable alone? | Decision |
| :-- | :-- | :-- |
| One natively annotated application project under the ratchet | yes | **MAINT-39** |
| The manifest `target` field | no — no member needs it without this one | **MAINT-39** (Must) |
| Filing what the first record finds | no — needs the record | **MAINT-39** (Should) |
| Correcting the statements the new member falsifies | no — true until the member lands | **MAINT-39** (Should) |
| A guard that every manifest member is swept and baselined | yes — changes no baseline | follow-on candidate |
| Fixing what the first record finds | yes — each its own reproduction | separate BUGs |
| Everything below | yes | follow-on candidates |

### Follow-on candidates
No IDs are minted here. Each candidate below is tracked only in this list, which MAINT-39-07 (W)
points to; none has a roadmap row, and minting one is a planning decision outside this feature.
Defects the first record finds are tracked as BUGs with roadmap rows (design §3.2).
- **More Neovim plugins** — `telescope.nvim`, `plenary.nvim`, `nvim-lspconfig` as members; DR-03 has
  their densities.
- **`LuaCATS/*` definition repositories as members** — a declaration-only (`---@meta`) shape the
  catalog already pins as libraries but no sweep highlights.
- **`lua-language-server`'s own trees as a member** — shape and preprocessing needs not measured.
- **LuaCATS-aware sweep metrics** — split `parseErrors` by Lua vs LuaCATS origin, and stop LuaCATS
  errors counting as oracle false rejects (DR-05); count unresolved LuaCATS type names.
- **Triage the type-inspection floors** — classify `lazynvim`'s `LuaTypeAssignability` and
  `LuaReturnTypeMismatch` hits into false positives and real findings.
- **A manifest-membership guard** — a routine-suite test that fails when a `corpus.json` member has
  no `@Test` sweep method in `LuaCorpusSweepTest` or no baseline file; today nothing notices a member
  listed but never swept.

## Premises

Load-bearing premises, each marked **read** (settled by reading) or **runtime** (settled only by
running), and the DR that settles it. Every runtime premise in this table has an executed DR.

| Premise | Kind | DR | Verdict |
| :-- | :-- | :-- | :-- |
| The source's licence permits pinning and fetching it | read | DR-01 | confirmed |
| `fetch-corpus.py` provisions the pinned SHA and the `neovim` archive unchanged | runtime | DR-02, DR-12 | confirmed |
| The source is natively and densely annotated, and its annotations reach LuaCATS PSI | runtime | DR-03 | confirmed |
| Real annotated files sweep at tolerable cost, with the library and target | runtime | DR-04, DR-12 | confirmed |
| The files parse cleanly | runtime | DR-05, DR-12 | **refuted** — LuaCATS parse defects; recorded as the floor |
| The member's `require`s resolve correctly | runtime | DR-06, DR-12 | **partly refuted** — BUG-500 and a chained-call defect |
| The member's counts are identical across identical full-shape runs | runtime | DR-07, DR-11, DR-12 | **refuted** before [[BUG-501]]'s fix; confirmed after it, on the probe and the real configuration |
| Adding the member leaves the other baselines unchanged | runtime | DR-08, DR-11, DR-12 | confirmed apart from [[BUG-541]]'s zerobrane flicker, which also occurs without the member |
| The BUG-541 rule tolerates the flicker and nothing else | runtime | DR-12 | confirmed on the four flicker records and a negative control |
| A manifest `target` reaches the pin, and a bad one is refused | runtime | DR-13 | confirmed, each check with its mutation |
| The library and the target change only definition-dependent counts | runtime | DR-12 | confirmed, with the new false positives of design §3.2 |
| CI's diagnostics grep matches the member's name | runtime | DR-09 | confirmed for `lazynvim` |
| The member-alone attribution arm is a usable determinism instrument | runtime | DR-10 | confirmed; the plan does not use it (design §2.1) |

### Premises examined (assumed vs chosen)
- **Same ratchet, same shape as every member** — chosen (design §9); its cost, cross-member state,
  is measured by DR-08, DR-11 and DR-12 rather than assumed away.
- **LuaJIT 2.1 target** — chosen: Neovim embeds LuaJIT 2.1. The manifest could express only
  Standard targets; the feature removes that constraint with a test-only field (design §2.5) rather
  than sweeping on Standard 5.1, which leaves `jit` undeclared (DR-12 arm C).
- **The `neovim` library** — chosen, since TARGET-11 ships it; without it `vim` is undeclared 530
  times (DR-12 arm B), a floor about missing definitions rather than the plugin.
- **The target is not identity-checked in the baseline** — chosen (design §9).
- **Tolerating BUG-541 instead of depending on it** — chosen: the flicker is zerobrane's and occurs
  without this member (DR-12 arm A); depending on its fix would block an independent member on
  another member's defect. The tolerance is one literal shape, never committed (design §3.1).
- **Determinism of gated counts** — assumed by the ratchet since MAINT-33; false for this member
  before [[BUG-501]]'s fix (DR-07), measured to hold after it (DR-11, DR-12), and re-checked by every
  Phase 1 record (Risk 1.1).

## Critical Risks

### Risk 1.1: The member's gated counts vary between identical runs
- **Impact**: a ratchet that fails at random, or a baseline recorded below the maximum so that every
  later run above it fails. Either ends with the sweep disabled.
- **Likelihood**: low — no variation in DR-11's or DR-12's records; a residual below the rates
  design §3.1 bounds is not excluded.
- **Mitigation**: design §3.1 step 4.

### Risk 1.2: The member perturbs another member's counts
- **Impact**: admitting it would move a committed baseline it does not own.
- **Likelihood**: low — nothing but BUG-541's flicker moved in DR-11 or DR-12, and the flicker occurs
  without the member. Whether the member changes the flicker's rate is not established (DR-08).
- **Mitigation**: design §3.1 steps 3 and 4 stop on any other movement.

### Risk 1.3: The CI host disagrees with the builder
- **Impact**: the PR's `corpus` job red on a baseline the builder recorded.
- **Likelihood**: low; not measured for this member before the PR.
- **Mitigation**: the PR gate (implementation-plan) stops on it with CI's diagnostics.

### Risk 1.4: BUG-541's flicker reaches a committed baseline
- **Impact**: a committed `zerobrane.baseline` at 17 makes every 19 run fail as a regression.
- **Likelihood**: high without a rule — half of DR-12's records read 17.
- **Mitigation**: design §3.1 step 3 restores the file from `HEAD`; the repeat never copies into the
  tree.

## Design Gaps
None open. Each premise above is settled by its DR.

## Pre-Implementation De-risking Tasks

DR-01 to DR-10 ran at `ef0f0227`, before BUG-501's fix; each row says whether it ran locally or on
the libvirt builder. Raw output is under `$HOME/.cache/claude-scratch/lunar/maint-39/plan/` (`$P`
below). Their builder probes used the probe root `/home/builder/maint39`, holding the pinned checkout
as `test/corpus/lazy-nvim` and `test/corpus/lazynvim` (identical copies), a two-entry
`tooling/corpus/corpus.json`, and symlinks to the builder's `test/luac` and
`tooling/corpus/luac.json`; they called the unmodified `CorpusSweep.run` and `sweepAndRatchet`'s
steps with that root, and swept on Standard 5.1 without a library. DR-11 is BUG-501's executed
result, re-read here. DR-12 and DR-13 ran on the real repository root at `4a4f64c5` (`main` at
`ca108e35` merged into this branch) with design §2.1, §2.2 and §2.5 applied as temporary edits
(`$R3/probe-config.diff`, `$R3` = `$HOME/.cache/claude-scratch/lunar/maint-39/r3`), restored
afterwards.

| ID | Action and observed output | Verdict |
| :-- | :-- | :-- |
| MAINT-39-00-DR-01 | **Licence (read).** `lazy.nvim` `LICENSE` first line: `Apache License` / `Version 2.0, January 2004`; no `NOTICE` file in the checkout. The checkout is fetched into the out-of-repo `test/` tree and never committed or shipped; the baseline holds counts, repository paths and symbol names. | done — pinning permitted |
| MAINT-39-00-DR-02 | **Fetch.** `git ls-remote --tags https://github.com/folke/lazy.nvim.git v11.17.5 'v11.17.5^{}'` (2026-10-15) prints the single line `85c7ff3711b730b4030d03144f6db6375044ae82	refs/tags/v11.17.5`: no peeled line, so a lightweight tag on that commit. A copy of `fetch-corpus.py` and `definition_pins.py` with a one-entry manifest, `LUNAR_CORPUS_ROOT=$P/fetchprobe/test/corpus python3 tooling/corpus/fetch-corpus.py` (`$P/dr-fetch.log`): `fetching lazy-nvim @ 85c7ff37`, `lazy-nvim → 65 .lua files, 2.9M`, `exit=0`. **TC 1's mutation**, the same with the entry's `commit` ending `…ae83` (copies under `$HOME/.cache/claude-scratch/lunar/maint-39/r2-planner/fetchneg/`): `fatal: remote error: upload-pack: not our ref 85c7ff3711b730b4030d03144f6db6375044ae83`, `CalledProcessError` from `fetch_one` (`fetch-corpus.py:58`), `exit=1`. | done — confirmed |
| MAINT-39-00-DR-03 | **Density and PSI.** `python3 -I $P/density.py …` (`$P/density.out`) over each candidate's `lua/` at its latest tag: `lazy.nvim` `files=54 annotatedFiles=50`, tags `param=346 field=270 type=128 class=70 return=61 … alias=24 cast=15 generic=10 overload=4`, `classInheritance=19`; adding `tests/` gives `files=64` and no new tag kind. `plenary.nvim` `files=78 annotatedFiles=26 classInheritance=0`; `telescope.nvim` `files=71 annotatedFiles=30`; `nvim-lspconfig` `lua/` `files=439 annotatedFiles=80`, `lsp/` `418/418` but `type=445 brief=417` and no colon methods. TC 5's command, `grep -rlE '^\s*---\s*@' <dir> --include='*.lua' \| wc -l`, prints `50` on the checkout's `lua/` and `0` on `test/corpus/penlight/lua`. PSI: probe `testProbeLazyPerFile` (`$P/results/run2/perfile.txt`) counts 533 `LuaCatsLazyCommentImpl` elements over the 54 files; control files: a well-formed `---@class`/`---@field` block gives `[psiErrors, catsErrors, catsComments] = [0, 0, 1]`, a malformed one `[1, 1, 1]`. | done — `lazy.nvim` chosen |
| MAINT-39-00-DR-04 | **Cost after TYPE-12.** Member-alone sweep (`testProbeLazyAlone`, runs 1 and 3): `elapsedMs=37968`, `37881`; Gradle `BUILD SUCCESSFUL in 1m 4s`. In the full `'*Corpus*'` record (runs 7–10): `[corpus:lazynvim] … elapsedMs=29684`, `30077`, `28687`, `27531`; the whole record `6m 29s`, `6m 14s`, `6m 26s`, `6m 14s` against `5m 52s`, `5m 54s` without the member (controls). Per file (run 2, cold, sorted order): largest `forFile` `1572 ms` (`lazy/async.lua`, the first file, paying for its dependencies), largest highlight `1874 ms` (`lazy/view/render.lua`, `closure=32421`); 54 files in `28908 ms`. Synthetic control, same JVM: annotated `n=80 forFileMs=457 closure=2203`, `n=160 forFileMs=134 closure=4363`; unannotated `n=80 10 ms closure=570`, `n=160 12 ms closure=1130` — closure doubles with *n*. TYPE-12's own figure for annotated `n=160` (about 50 ms) is not reproduced in this JVM (134 ms, measured once, after the `n=80` case absorbed warm-up); its linear growth is. | done — confirmed |
| MAINT-39-00-DR-05 | **Parse.** Run 1's rendered metrics (`$P/results/run1/render.txt`): `parseErrors=17`, `oracleDisagreements=14`, `oracleFalseAccepts=0`, `lexerRoundTripFailures=0`, `unmergedTokens=0`, no `crash.` line, `inspection.LuaUndeclaredVariable=537` (`symbol…vim=530`, `…jit=7`). Run 2: `catsErrors` equals `psiErrors` in every file, so every parse error sits inside a LuaCATS comment, and each of the 14 files with one is an oracle false reject. Classes, read from each `parseErrorFile=` line's source: **optional `name?:` in a table-literal type or `fun` parameter** — `core/plugin.lua:16`, `pkg/rockspec.lua:13`, `view/float.lua:8`, `view/float.lua:28`, `view/init.lua:11`, `view/render.lua:19`; **numeric key `[1]`** — `core/handler/keys.lua:13`, `manage/runner.lua:6`, `manage/semver.lua:4`, `types.lua:52`, `view/render.lua:630`; **optional `fun` return `:boolean?` inside a table type** — `view/sections.lua:13`; **parenthesised return list `fun():(A?, B?)`** — `core/plugin.lua:149`; **`async fun(…)`** — `manage/task/init.lua:12`; **unnamed `fun` parameters `fun(LazyPlugin)`** — `manage/task/plugin.lua:65`; **spaced string-literal union `"a" \| "b"`** — `pkg/init.lua:11`; **`@see \|text\|`** — `core/cache.lua:228` (paths under `lua/lazy/`). | done — findings, recorded as the floor |
| MAINT-39-00-DR-06 | **Requires** (run 2, `$P/results/run2/requires.txt`; one line per recognised require and its target). Of 202 recognised: 182 resolve into the checkout under `moduleRoot` `lua`; 15 resolve to `/src/lua/lazy/manage/init.lua`, the first `init.lua` in the fixture — the absent modules `ffi`, `luassert`, `tokyonight` (BUG-500) and strings that are arguments of a call chained on `require(…)`, e.g. `"LazyDone"` from `require("lazy.stats").track("LazyDone")` (`lua/lazy/init.lua:116`); 5 unresolved — `mini.test` twice, `busted.runner`, and two `warn(…)` messages from `require("lazy.core.util").warn(…)` (`manage/reloader.lua:74`, `:81`). | done — two defects: BUG-500, and chained-call arguments recognised as requires |
| MAINT-39-00-DR-07 | **Determinism.** `inspection.LuaTypeAssignability` over identical runs (run 2 is DR-04's per-file probe, not a sweep): member-alone runs 1, 3–6 `447`, `445`, `443`, `445`, `445`; full-shape records 7–10 `447`, `447`, `447`, `439`; member-alone `pinned` arms T1–T4 (DR-10) `442`, `447`, `443`, `447`. Over these thirteen runs `447` occurs six times, `445` three, `443` twice, `439` and `442` once each. Every other rendered line is identical across runs 1–9; run 10, T1 and T3 also have `inspection.LuaReturnTypeMismatch=54` against `56` elsewhere. Runs 4–6 carried a temporary `println` of each type-inspection hit in `CorpusSweep.accumulateHits` (`$P/results/run{4,5,6}/hits.txt`): runs 5 and 6 add, over run 4, the pair `lua/lazy/view/render.lua LuaTypeAssignability @15167-15241` / `@15171-15180 Too many arguments: expected at most 2, got 3`, on `self:markdown(msg.msg, hls[msg.level] or "LazyTaskOutput", { indent = 6 })`. `M:markdown(str, hl, opts)` (`view/render.lua:482`) is annotated `---@param opts? {indent?: number, …}`, which uses DR-05's optional-key construct, although no parse error is reported on that line (the file's are at `:19` and `:630`). Hypothesis, not established: the engine sometimes reads a two-parameter signature for `markdown` beside the three-parameter Lua one, and which wins varies per JVM (compare BUG-498's order-dependent class merge). | done — **refuted** on this engine; [[BUG-501]] was filed from this row and its fix measured in DR-11 |
| MAINT-39-00-DR-08 | **Other members.** After each full-shape record, SHA-256 of every re-recorded corpus and torture file against the committed one (`$P/collect-full.sh`, `$P/results/run{7,8,9,10}`): runs 7, 9, 10 all equal; run 8 differs only in `zerobrane.baseline`: `inspection.LuaSuspiciousConcatenation=21` → `19`, `symbol.LuaSuspiciousConcatenation.nil=4` → `2`. Control, the unmodified tree twice (`$P/control-records.out`): all equal. The member runs before `zerobrane`. After BUG-501's fix the movement did not recur in any of DR-11's sixteen records. On main, [[BUG-515]] re-recorded zerobrane at `19`, and [[BUG-541]] (four records, members pinned, without this member) read `19, 17, 19, 19`, the difference being two `nil`-operand hits (`editor/menu_project.lua:96`, `:136`). With this member, DR-12's records 2, 5, 7 and 8 and arm C read `17`, each differing from the committed file in exactly `inspection.LuaSuspiciousConcatenation=19` → `17` and `symbol.LuaSuspiciousConcatenation.nil=3` → `1`. Run 8 here has the same shape (one inspection count and its `nil` symbol, both lower by 2). | done — the movement is BUG-541's flicker; nothing else moved in DR-11 or DR-12. Not established: whether this member changes the flicker's rate (one in four without it, four in eight with it). Design §3.1's BUG-541 rule tolerates exactly this shape |
| MAINT-39-00-DR-09 | **CI grep.** `echo "[corpus:lazynvim] files=54 parseErrors=0" \| grep -o "\[corpus:[a-z-]*\][^<]*"` prints the line (rc 0); the same with `lazy.nvim` prints nothing (rc 1). | done — name rule adopted (design §2.1) |
| MAINT-39-00-DR-10 | **The instrument.** Probe C is `sweepAndRatchet`'s attribution branch with the probe root; four member-alone `pinned` arms (`test --rerun --no-build-cache --tests 'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.testLazynvimCorpus' -PwithCorpus -PcorpusAttribution=pinned`, each `BUILD SUCCESSFUL` in 1m 3s–1m 7s), tallies copied back to `$P/results/tally/` (`$P/tally-probe.out`). Three distinct SHA-256 over the four tallies. `diff` of differing pairs shows only `inspection.LuaReturnTypeMismatch`, `inspection.LuaTypeAssignability` and `allsymbol.LuaTypeAssignability._`; `settingsBroadcasts=0`, `rootsTicks=0`, `physicalPsiChanges=0` in every tally. | done — the tally varies exactly where DR-07's variation is; unused by the plan, which samples the full shape |
| MAINT-39-00-DR-11 | **Determinism after [[BUG-501]]'s fix** (BUG-501 bug-report → Resolution; libvirt builder, `3ebb3d96` plus the fix diff committed as `cb05af0f`; logs `$B` = `$HOME/.cache/claude-scratch/lunar/bug-501/fix/`). The probe A and probe B methods of this file's DR rows, unchanged. **Member-alone**, eight separate builder runs (`$B/alone/`): `sha256sum $B/alone/run*-render.txt` gives one digest, `a0987fdc80f2c2811b1a5619857ca302804eb1214025c8644938d7ffe6a6e758`, in all eight. **Full shape**, sixteen separate runs of `test --rerun --no-build-cache -PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'` (`$B/sweep-full.sh 1 16`, `$B/full/`), each `BUILD SUCCESSFUL`: `sha256sum $B/full/run*/lazynvim-render.txt \| cut -c1-64 \| sort \| uniq -c` prints `16 a0987fdc…e758`, the member-alone digest; every `$B/full/run<i>-collect.txt` has six `EQUAL` lines (`luacheck`, `luarocks`, `penlight`, `zerobrane` and `torture-fuzzing-lua` baselines, `torture-fuzzing-lua.expected-accepts`, each re-recorded against the file committed at `3ebb3d96`) and `grep -l DIFFERENT` over them prints nothing. The block reads `inspection.LuaTypeAssignability=447`, `inspection.LuaReturnTypeMismatch=56`, `parseErrors=17`, `requires=202`. Before the fix, DR-07's full-shape records 7–9 rendered this same digest and record 10 rendered `c4745a9b…` (`sha256sum $P/results/run{7,8,9,10}/lazynvim-render.txt`). | done — deterministic in both shapes on the probe configuration; no other member moved. DR-12 measures the real configuration |
| MAINT-39-00-DR-12 | **The real configuration, eight full-shape records** (`$R3/record.sh 1` … `8`: `test --rerun --no-build-cache --stacktrace -PwithCorpus -PrecordCorpusBaseline --tests '*Corpus*'` on the libvirt builder, baselines and XML copied back to `$R3/rec/<i>/`, summary `$R3/analyse.sh`). `fetch-corpus.py` first (`$R3/fetch.log`): `fetching lazynvim @ 85c7ff37`, `lazynvim → 65 .lua files, 2.9M`, `neovim already at 779c3696 — skipping`, ending `Corpus ready at …/test/corpus` (the fetch ran locally, not on the builder); the counts come from commands on that checkout, not from the log: `find test/corpus/lazynvim/lua -name '*.lua' \| wc -l` gives 54 and TC 5's `grep` gives 50. Every record `BUILD SUCCESSFUL`, `tests="5" failures="0" errors="0"`, testcase order luacheck, luarocks, penlight, lazynvim, zerobrane. **Determinism**: `sha256sum $R3/rec/*/baselines/lazynvim.baseline \| cut -c1-64 \| sort \| uniq -c` prints `8 325cec090eba5f99ad11b688a75840d9a2a771defe9d4f7d3366ccf037c8b9f2`. **Other members**: `luacheck`, `luarocks`, `penlight` and the torture baseline and expected-accepts are `cmp`-equal to the committed files in all eight; `zerobrane.baseline` is equal in records 1, 3, 4, 6 and in 2, 5, 7, 8 differs by `diff` exactly `10c10 < inspection.LuaSuspiciousConcatenation=19 --- > …=17` and `35c35 < symbol.LuaSuspiciousConcatenation.nil=3 --- > …=1`. Design §3.1's BUG-541 rule prints nothing and `grep -cx` prints `1` on each of those four; on a negative control (record 2's file with `inspection.LuaUnusedLocal` set to `9999`) it prints `< inspection.LuaUnusedLocal=7` / `> inspection.LuaUnusedLocal=9999`. No record printed `IMPROVED` or a regression (record mode does not compare). **Cost**: `[corpus:lazynvim] … elapsedMs=` `43083`, `41229`, `39136`, `42202`, `40026`, `42674`, `42414`, `40610`; whole record `5m 52s`, `5m 48s`, `5m 40s`, `5m 35s`, `5m 33s`, `5m 38s`, `5m 39s`, `5m 36s`; arm A below, without the member, `4m 54s`. **Attribution arms**, one record each on the same tree with a temporary `println` of each `lua/lazy/` hit in `CorpusSweep.accumulateHits` (`$R3/arm{B,C,D}-hits.txt`): arm D, the configuration, renders `325cec09…` again, so the `println` moves nothing (`elapsedMs=37455`). Arm B, Standard 5.1 without a library, against DR-11's block: adds `inspection.LuaDeprecatedApi=1` (`loadstring` at `lua/lazy/core/plugin.lua:308`, the `---@deprecated` `loadstring` of `runtime/standard/lua-5.1/builtin.lua:194-198`, reached since BUG-515 pins 5.1) and turns `ballast.unclaimed` into `ballast.claimed` for `.editorconfig`, `.gitignore`, `md`, `yaml`, `yml` (the platform's file types, as in the other members' committed baselines); `requires=202 unresolvedRequires=5`, `parseErrors=17` and the seventeen `parseErrorFile=` lines unchanged; `elapsedMs=33386`. Arm C, Standard 5.1 with `neovim`, against B: `library.neovim=779c3696…`, `LuaDeprecatedApi` `1` → `7`, `LuaUndeclaredVariable` `537` → `7`, `symbol.LuaUndeclaredVariable.vim=530` gone; `elapsedMs=40860`. The configuration (D) against C: `LuaTypeAssignability` `447` → `448`, the line `inspection.LuaUndeclaredVariable=7` and `symbol.LuaUndeclaredVariable.jit=7` gone. The added type hit is `lua/lazy/pkg/rockspec.lua @5555-5558 nil value is not assignable to string`, the `nil` in `loadfile(file, nil, ret)` (`:228`), against `runtime/luajit/luajit-2.1/builtin.lua:190` `---@param mode? string`. D's seven `LuaDeprecatedApi` hits: `loadstring` (above); `tbl_islist` `minit.lua@71`, `termopen` `util.lua@3340`, `nvim_get_hl_by_name` `view/float.lua@3877` (genuinely deprecated, TARGET-11 DR-10); `clear` `manage/init.lua@1061` and `@4660`, `save` `docs.lua@4319` ([[BUG-508]], TARGET-11 DR-10). Arm A (no `testLazynvimCorpus`): `BUILD SUCCESSFUL`, four testcases, zerobrane at `17` with the same two-line diff. | done — one block in eight records; nothing else moved but BUG-541's flicker, which arm A shows without the member; the library and the target remove the `vim`/`jit` floor and add the false positives of design §3.2 |
| MAINT-39-00-DR-13 | **The `target` field** (design §2.5 and its tests, verbatim; `test --rerun --no-build-cache --tests '*BaselineRatchetTest*' --tests '*MemberTargetPinTest*'`, logs and XML `$R3/dr13/`). Correct code: `BUILD SUCCESSFUL`, `MemberTargetPinTest tests="4" failures="0"`, `BaselineRatchetTest tests="49" failures="0"`, each new testcase present. `LuaLanguageLevel.toString()` renders `Lua 5.1` (a variant formatting with it failed with `implies Lua 5.1, not Lua 5.4`), so design §2.5 formats `.name`. **Mutations**, each restored after its run: (b) the `require(… == parsed.luaLevel)` deleted, (c) `memberTarget` returning `standardTarget(entry.luaLevel)` and (d) `requireNotNull` replaced by `?: PlatformVersionRegistry.getVersions(platform).last()`, applied together because each reaches one test only: `BUILD FAILED`, `53 tests completed, 3 failed` — `manifestTargetMustImplyTheLuaLevel` (`got: null`), `manifestTargetMustBeRegistered` (`got: null`), `testManifestTargetPinsTheLuaJitRuntime` (`expected:<lua[jit-2].1> but was:<lua[-5].1>`); `manifestTargetParses` passed. (a) the `.let { parsed -> parsed.copy(target = entry.target(parsed)) }` dropped, alone: `manifestTargetParses` fails at its `jit` assertion (`BaselineRatchetTest.kt:363`), with the two refusal tests, which nothing then throws for. | done — each TC 10–13 check passes and its mutation turns it red. Run filtered by class, not the full suite |

## Test Case Gaps
- TC 8 (findings filed) is a review obligation, not an automated test.
- TC 5 checks the annotation density once, at admission. A later re-pin re-runs it with the new tag's
  count; nothing enforces that.
- TC 7's mutation proves the comparison loop can fail; its power against real nondeterminism is
  design §3.1 "Sample size", not a mutation.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
