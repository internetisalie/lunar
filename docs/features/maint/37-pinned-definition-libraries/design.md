---
id: "MAINT-37-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-37"
folders:
  - "[[features/maint/37-pinned-definition-libraries/requirements|requirements]]"
---

# Technical Design: MAINT-37 — Corpus Sweeps Run With Pinned Definition Libraries

All Kotlin in this feature is **test code** under `src/test/kotlin/net/internetisalie/lunar/corpus/`.
Nothing under `src/main/` changes, and there is **no `plugin.xml` change** (§7).

## 1. Architecture Overview

### Current State

`LuaCorpusSweepTest.sweepAndRatchet` (`LuaCorpusSweepTest.kt:84-103`) loads a `CorpusEntry`, pins
the language level, runs `CorpusSweep.run` and ratchets against
`src/test/resources/corpus/<name>.baseline`. No definition library is registered, so busted's
`describe`/`it` and wxLua's `wx`/`wxstc`/`wxaui` are reported undeclared by the thousand
(requirements → Overview). The per-symbol breakdown is capped at ten per inspection
(`CorpusSweep.SYMBOLS_PER_INSPECTION`, `CorpusSweep.kt:29`), so a committed baseline cannot say
where a count went once more than ten symbols move.

### Prior Art in This Repo

| Component | file:line | Relation |
| :-- | :-- | :-- |
| `CorpusEntry`, `CorpusManifest.parseEntry` | `corpus/CorpusManifest.kt:14-26`, `:77-100` | **Extended** with `definitionLibraries`. |
| `CorpusMetrics`, `CorpusBaseline.render/parse` | `corpus/CorpusMetrics.kt:10-76`, `:130-220` | **Extended** with `libraries` (rendered) and `allSymbolHits` (not rendered). |
| `CorpusGuards.assertIdentity` | `corpus/CorpusGuards.kt:58-82` | **Extended** with the library identity check. |
| `CorpusSweep.run`, `HitSink.symbols` | `corpus/CorpusSweep.kt:75-113`, `:255-258` | **Extended and decomposed**: the `CorpusMetrics(...)` construction moves into a new private `metricsOf` (§2.5), which fills `allSymbolHits`; `run` adds `libraries`. The uncapped map already exists as `sink.symbols`. |
| `CorpusSweep.applyModuleRoot` | `corpus/CorpusSweep.kt:139-147` | **Kept unchanged**; its write is now undone per member by `CorpusSourcePath.restore` (§2.13, MAINT-37-09). |
| `LuaCorpusSweepTest` | `corpus/LuaCorpusSweepTest.kt` | **Extended**: provisioning, release, attribution mode. |
| `LuaDefinitionLibraryEnabler` | `definitions/LuaDefinitionLibraryEnabler.kt:20-108`; used by `definitions/ui/LuaDefinitionLibrariesConfigurable.kt:30` | **Reused unchanged — it is the provisioning path.** The settings page is a shell over it, so `missingEntries` + `fetchAll` + `apply` is literally what a user's tick does (§3.3). The sweep constructs it with a local-archive fetcher (the class's own constructor seam, as `LuaDefinitionLibraryEnablerTest.kt:47-48` does). |
| `LuaSettingsChangeListener` | `project/LuaSettingsChangeListener.kt:22-49` | **Reused, not instantiated by this feature**: the subscriber that turns the enabler's broadcast into `PlatformLibraryIndex.reload()` + a daemon restart. The `postStartupActivity` `LuaTargetSyncStartup` (`toolchain/resolve/LuaTargetSyncStartup.kt:11`, `plugin.xml:570-571`) creates it for the light test project too (DR-09b, DR-10); the sweep asserts it at `setUp` (§3.13). |
| `TypeElevenGenerationSignalTest.testTheProductionEnablePathTicksTheGenerationTracker` | `type/TypeElevenGenerationSignalTest.kt:81-97` | **Pattern reused**: drives `LuaDefinitionLibraryEnabler.apply` in a light fixture with the trees already on disk, then drains the queue, and passes in the routine suite. Its explicit `LuaSettingsChangeListener.getInstance` (`:85`) is not copied (DR-09b). |
| `LuaDefinitionLibraryFetcher` + `LuaDefinitionArchiveSource` | `definitions/LuaDefinitionLibraryFetcher.kt:23-28`, `:42-96` | **Reused unchanged.** `LuaDefinitionArchiveSource` is a `fun interface` returning a local `Path` (`:23-28`); `LuaDefinitionLibraryFetcherTest.kt:130-133` hands it a local file (deliberately not a valid archive, to test failure). That a valid local `.tar.gz` built with `Compressor.Tar` extracts through the same chain is DR-02, executed. |
| `PlatformLibraryProvider.getExternalLibraries` (EP `additionalLibraryRootsProvider`, `plugin.xml:537`) | `project/PlatformLibraryProvider.kt:54-73` | **Consumed, unchanged**: the mechanism by which a leaked `sourcePath` becomes an indexed library (BUG-496, MAINT-37-09); TC 35 executes it. |
| `LuaDefinitionLibraryProvider` (EP `additionalLibraryRootsProvider`, `plugin.xml:541`) | `definitions/LuaDefinitionLibraryProvider.kt:29-56` | **Reused unchanged**; registration is asserted through it. |
| `LuaDefinitionCatalog.withDependencies` | `definitions/LuaDefinitionCatalog.kt:28-39` | **Reused**; it drops unknown ids, so this design checks them first. |
| `TypeElevenDefinitionLibraryTestCase` | `src/test/kotlin/net/internetisalie/lunar/type/TypeElevenDefinitionLibraryTestCase.kt:47-145` | **Pattern reused, class not extended.** It established the production-path idiom (real cache root, real catalog id, real settings, EP-registered provider, delete in `tearDown`). It is an abstract `BasePlatformTestCase`; `LuaCorpusSweepTest` already extends `BasePlatformTestCase` and must not inherit TYPE-11 helpers, so the idiom is restated in `CorpusDefinitionLibraries`. |
| `LibraryRootTestCase` | `definitions/LibraryRootTestCase.kt:33-117` | **Rejected for the sweep**: it registers an anonymous provider, which proves a mechanism no user hits. |
| `fetch-torture.py` `sha256_of` + strict-digest refusal | `tooling/corpus/fetch-torture.py:44-49`, `:68-76` | **Pattern copied** into `definition_pins.py`. Not imported: `fetch-torture.py`'s hyphenated name is not an importable module, and renaming it is outside scope. |
| `fetch-corpus.py` | `tooling/corpus/fetch-corpus.py:76-91` | **Extended**: fetches the pinned archives after the corpora. |
| CI `corpus` job (BUG-415) and the `changes` job's corpus filter | `.gitea/workflows/build-plugin.yml`: the `corpus:` job, `:295-397`; the `changes` job's filter, `:102` | **Extended and relied on.** It already runs this feature's gate (`fetch-corpus.py`, then `./gradlew test --rerun --no-daemon --stacktrace -PwithCorpus --tests '*Corpus*'`), whose filter is the invocation shape every record here uses (BUG-418). Extended in Phase 3 (§7 "CI"). The GitHub twin has no `corpus` or `changes` job (its header, `:16-21`). |

Searched and found nothing to extend for delta attribution: `git grep -n -i "attribut" src/test/kotlin/net/internetisalie/lunar/corpus tooling/corpus`
matches only per-inspection attribution by tool id (`CorpusSweep.kt:224-226`, `CorpusMetrics.kt:19,66`,
`tooling/corpus/README.md:53-54`) — attributing a highlight to the inspection that raised it, a
different job.

### Target State

```
corpus.json ──definitionLibraries──▶ fetch-corpus.py ──strict sha256/size──▶ test/corpus/.definitions/<id>-<version>.tar.gz
                                                                                    │ (rsync -L to the builder)
LuaCorpusSweepTest.setUp ─▶ CorpusDefinitionLibraries.requireSettingsSubscriber (startup-created subscriber exists)
LuaCorpusSweepTest ─▶ CorpusDefinitionLibraries.stage ─▶ LuaDefinitionLibraryEnabler (fetcher over the local archive)
                     │      .missingEntries + .fetchAll ─▶ LuaDefinitionLibraryFetcher.ensureCached (production extractor)
                     ├─▶ CorpusDefinitionLibraries.settle   (both arms: the window opens on a settled project — DR-10)
                     ├─▶ CorpusRefreshProbe.attach          (observation window opens)
                     ├─▶ CorpusDefinitionLibraries.enable ─▶ .apply(ids) ─▶ setEnabledDefinitionLibrariesAndNotify ─▶ (invokeLater, drained by settle)
                     │             LuaSettingsChangedListener.TOPIC ─▶ PlatformLibraryIndex.reload() (roots change + stub rebuild)
                     │             PsiManager.dropResolveCaches()
                     │      ─▶ EP LuaDefinitionLibraryProvider registers the trees (asserted)
                     │  (bare arm: no stage; CorpusDefinitionLibraries.refreshUnpinned ─▶ notifyDefinitionRootsChanged ─▶ same TOPIC + drop, drained)
                     ├─▶ CorpusRefreshProbe.observe         (window closes, before the sweep)
                     ├─▶ CorpusSweep.run ─▶ metricsOf(...) .copy(libraries = enabledPins)
                     ├─ normal:      CorpusGuards.assertRatchet (library identity) / record
                     ├─ attribution: build/corpus-attribution/<bare|pinned>/<name>.tally (+ CorpusRefreshProbe lines) ─▶ attribute_delta.py
                     │               before arm (pinned mode, starting commit) ─▶ reconcile_baseline.py --offset (o_old)
                     │               after the full-shape record: reconcile_baseline.py (committed vs before→pinned; start effect; o_new)
                     └─ tearDown:    CorpusSourcePath.restore ─▶ CorpusDefinitionLibraries.release (apply(emptyList()))
```

## 2. Core Components

### 2.1 `net.internetisalie.lunar.corpus.CorpusEntry` (extend)
- **Responsibility**: carries the manifest's pin list.
- **Threading**: pure data.
- **Key API**: append one constructor parameter, defaulted so the one construction site
  (`CorpusManifest.parseEntry`) is the only one that changes:
  ```kotlin
  data class CorpusEntry(
      val name: String, val commit: String, val roots: List<String>,
      val luaLevel: LuaLanguageLevel, val moduleRoot: String?,
      val definitionLibraries: List<String> = emptyList(),
  )
  ```
  `CorpusManifest.parseEntry` sets it per §3.1.

### 2.2 `CorpusMetrics` / `CorpusBaseline` (extend)
- **Responsibility**: carry and serialise the pins; carry the uncapped symbol map for attribution.
- **Key API**:
  ```kotlin
  data class CorpusMetrics(/* existing fields */,
      val libraries: Map<String, String> = emptyMap(),     // id -> catalog version; rendered
      val allSymbolHits: Map<String, Int> = emptyMap(),    // "<toolId>.<symbol>" -> count; NOT rendered
  )
  object CorpusBaseline { const val LIBRARY_PREFIX = "library." /* render + parse per §4.3 */ }
  ```
- **`render`**: one line after `appendLine("unresolvedRequires=…")`, shaped like the existing
  `crashes` line (`CorpusMetrics.kt:150`):
  `metrics.libraries.toSortedMap().forEach { (id, version) -> appendLine("$LIBRARY_PREFIX$id=$version") }`.
- **`parse`** (`CorpusMetrics.kt:162-220`) is already over the 30-logic-line tripwire, so the
  library field is added **by decomposing it**, not by growing it. New private helpers in
  `CorpusBaseline`, each lifting an existing block verbatim except where noted:
  ```kotlin
  private fun rowsOf(text: String): List<Pair<String, String>>                 // :163-168
  private fun scalarsOf(rows: List<Pair<String, String>>): Map<String, String>  // :169-177, plus
      // .filterNot { it.first.startsWith(LIBRARY_PREFIX) }
  private fun prefixedCounts(rows: List<Pair<String, String>>, prefix: String): Map<String, Int>
      // rows.filter { it.first.startsWith(prefix) }.associate { it.first.removePrefix(prefix) to it.second.toInt() }
      // replaces the inspectionHits (:185-188), symbolHits (:189-192) and crashes (:215-218) blocks
  private fun parseBallast(rows: List<Pair<String, String>>): Map<String, BallastGroup>  // :195-208's fold
  private fun parseLibraries(rows: List<Pair<String, String>>): Map<String, String>
      // rows.filter { it.first.startsWith(LIBRARY_PREFIX) }.associate { it.first.removePrefix(LIBRARY_PREFIX) to it.second }.toSortedMap()
  ```
  `parse` becomes `val rows = rowsOf(text)`, `val scalars = scalarsOf(rows)`, and the
  `CorpusMetrics(...)` call with one argument per field (`libraries = parseLibraries(rows)` added),
  which is under the tripwire. `BaselineRatchetTest`'s existing render/parse round-trip covers the
  lift; TC 14/15 cover the new field.

### 2.3 `CorpusGuards.assertIdentity` (extend)
- Add, directly after the `commit` assertion:
  ```kotlin
  assertEquals(
      "Definition-library pins changed (corpus.json definitionLibraries or a catalog version moved); " +
          "the inspection floors are no longer comparable. Re-record with -PrecordCorpusBaseline.",
      baseline.libraries, observed.libraries,
  )
  ```
  Map equality is by entries, so a `TreeMap` from `parse` equals a `LinkedHashMap` from `run`.

### 2.4 `net.internetisalie.lunar.corpus.CorpusDefinitionLibraries` (new `object`, file `CorpusDefinitionLibraries.kt`)
- **Responsibility**: resolve, stage, enable, report and release a member's pinned libraries, and
  give a bare attribution arm the same settings broadcast without a library (§3.12). Staging (fetch
  and extraction) and enabling (`apply`) are separate public steps because the refresh observation
  window (§2.6a) must open between them, after a `settle` (§3.3, DR-10). `observeRefresh` is the one
  place that orders settle → attach → refresh → observe, used by the sweep and by TC 36/37 alike;
  `provision` composes `stage` and `enable` for callers that do not observe.
- **Threading**: called on the test's EDT (`BasePlatformTestCase` default). Extraction is blocking
  file I/O on that thread, which is acceptable in test code and is what
  `TypeElevenDefinitionLibraryTestCase.seedDefinitionLibrary` already does. The enabler's refresh is
  marshalled with `invokeLater` (`LuaProjectSettings.kt:198-204`) and its subscriber's
  `PlatformLibraryIndex.reload()` must run on the EDT with no read lock (`PlatformLibraryProvider.kt:144`);
  `settle` drains it on this EDT, outside any read action. `refreshRoots` runs its roots change inside
  `runWriteAction`.
- **Collaborators**: `LuaDefinitionCatalogLoader.load()` (`LuaDefinitionCatalogLoader.kt:29`),
  `LuaDefinitionCatalog.entry`/`withDependencies`,
  `LuaDefinitionLibraryEnabler(project, fetcher)` and its `missingEntries`, `fetchAll`, `apply`
  (`LuaDefinitionLibraryEnabler.kt:20-26`, `:61-98`), `LuaDefinitionLibraryFetcher(cacheRoot, source)`,
  `.cacheDir`, `LuaDefinitionLibraryFetcher.defaultCacheRoot()`,
  `project.getServiceIfCreated(LuaSettingsChangeListener::class.java)` (§3.13),
  `CorpusRefreshProbe.attach`/`observe` (§2.6a) with a `com.intellij.openapi.Disposable` parent,
  `LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries` (`LuaProjectSettings.kt:92`),
  `LuaProjectSettings.notifyDefinitionRootsChanged()` (`LuaProjectSettings.kt:199-204`, public),
  `AdditionalLibraryRootsProvider.EP_NAME`,
  `ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)`
  (the non-deprecated overload, as `PlatformLibraryProvider.kt:149-152` calls it),
  `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()` (chosen over `UIUtil.dispatchAllInvocationEvents`
  for the reason `LuaDefinitionEnableListTest.kt:65-69` records),
  `IndexingTestUtil.waitUntilIndexesAreReady(project)`, `VfsUtil.markDirtyAndRefresh`, `VfsUtil.findFile`,
  `com.intellij.openapi.progress.EmptyProgressIndicator`.
- **Key API**:
  ```kotlin
  object CorpusDefinitionLibraries {
      const val ARCHIVE_DIR = "test/corpus/.definitions"
      fun resolve(entry: CorpusEntry): List<LuaDefinitionEntry>                     // §3.2
      fun archiveFile(repoRoot: File, library: LuaDefinitionEntry): File            // "$ARCHIVE_DIR/${id}-${version}.tar.gz"
      fun requireSettingsSubscriber(project: Project)                              // §3.13
      fun stage(project: Project, entry: CorpusEntry, repoRoot: File): List<Path>  // §3.3 "Stage"; returns cache dirs
      fun enable(project: Project, entry: CorpusEntry, repoRoot: File, cacheDirs: List<Path>): List<Path> // §3.3 "Enable"
      fun observeRefresh(project: Project, parent: Disposable, refresh: () -> List<Path>): ObservedRefresh // §3.3 "Observe"
      fun provision(project: Project, entry: CorpusEntry, repoRoot: File): List<Path> // §3.3 "Provision"
      fun release(project: Project, cacheDirs: List<Path>)                         // §3.4
      fun enabledPins(project: Project): Map<String, String>                       // §3.9
      fun refreshRoots(project: Project)                                           // §3.4; also used by §3.10
      fun refreshUnpinned(project: Project)                                        // §3.12; the bare arm's broadcast
      private fun localEnabler(project: Project, repoRoot: File): LuaDefinitionLibraryEnabler
      private fun fetchLocally(enabler: LuaDefinitionLibraryEnabler, libraries: List<LuaDefinitionEntry>, repoRoot: File): List<Path>
      private fun settle(project: Project)                                         // §3.3 "Settle"
      private fun assertRegistered(project: Project, cacheDirs: List<Path>)
  }
  data class ObservedRefresh(val cacheDirs: List<Path>, val observation: RefreshObservation)
  ```
  `ObservedRefresh` is a top-level class in `CorpusDefinitionLibraries.kt`. `enable` takes three
  parameters besides `Project`, the contract's cap; it needs `repoRoot` only to build the
  local-archive enabler, so `apply`'s background task can never reach the network. `observeRefresh`
  takes one besides `Project` and the `Disposable`.

### 2.5 `CorpusSweep.run` (extend by decomposition)
`run` (`CorpusSweep.kt:75-113`) is already at the 30-logic-line tripwire, so it is **not grown**.
The `CorpusMetrics(...)` construction (`:89-112`) moves verbatim into a new private helper, with
`allSymbolHits` added:
```kotlin
private fun metricsOf(context: SweepContext, swept: List<SweptFile>, sink: HitSink): CorpusMetrics
    // val checkoutDir = CorpusManifest.checkoutDir(context.repoRoot, context.entry.name)
    // return CorpusMetrics(commit = context.entry.commit, …every existing argument unchanged…,
    //     ballast = ballast(checkoutDir, context.entry), …,
    //     allSymbolHits = sink.symbols.toSortedMap())
```
`SweepContext` (`:70-73`), `SweptFile` (`:48`) and `HitSink` (`:255-258`) are already private to
`CorpusSweep`, so the helper needs no visibility change. `run`'s last statement becomes
```kotlin
return metricsOf(context, swept, sink).copy(libraries = CorpusDefinitionLibraries.enabledPins(fixture.project))
```
`run` keeps its parameter list `(fixture, entry, repoRoot)` and drops to well under the tripwire;
`metricsOf` holds the one constructor call (one argument per line) plus the `checkoutDir` line.
`libraries` is applied in `run`, not in `metricsOf`, because it needs `fixture.project` and
`metricsOf` already has three parameters.

### 2.6 `net.internetisalie.lunar.corpus.CorpusAttributionMode` and `CorpusAttribution` (new, file `CorpusAttribution.kt`)
- **Threading**: pure, plus one file write.
- **Key API**:
  ```kotlin
  enum class CorpusAttributionMode(val dirName: String) {
      BARE("bare"), PINNED("pinned");
      companion object {
          const val PROPERTY = "lunar.corpus.attribution"
          fun fromProperty(value: String?): CorpusAttributionMode?   // §3.8
          fun forRun(attribution: String?, record: String?): CorpusAttributionMode?   // §3.8
      }
  }
  object CorpusAttribution {
      const val ALL_SYMBOL_PREFIX = "allsymbol."
      fun tallyFile(repoRoot: File, mode: CorpusAttributionMode, name: String): File
          // File(repoRoot, "build/corpus-attribution/${mode.dirName}/$name.tally")
      fun checkArm(mode: CorpusAttributionMode, metrics: CorpusMetrics)             // below
      fun renderTally(metrics: CorpusMetrics, refresh: RefreshObservation): String   // §4.4
      fun write(tally: File, metrics: CorpusMetrics, refresh: RefreshObservation)    // mkdirs, writeText, echo to stdout
  }
  ```
  `checkArm` holds the tally's content checks, so a wrong tally is a red test rather than a hand
  read. For `PINNED` it returns at once. For `BARE`, in this order:
  `check(metrics.libraries.isEmpty()) { "the bare arm registered libraries ${metrics.libraries}: the enable list was not emptied" }`,
  then
  `check(metrics.allSymbolHits.isNotEmpty()) { "the bare arm recorded no symbol hits: allSymbolHits is not filled from the sweep's sink" }`.
  The second holds for every member's unpinned configuration, which is the bare arm's: every
  committed member baseline carries `symbol.` lines (`/usr/bin/grep -c '^symbol\.'` printed
  `luacheck` 36, `luarocks` 33, `penlight` 28, `zerobrane` 39 on `0f511b21`), and `symbolHits` is the
  capped subset of the same `sink.symbols` that `allSymbolHits` carries uncapped
  (`CorpusSweep.kt:100`, `:154`; §2.5). The pinned arm is not checked: a pin removes hits, and a
  pinned tally with no library is refused by the classifier's row f (§3.5). The refresh values are
  not checked here: an arm with a wrong refresh must still write its tally, because TC 38's mutants
  are proven by the classifier reading it (§3.5 rows c and d).
  `renderTally` = `CorpusBaseline.render(metrics)`, then
  `appendLine("settingsBroadcasts=${refresh.settingsBroadcasts}")`,
  `appendLine("rootsTicks=${refresh.rootsTicks}")`,
  `appendLine("physicalPsiChanges=${refresh.physicalPsiChanges}")`, then
  `metrics.allSymbolHits.toSortedMap().forEach { (key, count) -> appendLine("$ALL_SYMBOL_PREFIX$key=$count") }`.
  `write` echoes `"[corpus-attribution] wrote ${tally.path}:\n$text"`, mirroring
  `LuaCorpusSweepTest.recordBaseline`'s echo (`:140-145`).

### 2.6a `net.internetisalie.lunar.corpus.CorpusRefreshProbe` and `RefreshObservation` (new, file `CorpusRefreshProbe.kt`)
- **Responsibility**: observe the **effects** of a definition-roots refresh, not only its publish,
  so a pair whose arms received different refreshes is refused (§3.5 rows c and d) and TC 36–38 can
  see a missing publish, a missing reload or a missing resolve-cache drop.
- **Window**: from `attach` to `observe`. The only caller is `CorpusDefinitionLibraries.observeRefresh`
  (§3.3 "Observe"), which attaches after `settle` has returned, so a dumb-mode session opened before
  the refresh (by fetch and extraction in the pinned arm) is not counted inside the window (DR-10).
- **Threading**: subscriptions are message-bus callbacks on the publishing thread (the EDT, inside
  `settle`'s drain or a dumb-mode transition); `observe` runs on the test EDT. Plain `Int` fields; no
  synchronisation needed.
- **Memory**: holds no `Project`. Its subscriptions live on one connection disposed with the
  `parent` `Disposable` (the test's `testRootDisposable`); `observe` takes the project as an argument.
- **Key API**:
  ```kotlin
  data class RefreshObservation(val settingsBroadcasts: Int, val rootsTicks: Long, val physicalPsiChanges: Int)

  class CorpusRefreshProbe private constructor(private val rootsAtAttach: Long) {
      private var settingsBroadcasts = 0
      private var physicalPsiChanges = 0

      fun observe(project: Project): RefreshObservation =
          RefreshObservation(
              settingsBroadcasts,
              ProjectRootModificationTracker.getInstance(project).modificationCount - rootsAtAttach,
              physicalPsiChanges,
          )

      companion object {
          fun attach(project: Project, parent: Disposable): CorpusRefreshProbe {
              val probe = CorpusRefreshProbe(ProjectRootModificationTracker.getInstance(project).modificationCount)
              val connection = project.messageBus.connect(parent)
              connection.subscribe(LuaSettingsChangedListener.TOPIC, object : LuaSettingsChangedListener {
                  override fun onSettingsChanged() { probe.settingsBroadcasts++ }
              })
              connection.subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener {
                  override fun beforePsiChanged(isPhysical: Boolean) { if (isPhysical) probe.physicalPsiChanges++ }
              })
              return probe
          }
      }
  }
  ```
  Imports: `com.intellij.openapi.Disposable`, `com.intellij.openapi.project.Project`,
  `com.intellij.openapi.roots.ProjectRootModificationTracker`, `com.intellij.psi.impl.AnyPsiChangeListener`,
  `com.intellij.psi.impl.PsiManagerImpl`, `net.internetisalie.lunar.settings.LuaSettingsChangedListener`.
- **Fields** (platform sources and their presence in the 2026.1.3 test platform: risks-and-gaps →
  Evidence E-1):
  - `settingsBroadcasts` — publishes of `LuaSettingsChangedListener.TOPIC`
    (`settings/LuaSettingsChangedEvent.kt:29`). Catches an arm that missed or doubled the publish.
  - `rootsTicks` — the change in `ProjectRootModificationTracker.getInstance(project).modificationCount`.
    The only `makeRootsChange` on the refresh path is `PlatformLibraryIndex.reload()`
    (`PlatformLibraryProvider.kt:145-155`), run by the `LuaSettingsChangeListener` subscriber, so
    `rootsTicks` is the evidence that the **reload ran**. Prior art for reading it across the enable
    path: `TypeElevenGenerationSignalTest.kt:86-97`.
  - `physicalPsiChanges` — `beforePsiChanged(true)` calls on `PsiManagerImpl.ANY_PSI_CHANGE_TOPIC`,
    published by the reload's roots change, by `PsiManagerImpl.dropResolveCaches()`, and by each
    dumb-mode entry and exit (composition: DR-07b).
- **Values.** Both arms read `RefreshObservation(1, 1, 4)` in the light fixture (DR-10). No arithmetic
  on the tuple isolates the drop, because the dumb-mode events depend on how indexing runs, so tests
  assert the **whole tuple**. In the sweep the libraries are the real upstream trees, so the
  classifier compares the two arms for equality (§3.5 row d), and TC 38 measures the sweep's values.

### 2.7 `LuaCorpusSweepTest` (extend)
- New fields `private var provisioned: List<Path> = emptyList()` and
  `private var savedSourcePath: String = ""`.
- `setUp()`: directly after `super.setUp()`, `CorpusDefinitionLibraries.requireSettingsSubscriber(project)`
  (§3.13). Appended after the existing
  `allowRootAccess`:
  `VfsRootAccess.allowRootAccess(testRootDisposable, LuaDefinitionLibraryFetcher.defaultCacheRoot().toString())`,
  `LuaProjectSettings.getInstance(project).state.enabledDefinitionLibraries = mutableListOf()`
  (the light project is shared across classes — `LuaDefinitionLibraryProviderTest.kt:27-29`), and
  `savedSourcePath = CorpusSourcePath.snapshot(project)` (MAINT-37-09).
- New `override fun tearDown()`, in this order (§3.10 explains it):
  ```kotlin
  try {
      val moduleRootRestored = CorpusSourcePath.restore(project, savedSourcePath)
      CorpusDefinitionLibraries.release(project, provisioned)
      if (moduleRootRestored && provisioned.isEmpty()) CorpusDefinitionLibraries.refreshRoots(project)
  } finally {
      super.tearDown()
  }
  ```
- `sweepAndRatchet(name)` after setting the language level:
  1. `val attribution = attributionMode()`; then
     `if (attribution != null) CorpusAttribution.tallyFile(repoRoot, attribution, name).delete()`, so
     an arm that fails before step 4 leaves no tally behind rather than an older run's.
  2. `val refresh = refreshFor(attribution, entry, repoRoot)` — a new
     `private fun refreshFor(attribution: CorpusAttributionMode?, entry: CorpusEntry, repoRoot: File): RefreshObservation`,
     in this order:
     1. `val bare = attribution == CorpusAttributionMode.BARE`.
     2. `val staged = if (bare) emptyList() else CorpusDefinitionLibraries.stage(project, entry, repoRoot)`;
        then `provisioned = staged`, before the window opens — as §2.8 does for TC 36 — so `tearDown`
        releases the trees even if `enable` throws inside the window.
     3. `val observed = CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) { if (bare) { CorpusDefinitionLibraries.refreshUnpinned(project); emptyList() } else CorpusDefinitionLibraries.enable(project, entry, repoRoot, staged) }`.
        The bare arm gets the pinned arm's broadcast without its library (§3.12). An unpinned member
        (`staged` empty) in the pinned or the routine mode gets no broadcast, and its observation is
        `(0, 0, 0)`.
     4. `provisioned = observed.cacheDirs` — equal to `staged` when `enable` returns normally (TC 36
        asserts `e.cacheDirs == staged`), and empty in the bare arm, so `tearDown` is unchanged there.
     5. `return observed.observation` — read after the refresh has been drained and indexing has
        settled, **before** the sweep, so the sweep's own PSI events are outside the window.
  3. `val observed = measure(name, entry, repoRoot)` — a new
     `private fun measure(name: String, entry: CorpusEntry, repoRoot: File): CorpusMetrics` holding
     the existing timed `CorpusSweep.run` + `report` lines (`LuaCorpusSweepTest.kt:92-95`) verbatim
     and returning `observed`. Extracted unconditionally, so `sweepAndRatchet` stays under the
     tripwire with the new steps.
  4. `if (attribution != null) { CorpusAttribution.checkArm(attribution, observed); CorpusAttribution.write(CorpusAttribution.tallyFile(repoRoot, attribution, name), observed, refresh); return }`.
     `checkArm` runs first, so an arm that fails it leaves no tally (step 1 deleted the old one).
  5. Existing record-or-ratchet, unchanged.

  The routine and record modes run the same steps and discard `refresh`, so every mode provisions
  through one path.
- New `private fun attributionMode(): CorpusAttributionMode?` =
  `CorpusAttributionMode.forRun(System.getProperty(CorpusAttributionMode.PROPERTY), System.getProperty("lunar.corpus.record"))`.
  The mutual-exclusion rule lives in `forRun` (§3.8), where TC 48 asserts it in the routine suite;
  `attributionMode` only reads the system properties it passes. `CorpusAttributionMode.forRun(` is the
  only `CorpusAttributionMode` call with an argument list in `LuaCorpusSweepTest` (implementation-plan → Phase 2's
  wiring grep).

### 2.8 `net.internetisalie.lunar.corpus.DefinitionPinProvisioningTest` (new)
- `BasePlatformTestCase`; **neither the class name nor any test method name contains `Corpus`**
  (§2.16, which names every method and states why), so every case runs in the routine suite and
  stays outside the `--tests '*Corpus*'` shape. Needs no fetched corpus.
- `setUp`: `CorpusDefinitionLibraries.requireSettingsSubscriber(project)` directly after
  `super.setUp()` (§3.13); allow root access to `defaultCacheRoot()` and to a temp repo root; empty
  the enable list.
- Field `private var provisioned: List<Path> = emptyList()`. Each case assigns it the directories it
  provisions as soon as they are returned, before any assertion: TC 8, TC 10, TC 12 and TC 13 from
  `provision(...)`, TC 36 from `staged` directly after `stage` returns (so the trees are released
  even if `enable` throws inside the window). TC 12 and TC 36, which call `release` themselves, set
  it back to `emptyList()` directly after that `release` returns. TC 9, TC 11 and TC 37 never assign
  it. So a case that fails between provisioning and its own release still has its trees removed.
- `tearDown`: `try { CorpusDefinitionLibraries.release(project, provisioned) } finally { super.tearDown() }`.
  `release` with an empty list and an empty enable list returns at once (§3.4 step 1).
- TC 36 and TC 37 observe through `CorpusDefinitionLibraries.observeRefresh(project, testRootDisposable) { … }`,
  the same function §2.7's `refreshFor` uses, so the window they assert is the sweep's window. The
  topic subscription inside the probe is the shape of `LuaDefinitionEnableListTest.kt:77-86`. Each
  observation is asserted as a whole `RefreshObservation` with `assertEquals`, whose failure message
  prints both tuples.
- Archive helper: `private fun writeArchive(target: File, files: Map<String, String>)` using
  `com.intellij.util.io.Compressor.Tar(target.toPath(), Compressor.Tar.Compression.GZIP).use { tar -> files.forEach { (path, text) -> tar.addFile(path, text.toByteArray()) } }`.
  Entry names use the **real** catalog `rootPrefix` of `busted`/`luassert`, read from
  `LuaDefinitionCatalogLoader.load().entry(id)`, so a catalog re-pin cannot break the fixture.
- Highlighting: `myFixture.enableInspections(LuaUndeclaredVariableInspection())` in `setUp`; per case
  `myFixture.configureByText("x_spec.lua", "describe(\"a\", function() it(\"b\", function() end) end)\n")`
  then `myFixture.doHighlighting().mapNotNull { it.description }`, asserting on the
  `Undeclared variable '<name>'` text the inspection registers
  (`LuaUndeclaredVariableInspection.kt:56-61`).
- Covers TC 8–13, TC 36 and TC 37 (TC 36 calls `stage`, then `observeRefresh` around `enable`, then
  `observeRefresh` around `release`; TC 37 calls `observeRefresh` around `refreshUnpinned`, with no
  `stage`).

### 2.9 `build.gradle.kts` (extend)
Inside the `test` task block, after the `recordCorpusBaseline` block (`build.gradle.kts:332-336`):
```kotlin
if (project.hasProperty("corpusAttribution")) {
    systemProperty("lunar.corpus.attribution", project.property("corpusAttribution").toString())
}
```

### 2.10 `tooling/corpus/definition_pins.py` (new, Python 3 stdlib)
```python
SCRIPT_DIR: Path         # Path(__file__).resolve().parent, as fetch-corpus.py:21
REPO_ROOT: Path          # SCRIPT_DIR.parent.parent, as fetch-corpus.py:22
MANIFEST: Path           # SCRIPT_DIR / "corpus.json", as fetch-corpus.py:23
CATALOG: Path            # REPO_ROOT / "src/main/resources/definitions/lunar-definitions-catalog.json"
class PinError(Exception): ...
def load_catalog(path: Path) -> dict[str, dict]                       # {entry["id"]: entry}
def resolve_libraries(manifest: dict, catalog: dict[str, dict]) -> list[dict]   # §3.2
def archive_name(lib: dict) -> str                                     # f"{lib['id']}-{lib['version']}.tar.gz"
def sha256_of(path: Path) -> str                                       # copy of fetch-torture.py:44-49
def fetch_library(lib: dict, dest_dir: Path) -> Path                   # §3.7
def library_globals(archive: Path, root_prefix: str) -> set[str]       # §3.6
def verify_archives(libraries: list[dict], dest_dir: Path) -> list[str]  # §3.14
def main(argv: list[str]) -> int                                      # §3.14; `--verify DIR`
```
The module ends with `if __name__ == "__main__": raise SystemExit(main(sys.argv[1:]))`, so CI's
fixture step can run it as a script; `fetch-corpus.py`'s import of it is unaffected.

### 2.11 `tooling/corpus/fetch-corpus.py` (extend)
Add `DEFINITIONS_ROOT = CORPUS_ROOT / ".definitions"`. At the end of `main()`, before the final log:
```python
try:
    libraries = resolve_libraries(manifest, load_catalog(CATALOG))
    DEFINITIONS_ROOT.mkdir(parents=True, exist_ok=True)
    for lib in libraries:
        fetch_library(lib, DEFINITIONS_ROOT)
except PinError as failure:
    die(str(failure))
```
The import is `from definition_pins import CATALOG, PinError, fetch_library, load_catalog, resolve_libraries`
(the script's directory is `sys.path[0]` when it runs). Add `definitionLibraries` to the manifest's
`$comment` field list.

### 2.12 `tooling/corpus/attribute_delta.py` (new) and tests
```python
STRUCTURAL_KEYS = frozenset({"parseErrors", "requires", "unresolvedRequires", "oracleDisagreements",
    "oracleFalseAccepts", "oracleTimeouts", "lexerRoundTripFailures", "unmergedTokens"})   # S in §3.5
REFRESH_KEYS = ("settingsBroadcasts", "rootsTicks", "physicalPsiChanges")             # §3.5 rows c, d; order matters
class NotComparable(Exception): ...
@dataclass(frozen=True)
class Row: key: str; bare: int; pinned: int; delta: int; cls: str; evidence: str
def tracked(key: str) -> bool        # key in STRUCTURAL_KEYS or key.startswith(("inspection.", "crash."))
def parse_tally(text: str) -> dict[str, str]                                         # §4.4
def attribute(bare: dict[str, str], pinned: dict[str, str], library_globals: set[str]) -> list[Row]  # §3.5
def exit_code(rows: list[Row]) -> int        # 1 if any(r.cls == "UNEXPLAINED" for r in rows) else 0
def render(corpus: str, pinned: dict[str, str], rows: list[Row]) -> str             # §4.5
def main(argv: list[str]) -> int                                                     # §3.5 step 6
```
Tests: `tooling/corpus/test_definition_pins.py`, `tooling/corpus/test_attribute_delta.py` and
`tooling/corpus/test_reconcile_baseline.py`
(stdlib `unittest`), run with `mkdir -p $HOME/.cache/claude-scratch/lunar/maint-37` and then
`TMPDIR=$HOME/.cache/claude-scratch/lunar/maint-37 python3 -m unittest discover -s tooling/corpus -p 'test_*.py'`
(a missing `TMPDIR` is skipped silently, implementation-plan → Gates).
Fixture archives are built in a `tempfile.TemporaryDirectory()` with `tarfile.open(path, "w:gz")`.
TC 32, TC 41, TC 44–46 and TC 47 capture stderr and stdout with `contextlib.redirect_stderr(io.StringIO())` /
`contextlib.redirect_stdout(io.StringIO())`.

### 2.13 `net.internetisalie.lunar.corpus.CorpusSourcePath` (new `object`, file `CorpusSourcePath.kt`)
- **Responsibility**: MAINT-37-09 — snapshot and restore the shared light project's `sourcePath`.
- **Threading**: test EDT; a plain settings-state field write, as `CorpusSweep.kt:146` already does.
- **Key API**:
  ```kotlin
  object CorpusSourcePath {
      fun snapshot(project: Project): String =
          LuaProjectSettings.getInstance(project).state.sourcePath
      /** Writes [saved] back; true when the value had changed, so the caller must refresh roots. */
      fun restore(project: Project, saved: String): Boolean
  }
  ```
  `restore`: `val state = LuaProjectSettings.getInstance(project).state`;
  `if (state.sourcePath == saved) return false`; `state.sourcePath = saved`; `return true`.
  (`LuaProjectSettings.State.sourcePath`, `LuaProjectSettings.kt:73`, defaults to
  `PathConfiguration.DEFAULT_SOURCE_PATH`.)

### 2.14 `net.internetisalie.lunar.corpus.ModuleRootIsolationTest` (new)
- `BasePlatformTestCase`; class and test method names free of `Corpus` (§2.16, which names
  every method), so every case runs in the routine suite and outside the `*Corpus*` shape (as §2.8).
- `setUp`: `saved = CorpusSourcePath.snapshot(project)`. `tearDown`:
  `try { CorpusSourcePath.restore(project, saved); CorpusDefinitionLibraries.refreshRoots(project) } finally { super.tearDown() }`
  — so a red TC 35 cannot leak its temp root into the next class.
- TC 35 fixture: `val dir = Files.createTempDirectory("lunar-module-root")`,
  `dir.resolve("leakprobe.lua").writeText("return {}\n")`,
  `VfsRootAccess.allowRootAccess(testRootDisposable, dir.toString())`,
  `VfsUtil.markDirtyAndRefresh(false, true, true, dir.toFile())` (the shape of
  `LuaDefinitionLibraryEnablerTest.kt:30-33, :56`).
- Covers TC 33, 34, 35.

### 2.15 `tooling/corpus/reconcile_baseline.py` (new, Python 3 stdlib)
```python
from attribute_delta import STRUCTURAL_KEYS, parse_tally, tracked
SHAPE_OFFSET_BOUND = 2                      # §3.11 "The bound"; BUG-418's one measured sample, checked per run
SHAPE_RESIDUAL_BOUND = 2 * SHAPE_OFFSET_BOUND
HIGHLIGHT_FAILURES = "inspection.highlightFailures"
LIBRARY_PREFIX = "library."
@dataclass(frozen=True)
class Residual: key: str; committed: int; attributed: int; residual: int; blocking: bool
@dataclass(frozen=True)
class StartRow: key: str; before: int; bare: int; effect: int; blocking: bool
def always_blocking(key: str) -> bool        # key in STRUCTURAL_KEYS or key.startswith("crash.") or key == HIGHLIGHT_FAILURES
def key_delta(before: dict[str, str], after: dict[str, str]) -> dict[str, int]                 # §3.11 step 1
def reconcile(committed: dict[str, int], attributed: dict[str, int],
              bound: int = SHAPE_RESIDUAL_BOUND) -> list[Residual]                             # §3.11 step 3
def start_effect(before: dict[str, str], bare: dict[str, str]) -> tuple[str, list[StartRow]]  # §3.11 step 4
def residual_exit(rows: Sequence[Residual | StartRow]) -> int   # 1 if any(r.blocking for r in rows) else 0
def render(corpus: str, rows: list[Residual], title: str = "shape residual") -> str           # §4.7
def render_start(corpus: str, title: str, rows: list[StartRow]) -> str                        # §4.7
def main(argv: list[str]) -> int                                                                # §3.11 step 5
```
Covers TC 39–46 (`tooling/corpus/test_reconcile_baseline.py`).

### 2.16 Kotlin test names and the `*Corpus*` filter
- **Rule.** No test class this feature creates, and no test method it adds to a test class other
  than `LuaCorpusSweepTest`, has `Corpus` in its name. The classes meant to run only in the sweeps'
  JVM are `LuaCorpusSweepTest` and `LuaTortureCorpusTest` (§6); of them, this feature touches only
  `LuaCorpusSweepTest`. Every other Kotlin test this feature adds belongs in the routine suite and
  outside the sweeps' shape, and lives in a top-level class in `BaselineRatchetTest.kt`,
  `DefinitionPinProvisioningTest.kt` or `ModuleRootIsolationTest.kt`.
- **Why.** Gradle matches `*Corpus*` against test method names as well as class names, both in the
  routine suite's exclusion (`build.gradle.kts:313-317`) and in `--tests '*Corpus*'`
  ([BUG-497](../../bug-fixes/497-test-methods-named-corpus-leave-the-routine-suite/bug-report.md),
  fixed in `a90adac1`). So a `…Corpus…` test silently leaves the routine suite and joins the sweeps'
  JVM in every record and gate, where a co-resident test moves counts (`build.gradle.kts:318-322`).
  The match is case-sensitive: `BaselineRatchetTest`, in the lowercase `…lunar.corpus` package, runs
  in the routine suite and not in the corpus shape (BUG-497 → Resolution). So lowercase `corpus`
  moves no test and is not restricted, and neither is the name of anything that is not a test (a
  helper class such as `CorpusDefinitionLibraries`, a private `fun`).
- **Enforcement.** `SweepNamingGuardTest` (`src/test/kotlin/net/internetisalie/lunar/SweepNamingGuardTest.kt`,
  BUG-497's fix) runs in every routine suite and fails it when a compiled class that holds tests,
  or a test method in one (`@Test`, or a public JUnit3 `test…`), outside the two sweep classes
  contains `Corpus` (`filteredNames`, `:72-83`; `isCollectable`, `:92-99`). It scans every compiled
  top-level test class (`compiledTestClasses`, `:101-107`), so a test file outside the three named
  above is covered too. It does not see two shapes: nested classes (`classNamesUnder` drops every
  name containing `$`, `:121`) and classes that declare no test of their own and only inherit them
  (`testMethodNames` reads `declaredMethods`, `:87`; an empty list is ignored, `:76`). [[BUG-499]]
  covers the guard's gap. For this feature the gap is closed by the Rule, which places every
  test it adds in a top-level class, and by the Names table below, which declares each one as a
  method of that class. Every
  Kotlin phase's exit criteria run the full routine suite (implementation-plan → Phase 0, 1, 2 and
  3b "Exit criteria"), so a top-level test class or declared test method that breaks the Rule fails
  that phase. The guard checks names, not that a test ran: that is implementation-plan → Gates
  "Test names".
- **Names.** Each Kotlin test case is exactly this method:

  | TC | Class (style) | Method |
  | :-- | :-- | :-- |
  | 1 | `BaselineRatchetTest` (JUnit4, `@Test fun`) | `manifestDefinitionLibrariesParseDistinctAndDefaultEmpty` |
  | 2 | `BaselineRatchetTest` | `unknownDefinitionLibraryIdNamesTheCatalog` |
  | 3 | `BaselineRatchetTest` | `pinnedLibraryResolvesWithItsDependencies` |
  | 14 | `BaselineRatchetTest` | `libraryPinsRenderSortedAfterUnresolvedRequiresAndRoundTrip` |
  | 15 | `BaselineRatchetTest` | `baselineWithoutLibraryLinesParsesToNoPins` |
  | 16 | `BaselineRatchetTest` | `changedLibraryPinsFailTheRatchet` |
  | 17 | `BaselineRatchetTest` | `equalEmptyLibraryPinsPassTheRatchet` |
  | 18 | `BaselineRatchetTest` | `attributionModeParsesFromProperty` |
  | 19 | `BaselineRatchetTest` | `symbolTallyAndRefreshLinesStayOutOfTheBaseline` |
  | 48 | `BaselineRatchetTest` | `attributionAndBaselineRecordAreMutuallyExclusive` |
  | 49 | `BaselineRatchetTest` | `bareArmCheckRejectsLibrariesAndAnEmptySymbolTally` |
  | 8 | `DefinitionPinProvisioningTest` (JUnit3, public `fun test…()`) | `testProvisionedLibrarySuppressesUndeclaredGlobals` |
  | 9 | `DefinitionPinProvisioningTest` | `testWithoutProvisionDescribeAndItAreUndeclared` |
  | 10 | `DefinitionPinProvisioningTest` | `testStaleCachedTreeIsReplacedFromTheArchive` |
  | 11 | `DefinitionPinProvisioningTest` | `testMissingArchiveNamesTheFetchScript` |
  | 12 | `DefinitionPinProvisioningTest` | `testReleaseEmptiesTheEnableListAndDeletesTrees` |
  | 13 | `DefinitionPinProvisioningTest` | `testEnabledPinsIncludeDependencies` |
  | 36 | `DefinitionPinProvisioningTest` | `testEnableAndReleaseWindowsEachObserveOneRefresh` |
  | 37 | `DefinitionPinProvisioningTest` | `testUnpinnedRefreshObservesTheEnableWindowTuple` |
  | 33 | `ModuleRootIsolationTest` (JUnit3) | `testRestoreWritesBackAChangedSourcePath` |
  | 34 | `ModuleRootIsolationTest` | `testRestoreOfAnUnchangedSourcePathReportsNoChange` |
  | 35 | `ModuleRootIsolationTest` | `testRestoredSourcePathLeavesNoIndexedFileBehind` |

  The JUnit4 methods carry `@Test` and the JUnit3 ones are public and start with `test`, so the
  BUG-461 guard on `main` (`src/test/kotlin/net/internetisalie/lunar/UncollectedTestMethodGuardTest.kt`)
  collects them. Python tests (TC 4–7, 20–27, 32, 39–47) run under `unittest`, not Gradle, and are
  outside this rule.

## 3. Algorithms

### 3.1 Manifest parse (`CorpusManifest.parseEntry`)
1. `val raw = entry.getAsJsonArray("definitionLibraries")` — absent → `emptyList()`.
2. Otherwise `raw.map { it.asString.trim() }.filter { it.isNotEmpty() }.distinct()`.
3. Unknown ids are **not** checked here — the manifest parser has no catalog dependency;
   `CorpusDefinitionLibraries.resolve` checks (§3.2), which `provision` calls before anything else.

### 3.2 Resolve pins
Kotlin `resolve(entry)`:
1. `val catalog = LuaDefinitionCatalogLoader.load()`.
2. `val unknown = entry.definitionLibraries.filter { catalog.entry(it) == null }`.
3. `check(unknown.isEmpty()) { "Corpus entry '${entry.name}' pins definition libraries $unknown that src/main/resources/definitions/lunar-definitions-catalog.json does not carry" }`.
4. `return catalog.withDependencies(entry.definitionLibraries).sortedBy { it.id }`.

Python `resolve_libraries(manifest, catalog)`:
1. `pending = []`. For each `entry` in `manifest["corpora"]`: `ids = entry.get("definitionLibraries", [])`;
   if `ids` is not a list of `str` → `PinError(f"{entry.get('name', '?')}: definitionLibraries must be a list of catalog ids")`.
   For each stripped non-empty id: not in `catalog` → `PinError(f"{name}: definition library '{id}' is not in {CATALOG}")`;
   else append to `pending`.
2. `resolved = {}`. While `pending`: pop the first id; skip if in `resolved`; look it up (a `requires`
   naming a missing id → `PinError(f"catalog entry requires unknown definition library '{id}'")`);
   store; extend `pending` with its `requires` (default `[]`).
3. Return `[resolved[key] for key in sorted(resolved)]`. Cycles terminate because a resolved id is skipped.

### 3.3 Stage, settle, enable (`CorpusDefinitionLibraries`)

**Stage** — `stage(project, entry, repoRoot)`:
1. `val libraries = resolve(entry)`; if empty return `emptyList()` (the enable list stays as `setUp`
   left it — empty — and `enable` will fire no broadcast, so an unpinned member's sweep is unchanged).
2. Return `fetchLocally(localEnabler(project, repoRoot), libraries, repoRoot)`, where:
   - `localEnabler(project, repoRoot)` =
     `LuaDefinitionLibraryEnabler(project, LuaDefinitionLibraryFetcher(LuaDefinitionLibraryFetcher.defaultCacheRoot()) { library, _ -> archiveFile(repoRoot, library).toPath() })`
     (default notifier — it is never called, because nothing is missing when `apply` runs).
   - `fetchLocally(enabler, libraries, repoRoot)`:
     1. For each library: `val archive = archiveFile(repoRoot, library)`;
        `check(archive.isFile) { "Definition library archive ${archive.path} is missing. Run: tooling/corpus/fetch-corpus.py" }`.
     2. `val fetcher = LuaDefinitionLibraryFetcher(LuaDefinitionLibraryFetcher.defaultCacheRoot())`
        (used only for `cacheDir`, which depends on the root alone); for each library
        `fetcher.cacheDir(library).toFile().deleteRecursively()` — a tree left by another test class
        under the same id-version (`TypeElevenDefinitionLibraryTestCase` seeds that root) must never
        stand in for the real archive. `isCached` reads the real filesystem
        (`LuaDefinitionLibraryFetcher.kt:58-61`), so the deletion is seen at once.
     3. `val failed = enabler.fetchAll(enabler.missingEntries(libraries.map { it.id }), EmptyProgressIndicator())`;
        `check(failed.isEmpty()) { "Definition libraries $failed did not extract from $ARCHIVE_DIR under their catalog rootPrefix; see idea.log" }`.
     4. Return `libraries.map { fetcher.cacheDir(it) }`.

**Settle** — `settle(project)`: `PlatformTestUtil.dispatchAllEventsInIdeEventQueue()`, then
`IndexingTestUtil.waitUntilIndexesAreReady(project)`. After `stage` it closes the dumb-mode session
fetch and extraction open (DR-07b); after a refresh it runs the queued broadcast
(`PlatformLibraryIndex.reload()`: a roots change plus a stub-index rebuild) and the resolve-cache
drop, then waits for the indexing they start.
`waitUntilIndexesAreReady` after a roots or VFS change is prior art at
`TypeElevenDefinitionLibraryTestCase.kt:142`, `LibraryRootTestCase.kt:92` and
`BuildWorkspaceActionTest.kt:56`, and the dispatch before it at `LuaDefinitionEnableListTest.kt:65-74`.

**Enable** — `enable(project, entry, repoRoot, cacheDirs)`:
1. If `cacheDirs` is empty return `emptyList()` — nothing was staged, nothing is enabled, no
   broadcast fires.
2. `localEnabler(project, repoRoot).apply(entry.definitionLibraries)` — the **declared** ids; the
   provider adds dependencies itself (`LuaDefinitionLibraryProvider.kt:55`). The list differs from the
   empty one `setUp` left, so `setEnabledDefinitionLibrariesAndNotify` writes it and queues the
   broadcast + `dropResolveCaches()`. `apply`'s background task then finds `missingEntries` empty and
   returns without a second refresh (`LuaDefinitionLibraryEnabler.kt:69-70`). The queued broadcast
   runs either inside `apply` or in step 3 (DR-10); either way it is inside the observation window.
3. `settle(project)`.
4. `assertRegistered(project, cacheDirs)`: `watched = AdditionalLibraryRootsProvider.EP_NAME.extensionList.filterIsInstance<LuaDefinitionLibraryProvider>().flatMap { it.getRootsToWatch(project) }.map { it.toNioPath().toRealPath() }.toSet()`;
   `check(watched == cacheDirs.map { it.toRealPath() }.toSet()) { "LuaDefinitionLibraryProvider watches $watched; expected $cacheDirs" }`.
   This is the step that proves the **production** provider — not a test seam — carries the roots.
5. Return `cacheDirs`.

**Observe** — `observeRefresh(project, parent, refresh)`:
1. `settle(project)`.
2. `val probe = CorpusRefreshProbe.attach(project, parent)`.
3. `val cacheDirs = refresh()`.
4. `return ObservedRefresh(cacheDirs, probe.observe(project))`.

Every refresh passed in ends with its own `settle` (`enable` step 3, `release` step 3,
`refreshUnpinned` step 2), so step 4 reads after the refresh has drained and its indexing finished.

**Provision** — `provision(project, entry, repoRoot)` = `enable(project, entry, repoRoot, stage(project, entry, repoRoot))`.
For callers that observe no window (TC 8, TC 10–13). The sweep and TC 36 call `stage`, then
`observeRefresh` around `enable`.

**Why settle before attach**: otherwise the fetch-opened dumb session's exit lands inside the
window and the pinned arm reads differently from the bare arm, so §3.5 row d refuses every pair
(DR-07b, DR-10). No function here calls `LuaSettingsChangeListener.getInstance` (§3.13).

**Why fetching before `apply` reaches the user's end state.** The settings page's `apply(ids)` writes
the list and broadcasts before anything is on disk (a refresh that registers no root), then fetches
on a background task and broadcasts again (`LuaDefinitionLibraryEnabler.kt:61-78`); only the second
refresh registers the libraries. The sweep fetches first, synchronously, so its single broadcast is
that second one, with the same list on disk and in settings. The user's order is not used because
the background task's completion is not observable from the test thread, and a failed fetch there
only balloons where the sweep must fail.

### 3.4 Release
1. If `cacheDirs` is empty **and** `LuaProjectSettings.getInstance(project).enabledDefinitionLibraries`
   is empty, return — nothing was enabled, so no broadcast fires. (Keyed on the live list as well as
   `cacheDirs` because an `enable` that threw after `apply` — e.g. in `assertRegistered` — never
   returned its directories, yet left libraries enabled.)
2. `LuaDefinitionLibraryEnabler(project).apply(emptyList())` — the user unticking every library. The
   list changes, so the broadcast and cache drop are queued; `apply` returns before any fetch
   because the id list is empty (`LuaDefinitionLibraryEnabler.kt:64`).
3. `settle(project)` — so the next member, which shares the light project, starts with no library
   root.
4. `cacheDirs.forEach { it.toFile().deleteRecursively() }`.
5. `VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFile(LuaDefinitionLibraryFetcher.defaultCacheRoot(), true))`.

`refreshRoots(project)`: `runWriteAction { ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED) }`,
then `IndexingTestUtil.waitUntilIndexesAreReady(project)`. Used only where no settings broadcast
applies: §3.10 and TC 35.

### 3.5 Attribution (`attribute_delta.py`)
Input: two parsed tallies and the library global set **G**. Output: rows and an exit code.
`S = {parseErrors, requires, unresolvedRequires, oracleDisagreements, oracleFalseAccepts, oracleTimeouts, lexerRoundTripFailures, unmergedTokens}`.

1. **Comparability** — check in this order and raise `NotComparable(<message>)` (exit 2) at the
   first that holds; `<message>` is exactly the f-string shown:

   | # | condition | message |
   | :-- | :-- | :-- |
   | a | `bare["commit"] != pinned["commit"]` | `f"not the same corpus commit (bare {bare['commit']}, pinned {pinned['commit']})"` |
   | b | `bare["files"] != pinned["files"]` | `f"file counts differ (bare {bare['files']}, pinned {pinned['files']})"` |
   | c | for `key` in `REFRESH_KEYS`, then for `arm, tally` in `("bare", bare), ("pinned", pinned)`: `key not in tally` | `f"the {arm} tally has no {key} line"` |
   | d | for `key` in `REFRESH_KEYS`: `bare[key] != pinned[key]` | `f"refresh observable {key} differs (bare {bare[key]}, pinned {pinned[key]}): the arms did not receive the same refresh"` |
   | e | any bare key starts `library.` | `"the bare run carried libraries"` |
   | f | no pinned key starts `library.` | `"nothing to attribute: the pinned run registered no library"` |
   | g | `int(x.get("inspection.highlightFailures", "0")) > 0` for `x` in `bare`, `pinned` | `"highlight failures make inspection counts non-reproducible (BUG-390)"` |

   Rows c and d walk `REFRESH_KEYS` in its declared order, so the message names the first
   observable that is missing or differs. They are compared for **equality**, not against expected
   values, because `physicalPsiChanges` depends on how indexing of the real trees runs (§2.6a
   "Values"); TC 38 measures the sweep's own values for luacheck.
2. `keys = sorted(k for k in bare.keys() | pinned.keys() if tracked(k))`.
3. For each key: `b = int(bare.get(k, "0"))`, `p = int(pinned.get(k, "0"))`, `d = p - b`; skip `d == 0`.
4. `symbol_deltas(tool)` = for every symbol `s` with `allsymbol.<tool>.<s>` on either side,
   `int(pinned.get(..., 0)) - int(bare.get(..., 0))`, keeping non-zero values.
   `top(deltas, n=5)` = entries sorted by `(-abs(v), s)`, first `n`, rendered `f"{s}:{v:+d}"`, joined `", "`.
5. Classify:

   | key | condition | class | evidence |
   | :-- | :-- | :-- | :-- |
   | `inspection.LuaUndeclaredVariable` | let `sd = symbol_deltas("LuaUndeclaredVariable")`, `outside = {s∉G, v≠0}`, `risen = {s∈G, v>0}`, `covered = sum(sd) == d`; all of `not outside`, `not risen`, `covered` | `EXPLAINED` | `top(sd)` |
   | same | otherwise | `UNEXPLAINED` | `top(outside ∪ risen)`, plus `; symbol tally sums to {sum(sd)}, inspection delta {d}` when not `covered` |
   | `unresolvedRequires` | `d < 0` | `EXPECTED` | `library modules now resolve` |
   | `unresolvedRequires` | `d > 0` | `UNEXPLAINED` | `a library cannot unresolve a require` |
   | any other key in `S`, or `crash.*` | always | `UNEXPLAINED` | `a definition library cannot move this metric` |
   | other `inspection.<tool>` | `d < 0` | `TRIAGE-DROP` | `top(symbol_deltas(tool))` |
   | other `inspection.<tool>` | `d > 0`, `risen = {s: v>0}` non-empty and every `s ∈ G` | `TRIAGE-GLOBAL` | `top(...)` |
   | other `inspection.<tool>` | `d > 0`, otherwise (including no symbols at all) | `TRIAGE-CONTRACT` | `top(...)` |

6. `main`: parse `--corpus NAME --bare DIR --pinned DIR [--definitions DIR] [--catalog FILE]`
   (defaults `test/corpus/.definitions` and the catalog path, both relative to the repo root);
   read `DIR/NAME.tally` for each arm. Build G: for every pinned line `library.<id>=<v>`, in key
   order, with `catalog = load_catalog(catalog_path)`:
   1. `id not in catalog` → `NotComparable(f"library {id}: not in the catalog {catalog_path}")`.
   2. `catalog[id]["version"] != v` → `NotComparable(f"library {id}: the pinned tally carries {v}, the catalog has {catalog[id]['version']}")`.
   3. `archive = definitions_dir / f"{id}-{v}.tar.gz"`; not a file →
      `NotComparable(f"library {id}: archive {archive} is missing; run tooling/corpus/fetch-corpus.py")`.
   4. `G |= library_globals(archive, catalog[id]["rootPrefix"])`.

   Then `rows = attribute(bare, pinned, G)`; print `render(...)`; return `exit_code(rows)`. On
   `NotComparable` (raised by the G-building or by `attribute`) print
   `f"attribute_delta: not comparable: {message}"` to stderr and return `2`. The version check runs
   before the archive is opened, so TC 32's catalog needs no archive.

**Why these rules.** A definition library outside the project can only declare names and contract
types. So it may lower undeclared counts **for names it declares** and nothing else; it may resolve
requires; it cannot alter parsing, lexing, the oracle or recognised-require counting. Any other
inspection moving is real and must be looked at: a rise whose every rising symbol is a library
global is a project name colliding with a library global (`TRIAGE-GLOBAL`, e.g. busted's `file`
against a project local), anything else is a library contract being enforced (`TRIAGE-CONTRACT`).

### 3.6 Library globals
`library_globals(archive, root_prefix)`:
1. Open with `tarfile.open(archive, "r:gz")`. For each member that `isfile()`, whose name starts
   `root_prefix + "/"` and ends `.lua`: decode UTF-8 with `errors="replace"`.
2. For each line, match `^function\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(` or
   `^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)`; add group 1. Anchored at column 0, so nested and `local`
   declarations, dotted names (`function M.f()`) and comparisons (`x == y`) never match.
3. Return the set. Executed against the real pinned archives (2026-10-10) it yields exactly the
   sets listed in requirements → MAINT-37-06/-07.

### 3.7 `fetch_library(lib, dest_dir)`
1. `dest = dest_dir / archive_name(lib)`. If `dest.is_file()` and `dest.stat().st_size == lib["size"]`
   and `sha256_of(dest) == lib["sha256"]`: log `"<id> already at <version[:8]> — skipping"`, return `dest`.
2. `failures = []`. For each `url` in `lib["urls"]`, in order: `part = dest.with_name(dest.name + ".part")`;
   `urllib.request.urlopen(url, timeout=60)` copied into `part` with `shutil.copyfileobj`; on
   `OSError` append `f"{url}: {error}"`, unlink `part` if present, continue. Compute size and digest;
   if both equal the catalog's, `os.replace(part, dest)` and return `dest`; else append
   `f"{url}: sha256 {actual} size {actual_size}"` and unlink `part`.
3. Raise `PinError` listing `failures`, the expected `sha256` and `size`, and:
   `"GitHub regenerates /archive/ tarballs. Do NOT update the catalog pin without establishing the content is unchanged (diff -r the two extracted trees)."`

### 3.8 `CorpusAttributionMode.fromProperty`
`null` or blank → `null`; otherwise `entries.firstOrNull { it.dirName == value.trim() }` or
`error("lunar.corpus.attribution must be 'bare' or 'pinned', got '$value'")`. Case-sensitive.

`forRun(attribution, record)` = `fromProperty(attribution).also { mode -> check(mode == null || record != "true") { "-PcorpusAttribution and -PrecordCorpusBaseline are mutually exclusive: an attribution run never writes a baseline" } }`.
`record` is the `lunar.corpus.record` system property, which `-PrecordCorpusBaseline` sets to
`true` (`build.gradle.kts:334-336`). A record run (`attribution` absent) returns `null` and records;
an attribution run without the record flag returns its mode.

### 3.9 `enabledPins(project)`
`LuaDefinitionCatalogLoader.load().withDependencies(LuaProjectSettings.getInstance(project).enabledDefinitionLibraries).associate { it.id to it.version }.toSortedMap()`.
Read from the live settings rather than from the manifest so the baseline records what was
registered, not what was asked for; `provision` has already asserted they agree.

### 3.10 Module-root restore (`LuaCorpusSweepTest.tearDown`, MAINT-37-09)
1. `CorpusSourcePath.restore(project, savedSourcePath)` **first**, so that `release`'s broadcast
   (step 2) recomputes `PlatformLibraryProvider.getExternalLibraries` against the restored path and
   cannot register the member's `moduleRoot` as a "Search Trees" library
   (`PlatformLibraryProvider.kt:54-73`).
2. `CorpusDefinitionLibraries.release(project, provisioned)` — its broadcast, when it fires, is the
   roots change that drops any library the leaked path had registered.
3. If step 1 changed the value and `provisioned` is empty (no broadcast fired in step 2),
   `CorpusDefinitionLibraries.refreshRoots(project)`. That is penlight's `tearDown` whenever penlight
   is not provisioned: every shape before its pin lands, and the bare arm after.

The members swept before penlight are untouched (their `restore` returns `false` and nothing
pinned fires). penlight's own sweep is untouched: the restore runs after it.

### 3.11 Reconciliation of the committed delta (`reconcile_baseline.py`)
The classifier attributes the **member-alone** delta between two refreshed arms; the record commits
the **full-shape** delta between two configurations, one of which may have had no refresh at all.
Reconciliation accounts for the difference term by term, so that what is left is the shape offset
alone and the refresh's own effect is measured and labelled rather than folded into it.

**Terms**, for one member and one tracked key. `A` is the member-alone count with no settings refresh
and no library. `ε` is the effect of the definition-roots refresh (`reload()`, stub rebuild, daemon
restart, resolve-cache drop) on a member-alone run. `L` is a library's effect on a member-alone run.
`o` is the full-shape count minus the member-alone count **of the same configuration**, so any
difference between the refresh's effect in the two shapes is part of `o`. The record sequence
(implementation-plan → Phase 4) produces these records:

| record | step | first pin of the member | re-pin (previous pin `L_old`) | member not in `PAIRED` (configuration `C`, the same at S1 and S6) |
| :-- | :-- | :-- | :-- | :-- |
| `old` | S2: committed baseline | `A + o_old` | `A + ε + L_old + o_old` | `C + o_old` |
| `before` | S2a: `-PcorpusAttribution=pinned`, on the S1 tree | `A` | `A + ε + L_old` | `C` |
| `bare` | S4 | `A + ε` | `A + ε` | — |
| `pinned` | S4 | `A + ε + L` | `A + ε + L_new` | — |
| `new` | S6: re-recorded baseline | `A + ε + L + o_new` | `A + ε + L_new + o_new` | `C + o_new` |

`before` is a pinned-mode run of the configuration the **starting** commit gives the member, so it is
the member-alone counterpart of `old`. For a member with no pin there, `stage` returns empty and
`enable` fires no broadcast (§3.3), so `before` carries no refresh. For a member that already had a pin (a catalog
re-pin), `before` provisions the old version from the starting tree's catalog.

Then, in every column, `committed − attributed = o_new − o_old`:
- **First pin.** `committed = ε + L + (o_new − o_old)`; `attributed = key_delta(before, pinned) = ε + L`.
  The classifier (bare vs pinned) attributes `L`. `ε` is measured by itself as
  `key_delta(before, bare)` and reported as the **refresh effect** (step 4).
- **Re-pin.** `committed = L_new − L_old + (o_new − o_old)`, because `ε` is in both `old` and `new`;
  `attributed = key_delta(before, pinned) = L_new − L_old`. The classifier attributes `L_new`, the new
  library's whole effect on this engine. `L_old` is measured by itself as `key_delta(bare, before)` and
  reported as the **previous pin effect** (step 4).
- **Member not in `PAIRED`** (unpinned, already pinned and untouched, or withheld at S5): its
  configuration does not change, so `attributed = {}` and `committed = o_new − o_old`. A withheld
  member satisfies this only because S5 withholds in a **manifest pin** alone, where the catalog is
  unchanged and the member's `definitionLibraries` is set back to its S1 value. In a **catalog
  re-pin**, a member cannot be returned to its S1 configuration while the catalog change lands (its
  old version is no longer in the catalog), so an exit 1 or a row-g result there stops the whole
  sequence instead of withholding (implementation-plan → Phase 4, S5).

So the residual contains no refresh term, and every term that is not a residual has its own measured,
labelled row.

1. `key_delta(before, after)`: `{k: int(after.get(k, "0")) - int(before.get(k, "0")) for k in sorted(before.keys() | after.keys()) if tracked(k)}`,
   keeping only non-zero values. `parse_tally` reads a baseline and a tally alike (a baseline is a
   tally with no refresh or `allsymbol.` lines, and `tracked` ignores those keys).
2. Inputs per member: `committed = key_delta(parse_tally(old), parse_tally(new))`;
   `attributed = key_delta(parse_tally(before), parse_tally(pinned))` for a member still in `PAIRED`
   after S5, else `{}`.
   **Why `old` carries no pre-existing drift.** A green corpus gate would not prove it: `assertRatchet`
   fails only on regressions and passes improvements with an `IMPROVED … re-record` line
   (`CorpusGuards.kt:48-55`), `oracleFalseAccepts` is never gated (absent from `compare`'s list,
   `CorpusMetrics.kt:258-268`), and every `inspection.*` key goes ungated when `highlightFailures > 0`
   (`:250-253`). So the record sequence's pre-gate (implementation-plan → Phase 4, step S1) instead
   **records** the full shape on that same commit and requires `git status --porcelain -- src/test/resources/corpus/`
   to print nothing before anything else runs (unlike `git diff`, it also lists untracked files the
   record brought back). Empty output means a fresh full-shape record of the
   starting commit reproduces every committed baseline byte for byte — every key, gated or not — so
   `old` is that record, and `committed` contains only what the change itself moved.
3. `reconcile(committed, attributed, bound=SHAPE_RESIDUAL_BOUND)`: for `k` in
   `sorted(committed.keys() | attributed.keys())`: `c = committed.get(k, 0)`, `a = attributed.get(k, 0)`,
   `r = c - a`; skip `r == 0`; emit `Residual(k, c, a, r, always_blocking(k) or abs(r) > bound)`.
   `always_blocking(k)` is `k in STRUCTURAL_KEYS or k.startswith("crash.") or k == HIGHLIGHT_FAILURES`:
   structural and crash keys measure parsing, lexing, the oracle and require counting, which no shape
   difference can legitimately move, and `inspection.highlightFailures` is BUG-390's crash count,
   which the ratchet gates always.
4. `start_effect(before, bare)` measures the term `attributed` carries besides the classified library:
   - `pins = [(k.removeprefix(LIBRARY_PREFIX), v) for k, v in sorted(before.items()) if k.startswith(LIBRARY_PREFIX)]`.
   - **No pins** (first pin): title `"refresh effect"`. For each `k` in
     `sorted(k for k in before.keys() | bare.keys() if tracked(k))`: `b0 = int(before.get(k, "0"))`,
     `b1 = int(bare.get(k, "0"))`, `e = b1 - b0`; skip `e == 0`; emit
     `StartRow(k, b0, b1, e, always_blocking(k))`. A settings refresh with no new root can no more move
     a structural or crash key than a shape can.
   - **Pins** (re-pin): title `f"previous pin effect ({', '.join(f'{i}={v[:8]}' for i, v in pins)})"`.
     The same loop with `e = b0 - b1` (the old library's effect, `L_old`) and
     `blocking = (k == "unresolvedRequires" and e > 0) or (k != "unresolvedRequires" and always_blocking(k))`
     — the rules §3.5 applies to any library: it may resolve requires, it cannot unresolve one, and it
     cannot move any other structural or crash key.
   - Return `(title, rows)`.
5. `main`: parse `--corpus NAME --old FILE --new FILE [--before DIR --bare DIR --pinned DIR] [--offset]`
   with `argparse`. In this order:
   1. `--offset` together with any of `--before`/`--bare`/`--pinned` → print
      `reconcile_baseline: --offset compares two records and takes no --before, --bare or --pinned`
      to stderr, return `2`.
   2. One or two of `--before`/`--bare`/`--pinned` given → print
      `reconcile_baseline: --before, --bare and --pinned go together` to stderr, return `2`.
   3. `committed = key_delta(parse_tally(old text), parse_tally(new text))`.
   4. `--offset`: `rows = reconcile(committed, {}, SHAPE_OFFSET_BOUND)`; print
      `render(NAME, rows, "shape offset")`; return `residual_exit(rows)`.
   5. No tallies: `rows = reconcile(committed, {})`; print `render(NAME, rows)`; return `residual_exit(rows)`.
   6. Tallies (each at `DIR/NAME.tally`): `rows = reconcile(committed, key_delta(before, pinned))`;
      `title, start = start_effect(before, bare)`; print `render(NAME, rows)`, an empty line, and
      `render_start(NAME, title, start)`; return `residual_exit([*rows, *start])`.

**Offsets.** `--offset` with a member-alone tally as `--old` and a full-shape baseline as `--new`
prints that member's shape offset `o` per key. The record sequence runs it twice per member: at S2b
for `o_old` (`before` tally against `old`) and at S8 for `o_new` (`pinned` tally, or `before` for a
member not in `PAIRED`, against `new`).

**The bound.** `SHAPE_OFFSET_BOUND = 2` is the one sample BUG-418 measured of a full-shape offset on
an inspection count: zerobrane `LuaUndeclaredVariable` read 1 945 in the full gate shape and 1 947
member-alone (`docs/features/bug-fixes/418-corpus-inspection-counts-drift-across-identical-runs/bug-report.md:21`
for the values, `:78-79` for which harness produced which, `:84`; and `LuaCorpusSweepTest.kt:27-31`,
"reads ±2 off a baseline recorded by the full run"). One sample does not establish it for every member
and key (the same report's `LuaTypeAssignability` row, `:24`, spreads by 8–11 across harnesses on a
different tree), so the plan **checks** it on both sides of every member, every time the sequence
runs: `o_old` at S2b (first execution: DR-08) and `o_new` at S8. `SHAPE_RESIDUAL_BOUND = 2 * SHAPE_OFFSET_BOUND`
then holds by arithmetic for any member that passes both checks; `reconcile` still applies it.
- **What clears it.** The stop rule (implementation-plan → Gates) files a BUG carrying every offset output. The BUG closes only in
  one of the ways listed here. (a) It finds a cause, such as member state that outlives the member; the fix lands, and
  the sequence restarts from the fixing commit. (b) It shows the offset is deterministic shape
  behaviour: two consecutive full-shape records on the same commit are byte-identical, and DR-04 holds.
  Then `SHAPE_OFFSET_BOUND` returns to Step 9 review with the measured offsets as its calibration input,
  and the sequence restarts after that review passes. No other path clears it.

A blocking row stops the change under the **stop rule** (implementation-plan → Gates "Stop rule").
`inspection.*` residuals within the bound, and non-blocking start-effect rows, do not block. They
are recorded in `rebaseline-attribution.md` per member, under "Shape residual" and under the
start-effect title, with their values.

### 3.12 `refreshUnpinned(project)` — the bare arm's broadcast
1. `LuaProjectSettings.getInstance(project).notifyDefinitionRootsChanged()`
   (`LuaProjectSettings.kt:199-204`) — the same function `setEnabledDefinitionLibrariesAndNotify`
   calls in the pinned arm (`:184-189`), so it queues the same publish and `dropResolveCaches()`.
   The enable list is not touched.
2. `settle(project)` (§3.3).

Not through `apply`: `apply(emptyList())` on `setUp`'s empty list publishes nothing (DR-07b), and an
`apply` with a fake id would write it into the enable list.

Wrong implementations, each seen by the refresh observation (§2.6a):
- **Direct publish**: step 1 replaced by a direct `syncPublisher(LuaSettingsChangedListener.TOPIC).onSettingsChanged()`,
  as `LuaProjectConfigurable.kt:277` does. The reload runs and `dropResolveCaches()` does not, so the
  observation is predicted to lose the drop's event, reading `(1, 1, 3)`; DR-11 measures it.
- **No drain**: step 2 omitted. The publish and the drop sit in an `invokeLater` that nothing runs
  before `observe` (the failure `LuaDefinitionEnableListTest.kt:64-65` records).
- **No notify**: step 1 omitted. Nothing is published, reloaded or dropped.

### 3.13 `requireSettingsSubscriber(project)` — the subscriber precondition
`check(project.getServiceIfCreated(LuaSettingsChangeListener::class.java) != null) { "LuaSettingsChangeListener has not been created: the LuaTargetSyncStartup activity has not run for this project, so a settings broadcast would reload nothing. MAINT-37 relies on the activity (DR-09b)." }`,
with the import `net.internetisalie.lunar.project.LuaSettingsChangeListener`. Called directly after
`super.setUp()` in `LuaCorpusSweepTest` and `DefinitionPinProvisioningTest`.

**Why it is needed.** Without a subscriber, both arms publish to no one and read the same tuple, so
§3.5 row d would pass a pair that had no reload at all. The `LuaTargetSyncStartup` activity creates
the subscriber on a background dispatcher before the test body in every measured JVM (DR-09b,
DR-10), but no platform contract guarantees that order; if it ever fails, this check fails `setUp`
loudly. It cannot satisfy itself, because `getServiceIfCreated` reports without creating (DR-09b's
control). For the same reason no function in this design calls `LuaSettingsChangeListener.getInstance`:
the call would be dead in every measured JVM, and its omission an equivalent mutant.

**Why it is not acceptance.** Its failing branch is unreachable from any fixture, so it carries no
TC and its absence is not a mutant any TC claims to kill. The condition it guards is still observed
in the routine suite: with no subscriber, no `PlatformLibraryIndex.reload()` runs, so TC 36 and
TC 37 read `rootsTicks = 0` against their asserted `1`.

### 3.14 `definition_pins.py --verify` — the CI fixture check
`verify_archives(libraries, dest_dir)`: for each `lib` in `libraries`, in order,
`path = dest_dir / archive_name(lib)`; append `archive_name(lib)` when `not path.is_file()`, or
`path.stat().st_size != lib["size"]`, or `sha256_of(path) != lib["sha256"]` (checked in that order,
stopping at the first that holds). Return the appended names.

`main(argv)`: `argparse` with `--verify DIR` (required), `--manifest FILE` (default `MANIFEST`) and
`--catalog FILE` (default `CATALOG`). In this order:
1. `manifest = json.loads(Path(manifest).read_text())`; `libraries = resolve_libraries(manifest, load_catalog(Path(catalog)))`.
   On `PinError`: print `f"definition_pins: {error}"` to stderr, return `1`.
2. `missing = verify_archives(libraries, Path(DIR))`. Non-empty: print
   `f"definition_pins: not provisioned: {', '.join(missing)}; run tooling/corpus/fetch-corpus.py"`
   to stderr, return `1`.
3. Print `f"definitions ok ({len(libraries)} archives verified in {DIR})"` to stdout, return `0`.

With no pins (before Phase 4) `libraries` is empty and step 3 prints `definitions ok (0 archives …)`,
so the CI step this check joins (§7 "CI") passes before any member pins a library. It re-checks what
`fetch_library` checked at download time because `fetch-corpus.py`'s exit status is invisible to the
CI step, whose verdict is its last command (`.gitea/workflows/build-plugin.yml:364-367`).

## 4. External Data & Parsing

### 4.1 `tooling/corpus/corpus.json` — new optional field
```json
{ "name": "luacheck", "url": "…", "commit": "cc089e3f…", "roots": ["src", "spec"],
  "luaLevel": "LUA51", "definitionLibraries": ["busted"] }
```
Array of catalog ids; see §3.1 / §3.2. Parsed by Gson (Kotlin) and stdlib `json` (Python) — no
hand-written parser, per BUG-407.

### 4.2 `src/main/resources/definitions/lunar-definitions-catalog.json` (consumed, unchanged)
Fields read: `libraries[].id`, `.version`, `.urls`, `.sha256`, `.size`, `.rootPrefix`, `.requires`.
Kotlin reads it only through `LuaDefinitionCatalogLoader`; Python through `json.loads`.

### 4.3 Baseline lines
`library.<id>=<version>`, one per library in the closure, sorted by id, emitted immediately after
`unresolvedRequires=`. Example (luacheck after Phase 4):
```
unresolvedRequires=3
library.busted=5ed85d0e016a5eb5eca097aa52905eedf1b180f1
library.luassert=d3528bb679302cbfdedefabb37064515ab95f7b9
inspection.LuaGlobalCreation=…
```
Parse: key = text before the first `=` (the existing rule), prefix `library.` stripped.

### 4.4 Tally (`build/corpus-attribution/<mode>/<name>.tally`)
`CorpusBaseline.render(metrics)` verbatim, then one line per key of `REFRESH_KEYS`, in its order —
`settingsBroadcasts=<n>`, `rootsTicks=<n>`, `physicalPsiChanges=<n>` — the `RefreshObservation`
read once after the refresh (§2.6a; §2.7 step 2, `refreshFor`'s sub-step 5), then `allsymbol.<toolId>.<symbol>=<count>`
for every entry of `allSymbolHits`, sorted by key. `parse_tally` mirrors `CorpusBaseline.parse`: for each line
containing `=` and not starting `#`, key = text before the first `=`; the repeated keys
`parseErrorFile` and `oracleSite` are skipped.

### 4.5 Classifier output (markdown, stdout)
```
## luacheck — libraries: busted=5ed85d0e, luassert=d3528bb6
| key | bare | pinned | delta | class | evidence |
| :-- | --: | --: | --: | :-- | :-- |
| inspection.LuaUndeclaredVariable | … | … | … | EXPLAINED | it:-…, describe:-…, … |
```
Versions truncated to 8 characters in the heading only. When no key moved, a single row
`| — | | | 0 | NO-CHANGE | |`. Phase 4/5 paste this output verbatim into `rebaseline-attribution.md`.

### 4.6 Archive layout (consumed)
GitHub `archive/<sha>.tar.gz`: top directory `<repo>-<sha>/`, definitions under `library/`, a
`config.json` beside it. `rootPrefix` selects `library/` (production behaviour,
`LuaDefinitionCatalog.kt:49-52`). Verified on the real busted and luassert archives (requirements → MAINT-37-06).

### 4.7 Reconciliation output (markdown, stdout)
```
## zerobrane — shape residual
| key | committed | attributed | residual | blocking |
| :-- | --: | --: | --: | :-- |
| inspection.LuaShadowingVariable | +2 | 0 | +2 | no |
```
The heading is `f"## {corpus} — {title}"` (`title` is `shape residual`, or `shape offset` under
`--offset`). Each integer is rendered `f"{v:+d}"` when non-zero and `0` when zero; `blocking` is
`yes`/`no`. When `rows` is empty, a single row `| — | 0 | 0 | 0 | no |`.

`render_start` (pair mode only, after an empty line):
```
## luarocks — refresh effect
| key | before | bare | effect | blocking |
| :-- | --: | --: | --: | :-- |
| inspection.LuaShadowingVariable | 3 | 4 | +1 | no |
```
The heading is `f"## {corpus} — {title}"` with `start_effect`'s title. `before` and `bare` are the
plain counts (`f"{v}"`), and `effect` is rendered `f"{v:+d}"`. When `rows` is empty, a single row
`| — | 0 | 0 | 0 | no |`. Phases 4 and 5 paste every reconciliation output verbatim into
`rebaseline-attribution.md`.

## 5. Data Flow

### Example 1: routine gate on luacheck after Phase 4
`setUp` checks the startup-created settings subscriber exists, empties the enable list and snapshots
`sourcePath` → `sweepAndRatchet("luacheck")` → no attribution property → `stage` resolves
`[busted, luassert]`, deletes and re-extracts both archives through the enabler's `fetchAll` into
`<system>/lunar/definitions/busted-5ed85d0e…/` and `…/luassert-d3528bb6…/` → `settle` → the probe
attaches → `enable` calls `apply(["busted"])`, drains the queue (reload + cache drop), waits for
indexing, asserts the provider watches both → the observation is read and discarded →
`CorpusSweep.run` → `libraries = {busted, luassert}` → `assertRatchet`
passes identity and compares counts → `tearDown`: `restore` returns `false` (luacheck has no
`moduleRoot`), `release` unticks via `apply(emptyList())`, drains, deletes both trees.

### Example 2: attribution pair for penlight
`tooling/gce-builder/gce-builder.sh run "test --rerun --no-build-cache --tests 'net.internetisalie.lunar.corpus.LuaCorpusSweepTest.testPenlightCorpus' -PwithCorpus -PcorpusAttribution=bare"`
→ no staging; `settle`, the probe attaches, `refreshUnpinned` publishes and drains the same broadcast
the pinned arm's `apply` will → `build/corpus-attribution/bare/penlight.tally` with
`settingsBroadcasts=1`, `rootsTicks=1`, a `physicalPsiChanges=` line, and no `library.` line. Same with
`=pinned` → `…/pinned/penlight.tally` with the same refresh values and `library.busted=` and
`library.luassert=` lines. `rsync` both back;
`attribute_delta.py --corpus penlight …` → `EXPLAINED` for undeclared (all of `it`, `describe`,
`pending`, `before_each`, `after_each`, `setup` ∈ G), exit 0.

### Example 3: a catalog re-pin
The re-pin PR's `corpus` job fails with `Definition-library pins changed` until the PR also carries
the re-record, which is the record sequence (implementation-plan → Phase 4) with the catalog change
as S3.

## 6. Edge Cases

- **Entry with no pins** (zerobrane until TARGET-10 Phase 5b): `stage` and `enable` return empty, no broadcast
  fires, `libraries = {}`, baseline has no `library.` line — identical to today.
- **Stale tree in the shared cache root**: deleted before extraction (§3.3 "Stage", `fetchLocally` step 2; TC 10).
- **Archive absent on the builder** (fetch not run, or rsync skipped `test/`): `stage` fails with
  the fetch instruction before any measurement (TC 11).
- **A catalog `requires` naming a missing id**: Python `PinError`; Kotlin `withDependencies` drops it
  and `assertRegistered` then still passes for the libraries that do exist — acceptable because
  `LuaDefinitionCatalogLoaderTest` already refuses an unknown `requires` in the bundled catalog.
- **`allSymbolHits` keys containing `=`**: both sides split at the first `=`, consistently, so a
  pair compares like with like.
- **Attribution and record requested together**: refused (§2.7).
- **BUG-496 `sourcePath` leak**: removed by §3.10 in Phase 3b, before any pin, in its own commit
  and re-baseline. Without it, `release`'s roots change after a pinned penlight would recompute
  `PlatformLibraryProvider` with penlight's absolute out-of-project `lua/` path still set and index
  it as a library for zerobrane — a movement MAINT-37 itself would have caused. Member-alone
  attribution runs never sweep penlight before another member, so neither tally is affected either
  way.
- **Torture member in the full shape**: `LuaTortureCorpusTest` matches `*Corpus*`, so every
  full-shape record rewrites `torture-fuzzing-lua.baseline` and `torture-fuzzing-lua.expected-accepts`.
  It runs no inspections and reads no project setting this feature changes; both files must come
  back byte-identical (implementation-plan Phases 3b, 4 and 5 check it).

## 7. Integration Points

- **`plugin.xml`: no change.** The sweep reaches the existing
  `<additionalLibraryRootsProvider implementation="net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider"/>`
  (`plugin.xml:541`); asserting that it is that instance is §3.3 "Enable" step 4. TC 35 exercises the
  existing `PlatformLibraryProvider` registration (`plugin.xml:537`).
- **Message bus**: `net.internetisalie.lunar.settings.LuaSettingsChangedListener.TOPIC`
  (`settings/LuaSettingsChangedEvent.kt:29`) — published by the enabler, subscribed by
  `LuaSettingsChangeListener` and by `CorpusRefreshProbe`; and the platform's
  `com.intellij.psi.impl.PsiManagerImpl.ANY_PSI_CHANGE_TOPIC`, subscribed (read-only) by
  `CorpusRefreshProbe` (§2.6a).
- **Gradle**: §2.9; the existing `withCorpus` exclusion is unchanged. It and the `--tests '*Corpus*'`
  shape match test method names as well as class names, which §2.16's naming rule answers.
- **Settings key**: `LuaProjectSettings.State.enabledDefinitionLibraries`, written and cleared in test only.
- **Out-of-repo tree**: `test/corpus/.definitions/` (pushed by `gce-builder.sh`'s `rsync -aLz` of `test/`,
  `gce-builder.sh:147-151`).
- **CI** (`.gitea/workflows/build-plugin.yml`; Phase 3). These edits, and nothing else in the file:
  1. The `changes` job's corpus filter (`:102`) gains `|^src/main/resources/definitions/` directly
     after `^src/test/kotlin/net/internetisalie/lunar/corpus/`. The catalog is the version authority
     (requirements → Behavior Rules), so a PR that changes it must run the gate before merge; without
     this path it merges with the gate unrun, and the identity failure first appears on `main`.
  2. The `corpus` job's "Provision pinned fixtures" step (`:360-377`) is renamed
     `Provision pinned fixtures (corpus checkouts, definition archives, luac oracles, torture archive)`,
     and its last line gains `&& python3 tooling/corpus/definition_pins.py --verify test/corpus/.definitions`
     directly before `&& echo "fixtures ok"` (§3.14). `fetch-corpus.py` runs earlier in the same step,
     after the `test` symlink is re-pointed at `/cache/lunar/test`, so the archives land in the
     job's `test/corpus/.definitions/`.

  `.github/workflows/build-plugin.yml` has neither job (its header, `:16-21`: "the corpus lane is
  Gitea-only"), so the two workflow files stay equivalent in effect with no edit there.

## 8. Requirement Coverage

| Requirement | Priority | Implemented by (section) |
|-------------|----------|--------------------------|
| MAINT-37-01 | M | §2.1, §3.1, §3.2, §4.1, §2.16 (test names) |
| MAINT-37-02 | M | §2.10, §2.11, §3.2, §3.7, §3.14, §4.6, §7 "CI" |
| MAINT-37-03 | M | §2.4, §2.6a, §2.7, §2.8, §3.3, §3.4, §3.13, §2.16 (test names) |
| MAINT-37-04 | M | §2.2, §2.3, §2.5, §3.9, §4.3, §2.16 (test names) |
| MAINT-37-05 | M | §2.6, §2.6a, §2.7, §2.9, §2.12, §2.15, §3.5, §3.6, §3.8, §3.11, §3.12, §3.13, §4.4, §4.5, §4.7, §2.16 (test names) |
| MAINT-37-06 | M | §4.1 data change; procedure in implementation-plan Phase 4 |
| MAINT-37-07 | M | moved to TARGET-10-12: §4.1 data change, procedure in TARGET-10 implementation-plan Phase 5b |
| MAINT-37-08 | S | implementation-plan Phase 6 |
| MAINT-37-09 | M | §2.7, §2.13, §2.14, §3.4 (`refreshRoots`), §3.10, §2.16 (test names) |

## 9. Alternatives Considered

Only the alternatives an implementer might otherwise drift into:
- **Version in the manifest** (`{"id":"busted","version":"…"}`). Rejected: a second pin that can
  disagree with what users get; the catalog id plus the identity line forces a reviewable re-record.
- **Write `enabledDefinitionLibraries` directly and fire a bare `makeRootsChange`.** Rejected: it skips
  the settings broadcast (no `PlatformLibraryIndex.reload()`, no daemon restart) and
  `PsiManager.dropResolveCaches()`, which `LuaProjectSettings.kt:178-180` states new roots need.
- **`LibraryRootTestCase`'s anonymous provider.** Rejected: registration through the EP instance
  would be untested.
- **Calling `LuaSettingsChangeListener.getInstance` defensively.** Rejected: dead in every measured
  JVM, so its omission is an equivalent mutant; §3.13 makes the unproven ordering a loud failure.
- **A bare arm with no settings broadcast.** Rejected: the refresh would land in the delta, where a
  movement on any inspection but `LuaUndeclaredVariable` classifies as a non-blocking `TRIAGE-*` row.
- **Full-shape (`*Corpus*`) attribution pair.** Rejected: a member's library changes JVM state for
  the members after it (BUG-418), which would be classified as a library effect. Pairs are
  member-alone and the shape offset is reconciled (§3.11).
- **Reconciling against the bare arm** (`attributed = bare → pinned`). Rejected: the bare arm is not
  the configuration `old` records, so the refresh effect (first pin) or `−L_old` (re-pin) would land
  in the residual and stop the change with the wrong diagnosis (TC 44, TC 45).

## 10. Premises

| Constraint | Assumed or chosen | Verdict |
| :-- | :-- | :-- |
| The sweep must not use the network | chosen | Kept: reproducibility and the builder's egress. |
| Archives are byte-verified, not ADVISORY | chosen, against production's choice | Kept: production tolerates a repack because users must keep working; a ratchet must not. The cost — a GitHub repack blocks the fetch until a human confirms — is named in the error text (§3.7). |
| `.tar.gz` naming | inherited from `LuaArchiveExtractor.decompressorFor` | Fixed by production code this feature must not change. |
| The light project is shared across test classes | platform fact (`LuaDefinitionLibraryProviderTest.kt:27-29`) | Immovable; hence `setUp` empties the list and snapshots `sourcePath`, and `tearDown` restores both. |
| Provisioning through `LuaDefinitionLibraryEnabler` | chosen | Kept: it is the settings page's own behaviour class, so the sweep measures what a user gets; its cost (a stub-index rebuild per `apply`) is the user's cost too. |
| Only the pinned arm receives the settings broadcast | would follow from provisioning through `apply` alone | Removed: the bare arm calls `refreshUnpinned` (§3.12), and the classifier refuses a pair whose refresh observables differ. |
| Counting the publish is evidence of the refresh | assumed by a topic counter | Removed: a publish with no reload behind it, or without the cache drop, reads the same. `CorpusRefreshProbe` (§2.6a) records the roots tick and the PSI-change broadcast the refresh causes. |
| A green corpus gate means the committed baselines are current | inherited from reading the gate as an equality check | Removed: the ratchet passes improvements and leaves keys ungated (§3.11 step 2). The pre-gate records and requires `git status --porcelain` on the baselines to print nothing. |
| A test's class name decides whether it runs in the routine suite or in the sweeps' shape | assumed from the `*Corpus*` rule's class-only wording (BUG-497 → Root cause) | False: Gradle's filter matches test method names too (BUG-497). Removed: no new test class or test method name contains `Corpus`, every name is fixed in §2.16, and `SweepNamingGuardTest` fails the routine suite on any that does. |
| A record's invocation shape | inherited from CI's `corpus` job (`--tests '*Corpus*'`) | Kept on purpose: BUG-418 makes counts deterministic per shape, so a baseline is recorded with the filter that gates it. DR-12 recorded with that filter on `main` and reproduced every committed baseline. |
| The committed baseline is recorded in the full shape while attribution is member-alone | inherited (MAINT-33's gate shape) and chosen (§9) | Kept, and bridged: §3.11 reconciles the two per member against a `before` arm of the starting configuration, and blocks on a structural, crash or `highlightFailures` residual, or an `inspection.*` residual beyond the bound. |
| The reconciliation residual is the shape term alone | would hold only if `attributed` started from the configuration `old` records | Made true by construction: `attributed = before → pinned`, so the residual is `o_new − o_old` (§3.11). The refresh effect and a re-pin's previous-pin effect are measured separately and labelled. |
| BUG-418's ±2 bounds every member's offset on every key | one sample (zerobrane `LuaUndeclaredVariable`) | Not assumed: checked on both sides of every member on every run of the record sequence (S2b, S8; first execution DR-08). A stop has a stated exit (§3.11 "What clears it"). |
| `getServiceIfCreated` reports without creating | read from `ComponentManagerImpl.kt:759-763` | Executed (DR-09b's control). Relied on only by §3.13. |
| The sweep must instantiate `LuaSettingsChangeListener` itself | inherited from `TypeElevenGenerationSignalTest.kt:78-79`'s comment | Removed (DR-09b): the startup activity creates it; §3.13 asserts it exists. |
| Any window that brackets the refresh observes the same thing | assumed: a probe attached at `setUp` would bracket fetch and extraction too | Removed (DR-10): the window opens after `settle`, between `stage` and `enable` (§2.7, §3.3). |
| A member's `moduleRoot` leaking into later members | inherited (BUG-496) | Removed here (MAINT-37-09), not tolerated: tolerating it would let this feature's own roots changes turn the leak into an indexed library. |
| Libraries apply project-wide, not per root | chosen | Matches what a user enabling busted gets; `src/` files gain busted globals too, which attribution reports. |
| Symbols are capped at ten in the baseline | inherited (MAINT-33-10) | Kept for the baseline, removed for the tally — the only place the cap obstructs. |
| Python for tooling | inherited from `fetch-corpus.py`/`fetch-torture.py` | Chosen here on its merits: stdlib `json`/`tarfile`/`hashlib`, runs without the builder. |
| The corpus gate runs on the change that edits the catalog | assumed by "the identity line forces the re-record into the same change" | Made true: the CI `corpus` filter (`.gitea/workflows/build-plugin.yml:102`) does not select `src/main/resources/definitions/` on `main`, so a re-pin PR would merge with the gate unrun. Phase 3 adds the path (§7 "CI"); a push that bypasses PR CI is caught by `main`'s own `corpus` job and by S1's catalog branch. |
| The baselines are recorded on the machine that gates them | assumed by recording on the builder | False: CI's `corpus` job gates them on a `lunar-ci` VM (its comment records a cross-machine penlight failure, run 9733). Not removable here; checked instead: every phase that touches the corpus exits on its PR's `corpus` job green (implementation-plan → Gates, S12). |
| The starting commit's baselines are current | assumed by "Phase 1's tree is the first thing that could move them" | Removed: every no-movement check and every record sequence first re-records the base commit (part A, S1); DR-12 held for one commit, and `main` moves. |

## 11. Open Questions

_None._
