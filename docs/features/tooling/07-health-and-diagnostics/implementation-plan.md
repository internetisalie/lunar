---
id: "TOOLING-07-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-07"
folders:
  - "[[features/tooling/07-health-and-diagnostics/requirements|requirements]]"
---

# TOOLING-07: Implementation Plan

Sequencing note: this feature is last in the epic order (`00 → … → 07`, PRD roadmap). It
requires TOOLING-01/02/03 to be implemented (model, registry mutation + topic, project
state, resolver, exec-service PROBE class) and lands alongside TOOLING-05's deletion of
`tool/health/*` and TOOLING-06's `LuaToolchainConfigurable` (the banner link target).
Phases 1-2 can start as soon as 01/02/03 exist; Phase 3 needs 06's configurable class to
compile (or a temporary settings-id string, replaced before merge).

## Phases

### Phase 1: Health checker on the new model [Must]
- **Goal**: pure 3-stage check producing the contract §2.3 `LuaToolHealth`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.health.LuaToolHealthChecker` +
        `LuaToolCheckResult` (design §2.1) implementing the §3.1 algorithm with the
        injectable `LuaToolProbe`.
  - [x] Unit tests `toolchain/health/LuaToolHealthCheckerTest` with a recording fake
        probe (no IDE fixture; mirrors the hermetic style of the legacy
        `tool/health/LuaToolHealthCheckerTest.kt`).
- **Exit criteria**: TC-TOOLING-07-01/02/03 pass, including the zero-probe-invocation
  assertions for stage-1 failures and the mtime gate.

### Phase 2: Monitor — VFS watching, batching, registry writes, notifications [Must]
- **Goal**: reactive revalidation writing health only through the registry, with deduped
  balloons and env-root handling.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.health.LuaToolHealthMonitor`
        (design §2.2): watch sets + match predicate (design §3.2) with the predicate
        extracted as a testable pure function; `MergingUpdateQueue` batching;
        `revalidateAll` pass (design §3.3) incl. env-reason override, transition
        collection, env-deleted dedup set, EDT marshaling.
  - [x] Create `net.internetisalie.lunar.toolchain.health.LuaToolHealthStartup`
        (design §2.3).
  - [x] Tests: predicate unit tests (TC-10); `BasePlatformTestCase` tests for transition
        dedup (TC-06), env-root deletion (TC-07), and write-path purity/topic firing
        (TC-09) using temp-dir binaries and a topic-subscribing message-bus connection.
- **Exit criteria**: TC-TOOLING-07-06/07/09/10 pass; no call site outside
  `registry.updateToolCheck` mutates health (grep gate: `health =`/`isValid` writes absent
  from `toolchain/health`).

### Phase 3: Editor banner provider [Must]
- **Goal**: engaged-kind + runtime banner rules, linking the Toolchain page.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.health.LuaToolEditorNotificationProvider`
        (design §2.4) implementing §3.4 (`engaged`, `intendedTool`, rule ordering,
        dismiss action wired to the monitor).
  - [x] Add the `plugin.xml` replacement block (design §7): `postStartupActivity` +
        `editorNotificationProvider` (coordinate with TOOLING-05, which deletes the
        legacy `plugin.xml:425-431` block in the same change set).
  - [x] Tests: `BasePlatformTestCase` banner data-collection tests (TC-04, TC-05),
        covering the inspection-profile gate for luacheck (toggle `LuaCheck` via
        `myFixture.enableInspections`/profile API) and dismissal.
- **Exit criteria**: TC-TOOLING-07-04/05 pass; banner opens the TOOLING-06 configurable.

### Phase 4: Diagnostics [Should / Could]
- **Goal**: full-toolchain snapshot logging (+ optional action).
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.health.LuaToolDiagnostics`
        (design §2.5) emitting the §4.1 grammar; call it from the §3.3 pass.
  - [x] [Could] Create `LuaToolchainDiagnosticsAction` (design §2.6) + `<action>`
        registration (design §7).
  - [x] Test: snapshot format assertions (TC-08) by capturing log output or refactoring
        emission through a `(String) -> Unit` sink parameter defaulting to `log::info`.
- **Exit criteria**: TC-TOOLING-07-08 passes; every §4.1 line class appears for a
  populated registry.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-07-01 Three-stage health check | M | Phase 1 |
| TOOLING-07-02 Registry-mediated health writes | M | Phase 2 (write path), Phase 1 (no checker write-back) |
| TOOLING-07-03 Reactive VFS monitoring | M | Phase 2 |
| TOOLING-07-04 Editor banner | M | Phase 3 |
| TOOLING-07-05 State-transition notifications | M | Phase 2 |
| TOOLING-07-06 Diagnostics snapshot | S | Phase 4 |
| TOOLING-07-07 Diagnostics action | C | Phase 4 |

## Verification Tasks
- [ ] Unit/fixture tests per phase — cover TC-TOOLING-07-01…10 (mapping above).
- [ ] `tooling/gce-builder/gce-builder.sh run test` green (never local gradle);
      `run "ktlintFormat ktlintCheck"` before committing.
- [ ] Live verification per the `verify-in-ide` skill: delete a bound luacheck binary and
      an env root in the sandbox IDE; observe one balloon each, the banner with working
      *Configure toolchain* / *Dismiss* actions, and `[TOOLCHAIN-DIAG]` lines in
      `idea.log`.
- [ ] Grep gates: no `LuaProcessUtil` use in `toolchain/health`; no health mutation
      outside `updateToolCheck` calls.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Health checker on the new model | done | Must |
| Phase 2: Monitor — VFS, batching, writes, notifications | done | Must |
| Phase 3: Editor banner provider | done | Must |
| Phase 4: Diagnostics | done | Should/Could |
