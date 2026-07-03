---
id: "ROCKS-15-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-15"
folders:
  - "[[features/rocks/15-multi-version-development/requirements|requirements]]"
---

# Implementation Plan: ROCKS-15 — Multi-Version Rocks Development

> **Precondition:** ROCKS-14 is `done` (its `rocks.env` package — `HererocksEnvState`,
> `HererocksProvisioner`, `HererocksEnvBinder`, actions, `HererocksEnvGroup` — exists on disk).
> Do not start before then; every phase reuses those symbols.

## Phase 1 — Env set state + migration  [Must]

- Extend `LuaProjectSettings.State` with `hererocksEnvs: MutableList<HererocksEnvState>`,
  `activeEnvId: String`, and the `@Deprecated hererocksEnv` legacy field
  (`settings/LuaProjectSettings.kt`, design §2.1).
- Add `resolveAllEnvs()`, `activeEnv()`, `addEnv(spec)` and the `loadState` migration
  (design §2.2, §3.1).
- **Verify:** unit test round-trips two envs through `getState`/`loadState` (TC-2); a test loads a
  legacy `hererocksEnv` and asserts migration into the list + `activeEnvId` (TC-1).

## Phase 2 — Active-env switch  [Must]

- Add `setActiveEnvAndNotify(project, envId)` to `LuaProjectSettings` and the
  `rocks/env/HererocksEnvSet.kt` facade (design §2.2, §2.3, §3.2).
- **Verify:** `BasePlatformTestCase` with a fake env dir (`bin/lua`+`bin/luarocks`): switch to B
  ⇒ `HererocksEnvBinder.bind` invoked, `activeEnvId=="B"`, TOPIC counted (TC-3); unknown id ⇒ no-op
  (TC-4); `LuaRocksEnvironment.resolveExecutable` returns B's luarocks (TC-5).

## Phase 3 — Status-bar switcher  [Must]

- `rocks/env/LuaEnvStatusBarWidget.kt` + `LuaEnvStatusBarWidgetFactory.kt` (design §2.4, §2.5, §3.4).
- Register `<statusBarWidgetFactory>` in `plugin.xml` (design §7).
- **Verify:** unit test on `getText()` for active/empty (TC-6, label part); popup population +
  active-mark asserted via a headless list-model unit test; live popup click is a VNC check (TC-6).

## Phase 4 — Matrix runner + results  [Should]

- `rocks/env/matrix/MatrixRunner.kt` (`MatrixRow`/`Status`/`MatrixResult`, design §2.6, §3.3),
  `RunMatrixAction.kt`, `MatrixResultsToolWindow.kt`.
- Register `<toolWindow>` + `RunMatrix` action in `plugin.xml` (design §7).
- **Verify:** unit test builds per-env command lines for envs A,B with command `test` and asserts
  exactly two `luarocks test <rockspec>` lines with the right bin paths; with an injected fake
  runner giving A=0,B=1 assert row A=PASS, B=FAIL, aggregate FAIL (TC-7); empty env set ⇒
  `MatrixResult(emptyList())` + action disabled (TC-8).

## Phase 5 — Batch provisioning  [Could]

- `rocks/env/matrix/BatchProvisionAction.kt` + `BatchProvisionDialog` (design §2.7, §3.5).
- Register `BatchProvision` action in `plugin.xml` (design §7).
- **Verify:** unit test on the spec-derivation helper: rows [{PUC,5.3},{PUC,5.4}], base `/p/envs`
  ⇒ two specs with dirs `/p/envs/PUC-5.3`, `/p/envs/PUC-5.4` and fresh UUIDs (TC-9); with an
  injected fake provisioner assert two `provision(...,CREATE)` calls + `addEnv` on success.

## Phase 6 — Docs & status  [Must]

- Update `docs/features/rocks/requirements.md` epic row for ROCKS-15; regenerate `docs/status.md`
  (`python3 scripts/gen_status.py`) and `python3 scripts/align_icons.py`.
- Set `status: planned` only after the Step-9 review passes; then `in_progress`/`done` as phases land.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-15-01 | M | Phase 1 |
| ROCKS-15-02 | M | Phase 2 |
| ROCKS-15-03 | M | Phase 3 |
| ROCKS-15-04 | S | Phase 4 |
| ROCKS-15-05 | C | Phase 5 |

## Verification Tasks

- [ ] State/migration unit tests — cover TC-1, TC-2.
- [ ] Switch unit + `BasePlatformTestCase` — cover TC-3, TC-4, TC-5.
- [ ] Widget text/popup-model unit tests — cover TC-6 (label + list).
- [ ] Matrix command-build + aggregation unit tests — cover TC-7, TC-8.
- [ ] Batch spec-derivation + provisioner-call unit tests — cover TC-9.
- [ ] Run `human-verification-checklists.md` in the containerized GoLand over VNC.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Env set state + migration | todo | Must |
| Phase 2: Active-env switch | todo | Must |
| Phase 3: Status-bar switcher | todo | Must |
| Phase 4: Matrix runner + results | todo | Should |
| Phase 5: Batch provisioning | todo | Could |
| Phase 6: Docs & status | todo | Must |
