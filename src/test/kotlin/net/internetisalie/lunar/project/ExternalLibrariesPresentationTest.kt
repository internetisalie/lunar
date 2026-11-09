package net.internetisalie.lunar.project

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.path.PathConfiguration
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Files

/** BUG-60: External Libraries lists the standard library once and names each search-path directory. */
class ExternalLibrariesPresentationTest : BasePlatformTestCase() {
    private val settings get() = LuaProjectSettings.getInstance(project)

    override fun tearDown() {
        try {
            EdtTestUtil.runInEdtAndWait<RuntimeException> { settings.setTargetAndNotify(Target.default()) }
            settings.state.sourcePath = PathConfiguration.DEFAULT_SOURCE_PATH
        } finally {
            super.tearDown()
        }
    }

    fun testStandardLibraryIsListedOnce() {
        setTarget("5.1")
        val stdlib = RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())
        assertNotNull(stdlib)

        val holders =
            runReadAction {
                AdditionalLibraryRootsProvider.EP_NAME.extensionList
                    .flatMap { it.getAdditionalProjectLibraries(project) }
                    .filter { stdlib in it.sourceRoots }
            }

        assertEquals(1, holders.size)
    }

    fun testSearchPathDirectoriesWithTheSameNameAreToldApart() {
        val first = Files.createDirectories(Files.createTempDirectory("lunar_bug60_a").resolve("5.4"))
        val second = Files.createDirectories(Files.createTempDirectory("lunar_bug60_b").resolve("5.4"))
        listOf(first, second).forEach { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(it) }
        settings.state.sourcePath = "$first/?.lua;$second/?.lua"

        val names =
            runReadAction { PlatformLibraryProvider().getAdditionalProjectLibraries(project) }
                .map { (it as PlatformLibraryProvider.SearchPathLibrary).presentableText }

        assertEquals(2, names.toSet().size)
        assertTrue(names.all { it.endsWith("5.4") })
    }

    fun testDefaultSearchPathFollowsTheTargetVersion() {
        setTarget("5.1")
        settings.state.sourcePath = PathConfiguration.DEFAULT_SOURCE_PATH

        val leading = PathConfiguration.getStaticSourcePathPatterns(project).map { it.leadingPath }

        assertTrue(leading.toString(), "/usr/local/share/lua/5.1/" in leading)
        assertTrue(leading.toString(), leading.none { "5.4" in it })
    }

    private fun setTarget(version: String) =
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry(version, "lua-$version")))
        }
}
