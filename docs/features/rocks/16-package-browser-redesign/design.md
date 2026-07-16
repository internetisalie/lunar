---
id: "ROCKS-16-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "ROCKS-16"
folders:
  - "[[features/rocks/16-package-browser-redesign/requirements|requirements]]"
---

# Technical Design: ROCKS-16 — Plugins-Style LuaRocks Package Browser Redesign

## 1. Architecture Overview

### Current State

The browser is registered as the bottom tool window `LuaRocks Packages`
(`src/main/resources/META-INF/plugin.xml:76-80`) →
`LuaRocksPackageBrowserToolWindowFactory`
(`src/main/kotlin/net/internetisalie/lunar/rocks/browser/LuaRocksPackageBrowserToolWindowFactory.kt`).
Its inner `PackageBrowserPanel` (raw `JPanel(BorderLayout)`) drives a debounced
`SearchTextField` → `LuaRocksSearchService.search` → `JBList<LuaRockPackage>` with a `✓`
badge, and a `PackageDetailPanel` (raw `JTextArea`s, manual `BorderLayout`).

Insufficiencies (each a requirement below):

1. **Install target is wrong (load-bearing).** `LuaRocksActionHandler.install`
   (`browser/LuaRocksActionHandler.kt:33-58`) runs `luarocks install <name>` with **no
   `--tree` and no working directory**. So installs land in the resolved binary's default
   tree, NOT the project `lua_modules/` tree that
   `LuaRocksTreeLocator.treeRoot` (`rocks/LuaRocksTreeLocator.kt:30-35`) returns and that
   `LuaRocksLibraryProvider.kt:25`, `LuaRocksDependencyResolver.kt:20`, and
   `RockspecRunPathProvider.kt:18` all read. A browser install can be invisible to the rest
   of the plugin.
2. **No zero-query content, no Installed tab.** Empty query → "Enter a package name".
3. **Errors are swallowed.** `LuaRocksSearchService.search` logs a warning and returns
   `emptyList()` on non-zero exit / unresolved binary
   (`browser/LuaRocksSearchService.kt:57-60, 47-49`); the UI then shows the misleading
   "No packages found for X".
4. **Detail panel is raw Swing** — monospaced `JTextArea` (BUG-363), manual layout
   misalignment (BUG-365), `(no package selected)` label (BUG-367), `\n`-joined deps
   (BUG-368).
5. **Stale state** — install only updates the detail label; the list `✓` goes stale until
   re-search.
6. **Two similarly-named tool windows** (BUG-366).

### Prior Art in This Repo

- **Reuse unchanged (extend):**
  - `LuaRocksSearchService` (`browser/LuaRocksSearchService.kt`) — its
    `parseSearchOutput`/`parseInstalledOutput` porcelain parsers and 5-min TTL cache
    (`LuaRocksSearchCache.kt`) are kept for the Marketplace tab. **Extended**: `installed()`
    and `search()` gain a `treeRoot: Path?` parameter so the list/search route through the
    canonical tree (§2.3).
  - `LuaRocksMetadataService` (`browser/LuaRocksMetadataService.kt`) and `LuaRockMetadata`
    (`browser/LuaRockMetadata.kt`) — kept for the detail pane.
  - `LuaRocksEnvironment` (`rocks/LuaRocksEnvironment.kt`) — `resolveExecutable`,
    `resolveServer`, `withServer` reused for all CLI invocations.
  - `LuaRocksTreeLocator.treeRoot(project)` (`rocks/LuaRocksTreeLocator.kt:30`) — the single
    source of the canonical target tree.
  - `LuaRocksVersion` (`rocks/deps/LuaRocksVersion.kt:12`, `: Comparable`) — reused for update
    detection (§3.2).
  - `ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)`
    — the exact configure-hint navigation, already used at
    `toolchain/health/LuaToolEditorNotificationProvider.kt:84`. Reused verbatim for the error
    state's Configure link.
- **Replace:** `PackageDetailPanel` (raw Swing) is **replaced** by `PackageDetailPane`
  (`JBHtmlPane` + Kotlin UI DSL). `LuaRocksPackageBrowserToolWindowFactory`'s inner
  `PackageBrowserPanel` is **replaced** by `LuaRocksBrowserPanel` (two-tab). `LuaRocksActionHandler`
  is **replaced** by `LuaRocksInstallCommand` (arg builder) + `LuaRocksInstallExecutor`
  (background execution).
- **Reference (JetBrains, not our repo, for idiom only):** `PluginManagerConfigurable` and
  `platform/platform-impl/**/plugins/newui/` (`ListPluginComponent.kt`,
  `PluginDetailsPageComponent.kt`, `PluginsTab.kt`, `InstallButton.java`,
  `OneLineProgressIndicator.java`). We copy the *visual idiom*, not the code (those APIs are
  internal to the platform Plugins page). Precedent for tool-window (non-modal) hosting:
  PyCharm's `python/src/com/jetbrains/python/packaging/*` packages surface.

### Target State

A `LuaRocksBrowserPanel` hosts a `JBTabbedPane` with two tabs, each a
`OnePixelSplitter(false)` list-over-detail split reusing one shared `PackageDetailPane`:

- **Marketplace tab** — `SearchTextField` (300 ms `Alarm` debounce) → collapsed search rows.
- **Installed tab** — zero-query list from `luarocks list --porcelain --tree <root>`.

A single `LuaRocksBrowserModel` (per-panel, EDT-confined state) holds a `BrowserState` sealed
hierarchy (`Idle` / `Loading` / `Results` / `Installed` / `Error` / `NoTree`) and drives which
card the panel shows. All CLI work runs on pooled threads via the existing
`LuaToolExecutionService`; results marshal back to the EDT with `invokeLater`.

```
LuaRocksBrowserToolWindowFactory
  └─ LuaRocksBrowserPanel (JBTabbedPane)
       ├─ MarketplaceTab (SearchTextField + JBList<LuaRockRow>)  ─┐
       ├─ InstalledTab   (JBList<InstalledRockRow>)              ─┤→ shared PackageDetailPane
       └─ LuaRocksBrowserModel (BrowserState, EDT)                │   (JBHtmlPane + deps JBList
             ├─ LuaRocksSearchService     (reused, +treeRoot)     │    + InstallButton w/ progress)
             ├─ LuaRocksInstalledService  (new, list --tree)      │
             ├─ LuaRocksMetadataService   (reused)                │
             ├─ LuaRocksUpdateDetector    (new)                   │
             └─ LuaRocksInstallExecutor   (new) → LuaRocksInstallCommand (new, arg builder)
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.rocks.browser.LuaRocksInstallCommand`
- **Responsibility**: Pure builder for `install` / `remove` argument lists against a canonical
  tree; resolves the target tree from the project. No process execution.
- **Threading**: pure — callable from any thread (arg building); `resolveTargetTree` reads
  `project.basePath` only.
- **Collaborators**: `LuaRocksTreeLocator.treeRoot(project)` (`rocks/LuaRocksTreeLocator.kt:30`).
- **Key API**:
  ```kotlin
  object LuaRocksInstallCommand {
      fun resolveTargetTree(project: Project): Path?            // = LuaRocksTreeLocator.treeRoot(project)
      fun buildInstallArgs(treeRoot: Path, name: String, version: String?): List<String>
      fun buildRemoveArgs(treeRoot: Path, name: String): List<String>
  }
  ```

### 2.2 `net.internetisalie.lunar.rocks.browser.LuaRocksInstallExecutor`
- **Responsibility**: Runs install/remove/update on a `Task.Backgroundable`, using
  `LuaRocksInstallCommand` args + `LuaRocksEnvironment.resolveExecutable`; invalidates the
  cache and reports success/failure back on the EDT.
- **Threading**: launches `ProgressManager.getInstance().run(Task.Backgroundable)`; the CLI
  capture runs on the task's background thread; `onDone` fires on the EDT via `invokeLater`.
- **Collaborators**: `LuaRocksEnvironment` (`rocks/LuaRocksEnvironment.kt:51,62`),
  `LuaToolExecutionService.getInstance().capture(...)` (`toolchain/exec/LuaToolExecutionService.kt:23`),
  `LuaExecTimeout.INSTALL` (`toolchain/exec/LuaExecTimeout.kt:8`), `LuaRocksSearchCache`,
  `NotificationGroupManager` (group id `notification.group.lunar.luarocks`, `plugin.xml:668`).
- **Key API**:
  ```kotlin
  class LuaRocksInstallExecutor(private val project: Project) {
      fun install(request: InstallRequest, onDone: (Boolean) -> Unit)   // InstallRequest carries name/version/treeRoot
      fun remove(name: String, treeRoot: Path, onDone: (Boolean) -> Unit)
  }
  data class InstallRequest(val name: String, val version: String?, val treeRoot: Path)
  ```
  (`InstallRequest` is the 3-arg-cap context object per engineering-contract §3.)

### 2.3 `net.internetisalie.lunar.rocks.browser.LuaRocksInstalledService`
- **Responsibility**: Lists installed rocks from the canonical tree via
  `luarocks list --porcelain --tree <root>`, parsed to `InstalledRockRow`.
- **Threading**: pooled thread only (blocking `capture`).
- **Collaborators**: `LuaRocksEnvironment`, `LuaToolExecutionService`, `LuaExecTimeout.COMMAND`.
- **Key API**:
  ```kotlin
  object LuaRocksInstalledService {
      fun list(project: Project, treeRoot: Path): List<InstalledRockRow>   // non-zero exit → throws BrowserCliError
      internal fun parseInstalled(stdout: String): List<InstalledRockRow>
  }
  data class InstalledRockRow(val name: String, val version: String)
  ```

### 2.4 `net.internetisalie.lunar.rocks.browser.LuaRocksUpdateDetector`
- **Responsibility**: Decides whether an installed rock has a newer available version.
- **Threading**: pure.
- **Collaborators**: `LuaRocksVersion.parse` / `compareTo` (`rocks/deps/LuaRocksVersion.kt:17`).
- **Key API**:
  ```kotlin
  object LuaRocksUpdateDetector {
      fun hasUpdate(installedVersion: String, latestAvailable: String?): Boolean
      fun latestOf(rows: List<LuaRockRow>): String?     // max by LuaRocksVersion over a name's search rows
  }
  ```

### 2.5 `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserModel`
- **Responsibility**: Per-panel EDT-confined state machine; owns the current `BrowserState`,
  the Marketplace row list, the Installed row list, and the selected row; exposes mutation
  entry points the tabs and detail pane call.
- **Threading**: mutated on the EDT only; kicks CLI work to pooled threads and marshals back.
- **Collaborators**: all services above; fires `stateChanged` / `rowChanged` to a listener the
  panel registers.
- **Key API**:
  ```kotlin
  class LuaRocksBrowserModel(private val project: Project, private val listener: Listener) {
      fun runMarketplaceSearch(query: String)      // → Loading → Results | Error
      fun loadInstalled()                          // → Loading → Installed | Error | NoTree
      fun onInstallSucceeded(name: String)         // flips row.installed=true in place, invalidates cache
      fun onRemoveSucceeded(name: String)          // flips row.installed=false / drops installed row
      interface Listener { fun onState(state: BrowserState); fun onRowChanged(index: Int) }
  }
  sealed interface BrowserState {
      data object Idle : BrowserState
      data object Loading : BrowserState
      data class Results(val rows: List<LuaRockRow>) : BrowserState
      data class Installed(val rows: List<InstalledRockRow>) : BrowserState
      data class Error(val message: String) : BrowserState
      data object NoTree : BrowserState
  }
  data class LuaRockRow(val pkg: LuaRockPackage, var installed: Boolean, val hasUpdate: Boolean)
  ```

### 2.6 `net.internetisalie.lunar.rocks.browser.PackageDetailPane`
- **Responsibility**: Replaces `PackageDetailPanel`. Renders selected-package detail in the
  Plugins idiom: header (name + version picker), `JBHtmlPane` description, license/homepage,
  a `JBList<DependencyRow>` (clickable), and an inline Install/Uninstall/Update button with an
  in-place `OneLineProgressIndicator`-style progress; a `CardLayout` toggles Empty / Detail /
  Error / NoTree cards.
- **Threading**: EDT (UI); metadata fetch on pooled thread → `invokeLater`.
- **Collaborators**: `LuaRocksMetadataService.show` (`browser/LuaRocksMetadataService.kt:32`),
  `LuaRocksInstallExecutor`, `JBHtmlPane`
  (`com.intellij.ui.components.JBHtmlPane`), `JBPanelWithEmptyText`
  (`com.intellij.ui.components.JBPanelWithEmptyText`), `JBList`, `Desktop` for homepage.
- **Key API**:
  ```kotlin
  class PackageDetailPane(project: Project, private val model: LuaRocksBrowserModel) : JPanel(CardLayout()) {
      fun showPackage(row: LuaRockRow, versions: List<String>)
      fun showInstalled(row: InstalledRockRow)
      fun showEmpty()
      fun showError(message: String)     // renders Configure link
      fun showNoTree()
      internal fun dependencyRows(meta: LuaRockMetadata): List<DependencyRow>
  }
  data class DependencyRow(val raw: String) { val packageName: String get() = raw.substringBefore(' ').trim() }
  ```

### 2.7 `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserPanel`
- **Responsibility**: Top-level `JBTabbedPane` panel; builds the Marketplace and Installed
  tabs, the shared `PackageDetailPane`, wires selection listeners, and shows the active target
  tree path in a north status strip. Replaces the inner `PackageBrowserPanel`.
- **Threading**: EDT.
- **Collaborators**: `LuaRocksBrowserModel`, `PackageDetailPane`, `LuaRocksInstallCommand.resolveTargetTree`.
- **Key API**:
  ```kotlin
  class LuaRocksBrowserPanel(project: Project) : JBPanel<*>(BorderLayout()), LuaRocksBrowserModel.Listener
  ```

### 2.8 `net.internetisalie.lunar.rocks.browser.LuaRocksBrowserToolWindowFactory`
- **Responsibility**: Renames/replaces `LuaRocksPackageBrowserToolWindowFactory`; instantiates
  `LuaRocksBrowserPanel`. (Class renamed for clarity; the old file is deleted.)
- **Threading**: EDT (`createToolWindowContent`).
- **Key API**: `class LuaRocksBrowserToolWindowFactory : ToolWindowFactory, DumbAware`.

## 3. Algorithms

### 3.1 Canonical install / uninstall argument assembly
- **Input → Output**: `(treeRoot: Path, name: String, version: String?)` → `List<String>`.
- **Steps** (`buildInstallArgs`):
  1. Start `result = ["install", "--tree", treeRoot.toString()]`.
  2. `result += name`.
  3. If `version != null && version.isNotBlank()`, `result += version`.
  4. Return `result`.
- **`buildRemoveArgs`**: `["remove", "--tree", treeRoot.toString(), name]`.
- **`resolveTargetTree(project)`**: return `LuaRocksTreeLocator.treeRoot(project)` (may be
  `null`). When `null`, the caller renders `BrowserState.NoTree` and disables Install.
- **Execution** (in `LuaRocksInstallExecutor`): the full command line is
  `GeneralCommandLine(exe, *args.toTypedArray()).withWorkDirectory(treeRoot.parent?.toString())`
  where `exe = LuaRocksEnvironment.resolveExecutable(project)`. The `--server` global flag is
  NOT applied to install/remove (server affects search/manifest, not local tree ops).
- **Rules / edge handling**: `--tree <root>` is a luarocks global flag and precedes the
  subcommand semantics only for global flags; luarocks accepts `install --tree <root>` form
  (subcommand-scoped), which is what we emit for determinism. `treeRoot` is always the
  directory `LuaRocksTreeLocator` reads, guaranteeing visibility to consumers.

### 3.2 Update detection
- **Input → Output**: `(installedVersion: String, latestAvailable: String?)` → `Boolean`.
- **Steps** (`hasUpdate`):
  1. If `latestAvailable == null` return `false`.
  2. `installed = LuaRocksVersion.parse(installedVersion)`,
     `latest = LuaRocksVersion.parse(latestAvailable)`.
  3. Return `installed.compareTo(latest) < 0`.
- **`latestOf(rows)`**: filter `rows` to a single package name (caller-scoped), map to
  `LuaRocksVersion.parse(row.pkg.version)`, return `maxOrNull()?.toString()` (or `null` when
  empty). `LuaRocksVersion` is `Comparable` (`rocks/deps/LuaRocksVersion.kt:16`).
- **Edge handling**: an unparseable version parses to a zero-valued `LuaRocksVersion` (per
  existing `LuaRocksVersion.parse`), so a malformed installed version never spuriously reports
  an update against another malformed version (equal → false).

### 3.3 Marketplace search flow (state transitions)
- **Input**: debounced `query` from the Marketplace `SearchTextField`.
- **Steps** (`LuaRocksBrowserModel.runMarketplaceSearch`):
  1. On EDT: if `query.isBlank()` set `BrowserState.Idle` (neutral prompt) and return.
  2. Set `BrowserState.Loading`; fire `onState`.
  3. `executeOnPooledThread`: resolve `exe`; if `null` → post `BrowserState.Error(LUAROCKS_NOT_CONFIGURED)`.
  4. Else call `LuaRocksSearchService.search(query, project, treeRoot)`; on the new
     `BrowserCliError` (non-zero exit, §3.5) → post `BrowserState.Error(stderr)`.
  5. Else build `LuaRockRow`s (installed flag from the tree list; `hasUpdate` computed via
     §3.2 against `latestOf`) and post `BrowserState.Results(rows)`.
  6. All posts go through `invokeLater { … onState(state) }`.

### 3.4 In-place refresh after install/uninstall
- **Input → Output**: `name: String` → mutated row + single change event.
- **Steps** (`onInstallSucceeded`):
  1. Find the index `i` of the Marketplace row whose `pkg.name == name`.
  2. If found, set `rows[i].installed = true`; call `LuaRocksSearchCache.invalidateAll()`; fire
     `listener.onRowChanged(i)` (repaints just that cell).
  3. Independently, if the Installed tab is materialized, call `loadInstalled()` to refresh it.
- **`onRemoveSucceeded`** is symmetric: flip `installed=false` on the Marketplace row; drop the
  matching `InstalledRockRow` from the Installed list and fire the change.
- **Rule**: never trigger a full `runMarketplaceSearch` on install/uninstall — the current
  query stays; only the affected row and the Installed list change.

### 3.5 Honest error handling (replaces silent-empty)
- **Rule**: the browser distinguishes **empty result** (query ran, zero matches) from **failure**
  (binary unresolved OR non-zero exit). The current `LuaRocksSearchService.search` collapses
  both to `emptyList()` — this design threads failure through instead:
  - `LuaRocksSearchService.search`/`installed` and `LuaRocksInstalledService.list` throw
    `net.internetisalie.lunar.rocks.browser.BrowserCliError(message: String)` on unresolved
    binary or non-zero exit (carrying trimmed `stderr` or `LUAROCKS_NOT_CONFIGURED`). Existing
    non-browser callers keep the graceful path via a `searchOrEmpty` wrapper (§6).
  - The model catches `BrowserCliError` → `BrowserState.Error(message)`.
  - `PackageDetailPane.showError(message)` renders the message plus a Configure link that runs
    `ShowSettingsUtil.getInstance().showSettingsDialog(project, LuaToolchainConfigurable::class.java)`
    (verbatim from `toolchain/health/LuaToolEditorNotificationProvider.kt:84`).
- **Distinction check**: exit code 0 with empty stdout → `Results(emptyList())` renders an
  empty-list state ("No packages match \"X\""); any non-zero exit → `Error`.

## 4. External Data & Parsing

### 4.1 `luarocks list --porcelain --tree <root>` (Installed tab)
- **Format**: whitespace/tab-separated rows, one per installed rock:
  ```
  <name>\t<version>\t<status>\t<install-path>
  ```
  Real sample:
  ```
  inspect	3.1.3-0	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
  luassert	1.9.0-1	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
  ```
- **Parse strategy** (`LuaRocksInstalledService.parseInstalled`): for each non-blank line,
  `split(Regex("\\s+"))`; require `size >= 2`; take field 0 as `name`, field 1 as `version`;
  emit `InstalledRockRow(name, version)`. Lines with `< 2` fields are skipped. (This is a
  superset of the existing `parseInstalledOutput` which reads only field 0.)
- **Maps to**: `InstalledRockRow`.
- **Failure handling**: non-zero exit → throw `BrowserCliError(stderr)`; empty stdout with exit
  0 → empty list (a valid empty tree).

### 4.2 `luarocks show --porcelain <name> [version]` (dependencies)
- **Format**: reused exactly from ROCKS-02 (`LuaRocksMetadataService.parseShowOutput`,
  `browser/LuaRocksMetadataService.kt:53`). Dependency lines: `dependency\t<name>\t<label>`,
  producing `"<name> <label>"` strings in `LuaRockMetadata.dependencies`.
- **Parse strategy** (`PackageDetailPane.dependencyRows`): map each
  `LuaRockMetadata.dependencies` string to `DependencyRow(raw)`; `DependencyRow.packageName`
  = `raw.substringBefore(' ').trim()`. This yields the clickable token (`lua`, `luassert`).
- **Maps to**: `DependencyRow` rows in the deps `JBList`.
- **Failure handling**: unchanged — `show` returns `null` → detail pane shows "Could not load
  metadata." (still in the Detail card, not an Error card, since a failed `show` is not a
  configuration failure).

### 4.3 `luarocks search --porcelain <query>` (Marketplace)
- **Format & parser**: reused verbatim from `LuaRocksSearchService.parseSearchOutput`
  (`browser/LuaRocksSearchService.kt:102`); arch collapse and `LuaRockPackage` mapping are
  unchanged (TC-ROCKS-16-11 is a regression guard).

## 5. Data Flow

### Example 1: Marketplace search + canonical install
1. User types `inspect`; 300 ms `Alarm` fires `model.runMarketplaceSearch("inspect")`.
2. Model → `Loading`; pooled thread resolves exe + tree, runs `search`, builds rows with
   `installed`/`hasUpdate`; posts `Results`.
3. Panel renders the collapsed rows in the Marketplace `JBList`; selecting one calls
   `detailPane.showPackage(row, versions)` → pooled `show` → `JBHtmlPane` + deps list.
4. User clicks Install → `LuaRocksInstallExecutor.install(InstallRequest("inspect", ver, treeRoot))`
   builds `["install","--tree","/proj/lua_modules","inspect","<ver>"]`, runs it, exit 0 →
   `model.onInstallSucceeded("inspect")` flips the row `✓` in place and invalidates the cache.
5. `LuaRocksLibraryProvider`/`LuaRocksDependencyResolver` now see the rock (same tree).

### Example 2: Unresolved binary
1. User types `inspect`; model → `Loading`; pooled thread: `resolveExecutable` returns `null`.
2. Model posts `BrowserState.Error(LUAROCKS_NOT_CONFIGURED)`.
3. Panel shows the Error card; clicking Configure opens `LuaToolchainConfigurable`.

### Example 3: Installed tab, zero query
1. User switches to the Installed tab → `model.loadInstalled()`.
2. If `resolveTargetTree` is `null` → `NoTree` card ("Initialize a LuaRocks project"). Else
   pooled `LuaRocksInstalledService.list(project, treeRoot)` → `Installed(rows)`.
3. Each row's inline Uninstall runs `buildRemoveArgs(treeRoot, name)`; on success
   `model.onRemoveSucceeded(name)` drops the row in place.

## 6. Edge Cases

- **Non-browser callers of `LuaRocksSearchService`**: the throwing change (§3.5) must not break
  them. `search`/`installed` keep an `…OrEmpty` wrapper (`searchOrEmpty`, `installedOrEmpty`)
  returning the graceful empty on `BrowserCliError`; existing callers migrate to the wrapper.
  (Grep confirms the only current callers are the browser and its tests, so this is a contained
  change.)
- **Tree exists but empty**: `list` exit 0, empty stdout → `Installed(emptyList())` → empty-text
  "No rocks installed in this tree."
- **Search returns a rock already installed**: `installed=true` from the tree cross-ref; Install
  button becomes Uninstall (+ Update if `hasUpdate`).
- **Version picker + update**: Update button installs the `latestOf` version, not the currently
  selected picker value.
- **`treeRoot.parent` null** (tree at filesystem root — pathological): fall back to no working
  directory; `--tree` alone is authoritative.
- **Rapid tab switches / searches**: each pooled result checks the model's monotonically
  increasing `requestId` before posting; stale results are dropped (prevents a slow search
  overwriting a newer one).

## 7. Integration Points

Tool-window registration changes in `src/main/resources/META-INF/plugin.xml`. The browser
factory class is renamed; the dependency tool window keeps id `LuaRocks` but the design assigns
distinct human-readable stripe titles via each factory's `ToolWindow` (or a `key` bundle
entry). Ids are unchanged to preserve layout state; only the factory class and displayed titles
change.

```xml
<!-- plugin.xml (replaces the block at lines 75-80) -->
<toolWindow
        id="LuaRocks Packages"
        anchor="bottom"
        icon="net.internetisalie.lunar.lang.LuaIcons.ROCKET"
        factoryClass="net.internetisalie.lunar.rocks.browser.LuaRocksBrowserToolWindowFactory"/>
```

- **Stripe titles (BUG-366)**: the dependency tool window (`id="LuaRocks"`,
  `rocks/ui/LuaRocksToolWindowFactory.kt`) sets `toolWindow.stripeTitle = "LuaRocks Dependencies"`
  and `toolWindow.title = "LuaRocks Dependencies"` in `createToolWindowContent`; the browser sets
  `stripeTitle = "LuaRocks Packages"`. Both set `toolWindow.setToolTipText(...)` describing the
  role ("Browse & install LuaRocks packages" vs "Project dependency tree").
- **Notification group**: reuse `notification.group.lunar.luarocks` (`plugin.xml:668`).
- **Settings navigation**: `LuaToolchainConfigurable`
  (`toolchain/ui/LuaToolchainConfigurable.kt`, id
  `net.internetisalie.lunar.toolchain.ui.LuaToolchainConfigurable`) via `ShowSettingsUtil`.
- **No new extension points, indexes, or services** are registered; all new classes are plain
  objects/panels instantiated by the factory. `LuaToolExecutionService` /
  `LuaRockspecDiscoveryService` remain the only `@Service`s touched (both already registered).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| ROCKS-16-01 | M | §2.7, §2.8, §7 |
| ROCKS-16-02 | M | §2.1, §2.2, §3.1 |
| ROCKS-16-03 | M | §2.3, §3.3(Installed), §4.1 |
| ROCKS-16-04 | M | §2.6, §4.2 |
| ROCKS-16-05 | M | §2.6, §3.5, §7 |
| ROCKS-16-06 | M | §2.6 (Empty card, `JBPanelWithEmptyText`) |
| ROCKS-16-07 | M | §2.6, §4.2 |
| ROCKS-16-08 | M | §2.5, §3.4 |
| ROCKS-16-09 | M | §2.6 (`JBHtmlPane`/DSL, no `JTextArea`) |
| ROCKS-16-10 | M | §7 (stripe titles) |
| ROCKS-16-11 | M | §2.3, §4.3 |
| ROCKS-16-12 | S | §2.4, §3.2 |
| ROCKS-16-13 | S | §2.6 button + risks DR-05 (in scope, owner 2026-07-16) |
| ROCKS-16-14 | C | §2.6 (version picker) |

## 9. Alternatives Considered

- **Single tool window with tabs vs. two tool windows** (BUG-366): kept two tool windows
  (renamed) rather than merging the dependency tree into the browser, because the dependency
  tree is out of scope to rebuild and merging would entangle two independent lifecycles. Chosen:
  differentiate by stripe title/tooltip.
- **Modal Settings page (like real Plugins) vs. tool window**: product owner decided tool window
  (non-modal, project-scoped); PyCharm Python Packages precedent. Not re-litigated.
- **`--tree` vs. `--local` vs. working-directory-only**: `--tree <root>` is explicit and
  deterministic regardless of cwd; a bare working directory does not force luarocks to use the
  project tree. Chosen: explicit `--tree` (with cwd as a secondary hint).
- **Reusing platform Plugins-page components** (`ListPluginComponent`, `InstallButton`): those
  are platform-internal and tightly bound to `PluginUiModel`; we replicate the idiom with public
  `JB*`/UI-DSL components instead.

## 10. Open Questions

_None — the feature has cleared the planning bar. Deferred and uncertain items are tracked as DR tasks in risks-and-gaps.md._
