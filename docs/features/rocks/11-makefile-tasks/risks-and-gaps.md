---
id: ROCKS-11-RISKS
title: "Risks & Gaps"
type: risk
parent_id: ROCKS-11
folders:
  - "[[features/rocks/11-makefile-tasks/requirements|requirements]]"
---

# ROCKS-11: Risks & Gaps

## Critical Risks

### Risk 1.1: Makefile Language plugin API / pluginId unverifiable from local repos
- **Impact**: the optional `<depends>` line (ROCKS-11-04) names `com.jetbrains.lang.makefile` and
  assumes the plugin natively provides target run markers. If the `pluginId` is wrong, the optional
  `<depends>` silently never activates (best case) or, if it were a hard dependency, the plugin
  fails to load (not our case — it is optional). If the plugin does **not** provide native
  gutter-run for targets, the one-click promise is unmet.
- **Likelihood**: medium (id is very likely `com.jetbrains.lang.makefile`, but it cannot be
  confirmed from the local reference repos — see note below).
- **Mitigation**: gate ROCKS-11-04 entirely on DR ROCKS-11-00-01. The Must-scope template work
  (Phase 1) is independent and ships regardless. Because the dependency is `optional`, a wrong id
  cannot break Lunar loading.

> **The reference repos do NOT contain this plugin.** `~/Documents/src/lua/intellij-community` and
> `~/Documents/src/lua/intellij-plugins` are the only local IntelliJ sources, and the JetBrains
> "Makefile Language" plugin is **marketplace-distributed**, not part of either tree. Therefore the
> `pluginId`, its run-config/target API, and the optional-`config-file` behavior are **UNVERIFIED**
> and must be confirmed live in GoLand 2026.1.3 (DR ROCKS-11-00-01) before ROCKS-11-04 is marked
> done.

### Risk 1.2: Makefile plugin not bundled in GoLand 2026.1.3 (marketplace-only)
- **Impact**: out of the box, scaffolded projects have no one-click target run; users must install
  the plugin from the marketplace first. The feature must degrade gracefully, not error.
- **Likelihood**: high (it is confirmed marketplace-only, not bundled).
- **Mitigation**: the dependency is `optional`; the fallback (terminal / ROCKS-04 run config) is
  documented (ROCKS-11-05, design §6). A user-facing note can point at the marketplace plugin.

## Design Gaps

_None._ The one genuinely-unknown item (the Makefile plugin's API) is tracked as DR
ROCKS-11-00-01 below, not parked as an open question. The Must-scope template (design §3.1) is
fully specified.

## Technical Debt & Future Work
- **TBD: cross-platform `clean`** — `rm -rf` is POSIX-only (pre-existing in the current template at
  `LuaRocksTemplates.kt:73`); a Windows-portable `clean` is out of scope for this feature.
- **TBD: Makefile-plugin extension contribution** — if DR ROCKS-11-00-01 surfaces a useful target/
  run-config extension point, `lunar-makefile.xml` could register a contributor later. Deferred
  until the spike proves value (building custom markers is explicitly out of scope today).
- **TBD: tool-not-installed UX from the Makefile** — when a recipe's tool is missing from `PATH`,
  `make` fails with a shell error. Surfacing a Lunar notification for Makefile runs is out of scope
  (the Makefile plugin owns the run console).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-11-00-01 | In GoLand 2026.1.3: install the JetBrains "Makefile Language" plugin from the marketplace. Confirm its exact `pluginId` (expected `com.jetbrains.lang.makefile`) from the installed plugin descriptor. Confirm it provides native target gutter-run markers + a Makefile run-config for a scaffolded `Makefile`. Verify optional-`<depends>` + empty `config-file` behavior: (a) plugin present → `lunar-makefile.xml` loads without error; (b) plugin absent → it is skipped and Lunar loads normally. Record findings inline. | Risk 1.1; gates ROCKS-11-04 (design §7, Phase 2) | todo |

## Test Case Gaps
- Automated tests cannot cover ROCKS-11-04/-05 (plugin presence/absence) — these are manual
  GoLand checks (TC #6 + human-verification-checklists.md), dependent on DR ROCKS-11-00-01.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
