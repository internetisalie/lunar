---
id: "ROCKS-06-DESIGN"
title: "Technical Design"
type: "design"
status: "planned"
parent_id: "ROCKS-06"
folders:
  - "[[features/rocks/06-project-environment/requirements|requirements]]"
---

# Technical Design: ROCKS-06 — Project LuaRocks Environment

## 1. Architecture Overview

### Current State

- `LuaRocksSearchService.search` (`rocks/browser/LuaRocksSearchService.kt:34-43`) and
  `installed` (`:59-64`) build `GeneralCommandLine(exe, "search"/"list", "--porcelain", …)` with
  **no** `--server`, where `exe = LuaRocksSettings.getInstance().executablePath` (`:39`, `:60`).
- `PublishRockAction` (`rocks/publish/PublishRockAction.kt`) resolves `exe` the same way (`:60`),
  resolves the key from `LuaRocksApiKeyStore` (`:45`), and builds the upload command via
  `RockUploadCommand.build(exe, rockspecPath, apiKey)` (`rocks/publish/RockUploadCommand.kt:26-33`),
  hard-pinned to luarocks.org.
- `LuaRocksApiKeyStore` (`rocks/publish/LuaRocksApiKeyStore.kt`) uses a **single fixed**
  `KEY = "luarocks.org API key"` (`:20`).
- `LuaRocksRunConfiguration.buildCommandLine` (`rocks/run/LuaRocksRunConfiguration.kt:181-199`)
  only reaches `--server` via free-text `globalFlags` (`:184`).
- `LuaRocksSettings` (`rocks/run/LuaRocksSettings.kt`) is `@Service(APP)` with only
  `executablePath`; **no Configurable** is registered for it.

### Prior Art in This Repo

- **Project-override precedent** — `LuaProjectSettings` (`settings/LuaProjectSettings.kt`,
  `@Service(PROJECT)`, `Storage("lunar.xml")` → `.idea/lunar.xml`) holds `interpreter` (`:50`)
  and `projectToolBindings` (`:63`, "Overrides the global default … teams share via VCS"), with
  `setProjectToolBindingAndNotify` (`:126`) firing `LuaSettingsChangedListener.TOPIC`. **This
  design EXTENDS it** with one new `rocksServerUrl` field.
- **App-settings precedent** — `LuaRocksSettings` (`SimplePersistentStateComponent`, `lunar.xml`,
  `SettingsCategory.PLUGINS`). **EXTENDED** with a `serverUrl` field.
- **Executable resolution** — `LuaToolManager.getEffectiveTool(project, type)`
  (`tool/LuaToolManager.kt:163-177`) already implements project-binding > global > first-valid,
  returning a `LuaTool` whose `path` (`tool/LuaTool.kt:26`) is the binary. `LuaToolType.LUAROCKS`
  exists (`tool/LuaToolManager.kt:210`). **REUSED**, not duplicated.
- **Configurable precedents** — `LuaApplicationSettingsConfigurable` /
  `LuaProjectSettingsConfigurable` (`settings/`, registered in `plugin.xml:393-404`), and the
  Kotlin-UI-DSL `BoundConfigurable` form `LuaCheckSettingsPanel`
  (`analysis/luacheck/LuaCheckSettingsPanel.kt`, registered `plugin.xml:455-458`). The new LuaRocks
  page **follows `LuaCheckSettingsPanel`** (a `BoundConfigurable` with `panel { }` + `bindText`).
- **Credential store** — `LuaRocksApiKeyStore` (`rocks/publish/LuaRocksApiKeyStore.kt`) using
  `PasswordSafe` + `generateServiceName(SUBSYSTEM, KEY)`. **GENERALIZED** to per-server keying.
- **Tree discovery** — `LuaRocksTreeLocator.treeRoot` (`rocks/LuaRocksTreeLocator.kt:33`) discovers
  the tree; **left untouched** (out of scope per requirements).

### Target State

A single stateless resolver, `LuaRocksEnvironment`, centralizes "which executable" and "which
server" for a given `Project?`. `LuaRocksSearchService`, `PublishRockAction`, and
`RockUploadCommand` consume it. State lives in two extended settings classes; credentials move to
per-server keys in `LuaRocksApiKeyStore`. A new `LuaRocksSettingsConfigurable` (app) surfaces the
executable + default server; the existing project Configurable gains a server-override field.

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.LuaRocksEnvironment`

- **Responsibility**: Resolve the effective `luarocks` executable and registry server for an
  optional `Project`, applying the project-over-app precedence rules.
- **Threading**: Pure settings reads — EDT-safe; no I/O. (Callers run the resulting command on a
  background thread.)
- **Collaborators**: `LuaProjectSettings.getInstance(project).state.rocksServerUrl`,
  `LuaRocksSettings.getInstance().serverUrl` / `.executablePath`,
  `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUAROCKS)`.
- **Key API**:
  ```kotlin
  object LuaRocksEnvironment {
      /** Project override > app default > null (no --server). */
      fun resolveServer(project: Project?): String?
      /** TOOL-02 bound LuaRocks tool path, else LuaRocksSettings.executablePath. */
      fun resolveExecutable(project: Project?): String
      /** Appends ["--server", server] to [args] when [server] is non-null. */
      fun withServer(args: List<String>, server: String?): List<String>
  }
  ```

### 2.2 `net.internetisalie.lunar.rocks.run.LuaRocksSettings` (extended)

- **Responsibility**: Add the application-level default registry server URL.
- **Threading**: `SimplePersistentStateComponent`; EDT-safe reads.
- **Key API** (added):
  ```kotlin
  class State : BaseState() {
      var executablePath by string(DEFAULT_EXECUTABLE) // existing
      var serverUrl by string("")                      // ROCKS-06-01 (default empty)
  }
  var serverUrl: String
      get() = StringUtil.notNullize(state.serverUrl, "")
      set(value) { state.serverUrl = value }
  ```

### 2.3 `net.internetisalie.lunar.settings.LuaProjectSettings.State` (extended)

- **Responsibility**: Add the per-project server override, persisted in `.idea/lunar.xml`
  (VCS-shared), mirroring `projectToolBindings` (`:63`).
- **Threading**: `PersistentStateComponent`; EDT-safe reads.
- **Key API** (added to `State`, around `:53`):
  ```kotlin
  var rocksServerUrl: String = ""   // ROCKS-06-02 (default empty; VCS-shared)
  ```

### 2.4 `net.internetisalie.lunar.rocks.publish.LuaRocksApiKeyStore` (generalized)

- **Responsibility**: Store/read the upload API key in PasswordSafe, keyed by the resolved server.
- **Threading**: PasswordSafe access is fast; call off the EDT during upload (as today).
- **Collaborators**: `PasswordSafe.instance`, `generateServiceName(SUBSYSTEM, keyFor(server))`.
- **Key API** (replaces the fixed-key form):
  ```kotlin
  object LuaRocksApiKeyStore {
      const val SUBSYSTEM = "Lunar LuaRocks"           // unchanged (stable)
      const val LEGACY_KEY = "luarocks.org API key"    // back-compat for server == null/blank
      private fun keyFor(server: String?): String =
          if (server.isNullOrBlank()) LEGACY_KEY else "luarocks API key:$server"
      fun getApiKey(server: String?): String?
      fun setApiKey(server: String?, apiKey: String?)
  }
  ```

### 2.5 `net.internetisalie.lunar.rocks.publish.RockUploadCommand` (extended)

- **Responsibility**: Add an optional `server` parameter that appends `--server <url>`.
- **Threading**: Pure builder (unit-tested headlessly).
- **Key API** (extended signatures):
  ```kotlin
  fun arguments(rockspecPath: String, apiKey: String, force: Boolean = false, server: String? = null): List<String>
  fun build(executablePath: String, rockspecPath: String, apiKey: String, force: Boolean = false, server: String? = null): GeneralCommandLine
  ```

### 2.6 `net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable`

- **Responsibility**: Application Settings page for LuaRocks: executable path + default server URL.
- **Threading**: EDT (UI construction only).
- **Collaborators**: `LuaRocksSettings.getInstance()`. Follows `LuaCheckSettingsPanel`.
- **Key API**:
  ```kotlin
  class LuaRocksSettingsConfigurable : BoundConfigurable("LuaRocks") {
      override fun createPanel(): DialogPanel = panel {
          group("Executable") {
              row { textFieldWithBrowseButton().bindText(LuaRocksSettings.getInstance()::executablePath).label("luarocks executable:") }
          }
          group("Registry") {
              row { textField().bindText(LuaRocksSettings.getInstance()::serverUrl).label("Default server URL:")
                    .comment("Empty = luarocks.org default. Overridable per project.") }
          }
      }
  }
  ```

### 2.7 `net.internetisalie.lunar.settings.LuaProjectSettingsPanel` (extended)

- **Responsibility**: Add a "LuaRocks server URL (project override)" text row bound to
  `LuaProjectSettings.getInstance(project).state.rocksServerUrl`. Realized through the existing
  `LuaProjectSettingsConfigurable` (`settings/LuaProjectSettingsConfigurable.kt`); no new project
  Configurable is registered. Reset/apply follow the panel's existing `reset()`/`apply(state)`
  contract (`LuaProjectSettingsConfigurable.kt:18,29`).

## 3. Algorithms

### 3.1 Server resolution (`resolveServer`)

- **Input → Output**: `Project?` → `String?`
- **Steps**:
  1. If `project != null`: `val p = LuaProjectSettings.getInstance(project).state.rocksServerUrl.trim()`;
     if `p.isNotBlank()` return `p`.
  2. `val a = LuaRocksSettings.getInstance().serverUrl.trim()`; if `a.isNotBlank()` return `a`.
  3. Return `null`.
- **Rules / edge handling**: blank/whitespace-only at any layer is "unset" and falls through;
  `null` means "emit no `--server`".

### 3.2 Executable resolution (`resolveExecutable`)

- **Input → Output**: `Project?` → `String`
- **Steps**:
  1. If `project != null`:
     `LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.LUAROCKS)?.path?.takeIf { it.isNotBlank() }?.let { return it }`.
  2. Return `LuaRocksSettings.getInstance().executablePath`.
- **Rules / edge handling**: `getEffectiveTool` already skips invalid bindings and returns `null`
  when no valid tool exists (`LuaToolManager.kt:164-176`), so the fallback covers the unbound case.

### 3.3 Server-flag append (`withServer`)

- **Input → Output**: `(List<String>, String?)` → `List<String>`
- **Steps**: `return if (server.isNullOrBlank()) args else args + listOf("--server", server)`.
- **Rules / edge handling**: two-token form (`--server`, then the URL) so the URL is never
  word-split — matches `RockUploadCommand`'s existing single-token `--api-key=` discipline.

## 4. External Data & Parsing

ROCKS-06 produces command-line arguments and consumes only its own persisted settings; it does
**not** parse new external/CLI output. The porcelain parsing in `LuaRocksSearchService`
(`parseSearchOutput`, `parseInstalledOutput`) is unchanged — adding `--server` does not alter the
`<name> <version> <arch> <repo> [<namespace>]` line format the search service already handles
(`LuaRocksSearchService.kt:81-117`). No new parse format is introduced.

## 5. Data Flow

### Example 1: Search against a project's local rockserver

1. User (in Project A with `rocksServerUrl = "http://localhost:8080"`) searches "inspect".
2. `LuaRocksSearchService.search("inspect", project)` (project param added) calls
   `LuaRocksEnvironment.resolveExecutable(project)` → `/usr/bin/luarocks` (TOOL-02 or fallback) and
   `resolveServer(project)` → `"http://localhost:8080"`.
3. It builds `withServer(listOf(exe, "search", "--porcelain", "inspect"), server)` →
   `GeneralCommandLine(exe, "search", "--porcelain", "inspect", "--server", "http://localhost:8080")`.
4. `LuaProcessUtil.capture(...)` runs on the existing background thread; output parsed as before.

### Example 2: Publish to a custom registry

1. User publishes `foo-1.0.rockspec` in Project A.
2. `PublishRockAction.actionPerformed` computes `server = resolveServer(project)`,
   `exe = resolveExecutable(project)`, `apiKey = LuaRocksApiKeyStore.getApiKey(server)` (prompting
   + `setApiKey(server, …)` on first use for that server).
3. `RockUploadCommand.build(exe, path, apiKey, server = server)` →
   `[exe, "upload", path, "--api-key=…", "--server", "http://localhost:8080"]`, run on
   `Task.Backgroundable`.

### Example 3: Nothing configured (regression)

1. Project B has no override; app `serverUrl` blank. `resolveServer(projectB)` → `null`.
2. `withServer(args, null)` returns `args` unchanged → identical command line to today; upload uses
   the legacy credential key.

## 6. Edge Cases

- **Whitespace-only URL** → treated as unset (trimmed → blank) at both layers.
- **Bound tool became invalid** → `getEffectiveTool` returns `null`; falls back to app executable.
- **Server changed after a key was stored** → key lookup is per-server, so a new server prompts for
  its own key; the old server's key is untouched.
- **Legacy users with a stored `"luarocks.org API key"`** → `getApiKey(null)`/blank-server path
  reads `LEGACY_KEY`, so existing keys keep working.
- **Credentials must never reach XML** → only `serverUrl`/`rocksServerUrl` are in `BaseState`/
  `State`; the API key is only in PasswordSafe.

## 7. Integration Points

```xml
<!-- plugin.xml — new application Configurable for LuaRocks (mirrors the LuaCheck registration
     at plugin.xml:455-458 and sits beside the ROCKS-04 applicationService at :449-450). -->
<applicationConfigurable groupId="tools"
        instance="net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable"
        id="net.internetisalie.lunar.rocks.run.LuaRocksSettingsConfigurable"
        displayName="LuaRocks"/>
```

- No new `<projectConfigurable>` is added: the project server override is surfaced through the
  **existing** `net.internetisalie.lunar.settings.LuaProjectSettingsConfigurable`
  (`plugin.xml:399-404`) by extending its panel (§2.7).
- The existing `<applicationService serviceImplementation="…LuaRocksSettings"/>` (`plugin.xml:450`)
  already registers `LuaRocksSettings`; extending its `State` needs no new registration.
- `LuaProjectSettings` is already a project `@Service` (`plugin.xml` not required — registered via
  `@Service` annotation in `settings/LuaProjectSettings.kt:14`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-06-01 | M | §2.2 |
| ROCKS-06-02 | M | §2.3 |
| ROCKS-06-03 | M | §2.1, §3.1 |
| ROCKS-06-04 | M | §2.1 (§3.3), §2 consumers (`LuaRocksSearchService`), §5 Ex.1 |
| ROCKS-06-05 | M | §2.5, §3.3, §5 Ex.2 |
| ROCKS-06-06 | M | §2.1, §3.2 |
| ROCKS-06-07 | M | §2.4 |
| ROCKS-06-08 | S | §2.6, §2.7, §7 |

## 9. Alternatives Considered

- **Add a project-level executable override** — rejected; TOOL-02 `projectToolBindings` already
  owns per-project tool selection. A second override would compete and confuse precedence.
- **Store the server in PasswordSafe with the key** — rejected; the server URL is not a secret and
  must be VCS-shared, so it belongs in `.idea/lunar.xml`. Only the key is secret.
- **Put `--server` only in run-config `globalFlags`** — that is the status quo (free-text, per-run,
  not shared); rejected as the primary mechanism because it does not cover search/publish or team
  sharing. `globalFlags` remains available for per-run `--tree`/overrides.
- **A LuaRocks-specific credential namespace per server via a new SUBSYSTEM** — rejected; reuse the
  existing `SUBSYSTEM` and vary only the key, preserving the stable namespace contract.

## 10. Open Questions

_None — feature has cleared the planning bar._
