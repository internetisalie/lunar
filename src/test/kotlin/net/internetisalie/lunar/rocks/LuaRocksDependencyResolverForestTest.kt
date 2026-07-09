package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

/**
 * ROCKS-09 TC #7 / #8: [LuaRocksDependencyResolver.resolveAll] resolves one [DependencyNode] root
 * per discovered rockspec (a forest), and a missing dependency is flagged per root.
 *
 * Uses a real on-disk content root so the bundled `rockspec.lua` bridge subprocess can read each
 * rockspec (a `temp://` light fixture is not subprocess-readable). Requires a `lua` interpreter on
 * `PATH`; if absent the bridge returns null and `resolveAll` yields no roots — the assertions below
 * gate on the rockspec count, matching the "unparseable rockspec contributes no root" rule.
 */
class LuaRocksDependencyResolverForestTest : BasePlatformTestCase() {

    private lateinit var tempFixture: TempDirTestFixture
    private var contentRoot: VirtualFile? = null

    override fun setUp() {
        super.setUp()
        tempFixture = TempDirTestFixtureImpl()
        tempFixture.setUp()
        // TOOLING-05 Phase 3: RockspecBridge.read resolves the runtime via the resolver, so bind a
        // real interpreter (mirrors TOOLING-01 PATH discovery) for the bridge to launch.
        RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
    }

    override fun tearDown() {
        try {
            RockspecRuntimeTestSupport.reset(project)
            contentRoot?.let { PsiTestUtil.removeContentEntry(module, it) }
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            try {
                tempFixture.tearDown()
            } catch (e: Throwable) {
                addSuppressedException(e)
            } finally {
                super.tearDown()
            }
        }
    }

    private fun registerContentRoot() {
        val root = tempFixture.getFile("") ?: error("No temp dir root")
        WriteAction.runAndWait<Throwable> { PsiTestUtil.addContentRoot(module, root) }
        contentRoot = root
    }

    private fun luaAvailable(): Boolean =
        System.getenv("PATH").orEmpty().split(':').any { java.io.File(it, "lua").canExecute() }

    fun testForestHasOneRootPerDiscoveredRock() {
        val names = listOf("adt", "channels", "cmd", "meteor", "pipe", "platform", "ramdisk", "runtime", "ssdpd", "utils")
        for (name in names) {
            tempFixture.createFile(
                "rocks/$name/$name-1.0-1.rockspec",
                "package = \"$name\"\nversion = \"1.0-1\"\nsource = { url = \"x\" }\ndependencies = { \"lua >= 5.1\" }\n",
            )
        }
        registerContentRoot()

        val discoveredCount = LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().size
        assertEquals("Discovery must find all 10 rockspecs", 10, discoveredCount)

        if (!luaAvailable()) return
        val roots = LuaRocksDependencyResolver.resolveAll(project)
        assertEquals("One resolved root per discovered rock", 10, roots.size)
        assertTrue("Every root is a source root (non-transitive)", roots.all { !it.isTransitive })
        assertEquals(names.toSet(), roots.map { it.packageName }.toSet())
    }

    fun testMissingDependencyFlaggedPerRoot() {
        tempFixture.createFile(
            "foo-scm-1.rockspec",
            "package = \"foo\"\nversion = \"scm-1\"\nsource = { url = \"x\" }\ndependencies = { \"ghost\" }\n",
        )
        registerContentRoot()

        assertEquals(1, LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().size)
        if (!luaAvailable()) return
        val roots = LuaRocksDependencyResolver.resolveAll(project)
        assertEquals(1, roots.size)
        val root = roots.single()
        assertEquals("foo", root.packageName)
        val ghost = root.children.singleOrNull { it.packageName == "ghost" }
            ?: error("Expected a 'ghost' child node, got ${root.children.map { it.packageName }}")
        assertNull("Unresolved (not installed) dependency must be a missing root", ghost.resolvedVersion)
    }
}
