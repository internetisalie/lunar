---
id: "MAINT-56-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-56"
folders:
  - "[[features/maint/56-representative-type-corpus/requirements|requirements]]"
---

# MAINT-56: Risks & Gaps

## Premises

| Premise | Kind | Settled by |
|---|---|---|
| Annotated code exists in every domain | runtime (what repositories contain) | DR-01, DR-13 — **refuted** for `openresty` and `awesome` |
| Young or small annotated projects exist per domain | runtime | DR-01, DR-02 — partly |
| Licences constrain membership | read | DR-03 — no; nothing is vendored |
| The fetcher can take a 250 MB repository for 3.5 kloc of Lua | runtime | DR-09 — only with sparse checkout |
| The full run fits ≤ 180 min on the builder | runtime | DR-04 — only on the fallback roots |
| Enabling definition libraries in the audit harness resolves calls | runtime | DR-08 — yes |
| Rule-based family mapping gives TC-13's families on the engine | runtime | DR-07 — no: a wrong argument also yields a declaration-anchored error; design §3.2 rule 4 classifies it |
| Each host's share of public Lua code is measurable | runtime | DR-12 — for hosts; **not** for the interpreter domains |
| Bug-fix mining is feasible and worth a feature of its own ([[MAINT-58]]) | runtime | DR-05 |
| A runtime-error proxy can be mapped to rules | runtime | DR-06 — no |
| `type_audit_summary` functions are importable as a library | runtime | DR-10 |

Constraints treated as chosen, not fixed: the release-tag pin rule and membership in `corpus.json`
(design §1); equal member weight inside a domain (design §3.3); the 2 % sampling line and the user-named
hosts, and the sampled set frozen at DR-12's measurement (design §2.0). Domain weights are measured (DR-12), and per-domain figures are always printed beside
every weighted figure.

## Executed De-risking

All probes ran on 2026-11-08 on the developer machine, against `origin/main` `54f428ed`, with clones in
`~/.cache/claude-scratch/lunar/maint56/` (shallow, `--filter=blob:none`, sparse `*.lua`), scripts run with
`python3 -I`. DR-04, DR-07 and DR-08 ran on the `libvirt` builder against the same commit plus a
throwaway probe test, since deleted.

### DR-01 — Annotation density of the candidates (executed)
**How:** per candidate, all non-test `*.lua` (paths matching `(^|/)(test|tests|spec|specs|deps|vendor|third[-_]party)(/|$)`
skipped), LuaCATS tag lines `^\s*---\s*@(param|return|class|field|type|alias|generic|overload)\b` per
1 000 non-blank lines, and the share of files with any tag.

| domain | candidate (tags/kloc, files tagged %) |
|---|---|
| nvim | lazy.nvim 74.0 (91); telescope 42.2 (46); snacks 104.4 (97); mini.nvim 17.6 (75); gitsigns 134.5 (92); oil 93.8 (93); lualine 29.5 (23); nvim-cmp 81.8 (63); blink.cmp 141.3 (87); plenary 45.0 (28); young: differ.nvim 124.0 (96), comment-translate 53.5 (89), org.nvim 33.6 (64), vellum.nvim 0 |
| openresty | lua-resty-core 0; Kong 2.7 (3); apisix 0; lua-resty-http 0; lua-resty-redis 0 |
| game | Kristal 78.2 (54); prism 196.8 (92); OpenSMCE 96.7 (91); sample-poki (Defold) 79.3 (61); hump, bump, lume, BYTEPATH, mrrescue 0 |
| datastore | bullmq 0; inngest 19.4 (8); tarantool/metrics 80.6 (28); healthcheck-role 83.8 (86); crud 0.1; vshard 0.3; xqueue 69.2 (46) |
| host | WeakAuras2 1.3 overall (68 % of files; one 1.3 M-line data file dilutes it); BigWigs 0; koreader 0.2; awesome 0 (LDoc only); young: QUI 40.3, PanelsPlus 20.2 |
| general | Penlight 0 (LDoc); json.lua, inspect.lua 0; LuaLS `script/` 43.4 (50); young: lde 111.6 (94) |

Annotated game code was located with GitHub code search for `"---@param" "love.graphics.draw"` (946
hits; Kristal, OpenSMCE, prism among the top repositories) and `"---@param" "go.get_position"` (Defold).
The same search for `ngx.var` returned 631 hits, 98 of them in `Kong/kong-pongo` (a test harness).

**Members on their first roots**, same tag regex extended by `enum|cast`, measured over the roots (the
roots design §2.1 lists, where they differ, are DR-04's and DR-13's):

| member | files | kloc | tags/kloc | member | files | kloc | tags/kloc |
|---|---|---|---|---|---|---|---|
| telescope | 73 | 19.5 | 37.8 | bullmq | 136 | 5.4 | 0.0 |
| gitsigns | 61 | 15.7 | 136.9 | inngest | 66 | 3.5 | 23.0 |
| blinkcmp | 121 | 11.4 | 145.3 | tntmetrics | 42 | 3.2 | 85.2 |
| differ | 79 | 26.3 | 125.1 | tntcrud | 78 | 13.6 | 0.1 |
| kong | 260 | 24.2 | 2.8 | weakauras | 74 | 68.8 | 23.1 |
| apisix | 50 | 11.2 | 0.0 | | | | |
| restyhttp | 3 | 1.3 | 0.0 | awesome | 40 | 9.3 | 0.0 |
| kristal | 163 | 37.7 | 82.6 | luals | 28 | 18.9 | 58.5 |
| prism | 85 | 10.1 | 164.0 | lde | 84 | 17.5 | 74.2 |
| opensmce | 126 | 20.6 | 106.3 | pokisample | 68 | 7.7 | 89.9 |

The domain column of the candidate table above is the genre grouping used at selection; densities by
host domain, over the full member set, are DR-13's.

**Verdict:** the premise "each domain includes LuaCATS-annotated projects" is **refuted for
`openresty`**: no candidate measured above 2.8 tags/kloc (DR-13 adds `awesome`). MAINT-56-02 therefore
measures and reports density rather than requiring annotated members; OpenResty stays
in the corpus because unannotated `ngx.*` code is what Lunar checks there, through the `openresty`
definition library.

### DR-02 — Young and small projects (executed)
**How:** `gh search repos --language=lua --created=">2025-06-01" --stars=20..200 --sort=updated --limit 40`,
then DR-01's measure on nine of them. Annotated young projects exist for `nvim` (differ.nvim,
comment-translate), `general` (lde) and `host` (QUI, but 428 kloc). The search returned no young
OpenResty or Redis project. `pokisample` (created 2026-04-27) is young; `opensmce`, `tntmetrics`,
`tntcrud` are small (< 100 stars). **Gap:** `openresty` has no young or small member (DR-13 adds young members for `wow`, `factorio`, `awesome`, `puc`).

### DR-03 — Licences (executed)
**How:** `gh api repos/<repo> --jq .license.spdx_id`. Results are in design §2.1; `inngest` is
`NOASSERTION`, `lua-resty-core`, `hump` and `BigWigs` report none (none of the three is a member).
**Why it is not a filter:** `fetch-corpus.py` puts checkouts in `test/corpus/`, reached through the
tracked symlink `test -> ../test`, outside the repository, and strips `.git`. The committed outputs are
counts, relative paths, line numbers and Lunar's own messages, so no licence question about vendoring
arises.

### DR-05 — Bug-fix mining feasibility (executed; motivates the split)
**How:** full bare clones of lazy.nvim, gitsigns.nvim, oil.nvim (blobless for Kristal, Kong);
`git log --no-merges`; fix commit = subject matching `^fix(\([^)]*\))?!?:`; small = ≤ 20 Lua lines changed in
≤ 2 Lua files (`--numstat`).

| repo | commits | conventional share | `fix:` | touch Lua | small | median Lua lines |
|---|---|---|---|---|---|---|
| lazy.nvim | 1735 | 1.00 | 464 | 463 | 384 | 5 |
| gitsigns.nvim | 1299 | 0.73 | 461 | 437 | 263 | 11 |
| oil.nvim | 528 | 0.91 | 213 | 213 | 149 | 9 |
| Kristal | 3011 | 0.00 | 0 | – | – | – |
| Kong | 10292 | 0.29 | 817 | – | – | – |

A seeded sample (`random.Random(56)`) of 25 small lazy.nvim and 20 small gitsigns fixes was classified by
reading each diff (one coder, no second rater):

| class | count | commits |
|---|---|---|
| type-class, statically visible | 6 | `b4a5a1209e` (record where a list is expected), `cdfa788881` (`ipairs` over a `string\|string[]` field), `232232da5a` (argument to a zero-parameter function), `1e107c91c0` (wrong record key), `0b3ac7a7cb` (number where string), `472f752943` (undefined field `Util.Path`) |
| type-class, inside a string command | 1 | `1805679095` |
| nil access / nil argument | 3 | `2ab3bdf0a4`, `9d18976c10`, `bae45ef449` |
| undeclared global | 2 | `2e3e65b0f7` (`op`), `6f8dbdbd41` (`dprint`) |
| logic, environment, documentation | 33 | the rest |

**Reading:** about 1 fix in 7 is type-class, so a per-rule real-bug rate needs hundreds of classified
fixes per domain; selection is mechanical only where conventional commits are used (Kristal has none).
The record-key and undefined-field bugs and every nil bug in the sample belong to rules Lunar does
not have. Whether Lunar reports any of them needs a parent/child Lunar run — [[MAINT-58]]'s core, not run here.

### DR-06 — Runtime-error proxy (executed; Won't)
**How:** `gh api -X GET search/issues -f q='"<message>" is:issue' --jq .total_count`.
`"bad argument #"` 59 583; `"attempt to index a nil value"` 38 068; `"attempt to call a nil value"`
20 834; `"attempt to compare"` 11 265; `"attempt to perform arithmetic on a nil value"` 6 000;
`"attempt to concatenate a nil value"` 3 580. The hits are dominated by end-user crash reports of mods,
games and editor configurations, with no code to map to a rule; nil-related messages together outnumber
`bad argument`. Not built (requirements, Out of Scope).

### DR-09 — Sparse fetch (executed)
**How:** GitHub tree API: `inngest/inngest` HEAD checkout is 249 MB (bullmq 11, WeakAuras2 52, apisix 32,
LuaLS 31, Kristal 27, koreader 20, Kong 16, awesome 5). Then `git init`; `remote add`; `git fetch --depth 1
--filter=blob:none origin 8ffc717d…`; `git sparse-checkout set --no-cone '/pkg/constraintapi/lua/'
'/pkg/execution/state/redis_state/'`; `git checkout FETCH_HEAD` → 1.5 MB, 121 files, 66 `.lua`, `HEAD`
`8ffc717d61035d178b14007858687bbd7195764a`. Design §5.1's sparse path is this sequence.

### DR-10 — `type_audit_summary` as a library (executed)
`cd tooling/corpus && python3 -m unittest test_type_audit_summary` → `Ran 8 tests … OK`; the test module
imports the script by name, the same import `type_corpus.py` makes. Under `python3 -I` the script's
directory is not on `sys.path`, so the corpus scripts are run without `-I` (they are repository code).

### DR-11 — The bug map reproduces MAINT-54's table (executed)
**How:** design §3.6's rules, coded as a throwaway Python function, applied to every row of MAINT-54
`audit-results.md` "Cell → BUG" (parsed with `^\| (\S+) / (\S+) / (\S+) \| \d+ \| \d+ \| \d+ \| \[\[(BUG-\d+)\]\] \|`),
expecting the row's bug, or `unmapped` for BUG-38. Output: `45 rows; mismatches: []`. TC-16 is this
check made permanent.

### DR-04 — Run budget (executed, builder)
**How:** each member audited alone in a throwaway `BasePlatformTestCase` (builder `libvirt`,
`test --rerun --no-build-cache --tests '<one method>'`), copying the roots, pinning the target,
provisioning libraries where marked, scanning, selecting, measuring and computing the families, with
`elapsedMs` from before the copy to after the families. `kloc` is the printed non-blank line count.

| member | roots | libraries | kloc | largest file (lines) | elapsedMs | s/kloc |
|---|---|---|---|---|---|---|
| telescope | `lua` | — | 19.5 | 1 457 | 231 949 | 11.9 |
| telescope | `lua` | `neovim` | 19.5 | 1 457 | 500 232 | 25.7 |
| kristal | `src/engine/game` | `love2d` | 37.7 | 3 479 | 633 035 | 16.8 |
| weakauras | `WeakAuras` | — | 68.8 | 12 319 (`Prototypes.lua`) | > 2 843 000, aborted | > 41 |
| weakauras | `WeakAuras/RegionTypes`, `WeakAuras/BaseRegions` | — | 8.1 | 1 525 | 29 204 | 3.6 |

The full-root weakauras run was stopped by hand at 47 min with its test executor still busy; a thread
dump showed `LuaTypesVisitor.buildSnapshot` re-entered from `LuaTypesSnapshot.forFile` under
`AuditInjector`'s per-site re-analysis. Cost per site grows with the file, so a run is superlinear in the
largest file, not linear in kloc.
**Projection** (Σ kloc × the largest measured rate among members with no file over 3 500 lines, 25.7
s/kloc): every member on its first roots, 496.6 kloc → 213 min, and weakauras alone over 47 min. On
the fallback roots — weakauras and kristal as below, `omniinv` without `OmniInventory/UI` (largest file
6 777 lines), `snabb` on `src/lib/yang`, `luals` on `script/vm` (DR-13) — 352.0 kloc → 151 min.
**Branch applied:** the fallback roots, which design §2.1 lists: weakauras → `WeakAuras/RegionTypes`,
`WeakAuras/BaseRegions` (8.1 kloc, 45.2 tags/kloc); kristal → `src/engine/game/battle`,
`src/engine/game/common` (10.8, 129.1). The projection holds only while no root holds a file over 3 500
lines, so `type_corpus.py check --checkouts` enforces that cap (design §4.2). A member that fails the cap
has its roots narrowed to exclude the file and its row re-measured by DR-13's method and recorded there,
before the run; DR-13's largest-file table is the measurement against the cap.

### DR-07 — Family classification on the engine (executed, builder)
**How:** a throwaway `BasePlatformTestCase` in `corpus/audit/` ran TC-13's fixture and printed each
non-`HYPOTHESIS` error of `AuditInjector.keys(file, Shift.NONE)` with design §3.2's rules applied
(builder `libvirt`, `test --rerun --no-build-cache`, BUILD SUCCESSFUL, `tests="3" failures="0"`).
**Result:** `f(1, 2)` → `WARNING | LuaTypeAssignability | Too many arguments: expected at most 1, got 2`;
`f("x")` → **two** errors, `ERROR | LuaTypeAssignability | string is not assignable to number` and
`ERROR | LuaReturnTypeMismatch | string is not assignable to number`. The second is anchored on
`local function f(n) end`: `isReturnRelated()` (`TypeErrorClassification.kt:23`) accepts any error whose
element is a `LuaLocalFuncDecl`, so the rule-based mapping called it `return.mismatch`.
**Branch applied:** family rule 4 tests for an enclosing `LuaFinalStatement` instead (design §3.2), and
TC-13 expects `{arity.tooMany=1, assign.other=2}`. The message literals of rules 1–3 matched.

### DR-08 — Definition libraries reach the harness (executed, builder)
**How:** telescope audited twice with `CorpusDefinitionLibraries.provision` before the scan, once
skipped (`neovim` enabled, `LUAJIT:2.1`, `moduleRoot` `lua`), printing `AuditCoverage`.

| arm | enabled | unresolvedCallee | unresolvedMethod | eligible | population | sites | `LIBRARY` paths |
|---|---|---|---|---|---|---|---|
| bare | — | 2629 | 644 | 651 | 1955 | 108 | 1670 |
| libraries | `neovim` | 2287 | 644 | 837 | 2598 | 123 | 2313 |

**Result:** 342 callees resolve only with the library; provisioning reaches the harness. The branch
"stop and file a BUG" does not apply. Method calls are unchanged: telescope's methods are its own.

### DR-12 — Usage weights per host (executed)
**How:** GitHub code search through `gh api -X GET search/code -f q='"<signature>" extension:lua'
-f per_page=1 --jq .total_count` (authenticated `gh`; no token created), 7 s apart, 2026-11-08 15:15–15:19
UTC. Stability: `"vim.api"` gave 616 448 at 14:59, 15:01, 15:06, 15:15 and 15:19, and every signature
queried twice returned the same count. Precision: the first 40 hits of each signature, read by
repository name.

| domain | signature | count | share | first-40 precision |
|---|---|---|---|---|
| neovim | `vim.api` | 616 448 | 48.65 % | 40/40 |
| wow | `RegisterEvent` | 247 808 | 19.56 % | 40/40 |
| love | `love.graphics` | 111 360 | 8.79 % | 30/30 |
| luajit | `ffi.cdef` | 36 160 | 2.85 % | mostly standalone (torch, snabb, MoonGen); also OpenResty/LÖVE |
| factorio | `data:extend` | 33 088 | 2.61 % | 30/30 |
| awesome | `require("awful")` | 30 528 | 2.41 % | 40/40 |
| redis | `redis.call` + valkey `server.call` | 27 008 + 318 | 2.16 % | 30/30 |
| luanti | `minetest.get_node` | 20 160 | 1.59 % | 40/40 |
| puc | `#!/usr/bin/env lua` | 19 616 | 1.55 % | mostly standalone; 1 WoW |
| openresty | `ngx.log` | 18 656 | 1.47 % | 40/40 |
| mpv | `mp.get_property` | 18 160 | 1.43 % | 40/40 |
| pandoc | `pandoc.utils` | 16 672 | 1.32 % | 40/40 |
| nmap | `stdnse` | 15 968 | 1.26 % | heavy duplication: 21 repos in 40 hits, most copies of nmap |
| wezterm | `wezterm.action` | 12 064 | 0.95 % | 40/40 |
| wireshark | `Proto(` | 11 792 | 0.93 % | ~30/40 |
| koreader | `UIManager:show` | 8 480 | 0.67 % | — |
| solar2d | `display.newImage` | 7 168 | 0.57 % | — |
| hammerspoon | `hs.hotkey` | 6 648 | 0.52 % | — |
| computercraft | `peripheral.wrap` | 4 392 | 0.35 % | — |
| tarantool | `box.schema` | 3 004 | 0.24 % | 40/40 |
| defold | `msg.post` | 1 532 | 0.12 % | 39/40 (one OpenResty) |
| excluded: roblox | `game:GetService` | 251 904 | — | Luau |
| excluded: gmod | `hook.Add` | 85 632 | — | GLua |
| excluded: playdate | `playdate.graphics` | 4 232 | — | compound assignment |

Total of the measured, non-excluded hosts 1 267 030; the sampled weighted domains cover 86.0 % of it.
Signatures were chosen as the host's most-used API namespace call; candidates rejected: `CreateFrame`
(Roblox UI libraries, about 11/30 of its hits), `game.players` (case-insensitive search matches Roblox's
`game.Players`), the `OR` operator (`"ngx.say" OR "ngx.var" OR "ngx.log"` returned 4 264, fewer than any
one term). `language:Lua` instead of `extension:lua` counts `.nse` files: `stdnse` 93 568 against 15 968.

**Channel checks** (bias, not weights): Factorio mod portal `GET https://mods.factorio.com/api/mods?page_size=1`
→ `pagination.count` 24 824 mods, against 866 GitHub repositories with topic `factorio-mod`; Luanti
ContentDB `GET https://content.luanti.org/api/packages/?type=mod` → 3 204, against 637 with topic
`minetest-mod`; CurseForge `GET https://api.curseforge.com/v1/games` → 403 without an API key, so WoW's
channel is not measurable without an account; LuaRocks `manifest-5.1.json` → 5 210 modules (a package
count with no host attribution).

**Biases, recorded:** (1) recall differs by host — Neovim code names `vim.api` in most files, a Factorio
mod names `data:extend` in its data stage only, so file counts favour hosts whose API is called
everywhere; (2) hosts distributed outside GitHub (Factorio, Luanti, WoW addons on CurseForge/Wago) are
under-counted — the channel checks put Factorio's GitHub-visible share near 1 mod in 29; (3) vendored and
forked copies count again (nmap); (4) personal configuration (Neovim, AwesomeWM, WezTerm dotfiles) counts
as code in use, which it is, but it is not plugin code.

**Interpreter domains — not defensibly measurable.** Standalone code names no host API. A shebang marks
scripts, not libraries; `ffi.cdef` also occurs in OpenResty, LÖVE and Neovim code. Both counts are lower
bounds of unknown recall, and no residual (all Lua minus hosted) is usable: `extension:lua` alone counts
everything, including excluded dialects and vendored copies. **Decided:** `puc` and `luajit` are
sampled and reported per domain with `"weighted": false`, outside every weighted figure (design §2.0).

### DR-13 — Host-domain members and density (executed)
**How:** DR-01's member method (all `*.lua` under the roots, tag regex with `enum|cast`) over shallow
sparse clones of the candidates found by `gh api -X GET search/repositories -f q='topic:<t> language:Lua'
-f sort=stars`, once plain and once with `created:>2024-11-08`, for topics `wow-addon`, `factorio-mod`,
`minetest-mod`, `defold`, `luajit`, `awesomewm`, `cli`, `library`. Pins are each clone's `HEAD`.

| member | domain | files | kloc | tags/kloc | member | domain | files | kloc | tags/kloc |
|---|---|---|---|---|---|---|---|---|---|
| ouf | wow | 33 | 6.6 | 0.0 | beequeue | redis | 5 | 0.1 | 0.0 |
| omniinv | wow | 27 | 9.5 | 12.0 | tntqueue | tarantool | 14 | 3.9 | 0.0 |
| factoryplanner | factorio | 124 | 24.4 | 150.0 | druid | defold | 45 | 11.1 | 133.6 |
| rampant | factorio | 39 | 17.2 | 0.0 | monarch | defold | 5 | 1.6 | 0.0 |
| facc | factorio | 115 | 11.8 | 0.3 | ldoc | puc | 31 | 8.0 | 0.0 |
| lain | awesome | 31 | 3.6 | 0.0 | lnko | puc | 10 | 1.5 | 0.0 |
| somewm | awesome | 56 | 8.1 | 0.0 | fallo | puc | 1 | 0.4 | 236.5 |
| snabb | luajit | 14 | 8.3 | 0.0 | ljtk | luajit | 16 | 6.1 | 0.0 |

Rejected: `luarocks` and `penlight` (already `corpus.json` members), `qless-core` and `evolved.lua`
(all Lua at the repository root, and a root of `.` is not a copyable sweep root), `snabb`'s `src/lib`
(38.7 kloc; `src/lib/yang` taken), `omniinv`'s `OmniInventory/UI` (18.1 kloc, one 6 777-line file; DR-04), `awesome-wm-widgets` (one directory per widget). Luanti is unsampled
(1.59 %), so its candidates (`mesecons` 0.0, `technic` 0.1 tags/kloc) are not members.

**Largest file per member against the 3 500-line cap** (design §4.2). Executed 2026-11-08: every member
fetched at its design §2.1 pin by design §5.1's sparse sequence onto its roots, then `density`'s measure
(design §3.1: non-blank lines per `*.lua`, largest kept) in a throwaway script, run with `python3 -I`.
Control: every checkout's `HEAD` equals its pin, and DR-04's largest files reproduce (telescope 1 457,
weakauras on its fallback roots 1 525). `luals`'s `script/parser` holds `script/parser/compile.lua`,
4 904 lines, over the cap, so by DR-04's branch `luals`'s root is `script/vm` alone (21 files, 10.0 kloc,
86.5 tags/kloc). On the roots design §2.1 lists, every member is under the cap:

| member | largest file | lines | member | largest file | lines |
|---|---|---|---|---|---|
| telescope | `lua/telescope/pickers.lua` | 1 457 | beequeue | `lib/lua/checkStalledJobs.lua` | 52 |
| gitsigns | `lua/gitsigns/actions.lua` | 1 177 | kong | `kong/plugins/oauth2/access.lua` | 978 |
| blinkcmp | `lua/blink/cmp/init.lua` | 427 | apisix | `apisix/core/config_etcd.lua` | 1 069 |
| differ | `lua/differ/view.lua` | 2 453 | restyhttp | `lib/resty/http.lua` | 980 |
| weakauras | `WeakAuras/RegionTypes/DynamicGroup.lua` | 1 525 | tntmetrics | `metrics/quantile.lua` | 286 |
| ouf | `elements/tags.lua` | 827 | tntcrud | `crud/common/utils.lua` | 1 224 |
| omniinv | `OmniInventory/Omni/Rules.lua` | 910 | tntqueue | `queue/abstract.lua` | 921 |
| kristal | `src/engine/game/battle/enemybattler.lua` | 1 048 | pokisample | `app/modules/platform/poki_service.lua` | 372 |
| prism | `engine/lib/messagepack.lua` | 844 | druid | `druid/system/utf8.lua` | 936 |
| opensmce | `src/Game/Level.lua` | 1 611 | monarch | `monarch/monarch.lua` | 1 238 |
| factoryplanner | `modfiles/backend/handlers/generator.lua` | 1 832 | luals | `script/vm/compiler.lua` | 2 703 |
| rampant | `libs/Constants.lua` | 1 672 | ldoc | `ldoc/doc.lua` | 1 240 |
| facc | `scripts/gui/main_gui.lua` | 1 370 | lnko | `lnko/init.lua` | 516 |
| awesome | `lib/naughty/notification.lua` | 972 | fallo | `lua/fallo/init.lua` | 444 |
| lain | `util/dkjson.lua` | 678 | lde | `packages/lde-core/tests/build.test.lua` | 1 988 |
| somewm | `fishlive/services/wallpaper.lua` | 1 104 | snabb | `src/lib/yang/data.lua` | 1 800 |
| bullmq | `src/commands/moveToFinished-14.lua` | 200 | ljtk | `lang/bytecode.lua` | 1 247 |
| inngest | `pkg/constraintapi/lua/acquire.lua` | 422 |  |  |  |
**Host-domain densities**, the same run, over every member on design §2.1's roots, tags/kloc: love
126.3, neovim 107.5, defold 106.4, factorio 68.6, puc 48.6, luajit 40.7, wow 19.9, tarantool 13.3,
redis 9.0, openresty 1.9, awesome 0.0; all members, 352.0 kloc.
**Gaps:** `awesome` has no annotated member; `wow`'s annotation (Ketho's `vscode-wow-api` ships LuaCATS
for the WoW API) is not in Lunar's catalog, so WoW members run without host definitions — that is the
gap the WoW weight prices.

## Risks

### Risk 1.1: Small samples per domain cell
- **Impact:** with `CAP = 4` per cell, a domain-level silent rate rests on few measured sites.
- **Likelihood:** high. **Mitigation:** `results.md` prints `measured` beside every rate; rates with
  `measured < 3` are marked, as MAINT-54's route-divergence rule already treats them.

### Risk 1.2: Copied roots carry non-Lua files
- **Impact:** `inngest`'s roots hold Go files; `copyDirectoryToProject` copies them into the fixture.
- **Likelihood:** medium. **Mitigation:** DR-09 measured the roots at 121 files; DR-04's projection
  covers the cost.

### Risk 1.3: Usage weights move or mislead
- **Impact:** GitHub code-search counts drift, and DR-12's biases (per-host recall, off-GitHub
  distribution, duplicates) shift weight between hosts; Neovim alone carries over half the headline.
- **Likelihood:** high. **Mitigation:** `type_corpus.py usage` re-measures before the run (plan Phase 4);
  every weighted figure is printed with its per-domain figures and the unweighted mean; the channel checks
  stay recorded beside the counts.

### Risk 1.4: A member's run time is superlinear in its largest file
- **Impact:** one large file can exceed the budget alone (DR-04: weakauras's 12 319-line file).
- **Likelihood:** medium. **Mitigation:** the 3 500-line cap in `check --checkouts`; each member's
  `elapsedMs` is printed, so an overrun names its member.

## Technical Debt & Future Work
- **Bug-fix mining:** [[MAINT-58]], which multiplies `rule-weights.json` (design §4.3) by a per-rule
  real-bug rate; DR-05 is its evidence.
- **WoW definitions:** Ketho's `vscode-wow-api` LuaCATS is not in Lunar's catalog (DR-13); the WoW
  domain's weight is the case for adding it.
- **Finer usage weights:** the counts are files, so a host whose API appears in many files per project
  (Neovim, WoW) outweighs one confined to a single file (Factorio's `data.lua`). If the headline needs
  more precision, count distinct repositories per signature and weight them by repository activity.

## See Also
- Requirements: [requirements.md](requirements.md) · Design: [design.md](design.md)
