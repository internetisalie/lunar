---
id: "TOOLING-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING"
priority: "high"
folders:
  - "[[features/tooling/requirements|requirements]]"
---

# TOOLING Epic — Implementation Plan

Epic-level delivery sequence for the eight features (each has its own phase-level
`implementation-plan.md`; this document sequences them). It defines the dependency order,
the milestones, where work can parallelize, how the TOOLING-00 spike outcomes branch later
scope, and the effort estimate.

## How to read the estimates

Effort is **engineer-days for one engineer already fluent in this codebase and the IntelliJ
Platform SDK** — focused implementation + tests + review, not calendar time. Ranges are
relative sizing to calibrate against team velocity, **not a committed schedule**. The
midpoint total is ~66 eng-days; with integration, live VNC verification, ktlint/PR cycles,
and end-of-wave baseline test cleanup, budget **~60–95 eng-days** (~3–4.5 months solo,
~1.5–2.5 months for two engineers given the dependency chain).

## Dependency order

```
TOOLING-00  De-risking spikes ──┐ (gates recipes/feed/serialization/LuaJIT decision)
                                 ▼
TOOLING-01  Model & registry ────┬──────────────┬───────────────┬────────────┐
                                 ▼              ▼               ▼            ▼
TOOLING-02  Resolution & envs ───┼──▶ 03 ──▶ 04 ──▶ 05          06           07
                                 │    exec   provis  cutover     UI          health
                                 └────┴───────┴───────┘
```

Edges (a → b = a must land before b is complete):

- **00 → 01, 04, 05** — 00-06 (serialization tolerance) gates the clean-break persistence in
  01/02/05; 00-05 (platform-classpath + feed format) and 00-01/-02/-03/-04 (build/acquire
  recipes) gate 04; 00-03 decides the LuaJIT scope carried by 01's `luajit` kind and 04.
- **01 → 02, 03, 04, 06, 07** — the registry, kinds, probe, and app-state are foundational.
- **02 → 03, 04, 05, 06, 07** — resolver + project settings + environment lifecycle + events.
- **03 → 04, 05, 07** — the exec service and env builder (04 builds/installs through it; 07's
  health checker probes through it; 05's consumers call it).
- **04 → 05, 06** — 05 migrates the New-Project wizard onto provisioning; 06's *Provision…*
  dialog button invokes it.
- **05** — the cutover: every consumer switched, legacy deleted. Depends on 01–04.
- **06, 07** — branch off after 01/02/03; finalize after 04 (06's dialog) / 05 (page removals).

## Milestones

Each milestone is independently buildable and green. Nothing is user-visible until M4 — the
new subsystem **dark-lands** alongside the legacy one (new `toolchain.*` packages, new
persistence components, no consumer switched) and coexists until the M4 cutover.

| # | Milestone | Features | Est. (eng-days) | Exit gate |
|---|-----------|----------|----------------:|-----------|
| **M0** | De-risk | 00 | 5–8 | All six spikes' pass/fail recorded; build recipe proven on the gce-builder image; feed JSON format defined; serialization tolerance confirmed; **LuaJIT ship-vs-descope decided**; Windows path verified on the KVM VM |
| **M1** | Core model (dark) | 01 | 8–12 | Registry + kinds + discovery + probe + app-state land and round-trip; unit-tested; **no** consumer uses it yet; legacy untouched |
| **M2** | Resolution + execution (dark) | 02, 03 | 12–18 | Resolver precedence, environments, exec service, env builder land + tested; still no consumer switched |
| **M3** | Native provisioning | 04 | 12–20 | Provision a `{lua, luarocks, +tools}` environment end-to-end via the new actions/dialog; registered as `PROVISIONED` tools; **exercised before cutover** through its own UI |
| **M4** | Cutover + surface | 05, 06, 07 | 16–24 | Every consumer resolves through the registry; legacy packages deleted; one Lua settings tree; health/banners on the new model; **PRD use cases pass live (VNC)** |
| **M5** | Stabilize | — | 3–6 | End-of-wave baseline test cleanup (per the wave gate); full suite green; `docs/status.md` regenerated; live verification of the four PRD flows |

Per-feature detail: [00](00-de-risking/implementation-plan.md) ·
[01](01-toolchain-model/implementation-plan.md) ·
[02](02-resolution-and-environments/implementation-plan.md) ·
[03](03-execution-and-injection/implementation-plan.md) ·
[04](04-native-provisioning/implementation-plan.md) ·
[05](05-consumer-migration/implementation-plan.md) ·
[06](06-settings-ui/implementation-plan.md) ·
[07](07-health-and-diagnostics/implementation-plan.md).

## Per-feature effort

| Feature | Est. (eng-days) | Primary driver / risk |
|---------|----------------:|-----------------------|
| 00 De-risking | 5–8 | Source-build recipe + Windows-VM spike are the time; rest are quick tests |
| 01 Model & registry | 8–12 | Foundational; 8-kind descriptor table; two engines (discovery, probe) collapse into one each |
| 02 Resolution & envs | 7–10 | Precedence + environment lifecycle + target sync; **deletes the ROCKS-16 mode machine** |
| 03 Execution & injection | 5–8 | Consolidation of three known process/env-assembly idioms into one |
| 04 Native provisioning | 12–20 | **Largest + riskiest** — the genuinely new capability (strategies, feed, download/extract, dialog) |
| 05 Migration & removal | 8–12 | **Breadth risk** — ~10 consumer families rewired, legacy deleted, tests migrated, build kept green |
| 06 Settings UI | 5–7 | Two configurables, inventory table, project-page rewrite, page fold-ins |
| 07 Health & diagnostics | 3–5 | Mostly a port of the proven 3-stage checker/monitor/banners onto the new model |
| **Total (midpoint ~66)** | **~53–82** | +15–20% overhead → ~60–95 |

## Spike branch points (how M0 changes later scope)

The TOOLING-00 outcomes are not just risk-retirement — they **resize** M3/M4:

- **00-03 LuaJIT** — PASS → 04 ships LuaJIT `SourceBuildStrategy` (POSIX). FAIL/uncertain → the
  `luajit` kind gets an **empty `provisioning` list** (register-existing-binary only), trimming
  04 toward its low end (~12d) and removing a test axis.
- **00-02 Windows** — confirms prebuilt-only on Windows (LuaBinaries + standalone luarocks). If
  acquisition is unreliable, 04's Windows story narrows to "register existing binary," and the
  provisioning UI documents Windows as bring-your-own for v1.
- **00-04 C-rocks** — fixes the failure-UX copy 04/07 implement for `luarocks install busted`
  on a toolchain-less host; a hard blocker here would descope tool-install provisioning to
  "pure-Lua rocks only" (luacov), leaving busted/luacheck as register-or-BYO.
- **00-06 serialization** — must confirm the clean break loads stale `lunar.xml` without error
  before M1's persistence is trusted; a surprise here (nested legacy beans throwing) forces the
  app inventory tag rename already anticipated in the contract (§7).

## Parallelization (two engineers)

The chain 01→02→03→04→05 is the critical path; 06/07 and parts of 04 branch off it.

- **Eng A (critical path):** 00 spikes that gate the model (00-05, 00-06) → 01 → 02 → 03 → 04
  → lead 05.
- **Eng B:** the build/acquire spikes (00-01, 00-02, 00-03, 00-04) first (feed A's M0 gate),
  then **06** and **07** once 01/02/03 exist (both only consume the resolver/registry/exec
  surface, already pinned in the contract), then pair on **04** (provisioning is the biggest
  single item) and the **05** cutover (breadth work parallelizes cleanly by consumer).

This overlaps M3 with M2-tail and M4's 06/07 with M3, compressing wall-clock to ~1.5–2.5
months without breaking the dependency order.

## Cutover & safety strategy (M4)

- **Coexistence, then one deletion commit.** 01–04 dark-land; the M4 cutover switches
  consumers one family at a time (each commit green — see 05's phase plan), and the legacy
  packages (`tool/*`, `platform/LuaInterpreter*`, `rocks/env/Hererocks*`, `util/LuaProcessUtil`,
  `command/LuaCommandLine`, the per-tool settings services) are removed in the final commit once
  the last caller is migrated.
- **Clean break, no shims** (user decision, 2026-07-05): old persisted tags are ignored on
  load (verified by 00-06); users re-discover / re-bind once. No migration code.
- **Removal scope:** ~3,100 lines main source deleted outright, ~400 more gutted from the
  retained settings classes, ~1,500 lines of tests replaced; ~400 lines (matrix runner, env
  widget) relocate rather than delete. Net main-source is roughly flat-to-slightly-up
  (provisioning is new capability); the win is structural (four resolution paths → one).
- **Verification:** unit suite green after every commit (regression-relative wave gate); the
  four PRD use cases verified live over VNC (containerized GoLand; Windows checks on the KVM
  VM) per [human-verification-checklists.md](human-verification-checklists.md).

## Definition of done (epic)

- Every external-binary consumer resolves through the unified registry — **zero** direct
  `executablePath`-style fields remain in code.
- `hererocks` / Python is no longer referenced anywhere in the plugin.
- Exactly one app-level Lua settings tree + one project page.
- Provisioning a `5.4 + luarocks + luacheck` toolchain on a clean CI image, then running lint +
  a script, is green end-to-end.
- Full unit suite green; live VNC verification of the provisioning and binding flows passes.
