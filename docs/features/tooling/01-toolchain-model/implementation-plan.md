---
id: "TOOLING-01-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TOOLING-01"
folders:
  - "[[features/tooling/01-toolchain-model/requirements|requirements]]"
---

# TOOLING-01: Implementation Plan

Sequences [design.md](design.md) into build-green phases. Everything lands **dark** — no
legacy code path is modified; the only shared file touched is `plugin.xml` (one added
`applicationService` entry, design §8). Build/test via
`tooling/gce-builder/gce-builder.sh run test` per repo policy.

## Phases

### Phase 1: Model types [Must]
- **Goal**: the complete `toolchain.model` package — pure data, no services.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.model.LuaToolKind` + `ProbeSpec` +
    `RuntimeProbeSpec` + `LanguageLevelRule` + `Capability` + `ProvisioningSpec` — design §2.1
  - [x] Create `net.internetisalie.lunar.toolchain.model.LuaRegisteredTool` + `Origin` +
    `LuaToolHealth` + `isUsable` extension + `LuaRuntimeInfo` — design §2.3 (incl. the
    `luaVersion` field, per contract §2.2)
  - [x] Create `net.internetisalie.lunar.toolchain.model.SemanticVersion` implementing the
    §3.6 parse/compare rules (port of `tool/LuaToolValidator.kt:183-208`; legacy copy untouched)
  - [x] Unit tests: `SemanticVersionTest` (TC 11, mirroring the existing coverage in
    `src/test/kotlin/net/internetisalie/lunar/tool/LuaToolValidatorTest.kt`), model-default and
    the TC 23 `isUsable` truth-table test
- **Exit criteria**: `run test` green; TC 11, 23 pass.

### Phase 2: Kind registry & inference [Must]
- **Goal**: the 8 built-in kinds as data + filename inference.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry` with
    `BUILT_IN` exactly per the design §4.1 table (ids, displayNames, binaryNames, args,
    timeouts, regexes, runtime specs, luarocks `minVersion`/`luaVersionRegex`) — design §2.2
  - [x] Implement `inferKind` (exact-then-glob, §3.5), reusing `patternFromGlob`/`isGlob`
    moved/copied into `toolchain.discovery` (from `platform/LuaInterpreterService.kt:245-269`)
  - [x] Unit tests: TC 21 (`lua` kind descriptor completeness) + TC 22 (`all()`/`findById`
    over the 8 built-ins); every §4.1 sample output line matches its kind's `versionRegex` with
    the expected capture; `inferKind` TC 14; busted hardened-regex negative test (error prose
    does not match)
- **Exit criteria**: TC 14, 21, 22 pass; regex table verified against §4.1 samples verbatim.

### Phase 3: Probe engine [Must]
- **Goal**: `LuaToolProbe` — the one probe.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.probe.LuaToolProbe` **interface** (app
    service, `companion object { getInstance() }`, contract §10.3) + `LuaToolProbeImpl` with the
    §3.4 algorithm: file checks → `LuaProcessUtil.capture` (`util/LuaProcessUtil.kt:17`) with
    sentinel-code handling → §3.4-step-4 stream merge → pure interpretation helper; returns
    `LuaToolProbeResult(ok, version, luaVersion, runtime, failure)` with the §3.4 failure
    taxonomy (`"Timeout"` / `"Not executable"` / first non-blank merged line) — design §2.7;
    register `LuaToolProbeImpl` as an `applicationService`
  - [x] Unit tests on `interpret` with static strings (no subprocesses): TC 1-8 (incl.
    stderr-banner TC 2 via merge-order test, product-mismatch TC 8, minVersion TC 6,
    luaVersion TC 5, `LanguageLevelRule` evaluation for 5.1/5.4/unknown→fallback)
  - [x] Process-level tests with `@TempDir` shell-script fakes (POSIX; pattern from existing
    tool tests): happy path TC 1, missing-file TC 9, timeout TC 10 (script sleeps past a
    short test-spec timeout)
- **Exit criteria**: TC 1-10 pass; no EDT/read-action use anywhere in the probe.

### Phase 4: Discovery [Must]
- **Goal**: `LuaToolDiscovery` — the one scanner.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.discovery.LuaToolDiscovery` implementing
    §3.2 (root construction with env substitution + dir-glob expansion moved from
    `platform/LuaInterpreterService.kt:117-128,205-243`; two-pass exact/glob matching;
    canonical dedup) and §3.3 `platformCandidates` — design §2.6
  - [x] Unit tests with `@TempDir` roots injected via `extraRoots`: TC 12 (symlink dedup),
    TC 13 (glob claim `lua5.4`, exact claim `luajit`), executable-filter test,
    `platformCandidates(windows = true/false)` order test (mirrors
    `tool/LuaToolDescriptor.kt:34-39` expectations), `${VAR}` substitution test
- **Exit criteria**: TC 12-13 pass; deterministic output order asserted.

### Phase 5: Registry service, persistence & events [Must]
- **Goal**: `LuaToolchainRegistry` + `LuaToolchainListener` + `plugin.xml`.
- **Tasks**:
  - [x] Create `net.internetisalie.lunar.toolchain.registry.LuaToolchainListener` +
    `LuaToolchainEvent` — design §2.5
  - [x] Create `net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry`
    (`@Service(APP)` + `PersistentStateComponent<LuaToolchainAppState>`; top-level
    `LuaToolchainAppState` with `tools` + `globalBindings` + `kindOptions`, `RegisteredToolState`
    + `ProbeStatus` beans, §3.7 bean↔model mapping, §3.1 registration; the contract-§10.1 public
    surface `tools`/`toolsOfKind`/`tool`/`findByPath`/`globalBindings`/`setGlobalBinding`/
    `registerTool`/`registerProvisioned`/`unregisterTool`/`unregisterByEnvironment`/`refreshTool`/
    `updateToolCheck`/`autoDiscover`/`setKindOption`/`kindOption`; `stateLock` +
    `assertIsNonDispatchThread` guards) — design §2.4, §6
  - [x] Register in `plugin.xml`: the single `applicationService` entry of design §8, placed
    beside the legacy `LuaToolManager` entry (`plugin.xml:421-423`)
  - [x] Tests (`BasePlatformTestCase` for bus/service; plain JUnit for bean mapping):
    TC 15-17 (events on register/update/binding via an app-bus subscriber), TC 16 canonical
    refresh-in-place, TC 18 + TC 24 `XmlSerializer` round-trip (mirroring the persistence
    round-trip tests in `src/test/kotlin/net/internetisalie/lunar/tool/LuaToolManagerTest.kt`),
    TC 19 pure-reads (delete file → `tools()` unchanged → `refreshTool` updates + fires), TC 20
    unknown-kind rejection, TC 25 `updateToolCheck` no-event-when-unchanged (then event on
    change), `autoDiscover` end-to-end over `extraRoots`-seeded temp dirs
- **Exit criteria**: TC 15-20, 24-25 pass; full suite green (legacy tool/interpreter tests
  untouched and passing — proves the dark landing).

### Phase 6: Docs & verification [Must]
- **Goal**: close the loop.
- **Tasks**:
  - [x] `run "ktlintFormat ktlintCheck"` on the new packages; fix new-code violations only
  - [x] `python3 scripts/lint_docs.py docs` + `python3 scripts/gen_status.py`; set feature
    front-matter status per workflow
  - [x] Confirm zero references from legacy code into `toolchain.*` (dark-landing grep gate:
    `grep -rn "toolchain\." src/main/kotlin --include="*.kt"` shows only `toolchain.*`-internal
    hits)
- **Exit criteria**: build gate (`run build`) green incl. `:checkStatus`/`:lintDocs`.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| TOOLING-01-01 kind descriptor | M | Phase 1 |
| TOOLING-01-02 built-in registry | M | Phase 2 |
| TOOLING-01-03 tool & health model | M | Phase 1 |
| TOOLING-01-04 runtime info | M | Phase 1 (model), Phase 3 (filling) |
| TOOLING-01-05 semantic version | M | Phase 1 |
| TOOLING-01-06 inventory CRUD | M | Phase 5 |
| TOOLING-01-07 global bindings | M | Phase 5 |
| TOOLING-01-08 change events | M | Phase 5 |
| TOOLING-01-09 unified discovery | M | Phase 4 |
| TOOLING-01-10 unified probe | M | Phase 3 |
| TOOLING-01-11 kind inference | M | Phase 2 |
| TOOLING-01-12 persistence, clean break | M | Phase 5 |
| TOOLING-01-13 pure reads & threading | M | Phases 3-5 (guards + TC 19) |
| TOOLING-01-14 data-only extensibility | S | Phase 2 (§4.2 example documented in code KDoc) |
| TOOLING-01-15 Lua-compat capture | S | Phase 3 |

## Verification Tasks

- [x] Automate requirements TC 1-25 across Phases 1-5 as listed per phase
- [x] Regression gate: existing `tool/` + `platform/` test suites unchanged and green
  (`LuaToolValidatorTest`, `LuaToolManagerTest`, `LuaToolDiscoveryServiceTest`,
  `LuaToolBindingResolutionTest`, health tests)
- [x] Manual spot-check (optional until TOOLING-06 provides UI): scratch test invoking
  `autoDiscover()` on the dev machine and inspecting `lunar.xml` output against design §4.3

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Model types | done | Must |
| Phase 2: Kind registry & inference | done | Must |
| Phase 3: Probe engine | done | Must |
| Phase 4: Discovery | done | Must |
| Phase 5: Registry service, persistence & events | done | Must |
| Phase 6: Docs & verification | done | Must |
