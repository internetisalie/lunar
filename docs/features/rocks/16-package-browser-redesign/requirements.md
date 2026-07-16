---
id: "ROCKS-16"
title: "16: Plugins-Style LuaRocks Package Browser Redesign"
type: "feature"
status: "planned"
priority: "medium"
parent_id: "ROCKS"
folders:
  - "[[features/rocks/requirements|requirements]]"
---

# ROCKS-16: Plugins-Style LuaRocks Package Browser Redesign

## Scope

Redesign the existing **LuaRocks Packages** tool window (ROCKS-02) to mimic the built-in
IDE **Plugins** settings page (Settings → Plugins) in visual idiom and interaction model,
while remaining a **project-scoped tool window** (not a modal Settings page). The precedent
for porting the Plugins idiom onto a non-modal surface is PyCharm's *Python Packages* tool
window.

The redesign replaces the current raw-`javax.swing` browser (search field → `JBList` →
manual-`BorderLayout` `PackageDetailPanel` with `JTextArea`s) with a Kotlin-UI-DSL /
`JB*`-component surface that adds an **Installed** tab, an honest **error state**, an
**empty state**, a rich HTML detail pane, and — most importantly — **canonical install
semantics** so that a browser install lands in the same project rock tree
(`lua_modules/`) that the dependency tree, module resolution, and the library provider read.

This feature **absorbs and supersedes** BUG-363, BUG-365, BUG-366, BUG-367, BUG-368 (each
is folded in as an acceptance criterion below).

### In Scope

- **Plugins-idiom two-tab surface** — a `JBTabbedPane` (or DSL segmented control) with a
  **Marketplace** tab (registry search) and an **Installed** tab (project tree), mirroring
  the Plugins page's Marketplace/Installed split.
- **Canonical install/uninstall target** — every install/uninstall targets the project rock
  tree resolved by `LuaRocksTreeLocator.treeRoot`, via an explicit `--tree` argument, so the
  result is visible to `LuaRocksLibraryProvider`, `LuaRocksDependencyResolver`, and
  `RockspecRunPathProvider`. The active target tree is shown in the UI.
- **Installed tab** backed by `luarocks list --porcelain` against the canonical tree, with
  inline uninstall.
- **Zero-query experience** — the Installed tab is populated with no query; the Marketplace
  tab shows a neutral prompt state (no default registry catalog is fetched — see risks DR-02).
- **Update affordance** — detect `installed-version < latest-available` and surface an
  "Update" badge + button (Should).
- **Immediate in-place state refresh** — install/uninstall flips the row and the detail pane
  in place, without requiring a re-search.
- **Honest error state** — when the `luarocks` binary is unresolved or a CLI call fails, the
  panel renders an error state with a **Configure** hyperlink that opens the Toolchain
  settings page; never the misleading "No packages found".
- **Rich detail pane** — an HTML description pane (`JBHtmlPane`), a name/version header,
  license/homepage, a **clickable dependency list** (clicking a dependency selects/searches
  it), and an inline Install/Uninstall/Update button with in-place progress.
- **Empty state** — `JBPanelWithEmptyText` for "no package selected".
- **Tool-window differentiation** (BUG-366) — rename the two tool windows so their roles are
  unambiguous, and set stripe tooltips.

### Out of Scope

- Rebuilding the ROCKS-03 dependency-tree tool window's tree content (only its **name /
  tooltip** changes here, per BUG-366).
- A background auto-refreshing Marketplace catalog / popular-packages list — dropped for good:
  luarocks.org exposes no public JSON API for search/listing/download counts (risks Gap 2.1,
  DR-03 investigation 2026-07-16); the Marketplace tab keeps a neutral zero-query prompt.
- Multi-tree / system-tree install targets (v1 is the project-local tree only, consistent
  with `LuaRocksTreeLocator` v1 scope).

## Requirements Table

| ID | Requirement | Priority | Status | Description |
| :--- | :--- | :---: | :--- | :--- |
| **ROCKS-16-01** | **Two-tab Plugins-idiom surface** | **M** | Not Implemented | Tool window presents Marketplace and Installed tabs in the Plugins visual idiom, all built from `JB*` / Kotlin UI DSL components (no raw `javax.swing` layout). |
| **ROCKS-16-02** | **Canonical install target** | **M** | Not Implemented | Install/uninstall commands include `--tree <treeRoot>` resolved from `LuaRocksTreeLocator`, so results are visible to the rest of the plugin. The active tree path is shown in the UI. |
| **ROCKS-16-03** | **Installed tab (zero-query)** | **M** | Not Implemented | Installed tab lists installed rocks from `luarocks list --porcelain --tree <treeRoot>` with no search query, each with an inline Uninstall action. |
| **ROCKS-16-04** | **Rich detail pane** | **M** | Not Implemented | Detail pane renders an HTML description (`JBHtmlPane`), a name/version header, license, homepage link, and a clickable dependency list. |
| **ROCKS-16-05** | **Honest error state** | **M** | Not Implemented | On unresolved binary or non-zero CLI exit, the panel shows an error state with a Configure link to the Toolchain settings page — never "No packages found". |
| **ROCKS-16-06** | **Empty state** | **M** | Not Implemented | No-selection state renders via `JBPanelWithEmptyText`, not a `(no package selected)` label (absorbs BUG-367). |
| **ROCKS-16-07** | **Dependencies as clickable list** | **M** | Not Implemented | Dependencies render as a `JBList`, one row per dependency; activating a row selects/searches that dependency (absorbs BUG-368). |
| **ROCKS-16-08** | **Immediate in-place refresh** | **M** | Not Implemented | After install/uninstall, the originating row's installed state and the detail pane's buttons flip in place without a re-search. |
| **ROCKS-16-09** | **UI font & alignment parity** | **M** | Not Implemented | All text uses the standard IDE UI font on a consistent grid (absorbs BUG-363 font mismatch and BUG-365 alignment). |
| **ROCKS-16-10** | **Tool-window differentiation** | **M** | Not Implemented | The two tool windows are renamed to unambiguous roles with stripe tooltips (absorbs BUG-366). |
| **ROCKS-16-11** | **Marketplace search parity** | **M** | Not Implemented | Marketplace tab retains debounced `luarocks search --porcelain` search with the collapsed-arch result model and TTL cache from ROCKS-02. |
| **ROCKS-16-12** | **Update detection & affordance** | **S** | Not Implemented | For an installed rock whose latest available version is greater, surface an Update badge and an Update button that runs `luarocks install --tree <treeRoot>`. |
| **ROCKS-16-13** | **Add-to-rockspec affordance** | **S** | Not Implemented | An "Add to rockspec dependencies" action that appends the installed rock to the discovered project rockspec's `dependencies`. In scope for this feature (owner decision 2026-07-16, risks DR-05). |
| **ROCKS-16-14** | **Version picker parity** | **C** | Not Implemented | Retain the per-package version picker (ROCKS-02-06) in the detail pane; install uses the selected version. |

## Test Cases

### TC-ROCKS-16-01: Canonical install target arguments (unit — design §3.1)
- **Input**: `treeRoot = /proj/lua_modules`, `name = "inspect"`, `version = "3.1.3-0"`.
- **Action**: `LuaRocksInstallCommand.buildInstallArgs(treeRoot, name, version)`.
- **Expected Output**: `["install", "--tree", "/proj/lua_modules", "inspect", "3.1.3-0"]`
  (the `--tree <root>` pair precedes the package name; version last when non-null).

### TC-ROCKS-16-02: Install target arguments without a version (unit — design §3.1)
- **Input**: `treeRoot = /proj/lua_modules`, `name = "inspect"`, `version = null`.
- **Action**: `LuaRocksInstallCommand.buildInstallArgs(treeRoot, name, null)`.
- **Expected Output**: `["install", "--tree", "/proj/lua_modules", "inspect"]`.

### TC-ROCKS-16-03: Uninstall targets the canonical tree (unit — design §3.1)
- **Input**: `treeRoot = /proj/lua_modules`, `name = "inspect"`.
- **Action**: `LuaRocksInstallCommand.buildRemoveArgs(treeRoot, "inspect")`.
- **Expected Output**: `["remove", "--tree", "/proj/lua_modules", "inspect"]`.

### TC-ROCKS-16-04: No tree resolved → install disabled with hint (unit — design §3.5)
- **Input**: `treeRoot = null` (no `lua_modules` / `.luarocks` dir).
- **Action**: `LuaRocksInstallCommand.resolveTargetTree(project)` where `treeRoot` is null.
- **Expected Output**: returns `null`; the caller renders the no-tree hint state
  ("No project rock tree; initialize a LuaRocks project") and disables Install.

### TC-ROCKS-16-05: Installed-list parse per tree (unit — design §4.1)
- **Input** (mock `luarocks list --porcelain --tree /proj/lua_modules` stdout):
  ```
  inspect	3.1.3-0	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
  luassert	1.9.0-1	installed	/proj/lua_modules/lib/luarocks/rocks-5.4
  ```
- **Action**: `LuaRocksInstalledService.parseInstalled(stdout)`.
- **Expected Output**: `[InstalledRockRow("inspect","3.1.3-0"), InstalledRockRow("luassert","1.9.0-1")]`.

### TC-ROCKS-16-06: Update detection (unit — design §3.2)
- **Input**: `installed = "3.1.2-0"`, `latestAvailable = "3.1.3-0"` (both via `LuaRocksVersion.parse`).
- **Action**: `LuaRocksUpdateDetector.hasUpdate(installed, latestAvailable)`.
- **Expected Output**: `true`. For `installed = "3.1.3-0"`, `latestAvailable = "3.1.3-0"` →
  `false`; for `latestAvailable = null` → `false`.

### TC-ROCKS-16-07: Error state on non-zero exit (unit — design §3.5)
- **Input**: search CLI returns `LuaExecResult(exitCode = 1, stderr = "network unreachable")`.
- **Action**: `LuaRocksBrowserModel.runMarketplaceSearch("inspect")`.
- **Expected Output**: model transitions to `BrowserState.Error(message = "network unreachable")`
  (NOT `BrowserState.Results(emptyList())`); the panel renders the error card with a Configure link.

### TC-ROCKS-16-08: Unresolved binary → error state, not empty (unit — design §3.5)
- **Input**: `LuaRocksEnvironment.resolveExecutable(project)` returns `null`.
- **Action**: `LuaRocksBrowserModel.runMarketplaceSearch("inspect")`.
- **Expected Output**: `BrowserState.Error` whose message is the `LUAROCKS_NOT_CONFIGURED`
  string; the panel shows the Configure link.

### TC-ROCKS-16-09: Immediate in-place refresh after install (unit — design §3.4)
- **Input**: a `LuaRockRow(name="inspect", installed=false)` in the Marketplace model; install
  completes with exit code 0.
- **Action**: `LuaRocksBrowserModel.onInstallSucceeded("inspect")`.
- **Expected Output**: the same row is updated to `installed = true` in place; the model fires a
  single row-changed event (no full re-search); `LuaRocksSearchCache.invalidateAll()` is called.

### TC-ROCKS-16-10: Dependency parse into clickable rows (unit — design §4.2)
- **Input** (mock `luarocks show --porcelain inspect` stdout, dependency lines):
  ```
  dependency	lua	>= 5.1
  dependency	luassert	>= 1.7
  ```
- **Action**: `LuaRockMetadata` → `PackageDetailPane.dependencyRows(meta)`.
- **Expected Output**: `["lua >= 5.1", "luassert >= 1.7"]` as list rows; each row's *package
  token* (`lua`, `luassert`) is extractable via `DependencyRow.packageName` for the click-to-search
  action.

### TC-ROCKS-16-11: Marketplace search still collapses arch variants (unit — design §4.3)
- **Input**: the ROCKS-02 porcelain sample (two arch rows for `inspect 3.1.3-0`).
- **Action**: `LuaRocksSearchService.parseSearchOutput(...)` (reused unchanged).
- **Expected Output**: one collapsed `inspect 3.1.3-0` row — regression guard that redesign did
  not alter the parser.

### TC-ROCKS-16-12: Tool-window ids and stripe titles (integration — design §7)
- **Input**: the running IDE with the plugin loaded.
- **Action**: enumerate registered tool windows.
- **Expected Output**: the browser tool window id is `LuaRocks Packages` with stripe title
  "LuaRocks Packages"; the dependency tool window id remains `LuaRocks` but its stripe title is
  "LuaRocks Dependencies" (design §7).
