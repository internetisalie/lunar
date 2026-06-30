---
id: ROCKS-02-DESIGN
title: "Technical Design"
type: design
parent_id: ROCKS-02
priority: "medium"
folders:
  - "[[features/rocks/02-package-browser/requirements|requirements]]"
---

# Technical Design: Package Browser (ROCKS-02)

## 1. Architecture Overview

### Current State
No package-browser code exists. External processes run via
`net.internetisalie.lunar.util.LuaProcessUtil.capture(GeneralCommandLine, timeout):
ProcessOutput`. The `luarocks` binary path comes from `LuaRocksSettings` (defined in ROCKS-04
`net.internetisalie.lunar.rocks.run.LuaRocksSettings`, default `"luarocks"`). There is **no**
`LuaToolManager` — the prior draft's reference to it is replaced by `LuaRocksSettings`.

### Target State
A bottom tool window with a debounced search field, a results list, and a detail pane.
Search/metadata/install all shell out to `luarocks` **in `--porcelain` mode** (machine-readable,
so no fragile human-text parsing) on background threads, pushing results to the EDT.

```
search field ─debounce─▶ LuaRocksSearchService ─`luarocks search --porcelain`─▶ List<LuaRockPackage>
selection ──────────────▶ LuaRocksMetadataService ─`luarocks show --porcelain`──▶ LuaRockMetadata
Install/Uninstall ──────▶ LuaRocksActionHandler ─`luarocks install|remove`──▶ refresh
                          LuaRocksSearchCache (TTL + manual/Δ invalidation)
```

## 2. Core Components

### 2.1 Data models (`net.internetisalie.lunar.rocks.browser`)
```kotlin
data class LuaRockPackage(
    val name: String, val version: String, val arch: String,    // arch: "rockspec"|"src"|"all"|<bin-arch>
    val repo: String, val namespace: String, val isInstalled: Boolean = false)
data class LuaRockMetadata(
    val name: String, val version: String, val summary: String?, val detailed: String?,
    val license: String?, val homepage: String?, val issues: String?,
    val dependencies: List<String>, val modules: List<String>, val location: String?)
```

### 2.2 `net.internetisalie.lunar.rocks.browser.LuaRocksSearchService`
- **Responsibility**: Run `luarocks search`/`list` and parse porcelain output.
- **Threading**: background only (`capture` blocks); callers wrap in a `Task.Backgroundable`.
- **Key API**:
  ```kotlin
  object LuaRocksSearchService {
      fun search(query: String): List<LuaRockPackage>     // §3.1, §4.1
      fun installed(): Set<String>                        // `luarocks list --porcelain` names
  }
  ```

### 2.3 `net.internetisalie.lunar.rocks.browser.LuaRocksMetadataService`
  ```kotlin
  object LuaRocksMetadataService {
      fun show(name: String, version: String?): LuaRockMetadata?   // §3.2, §4.2
  }
  ```

### 2.4 `net.internetisalie.lunar.rocks.browser.LuaRocksActionHandler`
- **Responsibility**: install/uninstall under a background progress task.
  ```kotlin
  object LuaRocksActionHandler {
      fun install(project: Project, name: String, version: String?, onDone: (Boolean) -> Unit)
      fun uninstall(project: Project, name: String, onDone: (Boolean) -> Unit)   // §3.3
  }
  ```

### 2.5 `net.internetisalie.lunar.rocks.browser.LuaRocksSearchCache`
- **Responsibility**: In-memory cache of search results with TTL.
- **Key API**:
  ```kotlin
  object LuaRocksSearchCache {
      private const val TTL_MS = 300_000L                 // 5 min
      data class Entry(val results: List<LuaRockPackage>, val storedAtMs: Long)
      // ConcurrentHashMap<String /*query*/, Entry>
      fun get(query: String, nowMs: Long): List<LuaRockPackage>?   // null if absent or stale
      fun put(query: String, results: List<LuaRockPackage>, nowMs: Long)
      fun invalidateAll()                                  // on install/uninstall + Refresh
  }
  ```
- **Staleness policy** (the previously-undefined gap): an entry is stale when
  `nowMs - storedAtMs > TTL_MS`; `get` returns null for stale/missing (forcing a fresh CLI
  call). `invalidateAll` is called by `LuaRocksActionHandler` on success and by the Refresh
  toolbar action. `nowMs` is supplied by the caller (`System.currentTimeMillis()`).

### 2.6 `net.internetisalie.lunar.rocks.browser.LuaRocksPackageBrowserToolWindowFactory`
- **Responsibility**: Build the split-pane UI.
- **Threading**: EDT for Swing; CLI via `Task.Backgroundable`; updates via `invokeLater`.
- **Key API**:
  ```kotlin
  class LuaRocksPackageBrowserToolWindowFactory : ToolWindowFactory, DumbAware {
      override fun createToolWindowContent(project: Project, toolWindow: ToolWindow)
  }
  // SearchTextField + `Alarm`(300ms debounce); OnePixelSplitter(JBList<LuaRockPackage> | PackageDetailPanel)
  ```

## 3. Algorithms

### 3.1 Search (`LuaRocksSearchService.search`)
- **Steps**:
  1. `cached = LuaRocksSearchCache.get(query, System.currentTimeMillis())`; if non-null return.
  2. `exe = LuaRocksSettings.getInstance().executablePath`.
  3. `out = LuaProcessUtil.capture(GeneralCommandLine(exe, "search", "--porcelain", query), 15_000)`.
  4. If `out.exitCode != 0` → return empty list (and `log.warn` stderr).
  5. Parse each stdout line per §4.1 into `LuaRockPackage` (default `isInstalled=false`).
  6. Mark `isInstalled` for names in `installed()`.
  7. `LuaRocksSearchCache.put(query, results, now)`; return.
- **Edge**: blank query → return empty without calling the CLI.

### 3.2 Metadata (`LuaRocksMetadataService.show`)
- **Steps**: run `GeneralCommandLine(exe, "show", "--porcelain", name, *(version?.let{arrayOf(it)} ?: emptyArray()))`,
  `capture(…, 15_000)`; on success parse per §4.2 → `LuaRockMetadata`; non-zero exit → null.

### 3.3 Install / Uninstall (`LuaRocksActionHandler`)
- **Steps** (inside `ProgressManager` `Task.Backgroundable`):
  1. argv install = `[exe, "install", name] + (version?:[]) `; uninstall = `[exe, "remove", name]`.
  2. `out = LuaProcessUtil.capture(cmd, 120_000)`.
  3. On `exitCode == 0`: `LuaRocksSearchCache.invalidateAll()`; `onDone(true)`; show a success
     `Notification`. Else `onDone(false)` + error notification with `out.stderr`.
- **Note**: long/interactive installs that need a console use the ROCKS-04 run config instead;
  the browser button is the quick path (capture + notification).

## 4. External Data & Parsing

### 4.1 `luarocks search --porcelain <query>`
- **Format**: one result per line, **5 space-separated fields**:
  `<name> <version> <arch> <repo> <namespace>` (from luarocks `search.lua`:
  `util.printout(packagestr, version, repo.arch, nrepo, repo.namespace)`). `arch` is one of
  `rockspec`, `src`, `all`, or a binary arch (e.g. `linux-x86_64`). Sample:
  ```
  inspect 3.1.3-0 rockspec https://luarocks.org/manifests/kikito
  inspect 3.1.3-0 src https://luarocks.org/manifests/kikito
  ```
- **Parse strategy**: for each non-blank line, `val f = line.trim().split(Regex("\\s+"))`;
  **skip the line if `f.size < 4`**; map `name=f[0]`, `version=f[1]`, `arch=f[2]`, `repo=f[3]`,
  `namespace=f.getOrElse(4){""}` to `LuaRockPackage`. For the results list, **collapse** rows
  sharing `(name, version)` (the arch variants) into one entry, keeping the first repo/namespace;
  the distinct versions feed the Version Picker (ROCKS-02-06).
- **`installed()` parse**: `luarocks list --porcelain` emits the same field order; take `f[0]`
  (name) of each line with `f.size >= 1` into the returned `Set<String>`.
- **Failure handling**: a line with `< 4` fields is skipped (search) / blank lines ignored.

### 4.2 `luarocks show --porcelain <name> [<version>]`
- **Format**: tab-separated `key\tvalue[\tvalue2]` lines (from luarocks `cmd/show.lua`
  porcelain template). Keys: `namespace`, `package`, `version`, `summary`, `detailed`,
  `license`, `homepage`, `issues`, `labels`, `location`, and repeatable
  `command\t<name>\t<file>`, `module\t<name>\t<file>`,
  `dependency\t<name>\t<label>`, `build_dependency\t…`, `test_dependency\t…`,
  `indirect_dependency\t…`. Sample:
  ```
  package	inspect
  version	3.1.3-0
  summary	Human-readable representation of Lua tables
  license	MIT
  homepage	https://github.com/kikito/inspect.lua
  dependency	lua	>= 5.1
  ```
- **Parse strategy**: for each line, `val f = line.split('\t')`; `when (f[0])`:
  `"package"`→name, `"version"`→version, `"summary"`→summary, `"detailed"`→append,
  `"license"`→license, `"homepage"`→homepage, `"issues"`→issues, `"location"`→location,
  `"module"`→modules += `f[1]`, `"dependency"`→dependencies += `"${f[1]} ${f.getOrElse(2){""}}".trim()`.
  Ignore unrecognised keys. Build `LuaRockMetadata`.
- **Failure handling**: lines without a tab are ignored.

## 5. Data Flow

### Example 1: search "inspect" (TC-ROCKS-02-01)
Debounced input → `search("inspect")` → cache miss → `luarocks search --porcelain inspect` →
parse 5-field lines, collapse arch variants → list shows `inspect 3.1.3-0` (and older
versions) → cached.

### Example 2: select + install (TC-ROCKS-02-02/03)
Selection → `show("inspect", "3.1.3-0")` → detail pane shows summary/license/homepage/deps.
Install button → `LuaRocksActionHandler.install(project, "inspect", "3.1.3-0")` →
`luarocks install inspect 3.1.3-0` in background → success notification → cache invalidated →
the row re-renders as Installed.

## 6. Edge Cases

| Case | Handling |
| :--- | :--- |
| `luarocks` missing | `capture` non-zero/timeout → empty results + a one-time "configure luarocks" notice. |
| No network | search returns empty/non-zero; cached results (if fresh) still serve (ROCKS-02-07 best-effort). |
| Namespace empty | `getOrElse(4){""}`. |
| Same package, many arches | collapsed by `(name,version)` in the list. |
| Very large result set | list virtualised by `JBList`; no extra paging in v1. |

## 7. Integration Points

```xml
<!-- META-INF/plugin.xml, inside <extensions defaultExtensionNs="com.intellij"> -->
<toolWindow
    id="LuaRocks Packages"
    anchor="bottom"
    icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET"
    factoryClass="net.internetisalie.lunar.rocks.browser.LuaRocksPackageBrowserToolWindowFactory"/>
```
- Icon `LuaIcons.ROCKET`: `LuaIcons` currently has only `FILE` (which already loads
  `/icons/rocket_16.png`); add a `ROCKET` field backed by that asset (shared with ROCKS-03/04).
- Binary path: `LuaRocksSettings.getInstance().executablePath` (ROCKS-04).
- Notifications: reuse a `NotificationGroup` (the repo already declares notification groups);
  add `id="Lunar LuaRocks"` if a dedicated group is wanted.
- Services are stateless `object`s; the cache is a process-wide `object` (no registration).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-02-01 Search Interface | M | §2.6 (search field), §3.1 |
| ROCKS-02-02 Split-View Browser | M | §2.6 (`OnePixelSplitter`) |
| ROCKS-02-03 Remote Integration | M | §3.1, §4.1 (`luarocks search`) |
| ROCKS-02-04 Install/Uninstall | M | §2.4, §3.3 |
| ROCKS-02-05 Manifest Caching | S | §2.5 (TTL cache) |
| ROCKS-02-06 Version Picker | S | §4.1 (distinct versions), §2.6 |
| ROCKS-02-07 Offline Mode | C | §2.5 (serve fresh cache), §6 |

## 9. Alternatives Considered

- **`--porcelain` vs human output parsing**: porcelain is machine-stable (space/tab-delimited),
  eliminating fragile regex over localized human text. Chosen.
- **`LuaRocksSettings` vs TOOL epic**: the app-service binary path already exists (ROCKS-04);
  no dependency on the unbuilt TOOL epic.
- **Quick `capture` install vs run-config install**: the browser uses `capture`+notification
  for one-click; users needing a live console use the ROCKS-04 run config.

## 10. Open Questions

_None — feature has cleared the planning bar._
