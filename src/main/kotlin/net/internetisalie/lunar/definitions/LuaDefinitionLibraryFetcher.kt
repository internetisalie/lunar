package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.VfsUtil
import net.internetisalie.lunar.toolchain.provision.ArtifactPin
import net.internetisalie.lunar.toolchain.provision.ArtifactVerification
import net.internetisalie.lunar.toolchain.provision.LuaArchiveExtractor
import net.internetisalie.lunar.toolchain.provision.LuaArtifactDownloader
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Where a definition archive comes from. A seam so tests can supply the archive without a network
 * or a subclass — [LuaArtifactDownloader] is deliberately left final rather than opened for testing.
 */
fun interface LuaDefinitionArchiveSource {
    fun fetch(entry: LuaDefinitionEntry, indicator: ProgressIndicator): Path
}

/**
 * Resolves a catalog entry to an on-disk definition tree, downloading and extracting it once
 * (TARGET-08-03).
 *
 * The cache is **per user, not per project** (`<system>/lunar/definitions/<id>-<version>/`): two
 * projects enabling the same library share one tree, while the enable list stays per project.
 * Because `version` is the upstream commit SHA, a re-pinned entry lands in a different directory
 * and the old one is simply never consulted again.
 *
 * Performs blocking I/O — call only from a background task, never the EDT. Touches no PSI, and the
 * VFS only to refresh a freshly-extracted tree (see [verifyExtracted]).
 */
class LuaDefinitionLibraryFetcher(
    private val cacheRoot: Path = defaultCacheRoot(),
    private val source: LuaDefinitionArchiveSource = downloadingSource(),
) {

    /** Where [entry] lives once fetched. The directory need not exist. */
    fun cacheDir(entry: LuaDefinitionEntry): Path = cacheRoot.resolve("${entry.id}-${entry.version}")

    /** "Cached" means the directory exists and is non-empty — a half-extracted tree is not cached. */
    fun isCached(entry: LuaDefinitionEntry): Boolean {
        val dir = cacheDir(entry)
        return dir.isDirectory() && dir.listDirectoryEntries().isNotEmpty()
    }

    /**
     * Returns the cached tree for [entry], fetching it if absent, or **null** when the fetch fails.
     *
     * Null rather than an exception because failure is routine and non-fatal: TARGET-08-07 wants an
     * offline enable to leave the id in the persisted list — so a later online retry works — while
     * contributing no library root. The caller reports; this reports nothing to the user itself.
     *
     * A failed or empty extraction removes the partial directory, so [isCached] does not report a
     * tree that would index half a library — see [discard] for the one case that can defeat this.
     *
     * Cancellation is **not** a failure: [ProcessCanceledException] propagates untouched.
     */
    fun ensureCached(entry: LuaDefinitionEntry, indicator: ProgressIndicator): Path? {
        if (isCached(entry)) return cacheDir(entry)
        val target = cacheDir(entry)
        return try {
            val archive = source.fetch(entry, indicator)
            LuaArchiveExtractor.extract(archive, target, entry.rootPrefix, indicator)
            verifyExtracted(entry, target)
        } catch (cancellation: ProcessCanceledException) {
            // Never swallowed. The extractor's entryFilter calls checkCanceled, so a user cancelling
            // the progress lands here; treating it as a fetch failure would log a stack trace and
            // (once the caller balloons) tell them their own Cancel was an error.
            discard(target)
            throw cancellation
        } catch (failure: Exception) {
            LOG.warn("Failed to fetch definition library '${entry.id}': ${failure.message}", failure)
            discard(target)
            null
        }
    }

    /**
     * An extraction that produced nothing is a *failure*, and a loud one — it is the most likely
     * real-world fault here. `Decompressor` silently drops every entry that does not start with
     * [LuaDefinitionEntry.rootPrefix], so a stale prefix (one upstream force-push, or a hand-edited
     * re-pin) yields an empty tree with no exception at all. Returning null unlogged would leave
     * "fetch failed" in the UI and nothing whatsoever in `idea.log`.
     */
    private fun verifyExtracted(entry: LuaDefinitionEntry, target: Path): Path? {
        if (isCached(entry)) {
            // The VFS has not seen these files yet, and nothing downstream refreshes: the provider
            // resolves with `refreshIfNeeded = false` because it runs on the EDT, and
            // PlatformLibraryIndex.reload() rebuilds the index, not the VFS. Without this a
            // just-fetched library contributes no root until some unrelated refresh happens to
            // notice it. Safe here — this method only ever runs on the fetch background task.
            VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFileByIoFile(target.toFile(), true))
            return target
        }
        LOG.warn(
            "Definition library '${entry.id}' extracted no files: rootPrefix " +
                "'${entry.rootPrefix}' matched nothing in the archive. The pinned prefix is " +
                "probably stale for version '${entry.version}'.",
        )
        discard(target)
        return null
    }

    /**
     * Best-effort, but never silent. `deleteRecursively` can fail part-way on a locked entry and
     * leave a non-empty directory, which [isCached] would then report as a usable tree — so the
     * failure is logged rather than swallowed, and callers get the id to act on.
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun discard(dir: Path) {
        if (!dir.exists()) return
        runCatching { dir.deleteRecursively() }.onFailure { failure ->
            LOG.warn(
                "Could not fully remove the partial definition tree at $dir; a leftover " +
                    "non-empty directory will be treated as cached. Delete it by hand.",
                failure,
            )
        }
    }

    companion object {
        private val LOG = Logger.getInstance(LuaDefinitionLibraryFetcher::class.java)

        fun defaultCacheRoot(): Path = Path.of(PathManager.getSystemPath(), "lunar", "definitions")

        /**
         * The production source: the shared mirror-aware downloader, in ADVISORY mode. The URL's
         * commit SHA already pins the content and these stubs are never executed, so a pin mismatch
         * must warn rather than break every user on an upstream repack — see [ArtifactVerification].
         */
        private fun downloadingSource(): LuaDefinitionArchiveSource {
            val downloader = LuaArtifactDownloader()
            return LuaDefinitionArchiveSource { entry, indicator ->
                downloader.fetch(
                    ArtifactPin(entry.urls, entry.sha256, entry.size),
                    indicator,
                    ArtifactVerification.ADVISORY,
                )
            }
        }
    }
}
