---
id: TARGET-08-DESIGN
parent_id: TARGET-08
type: design
folders:
  - "[[features/target/08-on-demand-definition-libraries/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: TARGET-08 — On-demand LuaLS / LuaCATS Definition Libraries

## 1. Architecture Overview

### Current State

Lunar injects library roots through `AdditionalLibraryRootsProvider` implementations registered in `plugin.xml` (`src/main/resources/META-INF/plugin.xml:516-518`):

- `net.internetisalie.lunar.project.PlatformLibraryProvider` — bundled `runtime/` stdlib stubs (`src/main/kotlin/net/internetisalie/lunar/project/PlatformLibraryProvider.kt:41`).
- `net.internetisalie.lunar.rocks.library.LuaRocksLibraryProvider` — installed-rocks trees (`src/main/kotlin/net/internetisalie/lunar/rocks/library/LuaRocksLibraryProvider.kt:14`).

`LuaRocksLibraryProvider` is the exact prior-art pattern for TARGET-08: it resolves an **external, not-bundled** on-disk tree (`LuaRocksTreeLocator.treeRoot(project)`), exposes it as a `SyntheticLibrary` source root, and implements `getRootsToWatch`. Once a directory is a source root, the platform indexer + the existing LuaCATS `@meta` parser (`luacats/lang/lexer/luacats.flex:105`) index every `.lua` underneath — no per-feature indexer work is needed.

The TOOLING provisioning stack already implements robust off-EDT fetch + verify + extract:

- `LuaArtifactDownloader.fetch(urls, sha256, size, indicator)` — mirror-aware download, `.part` temp + atomic move, size then SHA-256 verify, on-disk cache under `<system>/lunar/downloads/` (`src/main/kotlin/net/internetisalie/lunar/toolchain/provision/LuaArtifactDownloader.kt:36`).
- `LuaArchiveExtractor.extract(archive, targetDir, rootPrefix, indicator)` — zip/tar.gz decompress with prefix strip + cancellation (`.../LuaArchiveExtractor.kt:23`).
- `LuaToolchainFeedLoader` — the bundled-JSON-catalog loader pattern (explicit parse, no reflection defaults, cached) (`.../feed/LuaToolchainFeedLoader.kt:14`).

### Prior Art in This Repo

Searched `src/main` for `AdditionalLibraryRootsProvider`, `SyntheticLibrary`, download/verify, and JSON-feed loading:

| Component | file:line | Relationship |
|-----------|-----------|--------------|
| `LuaRocksLibraryProvider` | `rocks/library/LuaRocksLibraryProvider.kt:14` | **Template — extended in spirit, not modified.** TARGET-08's new provider is a sibling `AdditionalLibraryRootsProvider` for a different root source (fetched definition trees vs. installed rocks). Not merged: rocks roots are user-installed, definition roots are Lunar-fetched from a catalog. |
| `PlatformLibraryProvider` / `PlatformLibraryIndex` | `project/PlatformLibraryProvider.kt:41,105` | **Reused, not duplicated.** `PlatformLibraryIndex.reload()` (`:134`) is called to refresh roots on enable-list change. The stdlib injection is untouched. |
| `LuaArtifactDownloader` | `toolchain/provision/LuaArtifactDownloader.kt:36` | **Reused as-is** for the download+verify+cache mechanic. |
| `LuaArchiveExtractor` | `toolchain/provision/LuaArchiveExtractor.kt:23` | **Reused as-is** for extraction. |
| `LuaToolchainFeedLoader` / `LuaFeedJsonParser` | `toolchain/provision/feed/LuaToolchainFeedLoader.kt:14` | **Pattern replicated** (a separate catalog loader; not reused directly — different schema). |
| `LuaProvisionException` | `toolchain/provision/LuaProvisionException.kt` | **Reused** as the fetch-error type. |

No existing component consumes community LuaCATS definition libraries — this is new. The design **reuses** the download/extract/reload infrastructure and **mirrors** the feed-loader + rocks-provider patterns; it does not duplicate them.

### Target State

Five new classes in a new package `net.internetisalie.lunar.definitions`:

1. `LuaDefinitionCatalog` / `LuaDefinitionEntry` — typed catalog model (data classes).
2. `LuaDefinitionCatalogLoader` — loads + validates the bundled catalog JSON.
3. `LuaDefinitionLibraryFetcher` — resolves an entry's on-disk cache, fetching (download+extract) on demand off-EDT.
4. `LuaDefinitionLibraryProvider` — `AdditionalLibraryRootsProvider` exposing enabled+cached trees as `SyntheticLibrary` roots.
5. `LuaDefinitionLibrariesConfigurable` (+ a small settings panel) — the enable/attribution UI.

Plus: two `List<String>` fields on `LuaProjectSettings.State`, a `setEnabledDefinitionLibrariesAndRefresh(...)` method, and one bundled JSON resource. Data flow: **enable (UI) → persist id in `lunar.xml` → fetch off-EDT → extract to cache → `reload()` roots → provider contributes root → indexer + LuaCATS parser index `@meta` defs → completion/resolution live.**

## 2. Core Components

### 2.1 `net.internetisalie.lunar.definitions.LuaDefinitionCatalog` / `LuaDefinitionEntry`

- **Responsibility**: typed, immutable model of the bundled catalog.
- **Threading**: pure JVM data; thread-safe.
- **Collaborators**: produced by `LuaDefinitionCatalogLoader`.
- **Key API**:
  ```kotlin
  data class LuaDefinitionCatalog(val catalogVersion: Int, val libraries: List<LuaDefinitionEntry>) {
      fun entry(id: String): LuaDefinitionEntry? = libraries.firstOrNull { it.id == id }
  }

  data class LuaDefinitionEntry(
      val id: String,           // stable key, e.g. "love2d"
      val displayName: String,  // e.g. "LÖVE (love2d)"
      val version: String,      // e.g. "11.4"
      val urls: List<String>,   // ordered mirror list to a .tar.gz/.zip
      val sha256: String,
      val size: Long,
      val rootPrefix: String?,  // top-level archive dir to strip, or null
      val license: String,      // SPDX id, e.g. "MIT"
      val attributionUrl: String,
  )
  ```

### 2.2 `net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader`

- **Responsibility**: parse + validate the bundled catalog JSON once, cache it.
- **Threading**: pure JVM (no PSI/VFS/EDT). Safe on any thread.
- **Collaborators**: `com.google.gson.JsonParser` (already used by `LuaToolchainFeedLoader`); throws `LuaProvisionException` on corruption.
- **Key API**:
  ```kotlin
  object LuaDefinitionCatalogLoader {
      const val RESOURCE = "/definitions/lunar-definitions-catalog.json"
      fun load(): LuaDefinitionCatalog                 // parse-once + cache (§3.1)
      fun parse(root: com.google.gson.JsonObject): LuaDefinitionCatalog  // testable
  }
  ```

### 2.3 `net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher`

- **Responsibility**: return the on-disk cache dir for an entry, fetching (download + extract) it when absent.
- **Threading**: **background only** (blocking I/O). Called from a `Task.Backgroundable`. Touches no PSI; no read/write action needed.
- **Collaborators**: `LuaArtifactDownloader` (injected, default `LuaArtifactDownloader()`), `LuaArchiveExtractor`, `PathManager.getSystemPath()`.
- **Key API**:
  ```kotlin
  class LuaDefinitionLibraryFetcher(
      private val downloader: LuaArtifactDownloader = LuaArtifactDownloader(),
      private val cacheRoot: Path = defaultCacheRoot(),   // <system>/lunar/definitions
  ) {
      fun cacheDir(entry: LuaDefinitionEntry): Path            // <cacheRoot>/<id>-<version>
      fun isCached(entry: LuaDefinitionEntry): Boolean         // dir exists & non-empty
      /** Returns the cache dir if present or successfully fetched; null on any failure. */
      fun ensureCached(entry: LuaDefinitionEntry, indicator: ProgressIndicator): Path?
      companion object { fun defaultCacheRoot(): Path }
  }
  ```
  The `downloader`/`cacheRoot` injection lets tests pass a spy downloader and a temp dir (TC 4, 8) — no network.

### 2.4 `net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider`

- **Responsibility**: expose each enabled + cached definition tree as a `SyntheticLibrary` source root.
- **Threading**: called by the platform (read context); reads settings + VFS only. Holds no `Project`/`VirtualFile` field (roots live only inside the per-call `SyntheticLibrary` value object, exactly like `LuaRocksLibraryProvider`).
- **Collaborators**: `LuaProjectSettings`, `LuaDefinitionCatalogLoader`, `LuaDefinitionLibraryFetcher` (for `cacheDir`/`isCached` only — never `ensureCached` here), `VfsUtil.findFile`.
- **Key API**:
  ```kotlin
  class LuaDefinitionLibraryProvider : AdditionalLibraryRootsProvider() {
      override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary>
      override fun getRootsToWatch(project: Project): Collection<VirtualFile>
      class DefinitionLibrary(private val root: VirtualFile, private val label: String)
          : SyntheticLibrary(), ItemPresentation { /* getSourceRoots = listOf(root); equals/hashCode on root */ }
  }
  ```

### 2.5 `net.internetisalie.lunar.settings.LuaProjectSettings.State` (edit)

- **Responsibility**: persist the per-project enable list.
- **Threading**: settings service; mutated on the EDT in `apply()`.
- **Collaborators**: existing `LuaProjectSettings.setTargetAndNotify` pattern; `PlatformLibraryIndex.reload()`; `PsiManager.dropResolveCaches()`.
- **Key API** (added):
  ```kotlin
  // in class State:
  var enabledDefinitionLibraries: MutableList<String> = mutableListOf()

  // in class LuaProjectSettings:
  fun setEnabledDefinitionLibrariesAndRefresh(ids: List<String>)   // §3.3
  ```

### 2.6 `net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable`

- **Responsibility**: the enable/attribution settings page.
- **Threading**: EDT (Swing). Fetch of newly-enabled rows is dispatched to a `Task.Backgroundable` via `newProjectBackgroundTask(...)` (`util/LuaTaskUtil.kt`); no fetch runs on the EDT.
- **Collaborators**: `LuaProjectSettings`, `LuaDefinitionCatalogLoader`, `LuaDefinitionLibraryFetcher`, `newProjectBackgroundTask`, `ProgressManager`, `HyperlinkLabel`/`BrowserUtil`.
- **Key API**:
  ```kotlin
  class LuaDefinitionLibrariesConfigurable(private val project: Project) : Configurable {
      override fun getDisplayName(): String = "Definition Libraries"
      override fun createComponent(): JComponent
      override fun isModified(): Boolean
      override fun apply()   // §3.4
      override fun reset()
  }
  ```

## 3. Algorithms

### 3.1 Catalog load & validate

- **Input → Output**: bundled resource `/definitions/lunar-definitions-catalog.json` → `LuaDefinitionCatalog`.
- **Steps** (mirrors `LuaToolchainFeedLoader.load`/`parse`, `.../feed/LuaToolchainFeedLoader.kt:21,35`):
  1. If `cached != null` return it (double-checked `synchronized(this)`).
  2. Open the resource stream; missing → `LuaProvisionException("Corrupt definitions catalog: resource '<RESOURCE>' is missing.")`.
  3. `JsonParser.parseReader(...).asJsonObject`; JSON error → `LuaProvisionException("Corrupt definitions catalog: invalid JSON syntax.", cause)`.
  4. Read `catalogVersion` (int). For each element of `libraries` (array), require every field of `LuaDefinitionEntry` **explicitly** via `obj.get("field")` + null/type check; a missing required field (`id`, `displayName`, `version`, `urls`, `sha256`, `size`, `license`, `attributionUrl`) → `LuaProvisionException("Corrupt definitions catalog: entry '<id-or-index>' missing '<field>'.")`. `rootPrefix` is optional (null if absent).
  5. Cache and return.
- **Edge handling**: empty `libraries` array is valid (yields an empty catalog). Duplicate `id`s: first wins (`entry(id)` uses `firstOrNull`).

### 3.2 `ensureCached` (fetch-on-demand)

- **Input → Output**: `(LuaDefinitionEntry, ProgressIndicator)` → cache `Path` or `null`.
- **Steps**:
  1. `dir = cacheDir(entry)` = `cacheRoot.resolve("${entry.id}-${entry.version}")`.
  2. If `isCached(entry)` (dir exists and `Files.list(dir)` non-empty) → return `dir`.
  3. `try`:
     a. `archive = downloader.fetch(entry.urls, entry.sha256, entry.size, indicator)` — reuses the mirror/verify/cache contract in `LuaArtifactDownloader.fetch` (`.../LuaArtifactDownloader.kt:36`).
     b. Create `dir` (delete a stale partial dir first via `FileUtil.delete`).
     c. `LuaArchiveExtractor.extract(archive, dir, entry.rootPrefix, indicator)`.
     d. If after extraction `dir` is empty → delete `dir`, throw `LuaProvisionException("definition library '${entry.id}' extracted no files")`.
     e. return `dir`.
  4. `catch (ProcessCanceledException) { deleteQuietly(dir); throw }` (re-propagate cancellation).
  5. `catch (LuaProvisionException | IOException failure) { deleteQuietly(dir); notifyError(entry, failure); return null }`.
- **Rules / edge handling**: cancellation re-propagates; every other failure yields `null` (never a half-populated cache). `notifyError` posts an ERROR balloon on `notification.group.lunar.tools` (see `BalloonProvisionNotifier`, `.../LuaProvisionNotifier.kt:17`).

### 3.3 `setEnabledDefinitionLibrariesAndRefresh`

- **Input → Output**: `List<String>` (new enabled ids) → unit (persist + refresh).
- **Steps**:
  1. `state.enabledDefinitionLibraries = ids.toMutableList()`.
  2. `com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater { PlatformLibraryIndex.reload() }` — reuses the roots-change + stub-rebuild already used by TARGET-04 (`project/PlatformLibraryProvider.kt:134`, which runs its `WriteAction` on the EDT).
  3. In the same `invokeLater`, after `reload()`, `PsiManager.getInstance(project).dropResolveCaches()` (pattern from `settings/LuaApplicationSettingsPanel.kt:69`).
- **Rules**: `reload()` iterates all open projects (existing behaviour); acceptable here (the enable list is per-project but a global stub rebuild is the existing coarse mechanism). No new event topic is introduced.

### 3.4 Settings `apply()`

- **Input → Output**: UI checkbox state → persisted enable list + fetch of newly-enabled uncached rows.
- **Steps**:
  1. `newIds` = ids of checked rows.
  2. `toFetch` = catalog entries in `newIds` whose `!fetcher.isCached(entry)`.
  3. `LuaProjectSettings.getInstance(project).setEnabledDefinitionLibrariesAndRefresh(newIds)` (§3.3).
  4. If `toFetch` non-empty, run one `newProjectBackgroundTask("Fetching Lua definition libraries", project) { indicator -> toFetch.forEach { fetcher.ensureCached(it, indicator) }; invokeLater { PlatformLibraryIndex.reload() } }` and `ProgressManager.getInstance().run(task)`.
- **Rules**: apply never blocks the EDT on I/O; the initial `reload()` (step 3) registers already-cached rows immediately, the post-fetch `reload()` registers newly-downloaded ones. A row whose fetch fails simply stays uncached → contributes no root (§2.4).

### 3.5 Provider root resolution

- **Input → Output**: `Project` → `Collection<SyntheticLibrary>`.
- **Steps**:
  1. `ids = LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries`. Empty → return `emptyList()`.
  2. `catalog = LuaDefinitionCatalogLoader.load()`.
  3. For each `id` in `ids`: `entry = catalog.entry(id) ?: continue`; if `!fetcher.isCached(entry)` continue; `vf = VfsUtil.findFile(fetcher.cacheDir(entry), true) ?: continue`; add `DefinitionLibrary(vf, entry.displayName)`.
  4. Return the list.
- **Edge handling**: unknown id skipped; uncached id skipped; missing VFS skipped — all silently (no error; matches `LuaRocksLibraryProvider`'s `mapNotNull` behaviour). `getRootsToWatch` returns the same directories.

## 4. External Data & Parsing

### 4.1 Bundled catalog JSON `/definitions/lunar-definitions-catalog.json`

- **Format** (sample, v1 curated set — pins are placeholders resolved by DR-01/DR-02):
  ```json
  {
    "catalogVersion": 1,
    "libraries": [
      {
        "id": "love2d",
        "displayName": "LÖVE (love2d)",
        "version": "11.4",
        "urls": ["https://github.com/LuaCATS/love2d/archive/refs/tags/v11.4.tar.gz"],
        "sha256": "<sha256>",
        "size": 123456,
        "rootPrefix": "love2d-11.4",
        "license": "MIT",
        "attributionUrl": "https://github.com/LuaCATS/love2d"
      },
      {
        "id": "busted", "displayName": "busted", "version": "2.2",
        "urls": ["https://github.com/LuaCATS/busted/archive/refs/tags/v2.2.tar.gz"],
        "sha256": "<sha256>", "size": 34567, "rootPrefix": "busted-2.2",
        "license": "MIT", "attributionUrl": "https://github.com/LuaCATS/busted"
      }
    ]
  }
  ```
- **Parse strategy**: Gson `JsonParser` → manual field extraction per §3.1 (no reflection defaults — identical discipline to `LuaFeedJsonParser`). One entry per object in `libraries`.
- **Maps to**: `LuaDefinitionCatalog` / `LuaDefinitionEntry` (§2.1).
- **Failure handling**: any missing required field, non-object element, or invalid JSON → `LuaProvisionException`. The loader is the only consumer; the provider treats a load failure defensively by catching it and returning no roots (never crashes indexing).

### 4.2 Fetched archive contents

- **Format**: a `.tar.gz` (or `.zip`) whose (post-`rootPrefix`-strip) tree is a set of `.lua` files carrying `---@meta` LuaCATS annotations (the LuaCATS org repo layout). No parsing is done by TARGET-08 — the platform indexer + the existing LuaCATS lexer (`luacats/lang/lexer/luacats.flex:105`, `@meta` handling) parse them once the tree is a registered source root.
- **Parse strategy**: none in this feature — extraction only (`LuaArchiveExtractor`).
- **Failure handling**: unsupported archive format → `LuaArchiveExtractor` throws `LuaProvisionException` → §3.2 step 5 returns null + balloon.

## 5. Data Flow

### Example 1: Enable love2d (online, first time)

Settings UI → check `love2d` → `apply()` (§3.4): persist `["love2d"]` + `reload()` (no root yet, uncached) → background `ensureCached(love2d)` (§3.2): download `v11.4.tar.gz`, verify sha256+size, extract to `<system>/lunar/definitions/love2d-11.4/` → post-fetch `reload()` → provider (§3.5) now finds the cached dir → `DefinitionLibrary` root registered → indexer + LuaCATS parser index the `@meta` defs → `love.graphics.` completes in a `.lua` file.

### Example 2: Reopen project with love2d already enabled + cached

Project opens → platform calls `LuaDefinitionLibraryProvider.getAdditionalProjectLibraries` → §3.5 finds `love2d` enabled + cache dir present → registers the root immediately. No network, no fetch.

### Example 3: Enable openresty offline

`apply()` persists `["openresty"]`; background `ensureCached` (§3.2) → `downloader.fetch` throws (no network) → cache dir deleted, `null` returned, ERROR balloon on `notification.group.lunar.tools`. Provider (§3.5) skips `openresty` (uncached) → no root. The id stays in `lunar.xml`; a later online `apply()` retries.

## 6. Edge Cases

- **Enable list contains an id not in the catalog** → skipped in §3.5, ignored in §3.1. No error.
- **Cache dir exists but empty** (interrupted extract) → `isCached` false → re-fetch (§3.2 step 2).
- **Two projects enable the same library** → shared cache dir; second is a §3.2 step 2 hit.
- **`rootPrefix` null** → `LuaArchiveExtractor` extracts without stripping (existing `if (rootPrefix != null)` guard, `.../LuaArchiveExtractor.kt:24`).
- **Catalog JSON corrupt** → provider catches the `LuaProvisionException` and contributes no roots (indexing never breaks); the settings UI shows an error label instead of the table.
- **User disables a library** → `apply()` removes the id; `reload()` drops its root; the cache dir is left on disk (re-enabling is instant). Cache eviction is not in v1 scope.

## 7. Integration Points

```xml
<!-- plugin.xml — under existing <extensions defaultExtensionNs="com.intellij"> -->

<!-- Definition-library roots (TARGET-08); sibling to the two existing providers at :516-518 -->
<additionalLibraryRootsProvider
        implementation="net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider"/>

<!-- Settings page nested under the existing "Lua Project" configurable (:570-575) -->
<projectConfigurable
        parentId="net.internetisalie.lunar.toolchain.ui.LuaProjectConfigurable"
        instance="net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable"
        id="net.internetisalie.lunar.definitions.ui.LuaDefinitionLibrariesConfigurable"
        displayName="Definition Libraries"
        nonDefaultProject="true"/>
```

- **Notifications**: reuse the existing `notification.group.lunar.tools` group (`plugin.xml:672`) for fetch-error balloons — no new group.
- **Settings storage**: the new `enabledDefinitionLibraries` field rides the existing `@State(name = "LuaProjectSettings", storages = [Storage("lunar.xml")])` (`settings/LuaProjectSettings.kt:12-18`) — no new storage.
- **Roots refresh**: reuse `PlatformLibraryIndex.reload()` (`project/PlatformLibraryProvider.kt:134`).
- **New bundled resource**: `src/main/resources/definitions/lunar-definitions-catalog.json`.
- **Reused as-is** (no edit): `LuaArtifactDownloader`, `LuaArchiveExtractor`, `LuaProvisionException`, `newProjectBackgroundTask` (`util/LuaTaskUtil.kt`).

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| TARGET-08-01 | M | §2.1, §2.2, §3.1, §4.1 |
| TARGET-08-02 | M | §2.5, §7 (settings storage) |
| TARGET-08-03 | M | §2.3, §3.2, §4.2 |
| TARGET-08-04 | M | §2.4, §3.5, §7 (provider registration) |
| TARGET-08-05 | M | §2.5, §3.3 |
| TARGET-08-06 | S | §2.6, §3.4, §7 (projectConfigurable) |
| TARGET-08-07 | M | §3.2 (steps 4–5), §5 Example 3 |
| TARGET-08-08 | S | §2.6, §4.1 (license/attributionUrl) |

## 9. Alternatives Considered

- **Bundle the community defs in the plugin jar** — rejected: license/attribution burden per-file, jar bloat, staleness; the roadmap explicitly wants on-demand fetch. The catalog holds only URLs+hashes.
- **Fetch via the live GitHub API / git clone** — rejected for v1: needs auth/ratelimit handling and a git dependency; a pinned tarball URL + sha256 in the bundled catalog is simpler, verifiable, and offline-cacheable. Deferred as a graduation trigger (risks §2.1).
- **Reuse `LuaProvisionEngine` / `LuaToolProvisioner` for the fetch** — rejected: that pipeline is toolchain-binary-oriented (manifests, strategies, C-toolchain preflight, registry sink). TARGET-08 only needs download+extract, so it reuses the two leaf utilities (`LuaArtifactDownloader`, `LuaArchiveExtractor`) directly — lighter, per the roadmap's "lighter fetcher" option.
- **A new settings-changed event topic** — rejected: `PlatformLibraryIndex.reload()` + `dropResolveCaches()` is the established refresh mechanism (TARGET-04); no new topic needed.

## 10. Open Questions

_None — feature has cleared the planning bar. The two data-pinning tasks (curated v1 set membership; exact tarball URLs + sha256 + sizes) are tracked as de-risking tasks DR-01/DR-02 in [risks-and-gaps.md](risks-and-gaps.md); they are catalog-data population, not design decisions._
