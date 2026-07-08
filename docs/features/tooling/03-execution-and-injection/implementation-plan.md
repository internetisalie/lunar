---
id: "TOOLING-03-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-03"
folders:
  - "[[features/tooling/03-execution-and-injection/requirements|requirements]]"
---

# TOOLING-03: Implementation Plan

Precondition: TOOLING-01 (`LuaToolKindRegistry`) and TOOLING-02 (`LuaToolResolver`,
`LuaToolchainListener.TOPIC`) are implemented — this feature compiles against them.
Legacy code (`util/LuaProcessUtil`, `tool/LuaToolEnvironment`,
`tool/LuaTerminalEnvironmentService`, `command/LuaCommandLine`) is **left in place** and
deleted by TOOLING-05; the only legacy touch here is re-pointing
`META-INF/lunar-terminal.xml` (Phase 4).

## Phases

### Phase 0: Dependency check [Must]
- **Goal**: confirm the TOOLING-02 surface this feature calls exists as contracted.
- **Tasks**:
  - [ ] Verify `LuaToolResolver.resolve(project, kindId)`, a RUNTIME resolution entry
        point (`resolveRuntime` or equivalent), `LuaToolKindRegistry.allKinds()`
        declaration-ordered, and app-level `LuaToolchainListener.TOPIC` — per
        [../tooling-architecture.md](../tooling-architecture.md) §2–§4. Any mismatch is
        resolved by updating the architecture contract first, not by improvising here.
- **Exit criteria**: the four symbols compile from a scratch file; no contract deviation.

### Phase 1: Execution service [Must]
- **Goal**: the single subprocess entry point.
- **Tasks**:
  - [x] Create `toolchain/exec/LuaExecTimeout.kt` (design §2.1).
  - [x] Create `toolchain/exec/LuaExecResult.kt` — `LuaExecOutcome` + `LuaExecResult`
        (design §2.2).
  - [x] Create `toolchain/exec/LuaToolExecutionService.kt` — `capture`/`stream` +
        `@TestOnly` `*WithMillis` internals, read-lock escape, soft EDT assert,
        indicator handling (design §2.3, §3.1, §3.2).
- **Exit criteria**: TCs 1–9 pass (`LuaToolExecutionServiceTest`, plain
  `BasePlatformTestCase`-free JUnit where possible; EDT-assert case uses
  `LoggedErrorProcessor`).

### Phase 2: Environment model & builder [Must]
- **Goal**: one environment computation with the project-scoped cache.
- **Tasks**:
  - [x] Create `toolchain/exec/LuaLaunchEnvironment.kt` with `applyTo` (design §2.4,
        §3.5 — port the assertions of the existing `tool/LuaToolEnvironmentTest.kt` to
        the new class).
  - [x] Create `toolchain/exec/LuaExecutionEnvironmentBuilder.kt` — `pathPrependDirs`
        (§3.3), `build(sourcePathOverride)` (§3.4), `@Volatile` cache +
        `LuaToolchainListener.TOPIC` subscription (§3.7), `forProject` facade (§2.5).
- **Exit criteria**: TCs 10–16, 20 pass (`LuaExecutionEnvironmentBuilderTest` with a
  stubbed resolver; TC 13 as `BasePlatformTestCase` with a fixture rockspec, mirroring
  existing `RockspecRunPathProvider` tests).

### Phase 3: Interpreter command lines [Must]
- **Goal**: the `command/LuaCommandLine.kt` replacement.
- **Tasks**:
  - [ ] Create `toolchain/exec/LuaInterpreterCommandLines.kt` — `forBinary` (jar case
        kept) + `forProject` (design §2.6, §3.6).
- **Exit criteria**: TCs 18–19 pass; `forProject` returns null with no resolvable
  runtime and applies the §3.4 environment otherwise (stubbed resolver test).

### Phase 4: Terminal customizer move [Must]
- **Goal**: terminal PATH injection sourced from the builder; single live registration.
- **Tasks**:
  - [ ] Create `toolchain/terminal/LuaShellExecOptionsCustomizer.kt` (design §2.7).
  - [ ] Edit `META-INF/lunar-terminal.xml`: implementation →
        `net.internetisalie.lunar.toolchain.terminal.LuaShellExecOptionsCustomizer`
        (design §7.1). The old `tool/terminal/` class stays on disk (deleted in
        TOOLING-05) but is no longer registered.
- **Exit criteria**: TC 17 passes (recording fake `MutableShellExecOptions`); live VNC
  check per human verification (terminal `which` resolves bound tools and the project
  `lua`).

### Phase 5: Docs, lint & status [Must]
- **Goal**: green gates and truthful status.
- **Tasks**:
  - [ ] `tooling/gce-builder/gce-builder.sh run "ktlintFormat ktlintCheck"` and
        `run test` (full suite green, regression-relative).
  - [ ] Update this feature's front-matter status as phases land; regenerate
        `docs/status.md` (`python3 scripts/gen_status.py`).
- **Exit criteria**: build gate green; status rollup reflects reality.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-03-01 | M | Phase 1 |
| TOOLING-03-02 | M | Phase 1 |
| TOOLING-03-03 | M | Phase 1 |
| TOOLING-03-04 | M | Phase 1 |
| TOOLING-03-05 | M | Phase 1 |
| TOOLING-03-06 | M | Phase 1 |
| TOOLING-03-07 | M | Phase 2 |
| TOOLING-03-08 | M | Phase 2 |
| TOOLING-03-09 | M | Phase 2 |
| TOOLING-03-10 | M | Phase 2 |
| TOOLING-03-11 | M | Phase 2 |
| TOOLING-03-12 | M | Phase 4 |
| TOOLING-03-13 | M | Phase 3 |
| TOOLING-03-14 | M | Phase 2 |
| TOOLING-03-15 | S | design §7.2 (no code; consumed by TOOLING-05) |

## Verification Tasks

- [x] `LuaToolExecutionServiceTest` — TCs 1–9, 23 (capture, stream, outcomes, timeout via
      `*WithMillis`, cancellation, EDT soft-assert, stdin).
- [x] `LuaLaunchEnvironmentTest` — TCs 14–15 + the ported `LuaToolEnvironmentTest`
      cases (empty dirs no-op, blank existing PATH, separator join) + TCs 21–22 applyTo.
- [x] `LuaExecutionEnvironmentBuilderTest` — TCs 10–13, 16, 20–22 (real registry seeded
      + bound; topic-invalidation round trip).
- [ ] `LuaInterpreterCommandLinesTest` — TCs 18–19.
- [ ] `LuaShellExecOptionsCustomizerTest` — TC 17 (reverse-prepend order).
- [ ] Live VNC pass (verify-in-ide skill): terminal PATH injection with a bound tool +
      runtime after a fresh registration (exercises the TC-16 staleness fix end to end).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 0: Dependency check | done | Must |
| Phase 1: Execution service | done | Must |
| Phase 2: Environment model & builder | done | Must |
| Phase 3: Interpreter command lines | todo | Must |
| Phase 4: Terminal customizer move | todo | Must |
| Phase 5: Docs, lint & status | todo | Must |
