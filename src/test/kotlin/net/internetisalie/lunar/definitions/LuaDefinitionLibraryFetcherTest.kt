package net.internetisalie.lunar.definitions

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import net.internetisalie.lunar.toolchain.provision.LuaProvisionException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TARGET-08-03. Pure JVM, no network: the download is a spy, per the engineering contract's
 * light-fixture rule. The live fetch path is covered by the DR-03 spike and the VNC DoD instead.
 */
@RunWith(JUnit4::class)
class LuaDefinitionLibraryFetcherTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val indicator: ProgressIndicator = EmptyProgressIndicator()

    private fun entry(id: String = "busted") =
        LuaDefinitionEntry(
            id = id,
            displayName = "Busted",
            version = "5ed85d0e",
            urls = listOf("https://example.invalid/$id.tar.gz"),
            sha256 = "c33499e7",
            size = 2040,
            rootPrefix = "$id-5ed85d0e/library",
            license = "MIT",
            attributionUrl = "https://example.invalid/$id",
            requires = emptyList(),
        )

    /** Records whether the archive source was consulted at all. */
    private class SpySource(
        private val onFetch: () -> Path,
    ) : LuaDefinitionArchiveSource {
        var calls = 0
            private set

        override fun fetch(
            entry: LuaDefinitionEntry,
            indicator: ProgressIndicator,
        ): Path {
            calls++
            return onFetch()
        }
    }

    /** TC 4 — a pre-seeded cache is reused with zero downloader calls. */
    @Test
    fun preSeededCacheIsReusedWithoutDownloading() {
        val root = temp.newFolder("cache").toPath()
        val spy = SpySource { error("must not download") }
        val fetcher = LuaDefinitionLibraryFetcher(root, spy)
        val seeded = fetcher.cacheDir(entry()).also { it.createDirectories() }
        seeded.resolve("busted.lua").writeText("---@meta\n")

        assertTrue(fetcher.isCached(entry()))
        assertEquals(seeded, fetcher.ensureCached(entry(), indicator))
        assertEquals(0, spy.calls)
    }

    /** TC 8 — a failing download yields null, leaves no cache dir, and does not throw. */
    @Test
    fun failedDownloadYieldsNullAndNoCacheDir() {
        val root = temp.newFolder("cache").toPath()
        val spy = SpySource { throw LuaProvisionException("All download mirrors failed") }
        val fetcher = LuaDefinitionLibraryFetcher(root, spy)

        assertNull(fetcher.ensureCached(entry(), indicator))
        assertEquals(1, spy.calls)
        assertFalse(fetcher.cacheDir(entry()).exists(), "a failed fetch must leave no cache dir")
        assertFalse(fetcher.isCached(entry()))
    }

    /** An empty directory is not a cache hit — it would otherwise index half a library. */
    @Test
    fun emptyDirectoryIsNotCached() {
        val root = temp.newFolder("cache").toPath()
        val fetcher = LuaDefinitionLibraryFetcher(root, SpySource { error("unused") })
        fetcher.cacheDir(entry()).createDirectories()
        assertFalse(fetcher.isCached(entry()))
    }

    /**
     * Cleanup, tested for real. An earlier version of this seeded nothing and asserted a directory
     * that was never created did not exist — `discard()` could be deleted outright and it stayed
     * green, because `Decompressor` does not create the output dir until an entry survives.
     * Here the source writes a partial tree and *then* throws, so only cleanup can satisfy it.
     */
    @Test
    fun extractionFailureDiscardsPartialTree() {
        val root = temp.newFolder("cache").toPath()
        val partial = root.resolve("busted-5ed85d0e")
        val fetcher =
            LuaDefinitionLibraryFetcher(root) { _, _ ->
                partial
                    .resolve("library")
                    .createDirectories()
                    .resolve("partial.lua")
                    .writeText("---@meta\n")
                throw LuaProvisionException("archive truncated mid-extract")
            }

        assertNull(fetcher.ensureCached(entry(), indicator))
        assertFalse(partial.exists(), "the partial tree must be removed, not left for isCached")
    }

    /**
     * An archive that extracts nothing — the likeliest real fault, a stale `rootPrefix` — must fail
     * loudly rather than return null silently, and must leave no empty directory behind.
     */
    @Test
    fun emptyExtractionIsAFailureAndLeavesNothing() {
        val root = temp.newFolder("cache").toPath()
        val fetcher =
            LuaDefinitionLibraryFetcher(root) { _, _ ->
                temp.newFile("empty.tar.gz").toPath().also { it.writeText("not a tarball at all") }
            }

        assertNull(fetcher.ensureCached(entry(), indicator))
        assertFalse(fetcher.cacheDir(entry()).exists())
    }

    /** Cancellation is not a fetch failure — it must propagate untouched (never swallowed). */
    @Test
    fun cancellationPropagates() {
        val root = temp.newFolder("cache").toPath()
        val fetcher = LuaDefinitionLibraryFetcher(root) { _, _ -> throw ProcessCanceledException() }
        assertFailsWith<ProcessCanceledException> { fetcher.ensureCached(entry(), indicator) }
    }

    /** The cache is keyed by id AND version, so a re-pinned entry never reuses the old tree. */
    @Test
    fun cacheDirIsKeyedByIdAndVersion() {
        val root = temp.newFolder("cache").toPath()
        val fetcher = LuaDefinitionLibraryFetcher(root, SpySource { error("unused") })
        val repinned = entry().copy(version = "ffffffff")
        // assertEquals, not endsWith: this also catches a wrong cache ROOT.
        assertEquals(root.resolve("busted-5ed85d0e"), fetcher.cacheDir(entry()))
        assertEquals(root.resolve("busted-ffffffff"), fetcher.cacheDir(repinned))
    }
}
