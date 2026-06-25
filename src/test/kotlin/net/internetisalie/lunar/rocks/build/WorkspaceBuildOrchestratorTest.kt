package net.internetisalie.lunar.rocks.build

import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.rocks.DiscoveredRockspec
import net.internetisalie.lunar.rocks.RockspecData
import org.junit.Test
import java.nio.file.Path

class WorkspaceBuildOrchestratorTest : IndexedBasePlatformTestCase() {

    override fun tearDown() {
        try {
            WorkspaceBuildOrchestrator.testDiscoverySeam = null
            WorkspaceBuildOrchestrator.testBridgeReaderSeam = null
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testNormalizeDepName() {
        assertEquals("luafilesystem", WorkspaceBuildOrchestrator.normalizeDepName("luafilesystem >= 1.6.3"))
        assertEquals("lua", WorkspaceBuildOrchestrator.normalizeDepName("lua ~> 5.1"))
        assertEquals("adt", WorkspaceBuildOrchestrator.normalizeDepName("adt"))
        assertEquals("adt", WorkspaceBuildOrchestrator.normalizeDepName("adt >= 1.0, < 2.0"))
        assertEquals("foo", WorkspaceBuildOrchestrator.normalizeDepName("  foo  >=  1.0  "))
        assertNull(WorkspaceBuildOrchestrator.normalizeDepName("   "))
        assertNull(WorkspaceBuildOrchestrator.normalizeDepName(""))
    }

    @Test
    fun testUnparseableRockspecDropped() {
        // TC #7: A discovered rockspec whose RockspecBridge.read returns null is dropped
        val pathA = Path.of("a-1.0-1.rockspec")
        val pathB = Path.of("b-1.0-1.rockspec")

        WorkspaceBuildOrchestrator.testDiscoverySeam = {
            listOf(
                DiscoveredRockspec(pathA, "a"),
                DiscoveredRockspec(pathB, "b")
            )
        }

        WorkspaceBuildOrchestrator.testBridgeReaderSeam = { _, path ->
            if (path == pathA) {
                null // unparseable
            } else {
                RockspecData("b", "1.0-1", emptyList(), null, emptyMap(), emptyMap())
            }
        }

        val result = WorkspaceBuildOrchestrator.computeBuildOrder(project)
        assertTrue("Expected BuildPlan.Ordered", result is BuildPlan.Ordered)
        val ordered = (result as BuildPlan.Ordered).rocks
        assertEquals("Should drop the unparseable rock A and keep B", 1, ordered.size)
        assertEquals("b", ordered[0].packageName)
    }

    @Test
    fun testKernelFixtureBuildOrder() {
        // 10 Kernel/v0 rockspecs with a topo-sort setup
        val names = listOf(
            "adt", "channels", "cmd", "meteor", "pipe",
            "platform", "ramdisk", "runtime", "ssdpd", "utils"
        )

        WorkspaceBuildOrchestrator.testDiscoverySeam = {
            names.map { name -> DiscoveredRockspec(Path.of("rocks/$name/$name-1.0-1.rockspec"), name) }
        }

        WorkspaceBuildOrchestrator.testBridgeReaderSeam = { _, path ->
            val specName = path.parent.fileName.toString()
            val deps = when (specName) {
                "channels" -> listOf("pipe")
                "cmd" -> listOf("utils")
                "meteor" -> listOf("platform")
                "pipe" -> listOf("utils")
                "ramdisk" -> listOf("platform")
                "runtime" -> listOf("platform")
                "ssdpd" -> listOf("channels")
                "platform" -> listOf("utils")
                "utils" -> listOf("adt")
                else -> emptyList() // adt
            }
            RockspecData(specName, "1.0-1", deps, null, emptyMap(), emptyMap())
        }

        val result = WorkspaceBuildOrchestrator.computeBuildOrder(project)
        assertTrue("Expected BuildPlan.Ordered", result is BuildPlan.Ordered)
        val ordered = (result as BuildPlan.Ordered).rocks
        assertEquals(10, ordered.size)

        // Verify topological order constraints
        val indexByName = ordered.mapIndexed { idx, rock -> rock.packageName to idx }.toMap()

        fun assertDependencyOrder(dep: String, target: String) {
            val depIdx = indexByName[dep] ?: error("Missing dependency rock $dep")
            val targetIdx = indexByName[target] ?: error("Missing target rock $target")
            assertTrue("Dependency $dep (idx $depIdx) must be built before $target (idx $targetIdx)", depIdx < targetIdx)
        }

        assertDependencyOrder("pipe", "channels")
        assertDependencyOrder("utils", "cmd")
        assertDependencyOrder("platform", "meteor")
        assertDependencyOrder("utils", "pipe")
        assertDependencyOrder("platform", "ramdisk")
        assertDependencyOrder("platform", "runtime")
        assertDependencyOrder("channels", "ssdpd")
        assertDependencyOrder("utils", "platform")
        assertDependencyOrder("adt", "utils")
    }
}
