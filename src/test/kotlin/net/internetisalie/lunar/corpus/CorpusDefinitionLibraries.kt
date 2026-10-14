package net.internetisalie.lunar.corpus

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.definitions.LuaDefinitionCatalogLoader
import net.internetisalie.lunar.definitions.LuaDefinitionEntry
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryEnabler
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryFetcher
import net.internetisalie.lunar.definitions.LuaDefinitionLibraryProvider
import net.internetisalie.lunar.project.LuaSettingsChangeListener
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Path

/** The cache directories a refresh registered, and what its observation window saw (design §2.4). */
data class ObservedRefresh(
    val cacheDirs: List<Path>,
    val observation: RefreshObservation,
)

/**
 * Resolves, stages, enables, reports and releases a corpus member's pinned definition libraries
 * through [LuaDefinitionLibraryEnabler] — the class behind the settings page — from the local
 * verified archives `tooling/corpus/fetch-corpus.py` places under [ARCHIVE_DIR] (MAINT-37 design
 * §2.4, §3.2–§3.4, §3.9, §3.12, §3.13).
 *
 * Runs on the test's EDT. Staging and enabling are separate because the refresh observation window
 * must open between them, after [settle]: [observeRefresh] is the one place that orders them.
 */
object CorpusDefinitionLibraries {
    const val ARCHIVE_DIR = "test/corpus/.definitions"

    fun resolve(entry: CorpusEntry): List<LuaDefinitionEntry> {
        val catalog = LuaDefinitionCatalogLoader.load()
        val unknown = entry.definitionLibraries.filter { catalog.entry(it) == null }
        check(unknown.isEmpty()) {
            "Corpus entry '${entry.name}' pins definition libraries $unknown that " +
                "src/main/resources/definitions/lunar-definitions-catalog.json does not carry"
        }
        return catalog.withDependencies(entry.definitionLibraries).sortedBy { it.id }
    }

    fun archiveFile(
        repoRoot: File,
        library: LuaDefinitionEntry,
    ): File = File(repoRoot, "$ARCHIVE_DIR/${library.id}-${library.version}.tar.gz")

    fun requireSettingsSubscriber(project: Project) {
        check(project.getServiceIfCreated(LuaSettingsChangeListener::class.java) != null) {
            "LuaSettingsChangeListener has not been created: the LuaTargetSyncStartup activity has not run " +
                "for this project, so a settings broadcast would reload nothing. MAINT-37 relies on the " +
                "activity (DR-09b)."
        }
    }

    fun stage(
        project: Project,
        entry: CorpusEntry,
        repoRoot: File,
    ): List<Path> {
        val libraries = resolve(entry)
        if (libraries.isEmpty()) return emptyList()
        return fetchLocally(localEnabler(project, repoRoot), libraries, repoRoot)
    }

    fun enable(
        project: Project,
        entry: CorpusEntry,
        repoRoot: File,
        cacheDirs: List<Path>,
    ): List<Path> {
        if (cacheDirs.isEmpty()) return emptyList()
        localEnabler(project, repoRoot).apply(entry.definitionLibraries)
        settle(project)
        assertRegistered(project, cacheDirs)
        return cacheDirs
    }

    fun observeRefresh(
        project: Project,
        parent: Disposable,
        refresh: () -> List<Path>,
    ): ObservedRefresh {
        settle(project)
        val probe = CorpusRefreshProbe.attach(project, parent)
        val cacheDirs = refresh()
        return ObservedRefresh(cacheDirs, probe.observe(project))
    }

    fun provision(
        project: Project,
        entry: CorpusEntry,
        repoRoot: File,
    ): List<Path> = enable(project, entry, repoRoot, stage(project, entry, repoRoot))

    fun release(
        project: Project,
        cacheDirs: List<Path>,
    ) {
        if (cacheDirs.isEmpty() && LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.isEmpty()) return
        LuaDefinitionLibraryEnabler(project).apply(emptyList())
        settle(project)
        cacheDirs.forEach { it.toFile().deleteRecursively() }
        VfsUtil.markDirtyAndRefresh(
            false,
            true,
            true,
            VfsUtil.findFile(LuaDefinitionLibraryFetcher.defaultCacheRoot(), true),
        )
    }

    fun enabledPins(project: Project): Map<String, String> =
        LuaDefinitionCatalogLoader
            .load()
            .withDependencies(LuaProjectSettings.getInstance(project).enabledDefinitionLibraries)
            .associate { it.id to it.version }
            .toSortedMap()

    fun refreshRoots(project: Project) {
        runWriteAction {
            ProjectRootManagerEx
                .getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    fun refreshUnpinned(project: Project) {
        LuaProjectSettings.getInstance(project).notifyDefinitionRootsChanged()
        settle(project)
    }

    private fun localEnabler(
        project: Project,
        repoRoot: File,
    ): LuaDefinitionLibraryEnabler =
        LuaDefinitionLibraryEnabler(
            project,
            LuaDefinitionLibraryFetcher(LuaDefinitionLibraryFetcher.defaultCacheRoot()) { library, _ ->
                archiveFile(repoRoot, library).toPath()
            },
        )

    private fun fetchLocally(
        enabler: LuaDefinitionLibraryEnabler,
        libraries: List<LuaDefinitionEntry>,
        repoRoot: File,
    ): List<Path> {
        libraries.forEach { library ->
            val archive = archiveFile(repoRoot, library)
            check(
                archive.isFile,
            ) { "Definition library archive ${archive.path} is missing. Run: tooling/corpus/fetch-corpus.py" }
        }
        val fetcher = LuaDefinitionLibraryFetcher(LuaDefinitionLibraryFetcher.defaultCacheRoot())
        libraries.forEach { fetcher.cacheDir(it).toFile().deleteRecursively() }
        val failed = enabler.fetchAll(enabler.missingEntries(libraries.map { it.id }), EmptyProgressIndicator())
        check(failed.isEmpty()) {
            "Definition libraries $failed did not extract from $ARCHIVE_DIR under their catalog rootPrefix; see idea.log"
        }
        return libraries.map { fetcher.cacheDir(it) }
    }

    private fun settle(project: Project) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun assertRegistered(
        project: Project,
        cacheDirs: List<Path>,
    ) {
        val watched =
            AdditionalLibraryRootsProvider.EP_NAME.extensionList
                .filterIsInstance<LuaDefinitionLibraryProvider>()
                .flatMap { it.getRootsToWatch(project) }
                .map { it.toNioPath().toRealPath() }
                .toSet()
        check(watched == cacheDirs.map { it.toRealPath() }.toSet()) {
            "LuaDefinitionLibraryProvider watches $watched; expected $cacheDirs"
        }
    }
}
