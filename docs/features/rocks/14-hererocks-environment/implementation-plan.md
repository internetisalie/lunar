---
id: "ROCKS-14-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "ROCKS-14"
folders:
  - "[[features/rocks/14-hererocks-environment/requirements|requirements]]"
---

# Implementation Plan: ROCKS-14 — Hererocks Environment Lifecycle

## Phase 1 — Descriptor & state  [Must]

- Add `HererocksFlavor` enum and `HererocksEnvState` data class (design §2.1) in
  `rocks/env/HererocksEnvState.kt`.
- Extend `LuaProjectSettings.State` with `var hererocksEnv: HererocksEnvState? = null` and add
  `setInterpreterAndNotify(interpreter)` (design §2.2).
- **Verify:** unit test round-trips a `HererocksEnvState` through `getState()`/`loadState()`;
  `binDir()`/`luaExe()`/`luarocksExe()` assert correct POSIX + Windows paths (TC-4 helpers).

## Phase 2 — Locator  [Must]

- `rocks/env/HererocksLocator.kt` (design §2.3, §3.1).
- **Verify:** unit tests with a stubbed `findInPath`/`capture` cover TC-1/2/3.

## Phase 3 — Provisioner & arg builder  [Must]

- `rocks/env/HererocksProvisioner.kt` with `argsFor` (design §3.2), `Mode`, the concurrency guard,
  and the `Task.Backgroundable` runner (design §2.4).
- **Verify:** unit tests on `argsFor` cover TC-4/5; a test drives two concurrent `provision`
  calls for one directory and asserts the second is refused (TC-10) using an injected fake runner.

## Phase 4 — Binder  [Must]

- `rocks/env/HererocksEnvBinder.kt` (design §2.5, §3.3) — `bind`/`unbind`.
- **Verify:** `BasePlatformTestCase` — after `bind` with a fixture env dir (fake `bin/lua` +
  `bin/luarocks` scripts), assert the `LUAROCKS` project binding id matches the registered tool,
  `state.interpreter.path` is the env lua, and `state.hererocksEnv` is stored (TC-6). `unbind`
  clears all three.

## Phase 5 — Detection  [Must]

- `rocks/env/HererocksEnvDetector.kt` + `HererocksDetectStartup` (design §2.6, §3.4).
- Register `postStartupActivity` + notification group in `plugin.xml` (design §5).
- **Verify:** `BasePlatformTestCase` with a temp project dir containing `.lua/bin/lua` +
  `.lua/bin/luarocks` → `detect` returns that path (TC-7); empty project → `null` (TC-8).

## Phase 6 — Actions & dialog  [Should]

- `CreateHererocksEnvAction` + `CreateHererocksEnvDialog`, `Upgrade`/`Recreate`/`Remove` actions
  (design §2.7); register the action group in `plugin.xml` (design §5).
- **Verify:** action `update()` enablement tests (disabled when `hererocksEnv == null`); manual
  VNC pass per the human-verification checklist (RECREATE deletes+rebuilds; TC-9).

## Phase 7 — Docs & status  [Must]

- Update `docs/features/rocks/requirements.md` epic table row for ROCKS-14; regenerate
  `docs/status.md` (`python3 scripts/gen_status.py`) and align icons.
- Set this feature `status: planned` only after the Step-9 review passes, then `in_progress` /
  `done` as phases land.

## Verification summary

| Phase | Unit | Integration/Manual |
|-------|------|--------------------|
| 1 | state round-trip, path helpers | — |
| 2 | locator TC-1/2/3 | — |
| 3 | argsFor TC-4/5, guard TC-10 | — |
| 4 | binder TC-6 | — |
| 5 | detector TC-7/8 | — |
| 6 | action enablement | VNC: create/upgrade/recreate/remove live (TC-9) |
