package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test

/**
 * ROCKS-09 TC #12: `LuaRocksTreeLocator.allProjectRockspecs` delegates to the discovery service and
 * returns the same project rockspecs (one per `rocks/<name>/`), proving the single-scanner contract.
 */
class LuaRocksTreeLocatorDelegationTest : IndexedBasePlatformTestCase() {

    private val kernelRockNames = listOf(
        "adt", "channels", "cmd", "meteor", "pipe",
        "platform", "ramdisk", "runtime", "ssdpd", "utils",
    )

    @Test
    fun testAllProjectRockspecsDelegatesToDiscovery() {
        for (name in kernelRockNames) {
            myFixture.addFileToProject(
                "rocks/$name/$name-1.0-1.rockspec",
                "package = \"$name\"\nversion = \"1.0-1\"\n",
            )
        }
        lateinit var captured: Pair<List<*>, List<*>>
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val viaLocator = LuaRocksTreeLocator.allProjectRockspecs(project)
                val viaService = LuaRockspecDiscoveryService.getInstance(project)
                    .discoverRockspecPaths().map { it.rockspec }
                captured = viaLocator to viaService
            }
        }
        val (delegated, discovered) = captured
        assertEquals(10, delegated.size)
        assertEquals("Locator must return exactly the discovery service paths", discovered, delegated)
    }
}
