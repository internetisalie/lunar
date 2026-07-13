---
id: "REDIS-03-PLAN"
title: "Implementation Plan"
type: "plan"
status: "todo"
parent_id: "REDIS-03"
folders:
  - "[[features/redis/03-valkey-target/requirements|requirements]]"
---

# REDIS-03: Implementation Plan

Sequenced from `design.md` (bar cleared: algorithms §3, stub formats §3.2/§4, classes named §2,
plugin.xml §7). Each phase leaves the build green and is independently testable. REDIS-03 is
independent of REDIS-01/02 except Phase 4's seam wiring (isolated to the last phase so Phases 1–3
+ 5's registry/stub/inspection tests can land regardless of REDIS-01 status).

## Phases

### Phase 1: Platform registry + target derivation [Must]
- **Goal**: `LuaPlatform.VALKEY` selectable/derivable with correct level, library path, luacheck std.
- **Tasks**:
  - [x] Add `VALKEY("Valkey", "valkey")` to `net.internetisalie.lunar.platform.LuaPlatform`
        (after `REDIS`) — realizes design §2.1.
  - [x] Add the `LuaPlatform.VALKEY to listOf(VersionEntry("7.2","valkey-7.2","redis7"),
        VersionEntry("8","valkey-8","redis7"))` entry to `PlatformVersionRegistry` — realizes §2.2.
  - [x] Add `platform == LuaPlatform.VALKEY -> LuaLanguageLevel.LUA51` to
        `Target.getImplicitLanguageLevel()` and update its KDoc — realizes §2.3.
- **Exit criteria**: TC-REG-1, TC-REG-2, TC-REG-3, TC-REG-4 pass; full suite green (existing
  `PlatformVersionRegistryTest` / `LuaProjectSettingsTest` platform-iterating loops still pass with
  the new member).

### Phase 2: Valkey stub resources [Must]
- **Goal**: `server.*`, `SERVER_*`, and compat `redis.*`/`KEYS`/`ARGV` resolve under a Valkey target.
- **Tasks**:
  - [x] Create `src/main/resources/runtime/valkey/valkey-7.2/` and `…/valkey-8/`; byte-copy
        `redis.lua`, `global.lua`, `cjson.lua`, `cmsgpack.lua`, `bit.lua`, `struct.lua`, `os.lua`
        from `runtime/redis/redis-7/` into each — realizes §2.4.
  - [x] Author `server.lua` (`---@class server : redis` + `server = {}`) in both dirs — realizes
        §3.2.
  - [x] Author `server_global.lua` (`SERVER_NAME`/`SERVER_VERSION`/`SERVER_VERSION_NUM`) in both
        dirs — realizes §3.2.
- **Exit criteria**: TC-STUB-1, TC-STUB-2, TC-STUB-3, TC-STUB-4 pass (member/global resolution via a
  `BasePlatformTestCase` fixture with the target set to `Target(VALKEY, "8")`).

### Phase 3: Portability inspection + quick fix [Must]
- **Goal**: `server.*`/`SERVER_*` flagged under a Redis target; silent otherwise; `server.<x>` →
  `redis.<x>` quick fix.
- **Tasks**:
  - [ ] Create `LuaValkeyPortabilityInspection` (`analysis/inspections/`) with the §3.5 visitor and
        the Redis-target guard — realizes §2.7.
  - [ ] Create `LuaValkeyToRedisQuickFix` (`analysis/inspections/`) using
        `LuaElementFactory.createIdentifier` — realizes §2.8, §3.6.
  - [ ] Register the `<localInspection shortName="LuaValkeyPortability" …>` in `plugin.xml`
        (after `plugin.xml:227`) — realizes §7.1.
- **Exit criteria**: TC-INSP-1..6 pass; human checklist §3 confirms the gutter/quick-fix UX.

### Phase 4: Flavor detection + mismatch warning [Must]
- **Goal**: parse `INFO server` → flavor; warn once per session on target mismatch; wire the
  REDIS-01 seam.
- **Tasks**:
  - [ ] Create `LuaRedisServerFlavor` (`redis/connection/`) with `detect` + `mismatches` — realizes
        §2.5, §3.3.
  - [ ] Create the `LuaRedisFlavorWarning` project service + register `<projectService>` — realizes
        §2.6, §3.4, §7.2.
  - [ ] REDIS-01 seam wiring (DR-01): replace REDIS-01 design §4.3 inline heuristic with
        `LuaRedisServerFlavor.detect`; call `warnOnceIfMismatch` at the connect site — realizes §7.3.
        (Gated on REDIS-01 landing; if REDIS-01 is not yet merged, land the two new classes + their
        unit tests and defer only the call-site edit — tracked in risks-and-gaps.)
- **Exit criteria**: TC-FLV-1, TC-FLV-2, TC-FLV-3 pass; if REDIS-01 present, a live/integration
  check shows exactly one notification per mismatched connection per session (human checklist §2).

### Phase 5: Tests & docs [Must]
- **Goal**: automated coverage of every AC; user-facing note on the Valkey target.
- **Tasks**:
  - [ ] Add `PlatformVersionRegistryTest` cases / a `ValkeyTargetTest` for TC-REG-1..4.
  - [ ] Add a stub-resolution test (fixture `configureByText`, target = Valkey) for TC-STUB-1..4.
  - [ ] Add `LuaValkeyPortabilityInspectionTest` (positive/negative under both targets +
        STANDARD) and a quick-fix test for TC-INSP-1..6.
  - [ ] Add `LuaRedisServerFlavorTest` + `LuaRedisFlavorWarningTest` for TC-FLV-1..3.
  - [ ] Document the Valkey target + `--std redis7` reuse in the epic user guide / release notes.
- **Exit criteria**: all TC-* automated; `run build` (checkStatus/kover/lint) green.

## Requirement → Phase Coverage

| Requirement (AC) | Priority | Delivered in |
|------------------|----------|--------------|
| AC-1 VALKEY registry entries | S | Phase 1 |
| AC-2 platform enumeration + migration | S | Phase 1 |
| AC-3 `server.*` + `SERVER_*` stubs | S | Phase 2 |
| AC-4 `redis.*`/`KEYS`/`ARGV` under Valkey | S | Phase 2 |
| AC-5 flavor detect + warning | S | Phase 4 |
| AC-6 portability inspection + quick fix | S | Phase 3 |
| AC-7 inspection target-aware | S | Phase 3 |
| AC-8 unit tests | S | Phase 5 |

## Verification Tasks
- [x] Registry/target: `ValkeyTargetTest` — covers TC-REG-1..4.
- [x] Stubs: stub-resolution fixture test — covers TC-STUB-1..4.
- [ ] Inspection: `LuaValkeyPortabilityInspectionTest` + quick-fix test — covers TC-INSP-1..6.
- [ ] Flavor: `LuaRedisServerFlavorTest` + `LuaRedisFlavorWarningTest` — covers TC-FLV-1..3.
- [ ] Run `human-verification-checklists.md` (§1 stub UX, §2 flavor warning, §3 inspection/quick fix).

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Platform registry + target | done | Must |
| Phase 2: Valkey stub resources | done | Must |
| Phase 3: Portability inspection + quick fix | todo | Must |
| Phase 4: Flavor detection + warning | todo | Must |
| Phase 5: Tests & docs | todo | Must |
