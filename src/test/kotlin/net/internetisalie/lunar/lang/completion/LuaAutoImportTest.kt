package net.internetisalie.lunar.lang.completion

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.path.LuaModulePathResolver
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for the COMP-03-03 auto-import helper classes:
 * path resolution, export-style detection, name resolution and deduplication.
 */
@RunWith(JUnit4::class)
class LuaAutoImportTest : IndexedBasePlatformTestCase() {

    /** Point the source path at the directory holding [file] so the resolver can match it. */
    private fun configureSourcePathFor(file: VirtualFile) {
        val dir = file.parent.path
        LuaProjectSettings.getInstance(project).state.sourcePath = "$dir/?.lua;$dir/?/init.lua"
    }

    // --- LuaModulePathResolver ------------------------------------------------

    @Test
    fun testResolveNestedModulePath() {
        val target = myFixture.addFileToProject("net/http.lua", "return {}").virtualFile
        configureSourcePathFor(myFixture.addFileToProject("main.lua", "").virtualFile)

        val module = runReadAction { LuaModulePathResolver().resolve(target, project) }
        assertEquals("net.http", module)
    }

    @Test
    fun testResolveInitLuaNormalization() {
        // TC-03-05: foo/init.lua must resolve to "foo", not "foo.init".
        val target = myFixture.addFileToProject("foo/init.lua", "return {}").virtualFile
        configureSourcePathFor(myFixture.addFileToProject("main.lua", "").virtualFile)

        val module = runReadAction { LuaModulePathResolver().resolve(target, project) }
        assertEquals("foo", module)
    }

    @Test
    fun testResolveTopLevelModule() {
        val target = myFixture.addFileToProject("json.lua", "return {}").virtualFile
        configureSourcePathFor(myFixture.addFileToProject("main.lua", "").virtualFile)

        val module = runReadAction { LuaModulePathResolver().resolve(target, project) }
        assertEquals("json", module)
    }

    // --- LuaExportStyleDetector ----------------------------------------------

    @Test
    fun testDetectReturnStyle() {
        val target = myFixture.addFileToProject("mod_a.lua", "local M = {}\nreturn M").virtualFile
        val style = runReadAction { LuaExportStyleDetector().detect(target, project) }
        assertEquals(LuaExportStyle.RETURN_STYLE, style)
    }

    @Test
    fun testDetectGlobalStyle() {
        val target = myFixture.addFileToProject("mod_b.lua", "function doThing() end").virtualFile
        val style = runReadAction { LuaExportStyleDetector().detect(target, project) }
        assertEquals(LuaExportStyle.GLOBAL_STYLE, style)
    }

    // --- LuaImportNameResolver -----------------------------------------------

    @Test
    fun testNameFromClassAnnotation() {
        val target = myFixture.addFileToProject(
            "builder.lua",
            """
            ---@class Builder
            local Builder = {}
            return Builder
            """.trimIndent(),
        ).virtualFile
        val current = myFixture.configureByText("main.lua", "") as net.internetisalie.lunar.lang.psi.LuaFile

        val name = runReadAction {
            LuaImportNameResolver().resolve(target, LuaExportStyle.RETURN_STYLE, current, project)
        }
        assertEquals("Builder", name)
    }

    @Test
    fun testNameFromFilenameSanitized() {
        val target = myFixture.addFileToProject("json-utils.lua", "return {}").virtualFile
        val current = myFixture.configureByText("main.lua", "") as net.internetisalie.lunar.lang.psi.LuaFile

        val name = runReadAction {
            LuaImportNameResolver().resolve(target, LuaExportStyle.RETURN_STYLE, current, project)
        }
        assertEquals("json_utils", name)
    }

    @Test
    fun testNameGlobalStyleHasNoBinding() {
        val target = myFixture.addFileToProject("globals.lua", "function g() end").virtualFile
        val current = myFixture.configureByText("main.lua", "") as net.internetisalie.lunar.lang.psi.LuaFile

        val name = runReadAction {
            LuaImportNameResolver().resolve(target, LuaExportStyle.GLOBAL_STYLE, current, project)
        }
        assertNull(name)
    }

    @Test
    fun testNameConflictGetsSuffix() {
        val target = myFixture.addFileToProject("config.lua", "return {}").virtualFile
        val current = myFixture.configureByText(
            "main.lua",
            "local config = 1",
        ) as net.internetisalie.lunar.lang.psi.LuaFile

        val name = runReadAction {
            LuaImportNameResolver().resolve(target, LuaExportStyle.RETURN_STYLE, current, project)
        }
        assertEquals("config2", name)
    }

    // --- LuaDeduplicationChecker ---------------------------------------------

    @Test
    fun testDuplicateDetectedDoubleQuotes() {
        val file = myFixture.configureByText(
            "main.lua",
            """local m = require("net.http")""",
        ) as net.internetisalie.lunar.lang.psi.LuaFile
        assertTrue(runReadAction { LuaDeduplicationChecker.isAlreadyRequired(file, "net.http") })
    }

    @Test
    fun testNoDuplicateForDifferentPath() {
        val file = myFixture.configureByText(
            "main.lua",
            """local m = require("net.http")""",
        ) as net.internetisalie.lunar.lang.psi.LuaFile
        assertFalse(runReadAction { LuaDeduplicationChecker.isAlreadyRequired(file, "net.ws") })
    }

    @Test
    fun testDuplicateDetectedNoParenForm() {
        val file = myFixture.configureByText(
            "main.lua",
            """require "json"""",
        ) as net.internetisalie.lunar.lang.psi.LuaFile
        assertTrue(runReadAction { LuaDeduplicationChecker.isAlreadyRequired(file, "json") })
    }
}
