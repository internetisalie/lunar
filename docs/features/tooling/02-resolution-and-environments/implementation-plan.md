---
id: "TOOLING-02-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-02"
folders:
  - "[[features/tooling/02-resolution-and-environments/requirements|requirements]]"
---

# TOOLING-02: Implementation Plan

Precondition: TOOLING-01 has landed (`toolchain.model` types, `LuaToolKindRegistry`,
`LuaToolchainRegistry` with `tool`/`tools`/`registerTool`/`unregisterTool`/`unregisterByEnvironment` and the
app-state `globalBindings`). Legacy code (`tool/`, `rocks/env/`, the `InterpreterMode`
machine) stays untouched and running — deletion/cutover is TOOLING-05.

## Phases

### Phase 1: Shared event & state plumbing [Must]
- **Goal**: the topic, event payload, and persisted project state exist and round-trip.
- **Tasks**:
  - [x] Create (or extend, if TOOLING-01 created it) `net.internetisalie.lunar.toolchain.registry.LuaToolchainListener` + `LuaToolchainEvent` + the `LuaToolchainChange` enum with all ten change values (per contract §4) — realizes design §2.3. *(Reused as-is from TOOLING-01: all ten enum values already present.)*
  - [x] Create `net.internetisalie.lunar.toolchain.model.LuaEnvironmentState` in serializer-friendly form (skip if TOOLING-01 shipped it; align shape if it did) — realizes design §2.2.
  - [x] Create `net.internetisalie.lunar.toolchain.registry.LuaToolchainProjectSettings` (`@Service(PROJECT)` + `@State(name="LuaToolchainProjectSettings", storages=[Storage("lunar.xml")])`) persisting a top-level `LuaToolchainProjectState` with fields `bindings`/`environments`/`activeEnvironmentId`/`kindOptions`, plus `normalizeDir` and read accessors (`environments()`, `activeEnvironment()`) — realizes design §2.2. *(Phase 1: state + persistence + read accessors + `normalizeDir` only; mutators are Phase 2.)*
  - [x] Create `net.internetisalie.lunar.toolchain.registry.LuaKindOptionKeys` — realizes design §2.4.
- **Exit criteria**: `XmlSerializer` round-trip test green (TC 8); suite compiles with no
  behavior change anywhere else.

### Phase 2: Mutators & events [Must]
- **Goal**: every binding/environment/option mutation works and fires exactly the enumerated
  events.
- **Tasks**:
  - [x] Implement `LuaToolchainProjectSettings.setBinding` with no-op suppression + single-runtime invariant — realizes design §3.9, §3.3.
  - [x] Implement lifecycle ops `upsertEnvironment` / `upsertEnvironmentAndActivate` / `activateEnvironment` / `deactivateEnvironment` / `removeEnvironment` (incl. env-owned tool unregistration via `registry.unregisterByEnvironment` and pooled-thread dir delete) — realizes design §3.4.
  - [x] Implement `setKindOption` / `effectiveKindOption` (project scope) — realizes design §3.8.
  - [x] Augment TOOLING-01 `LuaToolchainRegistry`: add §3.3 single-runtime invariant to `setGlobalBinding` and change `kindOption` to return non-null `String` (`""` when absent) — realizes design §2.9. *(`setGlobalBinding`/`globalBindings()`/`setKindOption` already landed in TOOLING-01.)*
- **Exit criteria**: TC 9, 10, 11, 12 (bindings-untouched part), 15, 16 green; an
  event-recording test subscriber observes exactly the design §3.9 table per mutation.

### Phase 3: Resolver [Must]
- **Goal**: the one precedence implementation with detailed outcomes.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.resolve.LuaToolResolution` (+ `ResolutionSource`, `SkippedBinding`, `SkipReason`) — realizes design §2.1.
  - [x] Create `net.internetisalie.lunar.toolchain.resolve.LuaToolResolver` (`@Service(APP)`): `resolveDetailed` (§3.1 five tiers + skip rules), `resolve`, `resolveAll`, `notConfiguredMessage` — realizes design §3.1.
  - [x] Implement `resolveIn` (strict per-environment, no fallback) — realizes design §3.2.
  - [x] Implement `resolveRuntime` / `resolveRuntimeDetailed` (capability iteration over runtime kinds) — realizes design §3.3.
- **Exit criteria**: TC 1–7 green, including the prompt-exemplar sequence
  (env C → deactivate → B → unbind → A) and stale-skip cases TC 4–5.

### Phase 4: Target synchronization [Must]
- **Goal**: target/language level follow the effective runtime; mode machine semantics fully
  replaced.
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.toolchain.resolve.LuaTargetSynchronizer` (`@Service(PROJECT)`, `Disposable`, app-bus subscription, `lastAppliedRuntimeId` guard, fallback-tier exclusion, target stickiness, EDT apply + `PlatformLibraryIndex.reload`) — realizes design §2.5, §3.5.
  - [ ] Create `net.internetisalie.lunar.toolchain.resolve.LuaTargetSyncStartup : ProjectActivity` calling `ensureSynchronized()` — realizes design §2.5.
  - [ ] Register the startup activity in `plugin.xml` — realizes design §7.
- **Exit criteria**: TC 13, 14 green; full TC 12 green (activate/deactivate never mutates
  bindings; no stash fields exist); existing suite still green (synchronizer is inert while
  no bindings/environments exist in the new state).

### Phase 5: Detection & adoption [Must]
- **Goal**: env-shaped directories are detected and adoptable end to end (registration timing
  of the startup activity itself deferred to TOOLING-05 per design §2.8).
- **Tasks**:
  - [ ] Create `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentDetector` (conventional names, descriptor-driven `isEnvShaped`, `isKnownDirectory`) — realizes design §2.6, §3.6.
  - [ ] Create `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentAdopter` (`adopt`: register every found kind binary with `environmentId`, then `upsertEnvironmentAndActivate`) — realizes design §2.7, §3.7.
  - [ ] Create `net.internetisalie.lunar.toolchain.discovery.LuaEnvironmentDetectionStartup : ProjectActivity` (notification + Adopt action; **no plugin.xml registration in this feature**) — realizes design §2.8.
- **Exit criteria**: TC 17 green (detector heuristics against temp-dir fixtures; adopter
  against a stub registry); no duplicate-notification regression (legacy
  `HererocksDetectStartup` remains the only registered detector until TOOLING-05).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-02-01 Precedence resolution | M | Phase 3 |
| TOOLING-02-02 Outcome reporting | M | Phase 3 |
| TOOLING-02-03 Per-environment resolution | M | Phase 3 |
| TOOLING-02-04 Runtime resolution | M | Phase 3 (invariant enforced in Phase 2 mutators) |
| TOOLING-02-05 Project persistence | M | Phase 1 |
| TOOLING-02-06 Binding mutation API | M | Phase 2 |
| TOOLING-02-07 Environment lifecycle | M | Phase 2 |
| TOOLING-02-08 Mode machine replaced | M | Phases 2 + 4 (semantics); legacy deletion in TOOLING-05 |
| TOOLING-02-09 Target synchronization | M | Phase 4 |
| TOOLING-02-10 Change events | M | Phases 1 + 2 |
| TOOLING-02-11 Stale-binding fallback | M | Phase 3 |
| TOOLING-02-12 Kind-scoped options | M | Phases 1 + 2 |
| TOOLING-02-13 Removal cleanup | S | Phase 2 |
| TOOLING-02-14 Detection & adoption | M | Phase 5 |

## Verification Tasks

- [ ] `LuaToolchainProjectSettingsTest` — round-trip (TC 8), lifecycle (TC 10–11),
      bindings-untouched invariant (TC 12), kind options (TC 15), removal cleanup (TC 16).
      Use a `ToolchainSettingsTestCase` base that resets the new app+project state in
      `setUp`/`tearDown` (pattern:
      `src/test/kotlin/net/internetisalie/lunar/rocks/env/EnvSettingsTestCase.kt:13-41` —
      the light fixture shares one project across classes).
- [ ] `LuaToolchainEventsTest` — recording subscriber asserts the design §3.9
      mutation→event table and no-op suppression (TC 9).
- [x] `LuaToolResolverTest` — TC 1–7 (precedence, detailed outcomes, `resolveIn`,
      `resolveRuntime` + invariant) and stale skips (TC 4–5); seed tools directly into the
      registry state with explicit `LuaToolHealth` values (no disk probing in resolver
      tests).
- [ ] `LuaTargetSynchronizerTest` — TC 13–14 via `onEvent` invoked directly with crafted
      events + seeded runtime tools; EDT work via
      `EdtTestUtil.runInEdtAndWait` per the threading lesson in the agent guide.
- [ ] `LuaEnvironmentDetectorTest` / `LuaEnvironmentAdopterTest` — TC 17 with
      `myFixture.tempDirFixture` file layout (mirrors
      `src/test/kotlin/net/internetisalie/lunar/rocks/env/HererocksEnvDetectorTest.kt`).
- [ ] Full gate: `tooling/gce-builder/gce-builder.sh run test` then
      `run "ktlintFormat ktlintCheck"`; regenerate `docs/status.md`
      (`python3 scripts/gen_status.py`) after status changes.
- [ ] Live verification is deferred to TOOLING-05/06 (no user-visible entry points exist
      until consumers/UI cut over); the detection notification can be smoke-checked then via
      the `verify-in-ide` skill.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Shared event & state plumbing | done | Must |
| Phase 2: Mutators & events | done | Must |
| Phase 3: Resolver | done | Must |
| Phase 4: Target synchronization | todo | Must |
| Phase 5: Detection & adoption | todo | Must |
