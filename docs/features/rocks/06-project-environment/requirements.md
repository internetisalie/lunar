---
id: ROCKS-06
title: "06: Project LuaRocks Environment"
type: feature
status: "in_progress"
priority: high
parent_id: ROCKS
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-06: Project LuaRocks Environment

## Overview

Today every LuaRocks consumer is hardwired to the default registry (luarocks.org) with no
project targeting, blocking custom-registry use (e.g. a private/local rockserver). This feature
introduces a **layered LuaRocks environment**: an application-level default registry server plus
a per-project override (VCS-shared), so search / list / publish all emit the resolved `--server`.
The `luarocks` executable continues to resolve through the existing **TOOL-02**
`projectToolBindings` (falling back to the app `LuaRocksSettings.executablePath`), and upload
credentials become **per-server**, kept in PasswordSafe. Parent epic: [[features/rocks/requirements|ROCKS]].

## Scope

### In Scope

- **App-default server URL**: a new application-level setting on `LuaRocksSettings` holding the
  default LuaRocks registry/server URL (empty = use the LuaRocks built-in default, luarocks.org).
- **Project server override**: a new per-project field on `LuaProjectSettings` (stored in
  `.idea/lunar.xml`, VCS-shared) that, when non-blank, overrides the app default.
- **Resolved-server emission**: `LuaRocksSearchService.search` / `installed`, and the publish path
  (`RockUploadCommand` / `PublishRockAction`) emit `--server <url>` (search/list) and
  `--server <url>` (upload) from the resolved value.
- **Executable resolution via TOOL-02**: a shared resolver that returns the effective `luarocks`
  path via `LuaToolManager.getEffectiveTool(project, LuaToolType.LUAROCKS)`, falling back to
  `LuaRocksSettings.executablePath`. No new executable override is added.
- **Per-server credentials**: generalize `LuaRocksApiKeyStore` so the upload API key is keyed by
  the **server URL** (with a back-compat fall-through to the legacy single key), stored only in
  PasswordSafe / never in VCS XML.
- **Settings Configurable**: a LuaRocks settings page (application-level) surfacing the
  `executablePath` (no Configurable exists for it today) and the default server URL; plus a
  project-level server-override field.

### Out of Scope

- **Rock tree configuration** — the tree is *discovered* (`LuaRocksTreeLocator.treeRoot`), not
  configured. Per-run `--tree` already works via the run-config `globalFlags`. No tree setting is
  added (deferred to ROCKS-03's tree discovery work).
- **Dependency / package list** — the rockspec owns dependencies. ROCKS-06 stores *environment*
  only (server + resolved executable + credentials); it never persists a list of rocks.
- **Multiple simultaneous servers / mirror fan-out** — a single resolved server per project is in
  scope; `--only-server` semantics beyond the single resolved URL are deferred.
- **Live network publish/search verification** — covered by existing ROCKS-02 / ROCKS-08 manual
  checks; ROCKS-06 verifies command-line *shape* in the headless suite.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| ROCKS-06-01 | **App-default server setting** | M | `LuaRocksSettings` exposes a `serverUrl` string (default empty). Persisted application-wide in `lunar.xml`. |
| ROCKS-06-02 | **Project server override** | M | `LuaProjectSettings` exposes a project `rocksServerUrl` string (default empty), stored in `.idea/lunar.xml` so teams share it via VCS. |
| ROCKS-06-03 | **Server resolution rule** | M | A single resolver returns the effective server: project override if non-blank, else app default if non-blank, else `null` (no `--server`). |
| ROCKS-06-04 | **Search/list emit `--server`** | M | When the resolved server is non-null, `LuaRocksSearchService.search` / `installed` append `--server <url>` to the `luarocks` command; when null, no `--server` is added. |
| ROCKS-06-05 | **Upload emits `--server`** | M | When the resolved server is non-null, `luarocks upload` includes `--server <url>`; when null, it is omitted (legacy luarocks.org behavior). |
| ROCKS-06-06 | **Executable via TOOL-02** | M | The resolved `luarocks` path comes from `LuaToolManager.getEffectiveTool(project, LUAROCKS)?.path`, falling back to `LuaRocksSettings.executablePath`. No competing executable override is introduced. |
| ROCKS-06-07 | **Per-server credentials** | M | The upload API key is stored/read in PasswordSafe keyed by the resolved server URL, never in project XML. A blank resolved server uses the legacy luarocks.org key. |
| ROCKS-06-08 | **LuaRocks Configurable** | S | An application Configurable surfaces `executablePath` + default `serverUrl`; the existing project Configurable surfaces the project server override. |

## Detailed Specifications

### ROCKS-06-03: Server resolution rule

Resolution is performed by `LuaRocksEnvironment.resolveServer(project: Project?): String?`:

1. If `project != null` and `LuaProjectSettings.getInstance(project).state.rocksServerUrl` is
   non-blank, return it (trimmed).
2. Else if `LuaRocksSettings.getInstance().serverUrl` is non-blank, return it (trimmed).
3. Else return `null`.

A `null` result means "do not pass `--server`" — i.e. luarocks falls back to its own configured
default (luarocks.org). This preserves today's behavior exactly when nothing is set.

### ROCKS-06-06: Executable resolution

`LuaRocksEnvironment.resolveExecutable(project: Project?): String`:

1. If `project != null`, try `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUAROCKS)?.path`.
2. If that is `null`/blank, fall back to `LuaRocksSettings.getInstance().executablePath`
   (default `"luarocks"`, resolved on PATH).

This is the **only** executable resolution path; ROCKS-06 adds no new executable field. It
generalizes the current direct `LuaRocksSettings.getInstance().executablePath` reads in
`LuaRocksSearchService` and `PublishRockAction`.

### ROCKS-06-07: Per-server credential keying

`LuaRocksApiKeyStore` is generalized from one fixed key to a per-server key:

- The credential-store `KEY` becomes `"luarocks API key:<server>"` where `<server>` is the
  resolved server URL, or the legacy literal `"luarocks.org API key"` when the resolved server is
  `null`/blank (back-compat: existing stored keys keep working).
- `getApiKey(server: String?)` / `setApiKey(server: String?, key: String?)` derive
  `CredentialAttributes` via `generateServiceName(SUBSYSTEM, keyFor(server))`.
- Credentials live only in `PasswordSafe`; they are never written to `lunar.xml`/`.idea/lunar.xml`.

## Behavior Rules

- **Precedence**: project override > app default > none. A blank string at any layer is treated as
  "unset" (falls through); only a non-blank, trimmed value is emitted.
- **No `--server` when unset**: when resolution yields `null`, the produced command line is
  byte-for-byte the same as today (no `--server` token). This is the regression guard.
- **Threading**: settings reads are cheap and may occur on the EDT; all `luarocks` process
  invocations stay on background threads (`LuaProcessUtil.capture`, `Task.Backgroundable`) as
  they do today.
- **Credentials never enter VCS**: only `serverUrl` (app) and `rocksServerUrl` (project) are
  persisted in XML; API keys live solely in PasswordSafe.

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | ROCKS-06-04 | Project A: `rocksServerUrl = "http://localhost:8080"` | `LuaRocksSearchService.search("x")` builds its command | The argument list contains `--server` immediately followed by `http://localhost:8080` (e.g. `[exe, "search", "--porcelain", "x", "--server", "http://localhost:8080"]`). |
| 2 | ROCKS-06-04 | Project B: `rocksServerUrl` blank, app `serverUrl` blank | `LuaRocksSearchService.search("x")` builds its command | The argument list contains **no** `--server` token (`[exe, "search", "--porcelain", "x"]`). |
| 3 | ROCKS-06-03 | Project C: `rocksServerUrl` blank, app `serverUrl = "https://reg.example"` | `LuaRocksEnvironment.resolveServer(projectC)` | Returns `"https://reg.example"`. |
| 4 | ROCKS-06-03 | Project D: `rocksServerUrl = "http://localhost:8080"`, app `serverUrl = "https://reg.example"` | `LuaRocksEnvironment.resolveServer(projectD)` | Returns `"http://localhost:8080"` (project override wins). |
| 5 | ROCKS-06-05 | Resolved server `http://localhost:8080`, rockspec `foo-1.0.rockspec`, key `K` | `RockUploadCommand.arguments("foo-1.0.rockspec", "K", server = "http://localhost:8080")` | List contains `--api-key=K` and `--server http://localhost:8080` (server token + value present). |
| 6 | ROCKS-06-05 | Resolved server `null`, rockspec `foo-1.0.rockspec`, key `K` | `RockUploadCommand.arguments("foo-1.0.rockspec", "K", server = null)` | List contains `--api-key=K` and **no** `--server` token (matches today's output). |
| 7 | ROCKS-06-07 | Server `http://localhost:8080`, stored credential set for that server | `LuaRocksApiKeyStore.getApiKey("http://localhost:8080")` | Returns the credential stored under key `"luarocks API key:http://localhost:8080"`. |
| 8 | ROCKS-06-07 | Server `null` (unset), legacy key `"luarocks.org API key"` present | `LuaRocksApiKeyStore.getApiKey(null)` | Returns the legacy-keyed credential (back-compat path). |
| 9 | ROCKS-06-06 | Project with a valid bound LuaRocks tool at `/opt/lr/luarocks` | `LuaRocksEnvironment.resolveExecutable(project)` | Returns `/opt/lr/luarocks` (TOOL-02 binding), not the app default. |
| 10 | ROCKS-06-06 | Project with no valid LuaRocks tool, app `executablePath = "luarocks"` | `LuaRocksEnvironment.resolveExecutable(project)` | Returns `"luarocks"` (app fallback). |

## Acceptance Criteria

- [ ] ROCKS-06-01..03: server state exists at both layers and resolves with project-over-app
      precedence (TC 3, 4).
- [ ] ROCKS-06-04: search/list append `--server` iff resolved server is non-null (TC 1, 2).
- [ ] ROCKS-06-05: upload appends `--server` iff resolved server is non-null (TC 5, 6).
- [ ] ROCKS-06-06: executable resolves via TOOL-02 with app fallback (TC 9, 10).
- [ ] ROCKS-06-07: credentials are keyed per-server with a legacy fall-through, in PasswordSafe
      only (TC 7, 8).
- [ ] ROCKS-06-08: a LuaRocks application Configurable surfaces executable + default server; the
      project Configurable surfaces the project server override.

## Non-Functional Requirements

- **Threading**: settings access stays cheap and EDT-safe (`SimplePersistentStateComponent`
  reads); all `luarocks` subprocess calls remain on background threads (engineering-contract §1
  THREADING SEGREGATION).
- **Memory**: no hard refs to `Project`/`Editor`/`VirtualFile` retained in the new resolver
  (it is a stateless object taking `Project?` per call).
- **Back-compat**: empty server + legacy credential key must reproduce today's exact behavior so
  existing users see no change (regression guards TC 2, 6, 8).

## Dependencies

- **TOOL-02** (`LuaProjectSettings.projectToolBindings`, `LuaToolManager.getEffectiveTool`) — the
  executable-resolution path.
- **ROCKS-02** (`LuaRocksSearchService`) and **ROCKS-08** (`PublishRockAction`,
  `RockUploadCommand`, `LuaRocksApiKeyStore`) — the consumers this feature retrofits.

## See Also

- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- Risks: [risks-and-gaps.md](risks-and-gaps.md)
- Checklists: [human-verification-checklists.md](human-verification-checklists.md)
