---
id: "MAINT-33-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "MAINT-33"
folders:
  - "[[features/maint/33-corpus-sweep/requirements|requirements]]"
---

# MAINT-33: Risks & Gaps

## Critical Risks

### Risk 1.1: The inspection pass makes the sweep too slow to run
- **Impact**: §3.3 opens an editor and runs `doHighlighting()` per file. At four projects the
  corpus is roughly 1,500 files; if the per-file cost is ~0.5 s the sweep exceeds 12 minutes and
  stops being run, which silently retires the whole gate.
- **Likelihood**: medium. The parse+require sweep over 291 files took 63.5 s, but highlighting is
  a much heavier path than a PSI walk.
- **Mitigation**: DR-01 measures it before Phase 3 is built. If the projected four-project runtime
  exceeds 10 minutes, **narrow the project's `roots`** (§4.1 column 3) so the inspection pass sees
  a smaller but stable file set, rather than dropping the metric — a stable subset still ratchets.
  Narrowing `roots` moves `files`, which is identity-checked, so it forces a deliberate re-record.
  MAINT-33-09 keeps the number visible thereafter.

### Risk 1.2: Corpus growth inflates every builder run
- **Impact**: `cmd_sync` (`gce-builder.sh:120-126`) pushes the whole dereferenced `test/` tree on
  **every** `run`, not just corpus runs. An unpruned KOReader checkout would tax every build in
  the repo, including ones unrelated to this feature.
- **Likelihood**: low now that KOReader is deferred — ZeroBrane is a few MB. Returns to medium whenever KOReader is revisited.
- **Mitigation**: `.git` stripping plus binaries-only pruning keeps the current corpus at 5.1 MB.
  Phase 5 must check `du -sh test/corpus` against the ~50 MB budget before recording baselines,
  and KOReader is fetched **without submodules**. If it cannot fit, pin a subset of its Lua tree
  by narrowing `roots` and pruning aggressively rather than admitting the whole repo.

### Risk 1.3: A false-green run is mistaken for coverage
- **Impact**: the first prototype run reported `BUILD SUCCESSFUL` with no test output and no test
  count, which is indistinguishable at a glance from "zero tests matched the filter". It had in
  fact run, but only inspecting `build/test-results/test/*.xml` on the builder proved it.
- **Likelihood**: high — it already happened once, on run one.
- **Mitigation**: every verification task in the plan states the count or artifact to check, not
  just the build status. Phase 2 additionally proves the ratchet can fail by inverting assertions,
  so a passing gate means something.

### Risk 1.6: A corpus checkout can break the builder sync outright *(hit 2026-08-03, mitigated)*
- **Impact**: `cmd_sync` pushes `test/` with `rsync -aLz` — dereferencing. ZeroBrane ships a
  **recursive symlink** (`zbstudio/ZeroBraneStudio.app/Contents/ZeroBraneStudio` → its own
  ancestor), which rsync follows until it hits "Too many levels of symbolic links (40)" and aborts
  the entire sync. Every builder run in the repo fails, not just corpus ones.
- **Likelihood**: was certain once ZeroBrane was added; now mitigated.
- **Mitigation**: `fetch-corpus.sh` runs `find "$dest" -type l -delete` after pruning. A read-only
  corpus never needs symlinks, so this is general — it protects future additions too.
- **How it presented — worth remembering**: the wrapper still exited 0, the log's last line was the
  rsync error, and `build/test-results/` held the *previous* run's XMLs. Checking test counts alone
  would have reported a passing run that never started. Same false-green shape as Risk 1.3, via a
  different route; always confirm the results timestamp is fresh.

### Risk 1.4: The ratchet ossifies a defect
- **Impact**: baselining a floor makes existing defects invisible. `unresolvedRequires=12` on
  luarocks records twelve real failures as "acceptable" forever.
- **Likelihood**: high — inherent to the mechanism.
- **Mitigation**: the baseline records the *file list* for parse errors so the floor stays
  legible, and improvements are surfaced with a re-record prompt rather than silently accepted.
  Treat every non-zero floor as a backlog item, not a settled fact — BUG-389 is the first example
  of the corpus doing exactly this.

### Risk 1.5: The sweep may not scale to a very large tree at all *(RESOLVED — it scales, but too slowly)*
> **2026-08-03**: DR-06 ran KOReader's 477 files end-to-end. `copyDirectoryToProject` and indexing
> were never the bottleneck and nothing broke — the cost is the per-file highlight pass, at
> 1.28 s/file. The design holds at 3× the current tree; only the time budget fails.
- **Impact**: Risk 1.1 covers *highlighting* cost. The copy-and-index cost of
  `copyDirectoryToProject` over a tree ~10× the current 291 files is untested; the 63.5 s datum
  says nothing about it. If it does not scale, Phase 5 cannot land as designed.
- **Likelihood**: medium.
- **Mitigation**: DR-06 measures it before Phase 5 commits to KOReader. Fallback is the same lever
  as Risk 1.1 — narrow `roots` to a representative subtree.

## Design Gaps

### Gap 2.1: ~~How an inspection hit is attributed~~ — RESOLVED, design §3.3
- **Resolution (2026-08-03)**: `HighlightInfo.getInspectionToolId()` exists and is public —
  `platform/analysis-impl/src/com/intellij/codeInsight/daemon/impl/HighlightInfo.java:462`. Design
  §3.3 now specifies it outright, along with the `unattributed` disposition for a null id and the
  severity-filtered `doHighlighting` overload. The description-prefix fallback is dropped entirely.
- **Residual**: whether the id is reliably *populated* for `LocalInspectionTool`-produced infos in
  a headless fixture is an empirical question, now DR-02 (narrowed).

### Gap 2.3: ~~Whether the fixture's file-type registry makes `claimed` meaningful~~ — MEASURED, DR-05 outcome

- **Resolution (2026-08-03)**: the registry is fine — Lunar's own registrations are visible in the
  fixture (`.lua`, `.busted` and the exact name `.luacheckrc` all report claimed). The fixture
  artefact predicted here (bundled Markdown/YAML plugins absent) is real but small: `md`, `yml`,
  `sh`, `png` report unclaimed and are noise, as expected.
- **What actually broke the signal is §3.4 step 4's own rule**, not the registry. "A group is
  `claimed` iff **every** file in it is claimed" lets a single outlier flip a large group:
  luarocks' 31 `.rockspec` report **claimed**, while luacheck's 54 report **unclaimed** — same
  extension, opposite verdicts, and the second is wrong as an integration signal.
- **Proposed refinement (deferred)**: record the unclaimed *count* per group rather than a boolean,
  so `tl=117 (117 unclaimed)` reads as a gap and `rockspec=54 (1 unclaimed)` plainly does not. That
  is a baseline-format change and a re-record; the metric is ungated, so it can wait.
- **Genuine finding meanwhile**: `luacheckrc=18` unclaimed is **not** an artefact. `plugin.xml:100`
  registers the exact name `.luacheckrc` only, so luacheck's own `*_config.luacheckrc` spec
  fixtures — and any user's `myproject.luacheckrc` — get no support. Worth its own bug.

### Gap 2.3a: original question (retained for context)
- **Question**: a `BasePlatformTestCase` `FileTypeManager` holds platform core types plus the
  plugin under test, not the bundled Markdown/YAML/etc. plugins of a real IDE. luarocks alone
  contributes 70 `md`, 14 `rock`, 13 `q`, 4 `zip`, 4 `c`, 3 `sh`, 2 `yml` — most will report
  unclaimed as a fixture artefact, drowning the real signal.
- **Options / leaning**: record the registry contents once and derive a fixed **ignore list** of
  groups that are unclaimed only because the fixture lacks the bundled plugin; report the rest.
  Rejected alternative: querying a real IDE's registry, which the sweep has no access to.
- **Resolved by**: DR-05, before the Phase 4 inventory is interpreted (it may be *recorded*
  earlier; §3.4's caveat says so).

### Gap 2.2: Whether ZeroBrane Studio's wxLua globals swamp the inspection metric
- **Question**: ZeroBrane leans on a large implicit global surface (`wx`, `ide`, `ide.config`).
  `LuaUndeclaredVariable` may report thousands of hits, making its baseline a number nobody can
  act on.
- **Options / leaning**: keep it and treat a huge floor as the finding (it argues for a
  definition-library or a per-project `.luacheckrc`-style global declaration); or exclude
  ZeroBrane from the inspection metric while keeping it for parse/require.
- **Resolved by**: DR-03, run during Phase 5 before recording ZeroBrane's baseline.

### Gap 2.4: The two `shortName`-less inspections' baseline keys
- **Question**: `LuaTypeAssignabilityInspection` (`plugin.xml:190`) and
  `LuaReturnTypeMismatchInspection` (`plugin.xml:198`) declare no `shortName`, so their ids come
  from `InspectionProfileEntry.getShortName` (`InspectionProfileEntry.java:363-364`). The
  derivation says `LuaTypeAssignability` / `LuaReturnTypeMismatch`, but the *observed*
  `getInspectionToolId()` is what actually keys the baseline.
- **Options / leaning**: include both (they are the highest-value entries in a false-positive
  metric, being ERROR-level type-engine checks) and verify the ids empirically on first record.
  Rejected: excluding them to dodge the uncertainty, which would leave the noisiest surface
  unmeasured.
- **Resolved by**: DR-07, at the Phase 3 first record.

## Technical Debt & Future Work

- **TBD: CI participation** — the sweep is builder-only. If the corpus ever shrinks enough (or CI
  gains a persistent cache), a nightly corpus job would catch regressions without a human running
  the gate. Out of scope here.
- **TBD: Fixing what the sweep finds** — every finding is a separate `BUG-*`. This feature
  deliberately ships no fixes, so that the ratchet's floor and the fixes are reviewable apart.
- **TBD: Unresolved qualified-name references** — a natural third defect metric (`a.b.c` that
  resolves to nothing), deferred to keep Phase 1 small.
- **RESOLVED 2026-08-03: the ballast `claimed` flag was all-or-nothing and hid groups (DR-05
  residual).** `CorpusSweep.ballast` marked a group claimed only when **every** member was claimed,
  so one unrecognised file flipped the whole group. That is what failed verification Scenario 4.1.
  - **The outlier was `luacheck/spec/folder/rockspec`** — a file literally named `rockspec`, with no
    extension. `groupKey` returns the whole filename when there is no dot and the bare extension
    otherwise, so extensionless `rockspec` collides with the `*.rockspec` extension group: 53 real
    rockspecs + 1 extensionless file = the observed 54, all reported unclaimed. Lunar claims
    `.rockspec` perfectly well (`plugin.xml:99`, `extensions="lua;rockspec"`); the inventory was
    misreporting it.
  - **Scenario 4.1's expected `claimed.rockspec=53` was right all along** — an earlier revision of
    this file wrongly called it "projected rather than measured". It was measured; the
    implementation was wrong.
  - **Fix**: `BallastGroup` now carries `claimed`/`unclaimed` *counts* rather than a flag, and a
    mixed group renders both rows under the same key (`ballast.claimed.rockspec=53` +
    `ballast.unclaimed.rockspec=1`). No key renaming, so groups with a uniform disposition render
    exactly as before; parsing folds the two rows instead of letting the later win. Regression test:
    `BaselineRatchetTest.mixedBallastGroupReportsBothDispositions`.
  - **Impact was reporting-only** — ballast is advisory and ungated (§3.2), so no gate was ever
    wrong and no baseline was unsafe.
  - **Still open (small)**: the key namespace itself still conflates "whole filename" with
    "extension", so an extensionless `foo` and a `*.foo` group share a key. Now visible rather than
    silently destructive, but a future `groupKey` could separate the two namespaces.

- **TBD: KOReader — MEASURED and parked on runtime alone (2026-08-03)**. Admitted at `v2026.07.2`
  (`f1371f25`), swept once end-to-end, then reverted. The estimates this was originally deferred on
  were half wrong, so the real numbers are recorded here rather than re-guessed later:

  | | Estimated at deferral | Measured |
  |---|---|---|
  | Checkout size | "strains the ~50 MB rsync budget" | **12 MB** pruned; 23 MB corpus total — comfortable (**DR-04 resolved: PASS**) |
  | Lua tree | "roughly an order of magnitude larger" | **572 `.lua`**, 477 indexed under `frontend,plugins` — 3× luarocks, not 10× |
  | Sweep time | (unknown) | **609 s = 10.2 min alone**; 1.28 s/file, the worst rate in the corpus. Four-project total **14 m 39 s** vs the 10-minute ceiling (**DR-06 resolved: FAIL**) |

  So size was never the obstacle; **runtime is**, and narrowing to `frontend` alone (354 files)
  projects to ~12 min total, still over. Parked rather than narrowed: a subtree that still breaches
  the budget buys nothing but lost coverage.

  It earned its keep in one run regardless — it found **BUG-393** (three LDoc annotation
  constructs reported as syntax errors), a defect class no other corpus project reaches, since
  none of them use LDoc.

  Its `require`s also proved the metric needs `moduleRoot`: KOReader names modules relative to
  `frontend/` (`require("ui/uimanager")` → `frontend/ui/uimanager.lua`), so **3444 of 4528**
  read unresolved — an artifact of a layout the manifest could not describe, not a defect.
  The manifest's 7th column now exists for this (§4.1), covered by
  `LuaSourcePathModuleResolutionTest`, so re-admitting KOReader is a manifest row and a `@Test`.

  **To revisit**, the runtime ceiling is the only thing to decide: either raise it deliberately
  (the sweep is opt-in, builder-only, and never runs in CI) or cut the highlight pass's cost.

- **RECOMMENDED REPLACEMENT: Penlight — the LDoc coverage at an eighth of the cost (measured
  2026-08-04).** KOReader's one durable contribution was reaching LDoc annotations, which no other
  pinned project uses. That does not require an application of KOReader's size; it requires a
  project *documented with LDoc*. Candidates were fetched and counted rather than reasoned about:

  | Project | `.lua` | `@param[opt=` | `@func` | backtick in `@param` | LDoc tags |
  |---|---|---|---|---|---|
  | **Penlight** | 115 (39 in `lua/`) | **14** | **47** | **21** | **1077** (917 in `lua/`) |
  | ldoc | 75 | 9 | 0 | 1 | 120 |
  | busted | 98 | 0 | 0 | 0 | 7 |
  | luasocket | 58 | 0 | 0 | 0 | 0 |

  Penlight carries **all three constructs BUG-393 was found through**, all of them inside `lua/`,
  and 917 LDoc tags in 39 library files. ldoc — which dogfoods its own format — has an eighth the
  tag density and none of the `@func` usage.

  Proposed manifest row (tag `1.15.0` → `e0bc8f7fce3b6a4fdef3660066f5006bf8456b32`):

  ```
  penlight<TAB>https://github.com/lunarmodules/Penlight.git<TAB>e0bc8f7f…<TAB>lua,spec<TAB>docs<TAB>LUA51<TAB>lua
  ```

  - **`roots = lua,spec` → 57 files ≈ 73 s** at the measured 1.28 s/file, taking the corpus to
    roughly 5 m 45 s against the 10-minute ceiling. KOReader was 477 files and 609 s. Same defect
    class, **~8× cheaper**, and no ceiling decision to make.
  - **`prune = docs`** — 1.3 MB of generated HTML, the bulk of the checkout; the Lua tree is 504 KB.
  - **`moduleRoot = lua`** — Penlight names modules `pl.utils` → `lua/pl/utils.lua`, so the 7th
    column added for KOReader finally has a user, and `LuaSourcePathModuleResolutionTest` covers it.
  - **`LUA51`**, matching the rest of the corpus; Penlight supports 5.1–5.4 via `pl/compat.lua`.
  - Incidental coverage: it is written in the paren-less `require 'pl.utils'` form throughout, which
    is BUG-389's path, and `pl.class` gives dense OOP/metatable/`__index` material for the type
    engine.

  **What is genuinely lost, and why that is acceptable:** Penlight is a *library*; KOReader is an
  *application*, with a deep require graph, global state and UI widget hierarchies. That shape is
  not unrepresented though — ZeroBrane Studio is a GUI application and luarocks is build tooling.
  What was unrepresented was LDoc, and that is exactly what Penlight restores. Scale itself was
  never the coverage; it was the cost.

  **Not yet wired in**: adding the row requires a baseline recorded from a builder run, and the
  ratchet must not be given a floor nobody has looked at.
- **TBD: More project shapes** — a Neovim config tree and an OpenResty service were considered
  and deferred; with KOReader parked, the corpus covers build tooling, a library with a
  hand-written parser, and a global-heavy 5.1 application — but no LuaJIT/FFI shape.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-33-00-DR-01 | Measure `openFileInEditor` + `doHighlighting()` cost per file over one corpus project; project the four-project runtime | Risk 1.1 | todo |
| MAINT-33-00-DR-02 | Confirm `getInspectionToolId()` is actually *populated* for `LocalInspectionTool` infos in a headless fixture, and measure how many land in `unattributed` on one corpus project | Gap 2.1 residual | todo |
| MAINT-33-00-DR-03 | Record ZeroBrane's `LuaUndeclaredVariable` count before baselining; decide keep-vs-exclude | Gap 2.2 | todo |
| MAINT-33-00-DR-04 | Confirm KOReader's on-disk size after a submodule-less depth-1 fetch and binary pruning, against the ~50 MB budget | Risk 1.2 | **done 2026-08-03 — PASS**: 12 MB pruned, 23 MB corpus total |
| MAINT-33-00-DR-05 | Dump the `FileTypeManager` registrations visible inside the sweep fixture; derive the ignore list of groups unclaimed purely by fixture artefact | Gap 2.3 | **residual — see below** |
| MAINT-33-00-DR-06 | Measure `copyDirectoryToProject` + indexing cost for a KOReader-sized tree (~10× the current 291 files) | Risk 1.5 | **done 2026-08-03 — FAIL**: 477 files swept in 609 s; four-project total 14 m 39 s vs the 10-minute ceiling. It scales (no failure, no blow-up), it is simply too slow to admit |
| MAINT-33-00-DR-07 | Confirm the two derived inspection ids (`LuaTypeAssignability`, `LuaReturnTypeMismatch`) against the first recorded baseline, rather than trusting `InspectionProfileEntry.getShortName`'s suffix-stripping blind | Gap 2.4 | todo |

## Test Case Gaps

- Malformed `corpus.tsv` rows and duplicate names are now specified (§4.1 failure handling) and
  covered by `BaselineRatchetTest.malformedManifestRowThrows` / `duplicateManifestNameThrows`
  (design §2.6, Phase 2). Previously untested and previously mis-messaged by a bare `singleOrNull`.
- The fetch script is verified only by running it; its failure paths (missing declared root, fetch
  of a non-tag SHA) are unexercised. Acceptable for tooling, but noted.
- No test asserts that `.corpus-sha` is absent from the ballast inventory (design §3.4 step 1
  excludes it); a regression there would silently add a spurious group.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
