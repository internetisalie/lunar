---
id: "TARGET-11-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "TARGET-11"
folders:
  - "[[features/target/11-neovim-definition-library/requirements|requirements]]"
---

# TARGET-11: Risks & Gaps

## The split

| Part | Shippable alone? | Decision |
| :-- | :-- | :-- |
| The `neovim` catalog entry, the `rootPrefix` invariant, their tests | yes | **TARGET-11** |
| Description-index values over 64 KB fail indexing (DR-02) | yes, own reproduction | [[BUG-503]], done (`84fa67b0`) |
| A `@deprecated` dotted function flags its root table (DR-10) | yes, own reproduction | [[BUG-504]], done (`33326f3f`) |
| A name declared `local` is indexed as a project-wide global (DR-11) | yes, own reproduction | [[BUG-509]], done (`a2399c0e`) |
| A `---@class` / `---@alias` local resolves as a value in other files (DR-11) | yes, own reproduction | [[BUG-510]], done (`a1d7226d`) |
| Parse time is exponential in parenthesis nesting; one Neovim file takes 8 s (DR-08) | yes, own reproduction | [[BUG-517]], **gates TARGET-11** (TARGET-11-09) |
| A qualified call resolves to another module's deprecated member (DR-10) | yes | [[BUG-508]], not blocking |
| A name resolves to a declaration of the wrong kind | yes | [[BUG-512]], not blocking; none of DR-10's deprecated hits is its |
| Global completion offers another file's class local (`Iter`, `uv`) as a bare value | yes | [[BUG-511]], not blocking |
| `function a.b.c()` never completes under `a.b.` (DR-04) | yes | [[BUG-505]], not blocking |
| Members of field-held tables never complete (DR-04) | yes | [[BUG-507]], not blocking |
| The attribution classifier misses dotted-function roots (DR-05) | yes, tooling only | [[BUG-506]], a dependency of MAINT-39 |
| Pinning `neovim` for a corpus member and measuring its cost there (DR-08) | yes | [[MAINT-39]] |
| `jit` (DR-09) | yes | TARGET-12 (LuaJIT runtime stubs) |

TARGET-11's one open blocker is BUG-517. TARGET-11 precedes MAINT-39, whose corpus pin consumes
this entry; TARGET-12 is independent of it.

## Premises

| Premise | Kind | DR | Status |
| :-- | :-- | :-- | :-- |
| A pinned archive and subtree carry Neovim's LuaCATS API | runtime | DR-01 | confirmed |
| The catalog's `rootPrefix` can express `runtime/lua` without a schema change | runtime | DR-03, DR-07 | confirmed; one test assertion changes (DR-06) |
| Enabling it declares `vim` and its members on lazy.nvim | runtime | DR-02, DR-03, DR-04 | `vim` and direct members confirmed; most nested namespaces **refuted** (BUG-505, BUG-507) |
| It adds no indexing error, parse error or false positive | runtime | DR-02, DR-03, DR-10 | confirmed on main, except [[BUG-508]]'s residual deprecated-API hits |
| It hides no real error in user code (no false negatives) | runtime | DR-11 | confirmed on main: of the probed candidate names, it declares only `vim` |
| The synthetic-archive tests can fail | runtime | DR-07 | confirmed: every TC 1–6 mutation turns its case red at the assertion |
| The corpus attribution can explain the pin's drop | runtime | DR-05 | **refuted** (BUG-506) |
| Cost is tolerable: library-on highlighting ≤ 1.5× library-off (TARGET-11-09) | runtime | DR-08 | **refuted** on main (1.71×). A Lunar parser defect, [[BUG-517]], accounts for about two-thirds of the sweep gap (DR-08) and gates TARGET-11 |
| `jit` belongs here | read | DR-09 | rejected |

Constraints examined: the `/library` suffix was inherited from LuaCATS addons and is generalised
(DR-06); the whole-repository download follows from the commit-archive source, and the release-asset
alternative is weighed in design §9; the commit-SHA pin is kept because a tag can move.

## Pre-Implementation De-risking Tasks

`$S` = `~/.cache/claude-scratch/lunar/target-11`; every run on the libvirt builder with
`test --rerun --no-build-cache`. Two evidence sets:
- **On main** (`$S/r4/`): HEAD `ebb432c1`, which is main `33326f3f` plus docs and so carries BUG-503,
  BUG-504, BUG-509 and BUG-510. No engine patch: each run's probe edits are its
  `results/<run>/tree.diff`, and the untracked probe classes are described in
  `$S/r4/probe-src/README.txt`. Only `s5-library-pre504` alters engine code, as a mutant. The lazy.nvim
  probe root `/home/builder/target11r4` (`$S/r4/stage.sh`) holds two copies of lazy.nvim `85c7ff37`,
  `lazynvim` without a pin and `lazynvimnv` pinning `neovim`, plus every catalog archive; each arm
  runs alone with `-PwithCorpus --tests '…LuaCorpusSweepTest.testProbeNv{Control,Library}'`.
- **Cost attribution** (`$S/r5/`, DR-08): HEAD `a6d2605c`, the same source plus docs. The engine edits
  in each run's `results/<run>/tree.diff` are counters, plus switches that default off. Each switch
  is on only in the variant named for it. The untracked probe classes are in `$S/r5/probe-src/`.
  Same probe root; `$S/r5/perfile.py` summarises the per-file times.
- **At `cb05af0f`** (`$S/results/`, `$S/r2/`, `$S/r3/`): the remaining evidence named per row. The
  `$S/r2/` and `$S/r3/` runs record their source diff in `results/<run>/tree.diff`.

| ID | Evidence | Verdict |
| :-- | :-- | :-- |
| TARGET-11-00-DR-01 | **Source.** `git ls-remote --tags https://github.com/neovim/neovim.git` (2026-10-15): latest stable `v0.12.6^{}` = `779c36969e31080503be2dfccbcc3f999e289606`. The commit archive downloaded five times between 07:34 and 08:26 local, every time `sha256 912c4e69…29f3`, `13736994` bytes (`$S/dl/`, `$S/dl2/`, `$S/fetch-catalog.out`, `$S/pin-check.out`, `$S/r2/tc7/`). Design §4.1's command, run from the worktree with the entry in place: `128 True True True 0` (`$S/r2/tc7.out`); with the entry's `sha256` changed: `PinError: neovim: no url produced the pinned archive` (`$S/r2/tc7-mutant.out`). `du -sb runtime/lua` `3146156`; `.lua` files also lie outside `runtime/lua` (`runtime/ftplugin/*.lua`, …). No member declares `jit`, and none has a column-zero `local vim` (grep, empty). `LICENSE.txt:3-4`: Apache-2.0 "except for parts … contributed under the Vim license"; `:186-207` lists MIT third-party libraries incl. lua-lpeg; `runtime/lua/vim/inspect.lua:6-9` is MIT. lazydev.nvim `ff2cbcba` registers `$VIMRUNTIME` (`lua/lazydev/config.lua:8`) and appends `/lua` (`lua/lazydev/workspace.lua:144-146`). `git ls-remote https://github.com/LuaCATS/neovim.git`: `Repository not found`. Release `v0.12.6` (GitHub API, `$S/r2/release-v0.12.6.json`) has platform assets with published digests; `nvim-linux-x86_64.tar.gz` (`sha256 474430d5…1d87`) holds `nvim-linux-x86_64/share/nvim/runtime/lua` = every commit-archive file byte-identical plus `jit/*.lua` (`python3 -I $S/r2/asset_compare.py …`, `$S/r2/asset-compare.out`). | done — pin and subtree chosen |
| TARGET-11-00-DR-02 | **Indexing the real tree.** At `cb05af0f` the library arm failed with `UTFDataFormatException … too long: 78363 bytes`, thrown while indexing `vim/_meta/vimfn.gen.lua` (`$S/results/run3-library/`); BUG-503 fixed it. On main, `s2-library` and `s4-library` each end `BUILD SUCCESSFUL`, `tests="1" skipped="0" failures="0" errors="0"`, with `[probe:refresh] lazynvimnv refreshMs=14242 provisioned=[…/system-test/lunar/definitions/neovim-779c36969e31080503be2dfccbcc3f999e289606]`. | done — indexes cleanly |
| TARGET-11-00-DR-03 | **Effect on lazy.nvim, on main.** `s1-control` and `s3-control` render identically, and so do `s2-library` and `s4-library` (`results/<run>/render.txt`). The control minus its `[probe]` lines hashes to `bc9696bb9473422c…`, the no-library render. `diff s1-control/render.txt s2-library/render.txt` prints exactly `> library.neovim=779c36969e31080503be2dfccbcc3f999e289606`, `> inspection.LuaDeprecatedApi=6`, `< inspection.LuaUndeclaredVariable=537` / `> inspection.LuaUndeclaredVariable=7` and `< symbol.LuaUndeclaredVariable.vim=530`; both keep `parseErrors=17`, `inspection.LuaTypeAssignability=447`, `inspection.LuaReturnTypeMismatch=56` and `symbol.LuaUndeclaredVariable.jit=7`. `vim.<caret>` offers 151 names (`s2-library/complete.txt`), the same list as at `cb05af0f` (`$S/results/run5-library/complete.txt`), which includes every distinct `vim.<name>` that lazy.nvim's `lua/` uses (`python3 -I $S/usage.py`, `$S/usage.out`, `comm` empty). `vim` multi-resolves to 779 declaration roots (`s2-library/refresh-resolve.txt`: `vim -> n=779`). | done — `vim` declared; only `jit` stays undeclared; no parse or type movement |
| TARGET-11-00-DR-04 | **Nested namespaces.** On main (`s2-library/complete.txt`): `vim.api.`, `vim.fn.`, `vim.uv.`, `vim.loop.`, `vim.fs.`, `vim.lsp.`, `vim.log.levels.`, `vim.diagnostic.`, `vim.keymap.`, `vim.json.`, `vim.treesitter.`, `vim.version.`, `vim.health.`, `vim.loader.` offer `n=0`; `vim.o.` `n=669`, `vim.bo.` `n=181`, `vim.v.` `n=95` (control `s1-control`: 1, 0, 0); `vim.g.` `n=3` in both arms (lazy.nvim's own `vim.g.*`). Every count equals `cb05af0f`'s. At `cb05af0f`, a shape probe gave `[]` for `M.sub.` (`function M.sub.deep()`), `M.typed.`, `M.req.` and `M.ctor.inner.` in library and project arms alike (`$S/results/run7-shapes/`), and BUG-505's prototype (`LuaReceiverMemberIndex.split` on the last separator, version 5 → 6; run 8) gave `vim.api.` 181 and `vim.fn.` 458, covering every `vim.api.*` and `vim.fn.*` name lazy.nvim uses, with the BUG-507 §2 namespaces still 0 (`$S/results/run8-split/coverage.txt`). | done — engine defects [[BUG-505]], [[BUG-507]]; out of scope (TARGET-11-08) |
| TARGET-11-00-DR-05 | **What declares `vim`, and what the classifier counts.** Executed shapes and classifier output are BUG-506 §1–§2. | done — [[BUG-506]] |
| TARGET-11-00-DR-06 | **Loader rule.** `--tests 'net.internetisalie.lunar.definitions.LuaDefinition*'` with the entry and the old rule: `neovim: rootPrefix should name the library dir to register, was 'neovim-779c…/runtime/lua'` (`$S/results/TEST-…LuaDefinitionCatalogLoaderTest.xml`). Design §2.2's rule in Kotlin: `LuaDefinitionCatalogLoaderTest` 18/18 with the entry (`$S/r2/results/r1-base/`, and on main `$S/r4/results/t1-base/`); fails naming `neovim` with `rootPrefix` = the wrapper (`$S/r2/results/r7-mut-wrapper`). In Python (`$S/rule_check.py`, `$S/rule-check.out`) it also rejects the wrapper plus `/`, `neovim-0000/runtime/lua` and the full prefix plus `/`. | done — rule replaced, not the entry |
| TARGET-11-00-DR-07 | **Design §2.4 test, and its mutations.** On main (`$S/r4/results/`): `t1-base` passes `NeovimDefinitionLibraryTest` 4/4 and `LuaDefinitionCatalogLoaderTest` 18/18. Each mutant edits only the catalog's `rootPrefix`, one run each, and prints the provisioned tree. `…/runtime/plugin` (`t2-mut-plugin`): extracted `[outside.lua]`; TC 3 fails at `assertDoesntContain` (`expected:<[]> but was:<[Undeclared variable 'vim']>`), and TC 5 and TC 6 fail too. `…/runtime/lua/vim/_core` (`t3-mut-core`): extracted `[shared.lua]`; only TC 5 fails (`expected:<[list_extend, schedule]> but was:<[list_extend]>`). `…/runtime` (`t4-mut-runtime`): extracted `[lua/vim/_core/shared.lua, lua/vim/_meta/builtin.lua, plugin/outside.lua]`; of that class only TC 6 fails (`expected:<[Undeclared variable 'outside_root']> but was:<[]>`). At `cb05af0f` (`$S/r2/results/`): the same three outcomes, plus the wrapper (`r7-mut-wrapper`: TC 1 and TC 2 fail; `…/runtime` also fails TC 1, whose expected entry pins the prefix) and no `enableInspections` (`r8-mut-tc4`: TC 4 and TC 6 fail while TC 3 stays green, which is the vacuity TC 4 guards). `…/runtime/lua2` (`r9-mut-lua2`) errors in `CorpusDefinitionLibraries.fetchLocally` before any assertion, so it is not used. **A fixture written under `library.rootPrefix` moves with a mutated prefix**, so no mutation of the prefix can reach its assertions (`r4-mut-runtime`, `Target11R2LayoutProbeTest`: prefix-relative members extract to `[plugin/outside.lua, vim/_core/shared.lua, vim/fs.lua]`; literal upstream paths extract to `[lua/vim/…, plugin/outside.lua]`, and `require("vim.fs")` lands on `<cache>/lua/vim/fs.lua`; `cacheExistsBefore=false` in every case). | done — design §2.4 writes literal paths |
| TARGET-11-00-DR-08 | **Cost, on main, and where it goes.** **Size.** Sweep `elapsedMs` (the `[corpus:…]` line): control `45124`, `47302`, `46830`, `44480` (`r4/s1`, `r4/s3`, `r5/c1`, `r5/c4`); library `80838`, `71620`, `81804`, `79795` (`r4/s2`, `r4/s4`, `r5/c2`, `r5/c6`). The ratio is 1.51–1.79 per pair and 1.71 on the means. **Window.** `c2` prints `[probe:phase] … sweepRootMs=2800 highlightMs=78967` against `c1`'s `4270` / `42516`, so the excess is highlighting. Provisioning and indexing finish before the window (`refreshMs=15157 dumbAfterRefresh=false`). **By inspection.** One highlighting pass over the 54 files after a PSI-modification tick, ms, control `c4` → library `c6`: none `21817` → `25883`; all but `LuaDeprecatedApi` `27187` → `30420`; `LuaDeprecatedApi` alone `31545` → `51474`; all ten `33378` → `62938`. **Inside `LuaDeprecatedApi`** (`c7`, three passes): its per-target loop takes 16–18 s over 448,649 targets, against the control's 85 ms over 12,794. Of that loop, 13.5–15.4 s per pass is six calls on `vim/lsp/_snippet_grammar.lua:LuaFuncDeclImpl` (`[probe:tag] … calls=6 ms=14525`). They are reached from lazy.nvim's own `M.parse(…)` ([[BUG-508]]'s shape), and building that file's AST takes 8 s ([[BUG-517]] §1). `vimfn.gen.lua`'s 268,500 calls take 0.70–0.79 s, and `api.gen.lua`'s 101,108 take 0.27–0.29 s. **Collapsing the 779-way fan-out does not close the gap.** In the same JVM (`c3`), collapsing `vim`'s bare-name results to one per file takes `LuaDeprecatedApi` alone from `51884` to `42821` ms and its loop from `17178` to `8042` ms. Collapsing to a single result gives `44346` / `9115` ms. Both stay well above the control's `31545`. Repeat passes vary by up to ~20 s, so how much of the gap the fan-out accounts for is not established. Skipping the 423,538 non-matching targets by stub name (`c5`) leaves the loop at 17–26 s. **Causal check** (`c8`, the inspection skips `_snippet_grammar.lua`): all ten `48056` ms (1.44× `c4`), sweep `elapsedMs=56723` (1.24× the control mean). **BUG-517's share**, per metric: on the sweep (TC 9's metric) the excess goes from 32.6 s to 10.8 s, about two-thirds removed; on the all-ten pass it goes from 29.6 s to 14.7 s, about half removed. **Inputs.** The probe used lazy.nvim `85c7ff37` twice, with `roots: ["lua"]`, `luaLevel: LUA51` and `moduleRoot: lua`, and `definitionLibraries: ["neovim"]` on the library arm. Design §4.2's `corpus.json` entries, fetched by `tooling/corpus/fetch-corpus.py` with `LUNAR_CORPUS_ROOT=$S/r6/corpus`, give checkouts that `diff -r` finds byte-identical to the DR-08 trees, `.corpus-sha` included, and the `neovim` archive with `sha256 912c4e69…29f3` (`$S/r6/fetch-corpus.out`). §4.2's probe code reads the repository root as `sweepAndRatchet` does; it was not itself run. **Residual excess**, measured but not attributed: +4 s with no inspection enabled. `c6` shows every Neovim library type snapshot rebuilt after each tick, as `pinnable=false`; that observation is [[BUG-519]] §1. **Per file** (all ten after a tick, `perfile.py c4 c6`): median `351` → `491` ms (1.34×), p90 `1146` → `2096` ms, max `3310` → `10615` ms (`lazy/async.lua`); the largest per-file ratio is 20.5×, or 5.4× with `c8`'s skip. **BUG-504** neither caused nor removed it: BUG-504's parent inspection gives `81357` (`r4/s5-library-pre504`). | done — the larger part is [[BUG-517]]; TARGET-11-09 fails today |
| TARGET-11-00-DR-09 | **`jit`** (read). Not declared under `runtime/lua` (DR-01). `LuaPlatform.LUAJIT` exists (`platform/LuaPlatform.kt:8`, versions at `platform/target/PlatformVersionRegistry.kt:26-29`); `src/main/resources/runtime/luajit/` holds only `.gitkeep`. | done — out of scope; TARGET-12 |
| TARGET-11-00-DR-10 | **Every `LuaDeprecatedApi` hit on lazy.nvim, on main** (`s2-library/hits.txt`): `'tbl_islist'` `lua/lazy/minit.lua@71`, `'termopen'` `lua/lazy/util.lua@3340`, `'nvim_get_hl_by_name'` `lua/lazy/view/float.lua@3877` (genuinely deprecated); `'clear'` `lua/lazy/manage/init.lua@1061` and `@4660`, `'save'` `lua/lazy/docs.lua@4319` (a project `M.clear()` / `M.save()` also resolving to `vim/lsp/codelens.lua`'s deprecated functions, [[BUG-508]]). Mutant `s5-library-pre504`: the same hits plus `'vim'` ×530. The `log` hits seen at `cb05af0f` are absent from both, because they needed the library's local `log` indexed as a global, which BUG-509 removed. | done — genuine hits only, plus [[BUG-508]]'s |
| TARGET-11-00-DR-11 | **False negatives in user code, on main** (`$S/r4/results/g1-globals/probe.txt`). A user file reads every root name in `$S/r2/fixed-globals.out` plus the control `zz_never_declared`, with `LuaUndeclaredVariableInspection` on and each pinned catalog library's registered tree as a library root, one arm per library (`Target11R4GlobalsProbeTest`). `none silent=[_ENV, _G, assert]`; `neovim silent=[_ENV, _G, vim, assert]`, and its `undeclared=` list includes `M`, `log`, `performResume`, `uv` and `Iter`; `luassert silent=[_ENV, _G, assert, match]`; `love2d` adds only `love`; `openresty` adds `jit`, `ndk` and the `resty_*` tables; `busted` adds its DSL names. `zz_never_declared` warns in every arm. Before BUG-509 and BUG-510, the `neovim` arm silenced `M`, `log`, `F`, `inspect`, `uv`, `Iter`, `performResume` and others besides `vim` (`$S/r3/results/b-fix-full/probe.txt`). No file under `runtime/lua` declares `function self.<…>` (grep, empty), so the implicit `self` that BUG-509 leaves unhandled does not arise here. The probed names are the candidates of BUG-506's prototype classifier; a name it misses would be a genuine Lua global. | done — of the probed names, the library adds only `vim` |

## Critical Risks

### Risk 1.1: GitHub regenerates the archive
- **Impact**: the IDE warns and keeps the file (ADVISORY); a strict corpus fetch fails with `PinError`.
- **Likelihood**: low; DR-01's downloads agree, but over about an hour only.
- **Mitigation**: re-pin per `definition_pins.GITHUB_WARNING` after `diff -r` of the two extracted
  trees, or move to the release asset (design §9).

### Risk 1.2: Highlighting Neovim code costs more
- **Impact**: with the library on, re-highlighting a lazy.nvim file after an edit is slower, and on a
  few files several times slower; the per-file figures are in DR-08.
- **Likelihood**: measured. [[BUG-517]] (the 8 s parse of `_snippet_grammar.lua`) accounts for about
  two-thirds of the sweep gap, TC 9's metric, and about half of the all-ten pass gap (DR-08). The rest
  is not attributed. [[BUG-519]] (library type snapshots never pinned) is a measured observation
  within it. BUG-519 owns that observation and does not gate TARGET-11, since TARGET-11-09 bounds the
  total.
- **Mitigation**: BUG-517 gates TARGET-11 against TARGET-11-09 (≤ 1.5×). With that file's parse taken
  out of one consumer, the ratio meets the bound (DR-08 `c8`), so the fix is predicted to suffice. If
  TARGET-11-09 still fails after the fix, the implementer stops and reports, and does not raise the
  bound (implementation-plan Phase 2).

### Risk 1.3: Engine fixes activate the library's contracts
- **Impact**: when BUG-505 or BUG-507 land, `@param`/`@return` on `vim.api.*` and the namespaces
  start applying, which may move type-inspection counts in Neovim code.
- **Likelihood**: unknown for BUG-507; for BUG-505's probe patch, nothing moved on lazy.nvim (DR-04).
- **Mitigation**: each bug's own gate sweeps the corpus.

## Technical Debt & Future Work
- **Re-pinning Neovim.** A new release is an edit of design §2.1's JSON, TC 1's expected entry and
  design §4.1's expected line.
- **TARGET-09 detection pattern for `vim`** (follow-on candidate): an entry for TARGET-09's matcher
  once that feature defines its field.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
