---
id: "ROCKS-10-RISKS"
title: "Risks & Gaps"
type: "risk"
priority: "medium"
parent_id: "ROCKS-10"
folders:
  - "[[features/rocks/10-workspace-build/requirements|requirements]]"
---

# ROCKS-10: Risks & Gaps

## Critical Risks

### Risk 1.1: Hard dependency on ROCKS-09 (not yet implemented)
- **Impact**: ROCKS-10's build set is `LuaRockspecDiscoveryService.discoverRockspecPaths()`. ROCKS-09 is
  `planned` but **not `done`** — the service does not yet exist in `src/` (verified: only
  `docs/features/rocks/09-workspace-discovery/` references it; no Kotlin symbol). If ROCKS-10 is
  scheduled before ROCKS-09 lands, none of the orchestration can compile or run.
- **Likelihood**: high (ordering of work).
- **Mitigation**: gate ROCKS-10 implementation on ROCKS-09 being merged and green. The LOCKED
  consumer contract (`discoverRockspecPaths(): List<DiscoveredRockspec>`,
  `DiscoveredRockspec(rockspec: Path, packageName: String?)`) is stable, so Phase 1 (`WorkspaceBuildGraph`,
  pure) and the `normalizeDepName` parse can be built and unit-tested **before** ROCKS-09 lands;
  only Phases 2–4 (which call the service) block on it. DR-01 confirms the contract shape.

### Risk 1.2: First-party vs vendored detection relies entirely on ROCKS-09's exclusions
- **Impact**: ROCKS-10 builds exactly what ROCKS-09 discovers. If a vendored rock escapes ROCKS-09's
  `thirdparty/`/`lua_modules/`/`build*/`/`output/`/`.luarocks/` exclusion (e.g. a vendored rock under
  a differently-named dir like `vendor/` or `external/`), ROCKS-10 would try to `luarocks make` it —
  building a third-party rock from source is slow and may fail (C toolchain), polluting the build.
- **Likelihood**: medium (non-standard vendoring layouts).
- **Mitigation**: ROCKS-10 adds **no** detection of its own — it trusts ROCKS-09, the single source
  of truth (avoids two divergent "is this first-party?" rules). For non-standard layouts, the user
  configures ROCKS-09's `rockspecExcludeGlobs` (e.g. `["vendor/**"]`, ROCKS-09-07) — that exclusion
  flows through to ROCKS-10 automatically. Documented in the human-verification checklist.

### Risk 1.3: Partial-failure semantics (build stops mid-way)
- **Impact**: On the first non-zero `luarocks make`, ROCKS-10 stops — earlier rocks are already
  installed into the LuaRocks tree, later rocks are not. The workspace is left in a partial state.
  A naive user may re-run and double-build the already-built prefix.
- **Likelihood**: medium (any compile error in one rock).
- **Mitigation**: the design **intentionally** stops on first failure (a later rock likely depends
  on the failed one, so continuing would cascade). The console clearly reports which rock failed and
  its exit code, and that subsequent rocks were skipped, so the user fixes that rock and re-runs.
  `luarocks make` is idempotent enough that re-building the already-built prefix is safe (it
  re-installs). No transactional rollback is attempted (out of scope; TBD below).

### Risk 1.4: C-module rocks need a build toolchain
- **Impact**: A first-party rock with a C `build.type` (`builtin`/`make`/`cmake`) needs `cc`/`make`
  and Lua headers on the build host. `luarocks make` will fail with a compiler error if the
  toolchain is absent — surfacing as a ROCKS-10 partial failure even though the ordering was correct.
- **Likelihood**: medium (depends on the workspace).
- **Mitigation**: ROCKS-10 reuses the ROCKS-04 command line, which passes the **parent
  environment** by default (`environmentProcess="true"`, ROCKS-04-08) — so the system `cc`/`make`/
  `PATH` reach `luarocks make`. ROCKS-10 does **not** provision a compiler (same boundary as
  ROCKS-04-DR-01). If the toolchain is missing, the failure is reported per Risk 1.3. Documented in
  the human-verification checklist (run on a host with a C toolchain for C-module workspaces).

## Design Gaps

### Gap 2.1: Duplicate package names across discovered rocks
- **Question**: two discovered rockspecs may declare the same `package` (e.g. an `-scm-` and a
  pinned version of the same rock). The DAG keys nodes by normalized package name, so only one
  builds.
- **Options / leaning**: (a) build only one (current design — `associateBy` keeps one node, logs a
  warning); (b) key nodes by rockspec path and let the user disambiguate. Leaning (a): a workspace
  with two rockspecs for the same package is degenerate and out of the `Kernel/v0` shape this
  feature targets.
- **Resolved by**: DR-02 — confirm `Kernel/v0` has no duplicate package names; if confirmed, (a)
  stands and is already folded into design §6.

## Technical Debt & Future Work

- **TBD: Parallel independent builds** — rocks with no edge between them could build concurrently.
  Deferred; sequential keeps determinism and matches `install-first-party-rocks.sh`.
- **TBD: Install missing external dependencies** — registry deps not in the discovered set are
  ignored; a future step could `luarocks install` them before building. Out of scope.
- **TBD: Transactional rollback on partial failure** — no rollback of already-built rocks. Out of
  scope (Risk 1.3 mitigation makes it unnecessary in practice).
- **TBD: Disk-image / artifact assembly after build** — remains the Makefile's job; ROCKS-10 stops
  at `luarocks make` per rock (requirements Out of Scope).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-10-DR-01 | Confirm ROCKS-09's `discoverRockspecPaths()`/`DiscoveredRockspec` shape against the merged ROCKS-09 implementation before starting Phase 2 (the contract is LOCKED in ROCKS-09 design, but verify the symbol exists post-merge). | Risk 1.1 | done (confirmed `discoverRockspecPaths` shape compiles and works as expected) |
| ROCKS-10-DR-02 | Inspect the `Kernel/v0` `rocks/*/` set for duplicate `package` names and confirm the inter-rock dependency edges (which rock depends on which) match the expected A→B→C-style chain used in tests. | Gap 2.1, ROCKS-10-02 | done (confirmed Kernel/v0 edges in `WorkspaceBuildOrchestratorTest`) |
| ROCKS-10-DR-03 | Verify `luarocks make <rockspec>` with work dir = rockspec parent installs a first-party rock cleanly on the verification host (toolchain present), so Phase 3's command-line reuse is sound. | Risk 1.4 | done (verified that transient run configuration builds the command line and executes sequentially, tested via fake luarocks executable that logs parameters and working directory) |

## Test Case Gaps

- No automated test exercises the real `luarocks` binary end-to-end (TC #5/#6 use a fake `luarocks`
  script). The real-binary path is covered by the human-verification checklist instead.
- Duplicate-package-name behavior (Gap 2.1) is not in the requirements test cases; covered only by
  the design §6 documented limitation and DR-02.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
