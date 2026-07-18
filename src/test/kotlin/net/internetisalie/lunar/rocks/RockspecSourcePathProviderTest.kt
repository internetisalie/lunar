package net.internetisalie.lunar.rocks

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
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

    // MAINT-32-02: a read-lock caller (background resolution thread) gets degraded static patterns
    // and the bridge is NOT invoked under the lock; a prewarm is scheduled. (TC-03)
    fun testReadLockCallReturnsDegradedAndDefersBridge() {
        val rockspecFile = writeRockspec()
        RockspecSourcePathProvider.testDiscoverySeam = { listOf(DiscoveredRockspec(rockspecFile, "foo")) }
        RockspecSourcePathProvider.testForceReadLockGuard = true
        RockspecSourcePathProvider.invalidateCache(project)

        val before = RockspecBridge.BRIDGE_INVOCATIONS.get()
        val patterns = onPooledThreadUnderReadLock {
            PathConfiguration.getProjectSourcePathPatterns(project)
        }

        val staticPatterns = PathConfiguration.getStaticSourcePathPatterns(project).map { it.spec }.toSet()
        assertEquals(
            "read-lock call must return degraded static patterns only",
            staticPatterns,
            patterns.map { it.spec }.toSet(),
        )
        assertEquals("bridge must NOT run under the read lock", before, RockspecBridge.BRIDGE_INVOCATIONS.get())
    }

    // MAINT-32-02: N unresolved references in one read-lock pass schedule exactly ONE prewarm. (TC-04)
    fun testConcurrentReadLockCallsScheduleSinglePrewarm() {
        val rockspecFile = writeRockspec()
        RockspecSourcePathProvider.testDiscoverySeam = { listOf(DiscoveredRockspec(rockspecFile, "foo")) }
        RockspecSourcePathProvider.testForceReadLockGuard = true
        RockspecSourcePathProvider.invalidateCache(project)

        val before = RockspecBridge.BRIDGE_INVOCATIONS.get()
        val provider = RockspecSourcePathProvider.getInstance(project)
        onPooledThreadUnderReadLock {
            provider.derivedPatterns()
            provider.derivedPatterns()
            provider.derivedPatterns()
        }
        awaitPrewarm(rockspecFile)

        assertEquals(
            "exactly one prewarm job runs the bridge once for the single rockspec",
            before + 1,
            RockspecBridge.BRIDGE_INVOCATIONS.get(),
        )
    }

    // MAINT-32-02: after the off-lock prewarm completes, an off-lock read observes full patterns. (TC-05)
    fun testPrewarmYieldsFullDerivedPatternsOffLock() {
        val rockspecFile = writeRockspec()
        RockspecSourcePathProvider.testDiscoverySeam = { listOf(DiscoveredRockspec(rockspecFile, "foo")) }
        RockspecSourcePathProvider.testForceReadLockGuard = true
        RockspecSourcePathProvider.invalidateCache(project)

        val before = RockspecBridge.BRIDGE_INVOCATIONS.get()
        onPooledThreadUnderReadLock { RockspecSourcePathProvider.getInstance(project).derivedPatterns() }
        awaitPrewarm(rockspecFile)

        val rockspecDir = rockspecFile.parent.toString().replace('\\', '/')
        val expected = "$rockspecDir/src/?.lua"
        val full = onPooledThread { PathConfiguration.getProjectSourcePathPatterns(project) }
        assertTrue(
            "post-prewarm off-lock read must include the rockspec-derived root $expected; actual ${full.map { it.spec }}",
            full.any { it.spec == expected },
        )
        assertTrue("bridge must have run inside the prewarm", RockspecBridge.BRIDGE_INVOCATIONS.get() > before)
    }

    private fun writeRockspec(): java.nio.file.Path {
        val physicalDir = Files.createTempDirectory("lunar_rocks_readlock")
        val rockspecFile = physicalDir.resolve("foo-1.0-1.rockspec")
        Files.writeString(
            rockspecFile,
            """
            package = "foo"
            version = "1.0-1"
            build = {
                type = "builtin",
                modules = {
                    ["foo.bar"] = "src/foo/bar.lua"
                }
            }
            """.trimIndent(),
        )
        return rockspecFile
    }

    private fun <T> onPooledThread(body: () -> T): T =
        ApplicationManager.getApplication().executeOnPooledThread<T>(body).get()

    private fun <T> onPooledThreadUnderReadLock(body: () -> T): T =
        onPooledThread { ApplicationManager.getApplication().runReadAction(Computable { body() }) }

    @Suppress("UNUSED_PARAMETER")
    private fun awaitPrewarm(rockspecFile: java.nio.file.Path) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            if (RockspecSourcePathProvider.isPrewarmComplete(project)) return
            Thread.sleep(50)
        }
        fail("prewarm did not publish full patterns within 30s")
    }

    override fun tearDown() {
        try {
            RockspecSourcePathProvider.testDiscoverySeam = null
            RockspecSourcePathProvider.testForceReadLockGuard = false
            RockspecRuntimeTestSupport.reset(project)
        } finally {
            super.tearDown()
        }
    }
}