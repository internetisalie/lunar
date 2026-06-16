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

### 1.2 Split the Dockerfile into targets — *solo* — ✅ DONE
- Refactored `docker/Dockerfile` into a multi-stage `target` layering:
  - `build` — ubuntu:24.04 + JDK21 + git/build deps + fontconfig/fonts (headless unit tests).
  - `ide` — `FROM build` + xvfb + native render libs + **pre-staged GoLand baked into a layer**
    (caches the ~1.2 GB so integration runs don't re-download it).
  - `integration-test` — `FROM ide`; new `integration-test-entrypoint.sh` starts Xvfb then
    `exec ./gradlew integrationTest` (exit code propagates for CI; `sudo chown`s the gradle-cache
    volume since a fresh named volume mounts root-owned). Repo bind-mounted at `/workspace`.
  - `vnc` — `FROM ide` + x11vnc/openbox/firefox + bundled plugin (today's interactive-debug image;
    still tagged `lunar-ide:latest`).
- Updated `docker/docker-helper.sh`: `build [target]` selects a target (default `vnc`); new
  `integration-test [gradle-args]` command runs the test image with the repo + a persistent
  `lunar-gradle-cache` volume. README "Build Targets" section documents all four.
- **Acceptance:** ✅ all four `docker build --target …` succeed; `vnc` verified serving VNC on
  :5900 (IDE launched, X display 1920×1080); `integration-test` entrypoint verified end-to-end
  (Xvfb + JDK21 + bind-mounted gradle wrapper resolve — `./gradlew --version` green). The
  `integrationTest` task itself passing is **2.2**'s scope.
- **Depends on:** nothing. (Addresses **Q2**.)

## Phase 2 — Verification infrastructure

### 2.1 COMP-03 durable regression guard — *solo* — ✅ DONE
- Added `LuaCrossFileCompletionHeavyTest` (commit `d21b7257`): a heavy project fixture +
  real-disk `TempDirTestFixtureImpl` with an `EmptyModuleFixtureBuilder` source content root, so
  `require()` resolution (real `LocalFileSystem`) and stub indexing work in-process. Reproduces
  `main → module_b → module_a` and asserts completion offers `helper_from_b` (direct) +
  `helper_from_a` (transitive). Gotchas pinned for next time: a module-less heavy project indexes
  nothing (must add a source content root), and heavy module building + PSI/VFS writes need the EDT
  (wrap lifecycle + body in `runInEdtAndWait`). Light `@Disabled` test kept as a pointer.
- **Acceptance:** ✅ test green; full suite 830 passed, 0 failed, 1 skipped (the pointer).
  (Completes **Q1**'s durable half — #1b.)
- **Depends on:** 1.1 (green baseline).

### 2.2 Get one integration test green end-to-end — *solo* — ✅ DONE
- `LuaCrossFileCompletionIntegrationTest` now passes in the docker `integration-test` image on
  GoLand 2026.1.3 (1 test, 0 failures, ~23s). The controlled image made the real failure legible —
  it was **not** the GCE-builder's exit-code-3/remote-dev mystery but a chain of four concrete
  gates, each found by clearing the prior one (commit `06172131`):
  1. **Bind-mount UID mismatch** — `lunar` was UID 1001 (ubuntu:24.04's default `ubuntu` squats on
     1000); gradle couldn't write `/workspace`. Fixed by baking the host UID/GID into the image
     (commit `9d9d8726`).
  2. **Commercial license modal** — fresh ide-starter config has no license → GoLand's Activation
     dialog blocked to a 5-min timeout. `IdeProductResolver.applyLicense` injects a key
     (`LUNAR_LICENSE_KEY` / host `goland.key`) via `IDETestContext.setLicense`; docker-helper mounts
     the host key. (Also caught a *self-inflicted* regression: removing the "dead" `testVersion`
     downgraded the ide-starter IDE to its 2024.3.1 fallback — `IdeProductResolver` reads it; fixed
     in `d3e259eb`.)
  3. **`completion.command.report.dir` unset** — `assertCompletionCommandContains` needs that
     system property; set via `applyVMOptionsPatch`.
  4. **No exit after the script** — the script-chain `runIDE` (unlike `runIdeWithDriver`) has no
     implicit quit; the open lookup idled to timeout. Append `closeLookup().exitApp()`.
- **Acceptance:** ✅ passes in the docker `integration-test` target. (Addresses **Q3**.)
- **Depends on:** 1.2 Dockerfile split.
- **Follow-ups (not blocking):** propagate `applyLicense` is already on all launch sites; consider
  pinning the ide-starter IDE to the baked image build to skip its separate ~1.2 GB download;
  retrofit the shallow no-IDE tests (that's 3.3).

## Phase 3 — Raise the floor (highest leverage)

### 3.1 Grounding audit of planned features — *sequential subagents (one per design)* — ✅ DONE (Wave 7 batch)
- Ran one grounding subagent per design, **sequentially** (one at a time — provider under load),
  each grepping every named PSI type / method / service / extension point against `src/main` +
  `src/main/gen` + `plugin.xml`, flagging fictional/EmmyLua APIs and duplications.
- **Scope (this pass):** the next-to-implement batch = Wave 7 serial formatter cluster
  `FORMAT-03/04/05/06` (Wave 6 completion features are already `done`, so they were not the
  frontier). All four **PASS (grounded)** — no fictional/EmmyLua symbols, no duplications; the
  INSP-03/09 failure mode did not recur. Per-design verdicts + non-blocking implementer notes
  recorded in `docs/planning-gaps.md` → "Grounding audit — Wave 7 formatter cluster".
- **Acceptance:** ✅ each audited design marked PASS with evidence; `docs/planning-gaps.md` updated.
  (Addresses **Q7**.)
- **Depends on:** Q8 (done — provides the rubric).
- **Remaining (next waves, not blocking Wave-6 readiness):** the other `planned` designs (Wave 8
  intentions, Wave 10 TOOL/ROCKS, …) still need a grounding pass before their wave begins.

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

**All five exit-criteria boxes are now ticked.** Remaining Phase-3 items are quality polish, not
readiness gates: 3.2 (coverage push, Kover-guided) and 3.3 (integration quality/depth — retrofit the
shallow no-assert integration tests; the docker harness from 2.2 is ready). Done so far: Q1, Q8,
Phase 1.1, 1.2, 2.1, 2.2, **3.1** (Wave 7 formatter grounding audit — all PASS), plus the GoLand
2026.1.3 bump. Working tree clean apart from `scratch/`.

**Integration-test harness now works (use it for 3.3):** `./docker-helper.sh build integration-test`
then `./docker-helper.sh integration-test [--tests "*Foo*"]`. It mounts the host GoLand license
(`LUNAR_LICENSE_KEY`, default newest `~/.config/JetBrains/GoLand*/goland.key`) so the commercial
Activation modal doesn't block; the `lunar` user is UID-matched to the host for `/workspace` writes;
a `lunar-gradle-cache` volume persists downloads. **Version knobs (three, all 2026.1.3, independent
on purpose):** gradle `platformVersion` (compile+unit), gradle `testVersion` (ide-starter IDE — read
by `IdeProductResolver.kt`, NOT the gradle plugin), docker `IDE_VERSION` (VNC IDE). For a script-chain
`runIDE` completion test, end the chain with `closeLookup().exitApp()` and set
`completion.command.report.dir` or it hangs to the timeout.

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
- [x] Dockerfile targets build; integration-test image exists (1.2).
- [x] COMP-03 has an in-process regression guard (2.1).
- [x] At least one integration test passes in the docker harness (2.2).
- [x] Next-to-implement designs pass the grounding audit (3.1) — Wave 7 formatter cluster, all PASS.
