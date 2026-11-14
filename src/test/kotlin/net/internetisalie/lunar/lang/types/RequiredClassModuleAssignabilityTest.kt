package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-98: a module whose returned local is a `@class` has two value sources, the class annotation
 * (methods) and the constructor (fields). The module export must present them as ONE class, not as a
 * closed union whose arms each hold half of it, or every member use on the `require`d local reports
 * `Class | { ... } is not assignable to { ... }`.
 */
@RunWith(JUnit4::class)
class RequiredClassModuleAssignabilityTest : BasePlatformTestCase() {
    private val consumer = "local log = require('mod')\nlog.show()\n"

    private val classModule =
        """
        --- @class Mod
        local M = {
          messages = {},
        }

        function M.show() end

        return M
        """.trimIndent()

    @Test
    fun testClassModuleMembersAreNotFlagged() {
        assertEquals(emptyList<String>(), assignabilityFindings(classModule))
    }

    @Test
    fun testClassModuleWithFieldTagIsNotFlagged() {
        val module = classModule.replace("--- @class Mod", "--- @class Mod\n--- @field messages table")
        assertEquals(emptyList<String>(), assignabilityFindings(module))
    }

    @Test
    fun testGenuineUnionStillFlagged() {
        val module = "local M = os.time() > 0 and { show = function() end } or { messages = {} }\nreturn M\n"
        assertTrue(assignabilityFindings(module).isNotEmpty())
    }

    @Test
    fun testModuleWithoutClassIsNotFlagged() {
        val module = classModule.replace("--- @class Mod\n", "")
        assertEquals(emptyList<String>(), assignabilityFindings(module))
    }

    @Test
    fun testFoldKeepsConstructorFieldsOnTheClass() {
        val use = addModuleAndConsumer(classModule)
        val type = runReadAction { LuaTypeManager.getInstance(project).resolveModule("mod", use) }
        assertTrue("expected a class, got $type", type is LuaClassType)
        assertEquals("Mod", (type as LuaClassType).name)
        val keys = runReadAction { type.getMembers().keys }
        assertTrue("members $keys", keys.containsAll(listOf("show", "messages")))
    }

    private fun addModuleAndConsumer(moduleText: String): PsiFile {
        myFixture.addFileToProject("mod.lua", moduleText)
        return myFixture.addFileToProject("use.lua", consumer)
    }

    private fun assignabilityFindings(moduleText: String): List<String> {
        val use = addModuleAndConsumer(moduleText)
        var findings = emptyList<String>()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                findings =
                    LuaTypesSnapshot
                        .forFile(use)
                        .getErrors()
                        .map { it.message }
                        .filter { it.contains("is not assignable") }
            }
        }
        return findings
    }
}
