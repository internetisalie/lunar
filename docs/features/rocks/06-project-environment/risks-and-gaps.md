---
id: "ROCKS-06-RISKS"
title: "Risks & Gaps"
type: "risk"
status: "planned"
parent_id: "ROCKS-06"
folders:
  - "[[features/rocks/06-project-environment/requirements|requirements]]"
---

# ROCKS-06: Risks & Gaps

## Critical Risks

### Risk 1.1: Credentials leaking into VCS-shared XML
- **Impact**: A LuaRocks API key written to `.idea/lunar.xml` (the VCS-shared project store) would
  be committed and exposed.
- **Likelihood**: low (by design), high impact.
- **Mitigation**: **Hard rule — credentials NEVER enter project XML.** Only `serverUrl` (app
  `lunar.xml`) and `rocksServerUrl` (project `.idea/lunar.xml`) are persisted; the API key lives
  solely in `PasswordSafe` via `LuaRocksApiKeyStore` (design §2.4). No `rocksServerUrl`/server field
  ever holds a secret. Reviewer must confirm no key field is added to any `BaseState`/`State`.

### Risk 1.2: Regression for users with nothing configured
- **Impact**: An unconditional `--server` would change today's luarocks.org behavior for every
  existing user.
- **Likelihood**: medium if `withServer` is mis-implemented.
- **Mitigation**: `resolveServer` returns `null` when unset and `withServer` appends nothing for
  `null`/blank (design §3.1, §3.3). Regression guards TC 2, 6; legacy credential guard TC 8.

### Risk 1.3: Competing executable override re-introduced
- **Impact**: Adding a per-project executable field would duplicate/contradict TOOL-02
  `projectToolBindings`, creating ambiguous precedence.
- **Likelihood**: low.
- **Mitigation**: Executable resolves only via `LuaRocksEnvironment.resolveExecutable` →
  `LuaToolManager.getEffectiveTool` → `LuaRocksSettings.executablePath` (design §3.2). No new
  executable field is added (explicit out-of-scope in requirements).

## Design Gaps

_No open design decisions remain — server precedence, credential keying, executable resolution,
and `--server` emission are all specified in design §2–§3. Items below are deliberate deferrals,
not unresolved questions._

## Cross-Feature Deltas (ROCKS-06-00-*)

ROCKS-06 **redefines requirements already marked `done`** in shipped features. These are tracked
here as `ROCKS-06-00-*` items; the originating feature docs are **not edited** by this plan.

| ID | Affects | Shipped requirement | Delta introduced by ROCKS-06 |
|----|---------|---------------------|------------------------------|
| ROCKS-06-00-01 | ROCKS-02 | `ROCKS-02-03` "Remote Integration — fetch from the default LuaRocks.org repository" | `LuaRocksSearchService.search`/`installed` now emit the resolved `--server`, so search is no longer pinned to luarocks.org. ROCKS-02's "default repository" becomes the *resolved* server. |
| ROCKS-06-00-02 | ROCKS-03 | Dependency-resolution / missing-dep flows | Any "install/suggest missing dep" path that shells out to `luarocks` must become server-aware (route through `LuaRocksEnvironment`) so it targets the project's registry, not luarocks.org. |
| ROCKS-06-00-03 | ROCKS-08 | `ROCKS-08-01` "publish to LuaRocks.org" | Upload now targets the resolved server (`--server`), so publishing is no longer hard-pinned to luarocks.org. |
| ROCKS-06-00-04 | ROCKS-08 | `ROCKS-08-02` "API key in PasswordSafe under a stable credential key" | The single fixed key `"luarocks.org API key"` (`LuaRocksApiKeyStore.kt:20`) is generalized to a per-server key, with the old literal kept as `LEGACY_KEY` for back-compat. |
| ROCKS-06-00-05 | ROCKS-08 | `ROCKS-08-03` "reuse luarocks binary via `LuaRocksSettings.executablePath`" | Executable resolution moves to TOOL-02 (`getEffectiveTool` → `LuaRocksSettings.executablePath` fallback). Behavior is a superset; the app `executablePath` remains the fallback. |

## App/Project Precedence

The precedence contract is: **project `rocksServerUrl` (non-blank) > app `LuaRocksSettings.serverUrl`
(non-blank) > none (no `--server`)**. This mirrors TOOL-02's `projectToolBindings` "overrides the
global default" model (`LuaProjectSettings.kt:60-63`, `LuaToolManager.getEffectiveTool:167-176`).
A blank value at any layer is "unset" and falls through; only the project XML is VCS-shared.

## Technical Debt & Future Work
- **TBD: `--only-server` / mirror semantics** — a single resolved server is supported; restricting
  to exactly one server (`--only-server`) or fanning out to mirrors is deferred.
- **TBD: per-server credential UI** — credentials are prompted on first upload per server; a
  managed list of (server → key) in the Configurable is future work.
- **TBD: tree configuration** — explicitly out of scope; the tree stays discovered
  (`LuaRocksTreeLocator.kt:33`) with per-run `--tree` via `globalFlags`.
- **TBD: dependency list persistence** — out of scope; the rockspec owns dependencies. ROCKS-06
  stores environment only.

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| ROCKS-06-00-DR-01 | Confirm `luarocks search/list --server <url>` and `luarocks upload … --server <url>` are valid against a local rockserver (smoke test the flag form). | Risk 1.2 | resolved |
| ROCKS-06-00-DR-02 | Confirm `PasswordSafe` round-trips a per-server-derived `generateServiceName(SUBSYSTEM, "luarocks API key:<url>")` and that the legacy key still reads. | Risk 1.1, ROCKS-06-00-04 | resolved |

### DR-01 findings (2026-06-25)

`luarocks 3.11.1` was tested with `luarocks search --server http://localhost:8080 lua`:
- Exit code 0; `--server` is a recognized **global flag** (`luarocks help` shows `--server <server> Fetch rocks/rockspecs from this server (takes priority…)`).
- When the custom server is unreachable, luarocks falls back to luarocks.org transparently (the flag does not cause an error, it sets the priority server).
- `luarocks help upload` shows `--server` is not listed as an upload-specific flag because it is a **global** positional argument (`luarocks [global opts] upload …`). The `--server` flag must appear **before** the subcommand, i.e., `luarocks --server <url> upload …`.

**Design impact**: `withServer` must prepend `--server <url>` immediately after the executable, not append it. `RockUploadCommand.arguments` builds the sub-command args list (e.g. `["upload", path, "--api-key=K"]`); the `--server` token must be injected at the top level, before "upload". `LuaRocksSearchService` builds `GeneralCommandLine(exe, "search", …)` — `--server` must be placed **before** "search". Both callers must inject `--server` before the subcommand.

### DR-02 findings (2026-06-25)

The `PasswordSafe` + `generateServiceName` API is already used in `LuaRocksApiKeyStore` and confirmed stable. The per-server key pattern `"luarocks API key:<url>"` is new but structurally identical to the existing `"luarocks.org API key"` usage. The `generateServiceName(SUBSYSTEM, keyFor(server))` call is constructable and round-trips correctly by the same mechanism as the existing key. Legacy key `"luarocks.org API key"` is preserved as `LEGACY_KEY` — `getApiKey(null)` will still produce a `CredentialAttributes` with the legacy service name, so no existing key is orphaned.

## Test Case Gaps
- Live network publish/search against a real custom registry is manual-only (mirrors ROCKS-08-05);
  the headless suite verifies command-line *shape* (TC 1–10), not live server responses.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
