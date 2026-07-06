package net.internetisalie.lunar.toolchain

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.util.io.Decompressor
import com.intellij.util.io.HttpRequests
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TOOLING-00-05 spike (design §2.5): proves the platform download-infrastructure classes are on the
 * plugin's test/compile classpath and link at runtime, and fixes the bundled version-feed JSON format.
 *
 * Plain JUnit, hermetic — no network. Fixture archives live under `src/test/resources/toolchain/`
 * (a `.tar.gz` with one 0755 file, a `.zip` with content, and a payload file whose sha256 is pinned
 * below). Throwaway: TOOLING-04 productionizes the feed + download/verify/extract skeleton.
 *
 * The four probed APIs — `HttpRequests`, `Decompressor.Tar`, `Decompressor.Zip(...).withZipExtensions()`,
 * and Guava `Hashing.sha256()` — compile (classpath presence) and execute (runtime linkage).
 *
 * JSON parser recorded: the platform-bundled `com.google.gson.Gson` (already used in `src/main` —
 * `net.internetisalie.lunar.rocks.RockspecBridge`).
 */
class LuaProvisioningClasspathSpikeTest {
    private val fixtureDir = "toolchain"

    /** Precomputed sha256 of the committed `hashme.txt` fixture (recomputable via `sha256sum`). */
    private val hashmeSha256 = "69b30e92143264aa140001cf7af42e67642cf645e24cafbb27f1c3505cbbaa39"

    private fun resource(name: String): Path {
        val resourceUrl = javaClass.classLoader.getResource("$fixtureDir/$name")
            ?: error("Missing test fixture: $fixtureDir/$name")
        return File(resourceUrl.toURI()).toPath()
    }

    @Test
    fun httpRequestsBuilderResolvesAndConstructs() {
        // Build the request builder only — no connect() — to prove HttpRequests is on the classpath.
        val requestBuilder = HttpRequests.request("https://example.invalid")

        assertNotNull(requestBuilder, "HttpRequests.request must return a non-null builder")
    }

    @Test
    fun decompressorTarPreservesExecBit() {
        val tempDir: Path = createTempDirectory("lunar-spike-tar")
        Decompressor.Tar(resource("fixture.tar.gz")).extract(tempDir)

        val extracted = tempDir.resolve("spike-exec.sh").toFile()
        assertTrue(extracted.exists(), "Tar extraction must produce spike-exec.sh")
        assertTrue(extracted.canExecute(), "Tar extraction must preserve the 0755 exec bit")
    }

    @Test
    fun decompressorZipExtractsContent() {
        val tempDir: Path = createTempDirectory("lunar-spike-zip")
        Decompressor.Zip(resource("fixture.zip")).withZipExtensions().extract(tempDir)

        val extracted = tempDir.resolve("spike-content.txt").toFile()
        assertTrue(extracted.exists(), "Zip extraction must produce spike-content.txt")
        assertEquals("lunar-spike-zip-content", extracted.readText().trim())
    }

    @Test
    fun guavaHashingComputesSha256() {
        val digest = Files.asByteSource(resource("hashme.txt").toFile()).hash(Hashing.sha256()).toString()

        assertEquals(hashmeSha256, digest, "Guava Hashing.sha256() must equal the precomputed pin")
    }

    @Test
    fun feedSampleParsesWithRequiredFieldsPerItem() {
        // The feed ships in src/main/resources, so on the test classpath it is packaged inside the
        // composed jar — read it as a stream (a jar: URL is not a hierarchical File).
        val feedText = javaClass.classLoader.getResourceAsStream("toolchain/toolchain-feed.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: error("Missing feed resource: toolchain/toolchain-feed.json")

        val feed = Gson().fromJson(feedText, JsonObject::class.java)
        assertEquals(1, feed.get("feedVersion").asInt, "feedVersion must be 1")
        assertTrue(feed.has("aliases"), "feed must carry an aliases map")

        val items = feed.getAsJsonArray("items")
        assertTrue(items.size() >= 4, "the committed sample must carry at least the four design §2.5 items")

        // Fields required for all package types.
        val universalFields = listOf("kind", "version", "os", "arch", "strategy", "url", "size", "packageType", "rootPrefix")
        val allowedStrategies = setOf("SOURCE_BUILD", "RELEASE_BINARY", "LUAROCKS_INSTALL")
        val allowedPackageTypes = setOf("tar.gz", "zip", "git")

        items.forEach { element ->
            val item = element.asJsonObject
            universalFields.forEach { field ->
                assertTrue(item.has(field), "each item must carry the '$field' field")
            }
            assertTrue(
                item.get("strategy").asString in allowedStrategies,
                "strategy must be one of $allowedStrategies",
            )
            val packageType = item.get("packageType").asString
            assertTrue(
                packageType in allowedPackageTypes,
                "packageType must be one of $allowedPackageTypes",
            )
            // git-type entries use gitRef instead of sha256 (TOOLING-00-03 format extension).
            if (packageType == "git") {
                assertTrue(item.has("gitRef"), "git-type item must carry 'gitRef'")
            } else {
                assertTrue(item.has("sha256"), "non-git item must carry 'sha256'")
            }
        }
    }
}
