package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test

/**
 * Light-fixture tests for [LuaRockspecDiscoveryService] over a `Kernel/v0`-shaped fixture.
 *
 * Covers ROCKS-09 TC #1 (10 rockspecs, no root), #3 (excluded dirs), #5 (one entry per file),
 * #6 (cached identity), and the Phase-0 `FilenameIndex` spike (ROCKS-09-00-01).
 */
class LuaRockspecDiscoveryServiceTest : IndexedBasePlatformTestCase() {

    private val kernelRockNames = listOf(
        "adt", "channels", "cmd", "meteor", "pipe",
        "platform", "ramdisk", "runtime", "ssdpd", "utils",
    )

    private fun addKernelFixture() {
        for (name in kernelRockNames) {
            myFixture.addFileToProject(
                "rocks/$name/$name-1.0-1.rockspec",
                "package = \"$name\"\nversion = \"1.0-1\"\n",
            )
        }
    }

    private fun <T> inEdtRead(block: () -> T): T {
        lateinit var result: () -> T
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val value = runReadAction(block)
            result = { value }
        }
        return result()
    }

    private fun discover(): List<DiscoveredRockspec> =
        inEdtRead { LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths() }

    // --------------------------------------------------- Phase-0 spike (ROCKS-09-00-01)

    @Test
    fun testFilenameIndexEnumeratesKernelRockspecs() {
        addKernelFixture()
        val found = inEdtRead {
            FilenameIndex.getAllFilesByExt(project, "rockspec", GlobalSearchScope.projectScope(project))
        }
        assertEquals("FilenameIndex must enumerate all 10 Kernel/v0 rockspecs", 10, found.size)
    }

    // --------------------------------------------------- TC #1

    @Test
    fun testDiscoversAllKernelRocksWithNoRootRockspec() {
        addKernelFixture()
        val discovered = discover()
        assertEquals(10, discovered.size)
        val relativeShapes = discovered.map { it.rockspec.toString().replace('\\', '/') }
        for (name in kernelRockNames) {
            assertTrue(
                "Expected a discovered path under rocks/$name/, got $relativeShapes",
                relativeShapes.any { it.endsWith("rocks/$name/$name-1.0-1.rockspec") },
            )
        }
    }

    // --------------------------------------------------- TC #3

    @Test
    fun testExcludedDirectoriesAreNotDiscovered() {
        myFixture.addFileToProject("build/b-1.0-1.rockspec", "package = \"b\"\n")
        myFixture.addFileToProject("build-5.4/c-1.0-1.rockspec", "package = \"c\"\n")
        myFixture.addFileToProject("output/o-1.0-1.rockspec", "package = \"o\"\n")
        myFixture.addFileToProject("thirdparty/vendored/t-1.0-1.rockspec", "package = \"t\"\n")
        myFixture.addFileToProject(".luarocks/l-1.0-1.rockspec", "package = \"l\"\n")
        assertTrue("All five excluded rockspecs must be filtered out", discover().isEmpty())
    }

    // --------------------------------------------------- TC #5

    @Test
    fun testOneEntryPerDiscoveredRockspec() {
        addKernelFixture()
        val discovered = discover()
        assertEquals(10, discovered.size)
        val distinctPaths = discovered.map { it.rockspec }.distinct()
        assertEquals("Each discovered rockspec has a distinct path", 10, distinctPaths.size)
    }

    // --------------------------------------------------- TC #6

    @Test
    fun testRepeatedCallReturnsCachedIdentity() {
        addKernelFixture()
        val service = LuaRockspecDiscoveryService.getInstance(project)
        val (first, second) = inEdtRead {
            service.discoverRockspecPaths() to service.discoverRockspecPaths()
        }
        assertSame("Repeated discovery with no edits must reuse the cached list", first, second)
    }

    // --------------------------------------------------- TC #10 (exclude glob override)

    @Test
    fun testExcludeGlobOverride() {
        myFixture.addFileToProject("a/a-1.0-1.rockspec", "package = \"a\"\n")
        myFixture.addFileToProject("vendor/v-1.0-1.rockspec", "package = \"v\"\n")
        LuaProjectSettings.getInstance(project).state.rockspecExcludeGlobs = mutableListOf("vendor/**")
        val discovered = discover()
        assertEquals(1, discovered.size)
        assertTrue(discovered.single().rockspec.toString().replace('\\', '/').endsWith("a/a-1.0-1.rockspec"))
    }

    // --------------------------------------------------- TC #11 (include allow-list override)

    @Test
    fun testIncludeGlobAllowList() {
        myFixture.addFileToProject("a/a-1.0-1.rockspec", "package = \"a\"\n")
        myFixture.addFileToProject("vendor/v-1.0-1.rockspec", "package = \"v\"\n")
        LuaProjectSettings.getInstance(project).state.rockspecIncludeGlobs = mutableListOf("a/**")
        val discovered = discover()
        assertEquals(1, discovered.size)
        assertTrue(discovered.single().rockspec.toString().replace('\\', '/').endsWith("a/a-1.0-1.rockspec"))
    }
}
