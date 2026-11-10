---
id: "MAINT-56-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-56"
folders:
  - "[[features/maint/56-representative-type-corpus/requirements|requirements]]"
---

# Technical Design: MAINT-56 — Representative Type-Checking Corpus

## 1. Architecture Overview

Nothing under `src/main` changes, and no `plugin.xml` entry is added. The feature extends this prior art;
what it adds is §5:

| Prior art | Extended how |
|---|---|
| `tooling/corpus/fetch-corpus.py` | `--manifest` argument; per-entry `sparse` (§5.1) |
| `CorpusManifest` (`src/test/.../corpus/CorpusManifest.kt`) | loads a manifest path other than `corpus.json` (§5.2) |
| MAINT-54 harness (`corpus/audit/`), `LuaTypeFalseNegativeAuditTest` | its per-member body moves, unchanged in effect, into `AuditMemberRun`, which both audit classes call (§5.2) |
| `tooling/corpus/type_audit_summary.py` | imported, never copied: `aggregate`, `rated`, `verdict`, `F_THRESHOLD`, `R_THRESHOLD` (§3.4) |
| `CorpusDefinitionLibraries.provision`/`release` (MAINT-37) | called by the representative run exactly as `LuaCorpusSweepTest` orders them (§5.2) |
| `LuaPlatform`, `PlatformVersionRegistry`, the definitions catalog | read only, to name each domain's Lunar support (§2.0) |

A separate manifest rather than new `corpus.json` entries is a choice: `corpus.json` members must each
carry a sweep method, a ratchet baseline and full-shape CTRF identity (`corpus_ctrf.py` `_shape` refuses a
partial member set), and CI's routine lane fetches every one of them (`build-plugin.yml:203`). A type
corpus needs none of that. Pins are commit SHAs of the default branch on the selection date, not release
tags: the tag rule exists so a ratchet compares byte-identical trees, and a SHA already gives that; the
default branch is closer to code being edited, which is where an IDE checker matters most, while a
release snapshot is code whose bugs have already been fixed.

Python runs on the developer machine. Kotlin runs on the test's EDT in a light fixture
(`BasePlatformTestCase`), PSI read directly, edits through MAINT-54's `AuditInjector` unchanged.

## 2. Data

### 2.0 Domains (`tooling/corpus/type-domains.json`)

A domain is the host application whose API the code scripts or, for code that targets no host, the
interpreter it runs on. That is the unit Lunar ships support in: a `LuaPlatform` target
(`platform/LuaPlatform.kt`, versions in `platform/target/PlatformVersionRegistry.kt`) with stubs under
`src/main/resources/runtime/<pathSegment>/`, or a definition library in
`definitions/lunar-definitions-catalog.json` (`busted`, `luassert`, `love2d`, `openresty`, `neovim`). A
domain's weight therefore says how much Lunar's support for that host matters. Hosts share a domain only
where they share one API.

The file is the home of the domain list and of the usage measurement, and is edited, not generated,
except for `counts` and `measuredAt`, which `type_corpus.py usage` rewrites (§4.2):
```
{ "schema": 1, "measuredAt": "YYYY-MM-DD", "query": "\"<signature>\" extension:lua",
  "domains": [{"id", "kind": "host|interpreter", "sampled": bool, "weighted": bool, "reason"?: str,
               "lunar": str, "hosts": [{"name", "counts": {"<signature>": int}}]}],
  "excluded": [{"name", "counts": {"<signature>": int}, "reason"}],
  "channels": [{"host", "source", "count", "githubTopic", "githubRepos"}] }
```
- **Count.** A host's count is the maximum over its signatures; a domain's is the sum over its hosts.
  The query is GitHub code search restricted to `extension:lua` — the file type Lunar opens and the audit
  copies (`LuaFileType`, `extensions="lua;rockspec"`).
- **Weight.** `weights_of(domains)`: for each domain with `sampled` and `weighted` true, its count divided
  by the sum of the counts of all such domains. A domain with `weighted: false` has no weight; its
  `reason` is printed.
- **Coverage.** `coverage(domains)` = Σ count over sampled weighted domains ÷ Σ count over every entry of
  `domains`; `excluded` counts are in neither sum (TC-24).
- **Sampled** (has members) when the host is one of Neovim, Redis/Valkey, OpenResty, Tarantool, WoW,
  Factorio, LÖVE, Defold (`NAMED_HOSTS` = `{"neovim", "redis", "openresty", "tarantool", "wow", "factorio",
  "love", "defold"}`), or its share of the measured total is ≥ 2 %, or it is an interpreter target. A
  domain's **share** is its count ÷ Σ count over every entry of `domains` (`excluded` is not in the sum).
- **The sampled set is frozen** at the measurement of risks-and-gaps DR-12. A later `usage` re-measure
  changes counts, and so weights and coverage, only; it never changes a `sampled` value or a member. A
  domain whose re-measured share crosses the 2 % line is listed by `share_crossings(domains) ->
  list[str]` — for each `kind: "host"` entry, in file order, not in `NAMED_HOSTS`, whose `sampled` differs
  from `share ≥ 0.02`: `"<id>: <share·100 to 2 places> % of measured usage crosses the 2 % sampling line;
  sampled stays <true|false>"` (TC-25). `summarize` prints the list in reading 2 (§3.7) and writes it to
  `usage.crossings` (§4.3). Adding or dropping members for a crossing is a later corpus revision, not
  this feature.
- **Excluded** when its code is not standard Lua 5.1–5.5, so Lunar cannot parse it; counts are recorded
  so the exclusion is visible, never weighted.

| id | kind | sampled | hosts: signatures | Lunar support (`lunar`) |
|---|---|---|---|---|
| neovim | host | yes | neovim: `vim.api` | library `neovim`; target `LUAJIT` 2.1 |
| wow | host | yes | wow: `RegisterEvent` | none — no target or library; members run as `LUA51` stdlib |
| love | host | yes | love: `love.graphics` | library `love2d` |
| factorio | host | yes | factorio: `data:extend` | none; `LUA52` stdlib |
| awesome | host | yes | awesome: `require("awful")` | none; `LUA53` stdlib |
| redis | host | yes | redis: `redis.call`; valkey: `server.call` | targets `REDIS` 5/6/7 (`runtime/redis`), `VALKEY` 7.2/8 (`runtime/valkey`) |
| openresty | host | yes | openresty: `ngx.log` | library `openresty`; target `NGX` (`runtime/ngx` holds no stubs) |
| tarantool | host | yes | tarantool: `box.schema` | target `TARANTOOL` 2.10; `runtime/tarantool` holds no stubs |
| defold | host | yes | defold: `msg.post` | none; `LUAJIT` 2.1 stdlib |
| puc | interpreter | yes, unweighted | puc: `#!/usr/bin/env lua` | target `STANDARD` 5.1–5.5 stdlib stubs; no definitions for `lfs`, `luasocket` or `luaposix` |
| luajit | interpreter | yes, unweighted | luajit: `ffi.cdef` | target `LUAJIT` 2.0/2.1 stubs (`runtime/luajit`, incl. `ffi.lua`) |
| luanti, mpv, pandoc, nmap, wezterm, wireshark, koreader, solar2d, hammerspoon, computercraft | host | no | one each: `minetest.get_node`, `mp.get_property`, `pandoc.utils`, `stdnse`, `wezterm.action`, `Proto(`, `UIManager:show`, `display.newImage`, `hs.hotkey`, `peripheral.wrap` | pandoc: target `PANDOC`, `runtime/pandoc` holds no stubs; the rest none |

`reason` for both interpreter domains: "no host signature: standalone code names no host API, so a
shebang or `ffi.cdef` count is a lower bound of unknown recall (risks-and-gaps DR-12)". Excluded:
`roblox` (`game:GetService`; Luau), `gmod` (`hook.Add`; GLua's `!=`, `&&`, `continue` and C comments),
`playdate` (`playdate.graphics`; the SDK's compound assignment operators). PICO-8 carts are `.p8` files in
a dialect and are not measured. The counts, the channel checks and the measurement's biases are
risks-and-gaps DR-12.

### 2.1 Members (`tooling/corpus/type-corpus.json`)

The home of the member list. The file is `{"$comment": str, "corpora": [<entry>, …]}`, the top-level
shape of `corpus.json`, which `fetch-corpus.py` (`manifest["corpora"]`) and `CorpusManifest`
(`getAsJsonArray("corpora")`) already read; `type_corpus.load(path)` returns its `corpora` list. Entry fields are `corpus.json`'s (`name`, `url`, `commit`, `roots`,
`luaLevel`, `moduleRoot`, `definitionLibraries`, `target`, `prune`) plus `domain` (a sampled id of §2.0),
`cohort`, `licence` (SPDX from the GitHub API, recorded, never a filter: nothing is vendored,
risks-and-gaps DR-03) and optional `sparse`. Every `url` is `https://github.com/<repo>.git`.
Abbreviations: **NV** = `luaLevel` `LUA51`, `target` `LUAJIT:2.1`, `definitionLibraries` `["neovim"]`,
`moduleRoot` `lua`; **NG** = `LUA51`, `NGX:latest`, `["openresty"]`; **LV** = `LUA51`, `LUAJIT:2.1`,
`["love2d"]`; **RD** = `LUA51`, `REDIS:7+`; **TT** = `LUA51`, `TARANTOOL:2.10`; **JIT** = `LUA51`,
`LUAJIT:2.1`; **W51** = `LUA51`, no target or library; **F52** = `LUA52`; **A53** = `LUA53`; **P54** =
`LUA54`.

| name | repo | commit | domain | cohort | licence | roots | config |
|---|---|---|---|---|---|---|---|
| telescope | nvim-telescope/telescope.nvim | `1e1bab730218eaf97fce743be3f56e51075928ac` | neovim | established | MIT | `lua` | NV |
| gitsigns | lewis6991/gitsigns.nvim | `ef707fb93301a9b856f36eae4c8f56f5b988a794` | neovim | established | MIT | `lua` | NV |
| blinkcmp | saghen/blink.cmp | `7b31e1b6497be70f620bbad83a789929248697ac` | neovim | established | MIT | `lua` | NV |
| differ | undont/differ.nvim | `b5b107d093307692d1c0f677ec9f10c743543f9b` | neovim | young | MIT | `lua` | NV |
| weakauras | WeakAuras/WeakAuras2 | `2837a63d8de281cbb3cb3b6c7d1f18df8aa31c93` | wow | established | GPL-2.0 | `WeakAuras/RegionTypes`, `WeakAuras/BaseRegions` | W51, `prune` `WeakAurasModelPaths` |
| ouf | oUF-wow/oUF | `c5ff6e4a09bfaf2186e205777e0fab964b5cddc1` | wow | established | MIT | `elements` | W51 |
| omniinv | Zendevve/OmniInventory | `6ada95f6cc227cd85850fc7d67ee339d4454631f` | wow | young | NOASSERTION | `OmniInventory/Omni`, `OmniInventory/Engine`, `OmniInventory/Modules`, `OmniInventory/DB` | W51 |
| kristal | KristalTeam/Kristal | `0ba9821cc0d43b8e8fb3b6b8379bbf36825f3110` | love | established | BSD-3-Clause | `src/engine/game/battle`, `src/engine/game/common` | LV |
| prism | PrismRL/prism | `72a17adc783ca823d90bf1ff26406f6998ba4863` | love | young | MIT | `engine`, `spectrum` | LV |
| opensmce | jakubg1/OpenSMCE | `a013fe3d8cee0d57b3c6158e28c87864750f7368` | love | small | MIT | `src` | LV |
| factoryplanner | ClaudeMetz/FactoryPlanner | `e2aadc111c0c12c858a713e22c0c1bcde99465f2` | factorio | established | MIT | `modfiles` | F52 |
| rampant | veden/Rampant | `92c00c655f726c7b634dc843a825266abf38fa62` | factorio | small | GPL-3.0 | `prototypes`, `libs` | F52 |
| facc | factoriocenter/factorio-admin-command-center | `0ba301595a79827044ee6e0c16c162bf9bddf996` | factorio | young | MIT | `scripts` | F52 |
| awesome | awesomeWM/awesome | `1a5163ebd2b0c92f0dd19ab9e02762b05f629a80` | awesome | established | GPL-2.0 | `lib/gears`, `lib/naughty` | A53, `moduleRoot` `lib` |
| lain | lcpz/lain | `34a8d51c957620012822a48790cbda9a9c43e059` | awesome | established | GPL-2.0 | `widget`, `layout`, `util` | A53 |
| somewm | raven2cz/somewm-one | `e9ba2bd7da8dced8043903dd3f97755bac14bb19` | awesome | young | MIT | `fishlive` | A53 |
| bullmq | taskforcesh/bullmq | `b18b6ddd3d4e6fe2d8a14d09fb733aea159a5de4` | redis | established | MIT | `src/commands` | RD |
| inngest | inngest/inngest | `8ffc717d61035d178b14007858687bbd7195764a` | redis | established | NOASSERTION | `pkg/constraintapi/lua`, `pkg/execution/state/redis_state` | RD, `sparse` |
| beequeue | bee-queue/bee-queue | `31337aca81446859070d07f1e8b73df1d2fbc97a` | redis | established | NOASSERTION | `lib/lua` | RD |
| kong | Kong/kong | `21032122ab80c973f368f6bc539f53985e7164e8` | openresty | established | Apache-2.0 | `kong/clustering`, `kong/plugins` | NG |
| apisix | apache/apisix | `4a9d5d5679eeb52fb9d0d7a753eb1ead84adb509` | openresty | established | Apache-2.0 | `apisix/core`, `apisix/discovery` | NG |
| restyhttp | ledgetech/lua-resty-http | `b1b2bfbec17b5345263e9a0be0ecd1d8411dcbb6` | openresty | established | BSD-2-Clause | `lib` | NG, `moduleRoot` `lib` |
| tntmetrics | tarantool/metrics | `6868a310e8608bc9d27ea5ff654cf913a37f9191` | tarantool | small | MIT | `metrics` | TT |
| tntcrud | tarantool/crud | `d47f0be34dec6885965fcca1ded2e3924d292f37` | tarantool | small | BSD-2-Clause | `crud` | TT |
| tntqueue | tarantool/queue | `d28002cce4a48634ab4dd3b72e147be1fecd59f2` | tarantool | established | NOASSERTION | `queue` | TT |
| pokisample | defold/sample-poki | `92d74c7d49fa3ef2fb397c521d03485119329265` | defold | young | MIT | `game`, `app`, `common`, `ui` | JIT |
| druid | Insality/druid | `3eeb5c7ab701bd99503cc83ff5aa49cbeffbea24` | defold | established | MIT | `druid` | JIT |
| monarch | britzl/monarch | `5c33c39830489925493163e741d5b22b4e5e3b9d` | defold | established | MIT | `monarch` | JIT |
| luals | LuaLS/lua-language-server | `a58fc7b01e1746c46275c71b0e61bfed40a6296d` | puc | established | MIT | `script/vm` | P54, `moduleRoot` `script` |
| ldoc | lunarmodules/ldoc | `e1dff24e1c519c96bbf95647c9aeac2dfb3b9ae0` | puc | established | NOASSERTION | `ldoc` | P54 |
| lnko | Owloops/lnko | `e37475fb10473b6a9efd844630cc3b2a1a366690` | puc | young | GPL-3.0 | `lnko` | P54 |
| fallo | NTBBloodbath/fallo | `b95e4996477e4f023bf1b86190c946476cca210e` | puc | young | LGPL-3.0 | `lua` | P54 |
| lde | lde-org/lde | `04a08edb7c6113508688ac65ccd34b085333bac2` | luajit | young | MIT | `packages/lde-core` | JIT |
| snabb | snabbco/snabb | `f32729274b0a1b0da612bface2eabd8b57eef200` | luajit | established | Apache-2.0 | `src/lib/yang` | JIT, `moduleRoot` `src` |
| ljtk | franko/luajit-lang-toolkit | `73c51557d9a8bb1e2f0a93fcf21d3ca49e69af74` | luajit | established | NOASSERTION | `lang` | JIT |

Cohort: `young` if the repository was created within 24 months of the pin date, else `small` if it has
< 100 stars, else `established`. The density measured at selection, and the domains it leaves without
annotated or young members, are risks-and-gaps DR-01 and DR-13; the classifications above are their
output.

### 2.2 Error families
`arity.tooMany`, `arity.tooFew`, `field.missing`, `return.mismatch`, `assign.other`.
Shape → family (for the weights file): `arity.tooMany`/`arity.tooFew`/`return.mismatch` → themselves;
every other shape (`arg.*`, `field.assign`, `lenient.*`) → `assign.other`.

## 3. Algorithms

### 3.1 Density (`type_corpus.density(checkout: Path, roots: list[str]) -> dict`)
Over every `*.lua` under each `checkout/root` (`Path.rglob`, sorted), read as UTF-8 with
`errors="replace"`: `files` += 1; `lines` += lines whose `strip()` is non-empty; `catsTags` += lines
matching `^\s*---\s*@(param|return|class|field|type|alias|generic|overload|enum|cast)\b`. `maxFileLines` = the largest per-file `lines`, `maxFile` its path relative to `checkout`. Returns
`{"files", "lines", "catsTags", "maxFileLines", "maxFile"}`. Per member `kloc = lines / 1000`; `catsPerKloc = catsTags / kloc`;
domain density = Σ`catsTags` / Σ`kloc` over its members.

### 3.2 Extras (`TypeAuditExtras`, Kotlin, per copied file)
`baselineFamilies(file)` takes the errors the type inspections report —
`LuaTypesSnapshot.forFile(file).getErrors()` with `ErrorSeverity.HYPOTHESIS` dropped, the source of
`AuditInjector.keys` (`AuditInjector.kt:205-213`) — and maps each through `family(error: ElementError)`,
first match wins:
1. `message.startsWith("Too many arguments")` → `arity.tooMany`
2. `message.startsWith("Too few arguments")` → `arity.tooFew`
3. `message.startsWith("Missing required field")` → `field.missing`
4. `PsiTreeUtil.getParentOfType(error.element, LuaFinalStatement::class.java, false) != null` →
   `return.mismatch`
5. otherwise `assign.other`

The message prefixes are the literals of `LuaTypeGraph.kt:866`, `:874` and `:962`. Rule 4 tests the
`return` statement itself, not `isReturnRelated()`: that predicate also accepts an error anchored on a
function declaration, which a wrong argument produces (risks-and-gaps DR-07).

`payload(member, commit, files)` sums the map over `files` into `ExtrasPayload` (§4.1), every family key
present, 0 when absent.

### 3.3 Exposure (`type_corpus.exposure`)
For member `m` and shape `s`: `pop(m, s)` = Σ `population` over `m`'s audit `cells` whose `shape == s`.
`E(s, d)` = mean over the members of domain `d` of `pop(m, s) / kloc(m)`, a member with no cell of `s`
contributing 0. Members are weighted equally inside a domain so a large member does not stand for its
domain. `E(s)` (pooled) = Σ `w_d · E(s, d)` over weighted domains (`w` from §2.0).

### 3.4 Catch rate and verdicts (`type_corpus.domain_verdicts`)
For each domain `d`: `cells_d = aggregate(payloads of d)["cells"]` (`type_audit_summary.aggregate`).
- Silent rate per shape: `r(s, d)` = Σ `population·rate` / Σ `population` over the entries of
  `rated(cells_d)` with shape `s`; `-` when there are none. Catch rate = `1 − r(s, d)`.
  Missed exposure `M(s, d) = E(s, d) · r(s, d)`, `-` when `r(s, d)` is.
- **Pooled, per shape** (`shapes.<s>.pooled` in §4.3), each over the weighted domains only, so an
  unweighted domain never enters a pooled figure: exposure `E(s)` as §3.3; silent rate `r(s)` = Σ
  `w_d · r(s, d)` / Σ `w_d` over weighted domains where `r(s, d)` is not `-`; `M(s)` = Σ `w_d · M(s, d)` /
  Σ `w_d` over the same domains. `r(s)` and `M(s)` are `-` (`null`) when no weighted domain rates `s`.
  `M(s)` is not `E(s) · r(s)`. Worked numbers: TC-22. This is a different figure from the pooled
  *verdict* below, which pools every member's cells by population.
- `verdict(cells_d)` gives `F_d`, `R_d`, `S_d`, `P_d` and the domain's verdict, by MAINT-54 design §3.6
  unchanged.
- **Weighted verdict (the headline):** `F_w` = Σ `w_d · F_d` / Σ `w_d` over weighted domains with
  `P_d > 0`; `R_w` the same over weighted domains with `S_d > 0`. `REOPEN` iff `F_w ≥ F_THRESHOLD` and
  `R_w ≥ R_THRESHOLD` (0.25, 0.5, imported).
- **Beside it:** the unweighted domain mean (`F_dm`, `R_dm`: plain means over all sampled domains, the
  same `P_d`/`S_d` conditions), the pooled verdict (`verdict` over `aggregate` of all payloads), and the
  coverage of §2.0.
- **Non-`LIBRARY` stratum:** the weighted computation repeated with every cell whose path is `LIBRARY`
  removed from each `cells_d` first.

### 3.5 Suspected false positives (`type_corpus.false_positives`)
`FP(f, d)` = mean over the members of `d` of `baseline_m[f] / kloc(m)` — each member's own rate, never
the domain's findings over its pooled lines; pooled = Σ `w_d · FP(f, d)` over weighted domains (TC-23).

### 3.6 Bug → rule mapping (`tooling/corpus/type_bug_map.json`, `type_corpus.bug_for_cell`, `bug_rules`)
The map is data, fixed before the run. Rules are tried in this order; first match wins; `*` matches
anything; no match → `unmapped`:

| order | bug | shapes | paths | forms |
|---|---|---|---|---|
| 1 | BUG-44 | `field.assign` | * | * |
| 2 | BUG-43 | * | `LIBRARY` | `METHOD` |
| 3 | BUG-40 | `arg.table`, `arg.class`, `arg.record` | `LIBRARY`, `PROJECT_FILE`, `CHECKOUT_FILE` | * |
| 4 | BUG-41 | `arg.array`, `arg.function`, `arg.optional`, `arg.primitive` | `PROJECT_FILE`, `CHECKOUT_FILE` | * |
| 5 | BUG-42 | every shape except `return.mismatch`, `field.assign` | `SAME_FILE` | `MEMBER`, `METHOD` |
| 6 | BUG-45 | `arg.optional`, `arg.primitive`, `arg.union` | `LIBRARY` | `MEMBER`, `GLOBAL` |
| 7 | BUG-45 | `return.mismatch` | * | * |

These rules reproduce every row of MAINT-54's "Cell → BUG" table except the `arity.*` rows of BUG-38,
which is done: a silent cell there now is a regression and is listed as `unmapped` (TC-16; executed
against the table, risks-and-gaps DR-11).

File format (`json.load`): `{"schema": 1, "cells": [{"bug", "shapes", "paths", "forms", "exceptShapes"}]}`;
`"*"` stands for any value and `exceptShapes` (default `[]`) lists shapes a `"*"` shape list excludes;
rule 5 is `"shapes": "*", "exceptShapes": ["return.mismatch", "field.assign"]`.

`bug_rules(members, rules) -> dict[str, list[str]]`: for every silent cell (`rate > 0` in its domain's
`rated` cells) of every member, `bug_for_cell` → the cell's shape; per bug, the sorted distinct shapes;
`unmapped` collects the rest. A bug inherits the weights of those shapes in `rule-weights.json`; a bug no
rule reaches (BUG-27, a prerequisite with no behaviour of its own, for one) carries no rule weight.

### 3.7 Decision readings (`results.md`)
1. **Rule weights:** every shape with its pooled exposure, catch rate, missed exposure and its family's
   pooled false-positive rate, sorted by pooled missed exposure descending then shape name, every shape
   whose pooled `M(s)` is `-` after all others (by shape name), each with its per-domain row; then the
   bug → rule list and the `unmapped` cells.
2. **Redesign:** the weighted verdict, each domain's verdict with its weight and density, the unweighted
   and pooled verdicts, the non-`LIBRARY` stratum, the coverage and `share_crossings` (§2.0). The reading states which of them
   agree with MAINT-54's REOPEN.

### 3.8 False-positive sample (Should)
`baseline` holds counts only, so `TypeAuditExtras.payload` also records `samples`: per family, the first 10
findings over `files` in `(AuditPaths.relativePath(file), range.startOffset)` order, each as
`ExtrasSample(file = AuditPaths.relativePath(file), line = document.getLineNumber(range.startOffset) + 1,
message)` (`AuditCallResolution.kt:44-51`), `range` = `error.element.textRange`. In `results.md` each
sample is read at its line in the checkout and classified `real defect` (the line fails when run with the
reported types), `false positive`, or `unclear`.

## 4. External Data

### 4.1 `build/type-corpus/extras/<member>.json`
Gson as `AuditOutput` (`disableHtmlEscaping`, pretty). `ExtrasPayload(schema: Int = 1, member: String,
commit: String, files: Int, baseline: Map<String, Int>, samples: Map<String, List<ExtrasSample>>,
timedOut: Boolean = false)` (`timedOut`: §5.2's member deadline stopped the measurement),
`ExtrasSample(file: String, line: Int, message: String)` (`file` relative to the copied root, `line`
1-based). Maps are `sortedMapOf` so key order is fixed.

### 4.2 `type_corpus.py`
`main(argv)`; subcommands:
- `check [--manifest P] [--sweep P] [--domains P] [--checkouts DIR]` — `check(load(P), names(sweep),
  load_domains(P), checkouts)`;
  prints `type-corpus ok (<n> members, <k> domains)` (`<k>` = sampled domains) and exits 0, or prints
  each error on stderr and exits 1.
- `usage [--domains P]` — `measure_usage(load_domains(P), search=gh_search, today=<UTC date>)`, written
  back to `P` as `json.dumps(obj, indent=2, ensure_ascii=False)` + newline. `gh_search(query) -> int` runs
  `gh api -X GET search/code -f q=<query> -f per_page=1 --jq .total_count` and sleeps 7 s after each call
  (code search allows 10 requests a minute); a non-zero exit stops the command with exit 2 and the
  `gh` stderr. `measure_usage` replaces every count in `domains` and `excluded` with
  `search(query.replace("<signature>", sig))`, each `"` in `sig` escaped as `\"` (unescaped, `require("awful")`
  makes a malformed phrase that counts 0; TC-26), and sets `measuredAt`.
- `summarize <run-dir> --checkouts <dir> --lunar-commit <sha> --out <file> [--manifest P] [--domains P]` —
  `<run-dir>` holds `audit/` and `extras/`; `<sha>` is the commit the run was built from. Exits 2 on a
  missing payload (`missing payload: <sub>/<name>.json`), a `commit` that differs from the manifest, or
  `schema != 1`; else writes `--out` (§4.3) and prints the Markdown of §3.7 to stdout.
`check` errors, in this order per entry then per domain: `<name>: missing '<field>'` (for `domain`,
`cohort`, `licence`), `<name>: unknown domain '<d>'` (not a sampled id in the domain file),
`<name>: unknown cohort '<c>'`, `<name>: name must be [a-z]+`, `<name>: duplicate`,
`<name>: also in corpus.json`, and, when `checkouts` is given, `<name>: <maxFile> has <n> lines, cap 3500`
(`density` over `checkouts/<name>`; risks-and-gaps DR-04); then `<domain>: <n> members, need 3` for each
sampled domain.

### 4.3 `tooling/corpus/rule-weights.json` (MAINT-55 and MAINT-58 interface)
`json.dumps(obj, indent=2, sort_keys=True)` + newline; numbers rounded to 4 places; `null` where §3 says `-`.
```
{ "schema": 1,
  "corpus": {"manifest": "tooling/corpus/type-corpus.json", "members": {"<name>": "<commit>"}},
  "lunarCommit": "<--lunar-commit>",
  "thresholds": {"F": 0.25, "R": 0.5},
  "usage": {"domains": "tooling/corpus/type-domains.json", "measuredAt": "<date>", "coverage": x, "crossings": ["<line>", ...]},
  "domains": {"<d>": {"members": [...], "weight": x|null, "kloc": x, "catsPerKloc": x, "F": x, "R": x, "verdict": "REOPEN|HOLD"}},
  "verdicts": {"weighted": {"F", "R", "verdict"}, "domainMean": {...}, "pooled": {...}, "nonLibrary": {...}},
  "shapes": {"<shape>": {"family": "<family>", "byDomain": {"<d>": {"exposurePerKloc": x, "silentRate": x|null, "missedPerKloc": x|null}}, "pooled": {...same three}}},
  "families": {"<family>": {"byDomain": {"<d>": {"findingsPerKloc": x}}, "pooled": {"findingsPerKloc": x}}},
  "bugs": {"<BUG-nn>": ["<shape>", ...], "unmapped": [{"shape", "path", "form", "domain"}]},
  "timedOut": ["<name>", ...] }
```
A member whose extras payload has `timedOut: true` is listed in `timedOut`, sorted, and enters no other
figure: `summarize` drops it before every measure of §3, so it is in no domain's `members`, `kloc`,
exposure, rate, verdict or bug list. Its commit stays in `corpus.members`. Reading 2 ends with
`- Timed out, in no figure above: <names|none>` (TC-27). A partial measurement is not used, because the
sites a member reaches before its deadline are the sites of its first files only.
A consumer multiplies `missedPerKloc` (or `findingsPerKloc`) by its own per-shape rate; the file holds
no real-bug rate.

## 5. Components and Integration

### 5.1 Python
- `fetch-corpus.py`: `main(argv: list[str] | None = None)` with `argparse` `--manifest` (default
  `MANIFEST`); every use of the `MANIFEST` global in `main` uses the argument. `fetch_one(entry)`, when
  `entry.get("sparse")`: after `remote add`, `git fetch --quiet --depth 1 --filter=blob:none origin
  <commit>`, `git sparse-checkout set --no-cone <sparse_patterns(entry)>`, then `checkout FETCH_HEAD`;
  `sparse_patterns(entry) -> list[str]` = `"/<root>/"` per root, then `"/<moduleRoot>/"` if set. The
  non-sparse path is unchanged.
- `tooling/corpus/type_corpus.py`. `load_run(run_dir, entries, checkouts) -> list[dict]` builds one record
  per manifest entry: `{"name", "domain", "kloc", "catsTags", "audit": <audit payload>, "extras": <extras
  payload>}` (`kloc` and `catsTags` from `density` over `checkouts/<name>` and the entry's roots). Every
  measure takes that list and the weights: `exposure(members, w) -> dict[shape, dict[domain, float]]`,
  `domain_verdicts(members, w, drop_library: bool) -> dict`, `false_positives(members, w) -> dict[family,
  {"byDomain": dict[domain, float], "pooled": float}]`, `bug_rules(members, rules) -> dict`. Also `COHORTS`, `load(path) -> list[dict]`,
  `load_domains(path) -> dict`, `weights_of(domains) -> dict[str, float]`, `coverage(domains) -> float`,
  `NAMED_HOSTS`, `share_crossings(domains) -> list[str]`,
  `measure_usage(domains, search, today) -> dict`, `gh_search(query) -> int`,
  `check(entries, sweep_names, domains, checkouts=None) -> list[str]`, `density`, `bug_for_cell(cell, rules) -> str`,
  `weights(members, domains, rules, meta) -> dict`, `render(weights) -> str`, `main`. Imports
  `type_audit_summary` (same directory, run as a script).
- `tooling/corpus/test_type_corpus.py` (TC-1–5, 9–12, 16, 18–27) and fixtures under
  `tooling/corpus/testdata/type_corpus/`.

### 5.2 Kotlin (all `src/test/kotlin/net/internetisalie/lunar/…`)
| FQCN / file | Change |
|---|---|
| `corpus.CorpusManifest` | `MANIFEST_PATH` becomes public; add `const val TYPE_MANIFEST_PATH = "tooling/corpus/type-corpus.json"`; `load(repoRoot: File, manifestPath: String = MANIFEST_PATH)`, `entry(repoRoot: File, name: String, manifestPath: String = MANIFEST_PATH)`; `parseEntry`, `required` and `target` take the path for their messages. |
| `corpus.audit.AuditOutput` | add `fun writeTo(directory: File, member: String, payload: AuditPayload)`; `write` = `writeTo(file(repoRoot, member).parentFile, member, payload)`. |
| `corpus.audit.AuditRunMode` (new enum) | `(manifestPath: String, auditDir: String, extrasDir: String?, logTag: String, enablesLibraries: Boolean, memberLimit: Duration?)`: `FALSE_NEGATIVE(MANIFEST_PATH, "build/type-audit", null, "type-audit", false, null)`, `REPRESENTATIVE(TYPE_MANIFEST_PATH, "build/type-corpus/audit", "build/type-corpus/extras", "type-corpus", true, 30.minutes)`. |
| `corpus.audit.MemberDeadline` (new) | `MemberDeadline(limitNanos: Long?, clock: () -> Long = System::nanoTime)`, `expired()`; `measureWithin(sites, deadline, measure): Bounded<T>(results, timedOut)` measures in order and stops before the first site at which `expired()`. A null limit never expires. |
| `corpus.audit.MemberDeadlineTest` (new, routine) | plain JUnit 4; TC-28. |
| `corpus.audit.AuditMemberRun` (new) | `internal class AuditMemberRun(fixture: CodeInsightTestFixture, mode: AuditRunMode)`; `fun run(name: String)`; `fun restore(savedSourcePath: String)`. Holds `savedTarget: SavedTarget?` and `provisioned: List<Path>`. `applyModuleRoot`, `copyRoots`, `luaFiles` move here verbatim from `LuaTypeFalseNegativeAuditTest`. |
| `corpus.audit.TypeAuditExtras` (new object) | `baselineFamilies(file: LuaFile): Map<String, Int>`, `internal fun family(error: ElementError): String`, `payload(member: String, commit: String, files: List<LuaFile>): ExtrasPayload`, `writeTo(directory: File, member: String, payload: ExtrasPayload)`; `ExtrasPayload`, `ExtrasSample` in the same file. |
| `corpus.audit.LuaTypeFalseNegativeAuditTest` | `setUp` unchanged plus `runner = AuditMemberRun(myFixture, AuditRunMode.FALSE_NEGATIVE)`; `tearDown` = `runner.restore(savedSourcePath)` then `super.tearDown()` in `finally`; each test calls `runner.run(name)`. |
| `corpus.audit.LuaRepresentativeTypeAuditTest` (new) | `@RunWith(JUnit4::class)`, `BasePlatformTestCase`; `setUp` = MAINT-54's plus `VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString())` (as `LuaCorpusSweepTest.kt:57-60`); one `test<Name>Member() = runner.run("<name>")` per §2.1 row. |
| `corpus.audit.TypeAuditExtrasTest` (new, routine) | `BasePlatformTestCase`; TC-7, TC-13. |
| `corpus.audit.RepresentativeMemberSetTest` (new, routine) | plain JUnit 4 as `BaselineRatchetTest`; TC-6. |

No new name contains `Corpus` (`SweepNamingGuardTest`) or matches `*FalseNegativeAudit*`.

**`AuditMemberRun.run(name)`**, in order: `startedAt = System.nanoTime()`; `repoRoot =
File(fixture.testDataPath)`; `entry = CorpusManifest.entry(repoRoot, name, mode.manifestPath)`;
`CorpusGuards.assertCorpusFetched(repoRoot, entry)`; `savedTarget = CorpusTarget.pin(project,
CorpusTarget.memberTarget(entry))`; if `mode.enablesLibraries`, `provisioned =
CorpusDefinitionLibraries.provision(project, entry, repoRoot)`; `applyModuleRoot`; `files = copyRoots`;
then MAINT-54's scan, select, measure and payload steps unchanged, written with
`AuditOutput.writeTo(File(repoRoot, mode.auditDir), name, payload)`; if `mode.extrasDir` is set,
`TypeAuditExtras.writeTo(File(repoRoot, it), name, TypeAuditExtras.payload(name, entry.commit, files))`;
print `[<logTag>:<name>] files=… population=… sites=… elapsedMs=…`, elapsed to just after the last write.
**Member deadline** ([[BUG-96]], [[BUG-97]]; risks-and-gaps DR-15): `run` starts `MemberDeadline(mode.memberLimit)`
first, and the measure step is `measureWithin(selected, deadline) { injector.measure(it) }`. When it
stops early, the audit payload holds the sites measured so far, the extras payload has `timedOut: true`,
and the log line ends ` TIMED_OUT limit=30m`. A member overruns the limit by at most one site. For
`FALSE_NEGATIVE` the limit is null, so MAINT-54's run is unchanged.
Split into private helpers to stay within 30 lines.

**`restore(savedSourcePath)`**: `restored = CorpusSourcePath.restore(project, savedSourcePath)`;
`CorpusDefinitionLibraries.release(project, provisioned)`; if `restored && provisioned.isEmpty()`,
`CorpusDefinitionLibraries.refreshRoots(project)`; in `finally`, `savedTarget?.let {
CorpusTarget.restore(project, it) }` — `LuaCorpusSweepTest.tearDown`'s order. For `FALSE_NEGATIVE`,
`release` returns at its first line (nothing enabled), so the sequence equals MAINT-54's current one.

### 5.3 Build
In `build.gradle.kts`'s `test` task, after the `withTypeAudit` block and with the same shape:
`if (!project.hasProperty("withTypeCorpus")) { filter { excludeTestsMatching("*RepresentativeTypeAudit*");
isFailOnNoMatchingTests = false } } else { val typeCorpusDir =
layout.buildDirectory.dir("type-corpus").get().asFile; doFirst { typeCorpusDir.deleteRecursively() } }`.
**Run command:** `tooling/gce-builder/gce-builder.sh run "test -PwithTypeCorpus --rerun --no-build-cache
--tests '*RepresentativeTypeAudit*'"`, never combined with `-PwithCorpus` or `-PwithTypeAudit` (BUG-418).
Copy-back as MAINT-54 design §5 with `type-corpus` for `type-audit`.

## 6. Requirement Coverage
-01 §2.1, §4.2 · -02 §3.1 · -03 §5.1 · -04 §5.2, §5.3, §4.1 · -05 §3.3 · -06 §3.4 · -07 §3.2, §3.5 ·
-08 §4.3 · -09 §3.6 · -10 §3.7 · -11 §3.8 · -12 §2.0, §4.2.

## 7. Alternatives Considered
- **Members in `corpus.json`:** rejected in §1.
- **Domains by genre** (game, datastore, embedded): a genre spans several host APIs and Lunar's support
  is per host, so a genre weight maps onto no library or stub set.
- **Equal domain weight:** treats a host with a hundred times less public code as equal to Neovim; kept
  as the unweighted reading beside the headline.
- **Weights from the corpus itself** (member count or kloc per domain): measures this sample, not use.
- **Distribution-channel counts** (Factorio mod portal, Luanti ContentDB, LuaRocks): count packages, not
  files, exist for a few hosts only, and the WoW channel (CurseForge) needs an API key; no common unit
  across hosts. Recorded as bias checks (risks-and-gaps DR-12).
- **Population-pooled verdict as the headline:** a single large member (weakauras) would outweigh a
  domain; kept as the pooled reading.
- **Extending MAINT-54's payload instead of a second file:** changes its schema-1 golden
  (`TypeAuditHarnessTest` TC-19) for fields MAINT-54 never reads.

## 8. Open Questions

_None. The interpreter domains are reported unweighted; that is decided (§2.0, risks-and-gaps DR-12)._
