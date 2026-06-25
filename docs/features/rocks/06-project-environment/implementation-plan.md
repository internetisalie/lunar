---
id: "ROCKS-06-PLAN"
title: "Implementation Plan"
type: "plan"
status: "planned"
parent_id: "ROCKS-06"
folders:
  - "[[features/rocks/06-project-environment/requirements|requirements]]"
---

# ROCKS-06: Implementation Plan

## Phases

### Phase 1: Environment state + resolver [Must]
- **Goal**: Both settings layers hold a server URL, and a single resolver returns the effective
  server + executable.
- **Tasks**:
  - [x] Extend `net.internetisalie.lunar.rocks.run.LuaRocksSettings.State` with
        `var serverUrl by string("")` and the `serverUrl` accessor — realizes design §2.2.
  - [x] Add `var rocksServerUrl: String = ""` to
        `net.internetisalie.lunar.settings.LuaProjectSettings.State` (beside `projectToolBindings`)
        — realizes design §2.3.
  - [x] Create `net.internetisalie.lunar.rocks.LuaRocksEnvironment` with `resolveServer`,
        `resolveExecutable`, `withServer` — realizes design §2.1, §3.1, §3.2, §3.3.
- **Exit criteria**: Unit tests for precedence and append logic pass (TC 1–4, 9, 10); build green.

### Phase 2: Server-aware consumers [Must]
- **Goal**: Search/list and the run-config builder emit the resolved `--server`.
- **Tasks**:
  - [x] Update `LuaRocksSearchService.search` / `installed` to take a `Project?` and build the
        command via `LuaRocksEnvironment.resolveExecutable` + `withServer(..., resolveServer(project))`
        — realizes design §2 consumers, §5 Ex.1. (Thread callers through; callers in the ROCKS-02
        browser pass their project.)
  - [x] (Optional alignment) leave `LuaRocksRunConfiguration.buildCommandLine` `--server`-via-
        `globalFlags` as-is; document that per-run flags still win (no code change required).
- **Exit criteria**: TC 1, 2 pass (search emits `--server` iff resolved; omitted when unset).

### Phase 3: Per-server credentials + upload server [Must]
- **Goal**: Upload targets the resolved server and uses per-server credentials.
- **Tasks**:
  - [ ] Generalize `LuaRocksApiKeyStore` to `keyFor(server)` with `getApiKey(server)` /
        `setApiKey(server, key)` and a `LEGACY_KEY` fall-through — realizes design §2.4, §3 (§4.x rules).
  - [ ] Extend `RockUploadCommand.arguments`/`build` with `server: String? = null` appending
        `--server <url>` — realizes design §2.5.
  - [ ] Update `PublishRockAction` to compute `server = resolveServer(project)`,
        `exe = resolveExecutable(project)`, key via `getApiKey(server)` / `setApiKey(server, …)`,
        and `RockUploadCommand.build(..., server = server)` — realizes design §5 Ex.2.
- **Exit criteria**: TC 5–8 pass; legacy key still resolves; build green.

### Phase 4: Configurables [Should]
- **Goal**: UI for the executable + default server (app) and the project override.
- **Tasks**:
  - [ ] Create `LuaRocksSettingsConfigurable` (`BoundConfigurable`, panel DSL) binding
        `executablePath` + `serverUrl` — realizes design §2.6.
  - [ ] Register it in `plugin.xml` as `<applicationConfigurable groupId="tools" …>` — realizes
        design §7.
  - [ ] Add a "LuaRocks server URL (project override)" row to `LuaProjectSettingsPanel`, wired
        through the existing `LuaProjectSettingsConfigurable.reset()/apply(state)` — realizes design
        §2.7.
- **Exit criteria**: Settings pages render and round-trip values (human-verification checklist).

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| ROCKS-06-01 | M | Phase 1 |
| ROCKS-06-02 | M | Phase 1 |
| ROCKS-06-03 | M | Phase 1 |
| ROCKS-06-04 | M | Phase 2 |
| ROCKS-06-05 | M | Phase 3 |
| ROCKS-06-06 | M | Phase 1 (resolver), Phase 2/3 (consumers use it) |
| ROCKS-06-07 | M | Phase 3 |
| ROCKS-06-08 | S | Phase 4 |

## Verification Tasks
- [x] Add `LuaRocksEnvironmentTest` — covers TC 1–4, 9, 10 (resolution + `withServer` append).
- [ ] Extend `RockUploadCommandTest` for the `server` param — covers TC 5, 6.
- [ ] Add `LuaRocksApiKeyStoreTest` for per-server keying + legacy fall-through — covers TC 7, 8.
- [x] Add/extend a `LuaRocksSearchService` command-shape test (capture/build the command line) —
      covers TC 1, 2.
- [ ] Run [human-verification-checklists.md](human-verification-checklists.md) in a sandbox IDE.

## Task Summary

| Phase | Status | Priority |
|-------|--------|----------|
| Phase 1: Environment state + resolver | todo | Must |
| Phase 2: Server-aware consumers | todo | Must |
| Phase 3: Per-server credentials + upload server | todo | Must |
| Phase 4: Configurables | todo | Should |
