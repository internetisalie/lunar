package net.internetisalie.lunar.toolchain.provision

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Network-free tests for [LuaArtifactDownloader] (design §2.6, §3.4). Every "download" source
 * is a `file://` URL pointing at a local fixture, so `HttpRequests.saveToFile` copies the bytes
 * locally — exercising the real fetch → size → hash → cache → atomic-move path offline. Covers
 * TC 4 (cache re-verification, mirror fallthrough, both-fail aggregation).
 *
 * Inherits [BasePlatformTestCase] only to obtain a live platform application (the download +
 * progress APIs require `ProgressManager`); it uses no `myFixture` PSI/VFS and an injected
 * `cacheDir` temp dir, so nothing here touches the EDT-only surface.
 */
class LuaArtifactDownloaderTest : BasePlatformTestCase() {
    private val indicator by lazy { EmptyProgressIndicator() }

    private fun sha256Of(file: Path): String =
        Files.asByteSource(file.toFile()).hash(Hashing.sha256()).toString()

    private fun writePayload(dir: Path, name: String, content: String): Triple<String, String, Long> {
        val file = dir.resolve(name)
        file.writeText(content)
        return Triple(file.toUri().toString(), sha256Of(file), file.fileSize())
    }

    fun testFetchesFromSingleSourceVerifiesAndCaches() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        val (url, sha, size) = writePayload(fixtures, "lua-5.4.8.tar.gz", "lua-source-bytes")

        val result = LuaArtifactDownloader(cacheDir).fetch(listOf(url), sha, size, indicator)

        assertEquals(cacheDir.resolve("lua-5.4.8.tar.gz"), result)
        assertEquals("lua-source-bytes", result.readText())
        assertFalse("the .part file must be moved away", cacheDir.resolve("lua-5.4.8.tar.gz.part").exists())
    }

    fun testReturnsCachedFileWhenHashMatches() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        val (url, sha, size) = writePayload(fixtures, "artifact.zip", "cached-bytes")
        cacheDir.resolve("artifact.zip").writeText("cached-bytes")

        val result = LuaArtifactDownloader(cacheDir).fetch(listOf(url), sha, size, indicator)

        assertEquals(cacheDir.resolve("artifact.zip"), result)
        assertEquals("cached-bytes", result.readText())
    }

    /** TC 4: a cached file whose SHA no longer matches the pin is deleted and re-fetched. */
    fun testDeletesStaleCacheAndReFetches() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        val (url, sha, size) = writePayload(fixtures, "artifact.zip", "fresh-correct-bytes")
        cacheDir.resolve("artifact.zip").writeText("stale-tampered-bytes")

        val result = LuaArtifactDownloader(cacheDir).fetch(listOf(url), sha, size, indicator)

        assertEquals("fresh-correct-bytes", result.readText())
    }

    /** TC 4: a first-source mismatch falls through to the mirror. */
    fun testFirstSourceMismatchFallsToMirror() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        val good = writePayload(fixtures, "good.tar.gz", "the-correct-payload")
        val badFile = fixtures.resolve("bad.tar.gz")
        badFile.writeText("wrong-payload-different-hash")

        val result = LuaArtifactDownloader(cacheDir).fetch(
            listOf(badFile.toUri().toString(), good.first),
            good.second,
            good.third,
            indicator,
        )

        assertEquals("the-correct-payload", result.readText())
    }

    /** TC 4: every mirror failing aborts with ALL errors listed. */
    fun testAllMirrorsFailingListsEveryError() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        val badA = fixtures.resolve("mirrorA.tar.gz").also { it.writeText("payload-a") }
        val badB = fixtures.resolve("mirrorB.tar.gz").also { it.writeText("payload-b") }
        val impossibleSha = "0".repeat(64)

        val failure = runCatching {
            LuaArtifactDownloader(cacheDir).fetch(
                listOf(badA.toUri().toString(), badB.toUri().toString()),
                impossibleSha,
                9L,
                indicator,
            )
        }.exceptionOrNull()

        assertTrue("both mirrors failing must raise LuaProvisionException", failure is LuaProvisionException)
        val message = failure?.message.orEmpty()
        assertTrue("aggregated error must name mirror A: $message", message.contains("mirrorA.tar.gz"))
        assertTrue("aggregated error must name mirror B: $message", message.contains("mirrorB.tar.gz"))
    }

    fun testSourceForgeDownloadSegmentUsesSecondToLastAsCacheKey() {
        val fixtures = createTempDirectory("lunar-dl-src")
        val cacheDir = createTempDirectory("lunar-dl-cache")
        // Emulate the SourceForge shape `…/files/{ver}/{group}/{file}/download` on disk: the real
        // bytes live in a file literally named `download` inside a dir named for the file.
        val fileDir = fixtures.resolve("files/3.1/win64/luarocks-3.1-win64.zip")
        fileDir.createDirectories()
        val (url, sha, size) = writePayload(fileDir, "download", "rocks-win-bytes")

        val result = LuaArtifactDownloader(cacheDir).fetch(listOf(url), sha, size, indicator)

        assertEquals(cacheDir.resolve("luarocks-3.1-win64.zip"), result)
    }
}
