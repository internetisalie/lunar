package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Integration tests for COMP-03-03 auto-import (TC-03-01 .. TC-03-06).
 *
 * Anchor-placement cases drive [LuaImportInserter] directly (deterministic). End-to-end
 * cases drive the completion popup and accept the auto-import lookup element, asserting the
 * resulting document text.
 */
@RunWith(JUnit4::class)
class LuaAutoImportIntegrationTest : IndexedBasePlatformTestCase() {

    private fun configureSourcePathFor(file: VirtualFile) {
        val dir = file.parent.path
        LuaProjectSettings.getInstance(project).state.sourcePath = "$dir/?.lua;$dir/?/init.lua"
    }

    /** Run [LuaImportInserter] inside a write command, like the production handler does. */
    private fun runInsert(file: LuaFile, statement: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            LuaImportInserter.insert(myFixture.editor, file, statement)
        }
    }

    // --- Anchor placement (TC-03-03, TC-03-04) -------------------------------

    @Test
    fun testGroupedWithExistingRequires() {
        // TC-03-03: new import joins the existing leading require block.
        val file = myFixture.configureByText(
            "main.lua",
            """
            local a = require("mod.a")
            local b = require("mod.b")

            print(a)
            """.trimIndent(),
        ) as LuaFile

        runInsert(file, """local c = require("mod.c")""")

        myFixture.checkResult(
            """
            local a = require("mod.a")
            local b = require("mod.b")
            local c = require("mod.c")

            print(a)
            """.trimIndent(),
        )
    }

    @Test
    fun testInsertIntoEmptyFile() {
        // TC-03-04 (empty file): import goes at the very top.
        val file = myFixture.configureByText("main.lua", "") as LuaFile
        runInsert(file, """local m = require("mod")""")
        myFixture.checkResult("""local m = require("mod")""" + "\n")
    }

    @Test
    fun testInsertAfterHeaderComments() {
        // TC-03-04 (header): import goes after the leading comment block, before code.
        val file = myFixture.configureByText(
            "main.lua",
            """
            -- Copyright header
            -- second line

            local x = 1
            """.trimIndent(),
        ) as LuaFile

        runInsert(file, """local m = require("mod")""")

        myFixture.checkResult(
            """
            -- Copyright header
            -- second line

            local m = require("mod")

            local x = 1
            """.trimIndent(),
        )
    }

    // --- End-to-end completion acceptance (TC-03-01, TC-03-02, TC-03-06) ------

    @Test
    fun testReturnStyleAutoImport() {
        // TC-03-01: completing a symbol from a return-style module inserts a local binding.
        // shapes.lua defines a unique global AND returns a table (return-style).
        myFixture.addFileToProject(
            "shapes.lua",
            """
            function shapesHelper() end
            local M = {}
            return M
            """.trimIndent(),
        )
        val current = myFixture.addFileToProject("main.lua", "shapesHelper<caret>").virtualFile
        configureSourcePathFor(current)

        acceptCompletion(current, "shapesHelper")

        val text = myFixture.editor.document.text
        assertTrue(
            "Expected a local require binding, got:\n$text",
            text.contains("""local shapes = require("shapes")"""),
        )
    }

    @Test
    fun testGlobalStyleAutoImport() {
        // TC-03-02: a module with no return yields a bare require(...).
        val current = myFixture.addFileToProject("main.lua", "globalThing<caret>").virtualFile
        myFixture.addFileToProject("globals.lua", "function globalThing() end")
        configureSourcePathFor(current)

        acceptCompletion(current, "globalThing")

        val text = myFixture.editor.document.text
        assertTrue(
            "Expected a bare require, got:\n$text",
            text.contains("""require("globals")""") && !text.contains("""= require("globals")"""),
        )
    }

    @Test
    fun testNoDuplicateWhenAlreadyRequired() {
        // TC-03-06: selecting another symbol from an already-required module adds nothing.
        val file = myFixture.configureByText(
            "main.lua",
            """
            local m = require("mod")

            print(1)
            """.trimIndent(),
        ) as LuaFile

        // Dedup short-circuits before any write, so the document is unchanged.
        assertTrue(runReadActionDedup(file, "mod"))
        runInsertIfNotDuplicate(file, "mod", """local m2 = require("mod")""")

        myFixture.checkResult(
            """
            local m = require("mod")

            print(1)
            """.trimIndent(),
        )
    }

    private fun runReadActionDedup(file: LuaFile, path: String): Boolean =
        com.intellij.openapi.application.runReadAction {
            LuaDeduplicationChecker.isAlreadyRequired(file, path)
        }

    private fun runInsertIfNotDuplicate(file: LuaFile, path: String, statement: String) {
        if (!runReadActionDedup(file, path)) runInsert(file, statement)
    }

    /** Trigger completion at the caret and accept the lookup element named [itemName]. */
    private fun acceptCompletion(file: VirtualFile, itemName: String) {
        WriteAction.runAndWait<RuntimeException> {
            com.intellij.psi.stubs.StubIndex.getInstance()
                .forceRebuild(Throwable("auto-import test: index globals"))
        }
        myFixture.configureFromExistingVirtualFile(file)
        myFixture.completeBasic()

        val lookup = LookupManager.getActiveLookup(myFixture.editor) as? LookupImpl
        if (lookup != null) {
            val element = lookup.items.firstOrNull { it.lookupString == itemName }
            if (element != null) {
                lookup.currentItem = element
                myFixture.finishLookup('\n')
            }
        }
    }
}
