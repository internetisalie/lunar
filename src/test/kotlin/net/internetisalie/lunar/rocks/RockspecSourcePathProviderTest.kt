package net.internetisalie.lunar.rocks

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.lang.path.SourcePathPattern
import java.nio.file.Files

class RockspecSourcePathProviderTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // TOOLING-05 Phase 3: RockspecBridge.read resolves the runtime via the resolver, so bind a
        // real interpreter (mirrors TOOLING-01 PATH discovery) for the bridge to launch.
        RockspecRuntimeTestSupport.registerRealLuaRuntime(project)
    }

    fun testRockspecPathDerivationAndInvalidation() {
        // Create a real physical file so RockspecBridge.read (Lua process) can read it
        val physicalDir = Files.createTempDirectory("lunar_rocks_test")
        val rockspecFile = physicalDir.resolve("foo-1.0-1.rockspec")
        Files.writeString(rockspecFile, """
            package = "foo"
            version = "1.0-1"
            build = {
                type = "builtin",
                modules = {
                    ["foo.bar"] = "src/foo/bar.lua"
                }
            }
        """.trimIndent())

        // Create a TEST-ONLY discovery stub
        RockspecSourcePathProvider.testDiscoverySeam = { _ ->
            listOf(DiscoveredRockspec(rockspecFile, "foo"))
        }
        RockspecSourcePathProvider.invalidateCache(project)

        // Must run off-EDT to compute
        val patterns = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread<List<SourcePathPattern>> {
            PathConfiguration.getProjectSourcePathPatterns(project)
        }.get()

        val rockspecDirPath = physicalDir.toString().replace('\\', '/')
        
        val expectedPatternSpec = "$rockspecDirPath/src/?.lua"
        assertTrue(
            "Patterns should include \$expectedPatternSpec. Actual: \${patterns.map { it.spec }}",
            patterns.any { it.spec == expectedPatternSpec }
        )


        // TC #9: Edit invalidates
        Files.writeString(rockspecFile, """
            package = "foo"
            version = "1.0-1"
            build = {
                type = "builtin",
                modules = {
                    ["foo.bar"] = "src/foo/bar.lua",
                    ["foo.baz"] = "lib/foo/baz.lua"
                }
            }
        """.trimIndent())

        // Must commit all documents so PsiModificationTracker ticks
        com.intellij.openapi.application.runWriteAction {
            myFixture.addFileToProject("dummy.lua", "-- edit")
        }

        // Check invalidation
        val newPatterns = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread<List<SourcePathPattern>> {
            PathConfiguration.getProjectSourcePathPatterns(project)
        }.get()
        
        val newExpectedPatternSpec = "$rockspecDirPath/lib/?.lua"
        assertTrue(
            "New patterns should include \$newExpectedPatternSpec. Actual: \${newPatterns.map { it.spec }}",
            newPatterns.any { it.spec == newExpectedPatternSpec }
        )
    }

    override fun tearDown() {
        try {
            RockspecSourcePathProvider.testDiscoverySeam = null
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }
}