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

### Risk 1.5: The sweep may not scale to a very large tree at all *(dormant — KOReader deferred)*
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
- **TBD: KOReader (deferred 2026-08-03)** — the LuaJIT/FFI shape is still the biggest gap in the
  corpus, but it does not fit the current budgets. DR-01 measured 42 s (luacheck, 132 files) and
  92 s (luarocks, 159 files) *with* the highlight pass; KOReader's Lua tree is roughly an order of
  magnitude larger, which blows the 10-minute ceiling on its own, and its submodule tree strains
  the ~50 MB rsync budget that every builder run pays. Revisit by pinning a narrow subtree
  (`frontend/` alone, say) rather than the whole repo, and re-measure — Risks 1.2 and 1.5 and
  DR-04/DR-06 exist for exactly that. Nothing in the design is KOReader-specific, so this is a
  manifest row plus a `@Test`, not rework.
- **TBD: More project shapes** — a Neovim config tree and an OpenResty service were considered
  and deferred; with KOReader parked, the corpus covers build tooling, a library with a
  hand-written parser, and a global-heavy 5.1 application — but no LuaJIT/FFI shape.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| MAINT-33-00-DR-01 | Measure `openFileInEditor` + `doHighlighting()` cost per file over one corpus project; project the four-project runtime | Risk 1.1 | todo |
| MAINT-33-00-DR-02 | Confirm `getInspectionToolId()` is actually *populated* for `LocalInspectionTool` infos in a headless fixture, and measure how many land in `unattributed` on one corpus project | Gap 2.1 residual | todo |
| MAINT-33-00-DR-03 | Record ZeroBrane's `LuaUndeclaredVariable` count before baselining; decide keep-vs-exclude | Gap 2.2 | todo |
| MAINT-33-00-DR-04 | Confirm KOReader's on-disk size after a submodule-less depth-1 fetch and binary pruning, against the ~50 MB budget | Risk 1.2 | **deferred** with KOReader |
| MAINT-33-00-DR-05 | Dump the `FileTypeManager` registrations visible inside the sweep fixture; derive the ignore list of groups unclaimed purely by fixture artefact | Gap 2.3 | todo |
| MAINT-33-00-DR-06 | Measure `copyDirectoryToProject` + indexing cost for a KOReader-sized tree (~10× the current 291 files) | Risk 1.5 | **deferred** with KOReader |
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
