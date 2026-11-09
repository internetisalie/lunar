package net.internetisalie.lunar.lang.path

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.nio.file.Files

/**
 * BUG-60: inside a search-path directory, a module resolves only to the file at its relative path,
 * so a bare `require("util")` does not land on an installed rock's nested `luassert/util.lua`.
 */
class SearchPathRequireTest : BasePlatformTestCase() {
    override fun tearDown() {
        try {
            LuaProjectSettings.getInstance(project).state.sourcePath = PathConfiguration.DEFAULT_SOURCE_PATH
        } finally {
            super.tearDown()
        }
    }

    fun testBareRequireDoesNotLandOnANestedFileInASearchPathDirectory() {
        val root = Files.createTempDirectory("lunar_bug60_tree")
        Files.createDirectories(root.resolve("luassert"))
        Files.writeString(root.resolve("luassert/util.lua"), "return {}")
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root)?.refresh(false, true)
        LuaProjectSettings.getInstance(project).state.sourcePath = "$root/?.lua"
        WriteAction.runAndWait<RuntimeException> {
            ProjectRootManagerEx
                .getInstanceEx(project)
                .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val bare =
            runReadActionBlocking { resolveModuleCandidates(project, "util").map { it.virtualFile.path }.toList() }
        val qualified =
            runReadActionBlocking {
                resolveModuleCandidates(
                    project,
                    "luassert.util",
                ).map { it.virtualFile.path }.toList()
            }

        assertTrue(
            "bare require must not reach luassert/util.lua: $bare",
            bare.none { it.endsWith("luassert/util.lua") },
        )
        assertTrue("qualified require still resolves: $qualified", qualified.any { it.endsWith("luassert/util.lua") })
    }

    private fun <T> runReadActionBlocking(action: () -> T): T =
        com.intellij.openapi.application.ReadAction
            .compute<T, RuntimeException>(action)
}
