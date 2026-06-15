---
id: "WAVE6-READINESS"
title: "Wave 6 Readiness — Tech-Debt Plan"
type: "plan"
status: "in_progress"
priority: "high"
folders:
  - "[[features]]"
---

# Wave 6 Readiness — Tech-Debt Plan

Remediation plan for the eight tech-debt questions raised before starting **Wave 6
(Completion Polish)**. Execute the phases in order; each task names who runs it (solo / subagent
/ workflow), its acceptance bar, and dependencies.

## Guiding principle

**In-process heavy fixtures are the default verification tool; UI integration tests are reserved
for things that genuinely need a running IDE** (debugger, run configs, real rendering). Anything
that only needs a real filesystem + real indexing (e.g. cross-file completion) belongs in a
`TempDirTestFixture`-backed unit test, not the fragile ide-starter harness.

## Status snapshot (what's already done)

- **Q1 — verify COMP-03 (shortest path):** ✅ DONE. Eyeballed in real GoLand via VNC; both direct
  (`helper_from_b`) and transitive (`helper_from_a`) symbols appear. Committed (`674e2e1e`).
- **Q8 — raise the plan-feature bar:** ✅ DONE. Added a **Grounding** axis (verify every named
  symbol exists in *this* repo; name existing components to extend/replace) to
  `.agents/skills/plan-feature/SKILL.md`, `templates/README.md`, `templates/design.md`; recorded
  the gap in `docs/planning-gaps.md` (committed `c1eef15b`). Note: `.agents/` is gitignored
  (local-only) — the SKILL/template edits live on this machine only.

## Phase 1 — Foundation (do first; unblocks everything else)

### 1.1 Restore a green test baseline — *solo* — ✅ DONE
- The baseline was more broken than the reported 5: **12 pre-existing failures** —
  `LuaFindUsagesTest` (3), `LuaFindUsagesCrossFileTest` (1), `LuaSafeDeleteTest` (2),
  `LuaNavigationTest` (6), and `LuaRequireTypeFlowTest` (1).
- Root cause (11 of them): a `LuaNameReference` lives on the `LuaNameRef` composite, not the
  IDENTIFIER leaf. `LuaBaseElement.getReferences()` dropped the element's own `getReference()` (broke
  Go to Declaration / `findReferenceAt`), and the word-index search inspects only the leaf's
  references (broke Find Usages / Safe Delete). Fixed in `LuaBaseElements.getReferences()` +
  `LuaNameReferenceSearcher` (PSI-scan candidate files). Commit `1537e6c5`. The held
  `LuaNameReference.kt` WIP was unnecessary and dropped.
- The 12th (`LuaRequireTypeFlowTest`) was an unfinished test ending in an unconditional
  `throw AssertionError` debug dump; finished with real assertions. Commit `74a5220d`.
- **Acceptance:** ✅ `./gradlew test` → 829 passed, 0 failed.

### 1.2 Split the Dockerfile into targets — *solo*
- Refactor `docker/Dockerfile` into a multi-stage `target` layering:
  - `build` — ubuntu + JDK21 + git/build deps only (reproducible `./gradlew build/test`).
  - `ide` — `FROM build` + xvfb + **pre-staged GoLand baked into a layer** (the "pre-installed IDE
    image"; caches the ~1.2 GB so integration runs don't re-download it).
  - `integration-test` — `FROM ide`, entrypoint runs `./gradlew integrationTest`.
  - `vnc` — `FROM ide` + x11vnc/openbox/firefox (today's interactive-debug behavior).
- Update `docker/docker-helper.sh` to build/select targets.
- **Acceptance:** `docker build --target build|ide|integration-test|vnc` each succeed; `vnc` target
  still serves the IDE on :5900.
- **Depends on:** nothing. (Addresses **Q2**.)

## Phase 2 — Verification infrastructure

### 2.1 COMP-03 durable regression guard — *solo*
- Add an in-process heavy test (`TempDirTestFixture` on real disk) that reproduces the transitive
  require chain so `LocalFileSystem` resolution + real stub indexing work — the regression guard the
  light fixture cannot provide. Keep the `@Disabled` light unit test as a pointer.
- **Acceptance:** test asserts `helper_from_a` + `helper_from_b` appear; runs green in `./gradlew
  test`. (Completes **Q1**'s durable half — #1b.)
- **Depends on:** 1.1 (green baseline).

### 2.2 Get one integration test green end-to-end — *solo*
- Run `LuaCrossFileCompletionIntegrationTest` in the new `integration-test` docker image; diagnose
  the GoLand **exit-code-3 / remote-dev** failure seen on the GCE builder (likely a launch flag /
  headless config). Pin the fix into the image.
- **Acceptance:** the integration test passes in the docker `integration-test` target. (Addresses
  **Q3**.)
- **Depends on:** 1.2 Dockerfile split.

## Phase 3 — Raise the floor (highest leverage)

### 3.1 Grounding audit of planned features — *workflow (subagent fan-out)*
- Run one verification subagent per planned `design.md`, prioritized by `execution-order.md`, using
  the new **Grounding** rubric: grep every named PSI type / method / service / extension point;
  flag foreign (EmmyLua) APIs and any existing component the design duplicates. (This is the same
  check that caught the INSP-09 `LuaLanguageLevelAnnotator` duplication.)
- **Scope:** the **next N** features slated for implementation (not all 59 at once) — at minimum the
  Wave 6 completion features about to be built.
- **Acceptance:** each audited design is marked PASS (grounded) or gets a fix list; update
  `docs/planning-gaps.md`. (Addresses **Q7**.)
- **Depends on:** Q8 (done — provides the rubric).

### 3.2 Unit-test coverage push — *solo, Kover-guided*
- Generate a Kover baseline; target gaps in high-logic/high-risk packages (analysis, type engine,
  completion).
- **Acceptance:** coverage rises on the targeted packages; no new red. (Addresses **Q6**.)
- **Depends on:** 1.1 (green baseline).

### 3.3 Integration-test quality & depth — *solo, selective*
- **Quality (Q4):** retrofit the shallow "no-crash + println" integration tests
  (`ProjectOpenIntegrationTest`, `LuaProgramExecution*`) with real assertions, using
  `assertCompletionCommandContains` / output assertions as the model.
- **Depth (Q5):** add IDE-level coverage only where it genuinely needs a running IDE — run-config
  execution output, debugger stepping (the docs' "Future" items). Do **not** duplicate what heavy
  fixtures cover.
- **Acceptance:** targeted integration tests assert behavior (not just absence of crash).
- **Depends on:** 2.2 (reliable harness).

## Dependency summary

```
Q8 (done) ─────────────► 3.1 grounding audit
1.1 green baseline ──┬──► 2.1 COMP-03 heavy fixture
                     └──► 3.2 coverage push
1.2 Dockerfile split ──► 2.2 integration green ──► 3.3 integration quality/depth
```

## Resume pointer

**Next up: 1.2 (Dockerfile split).** Done so far: Q1, Q8, and Phase 1.1 (all committed). Working
tree clean apart from `scratch/`. The 1.2 docker work and 2.2 integration run benefit from the
operational notes below.

### Operational notes (hard-won this session)
- **Driving the IDE over VNC:** open a project from a **container-owned dir** (e.g.
  `/home/lunar/<proj>` created via `docker exec -u lunar`), **not** the host bind-mount
  `/home/lunar/test` — that mount is owned by the host UID, so the container IDE can't create
  backup/save files and falls into a "Cannot Save Files" retry loop. The `vnc` MCP tool's key names
  are `Enter` / `BackSpace` (NOT `Return` / `Delete`, which type literal letters).
- **Refreshing the plugin in the running container** without a rebuild: `docker cp` the freshly
  built `build/distributions/.../lunar-*.jar` over
  `/home/lunar/.local/share/JetBrains/GoLand2026.1/lunar/lib/lunar-*.jar`, then `docker restart lunar-ide`.
- **2.2's known failure mode:** on the GCE builder the ide-starter run exited **code 3** with
  EventBus connection errors and `rdserver`/`RemoteModCommandExecutor` warnings (GoLand launched in
  remote-dev/backend mode), and produced no `idea.log`. The command script itself was correct. The
  controlled docker `integration-test` image (1.2) is the place to pin a launch-flag fix.

## Exit criteria for "ready to start Wave 6"

- [x] `./gradlew test` green (1.1) — 829 passed, 0 failed.
- [ ] Dockerfile targets build; integration-test image exists (1.2).
- [ ] COMP-03 has an in-process regression guard (2.1).
- [ ] At least one integration test passes in the docker harness (2.2).
- [ ] Wave 6's planned designs pass the grounding audit (3.1).
