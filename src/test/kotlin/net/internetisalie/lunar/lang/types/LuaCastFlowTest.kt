package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-99. `---@cast` was parsed but never read by the type engine, so a value the cast excludes
 * still reached every later use and, through the parameter, every caller.
 */
@RunWith(JUnit4::class)
class LuaCastFlowTest : BasePlatformTestCase() {
    private val definitions =
        """
        ---@class vm
        local vm = {}

        ---@class Obj
        ---@field type string

        ---@class Gen
        ---@field proto Obj

        ---@param source Obj
        ---@return boolean
        function vm.compileByGlobal(source)
            return true
        end

        ---@param proto Obj
        ---@return Gen
        function vm.newGen(proto)
            return { proto = proto }
        end

        return vm
        """.trimIndent()

    private val header =
        """
        ---@class vm
        local vm = require('mdefs')

        ---@param obj Obj
        ---@return (Obj|Gen)?
        function vm.getReturn(obj)
            if obj.type == 'function' then
                return obj
            end
            if obj.type == 'doc' then
                return vm.newGen(obj)
            end
            return nil
        end

        """.trimIndent()

    private val caller =
        """

        ---@param mfunc Obj
        local function bind(mfunc)
            local r = vm.getReturn(mfunc)
            if not r then
                return
            end
            vm.compileNode(r)
        end
        """.trimIndent()

    private fun compileNode(body: String) =
        """

        ---@param source Obj|Gen
        function vm.compileNode(source)
        $body
        end
        """.trimIndent()

    private fun guardedBody(cast: String) =
        "    if not source then\n        return\n    end\n    $cast\n    vm.compileByGlobal(source)"

    private fun errorsOf(source: String): List<String> {
        myFixture.addFileToProject("mdefs.lua", definitions)
        myFixture.configureByText("use.lua", source)
        return runReadAction { LuaTypesSnapshot.forFile(myFixture.file).getErrors().map { it.message } }
            .filter { it.contains("is not assignable") }
    }

    @Test
    fun testCastNarrowsParameterForLaterUses() {
        val errors = errorsOf(header + compileNode(guardedBody("---@cast source Obj")) + caller)
        assertEmpty(errors)
    }

    @Test
    fun testCastWithoutCallers() {
        val errors = errorsOf(header + compileNode(guardedBody("---@cast source Obj")))
        assertEmpty(errors)
    }

    @Test
    fun testCastOnFirstBodyLine() {
        val body = "    ---@cast source Obj\n    vm.compileByGlobal(source)"
        assertEmpty(errorsOf(header + compileNode(body) + caller))
    }

    @Test
    fun testCastSubtractForm() {
        val body = "    ---@cast source -Gen\n    vm.compileByGlobal(source)"
        assertEmpty(errorsOf(header + compileNode(body)))
    }

    @Test
    fun testCastRemoveNilForm() {
        val body = "    ---@cast source -?, -Gen\n    vm.compileByGlobal(source)"
        assertEmpty(errorsOf(header + compileNode(body)))
    }

    @Test
    fun testCastAddForm() {
        val body = "    ---@cast source +Obj\n    vm.compileByGlobal(source)"
        val errors = errorsOf(header + "\n---@param source Gen\nfunction vm.compileNode(source)\n$body\nend")
        assertTrue("+T adds to the type, Gen stays reachable: $errors", errors.isNotEmpty())
    }

    @Test
    fun testNoCastControl() {
        val body = guardedBody("")
        val errors = errorsOf(header + compileNode(body) + caller)
        assertTrue("without the cast Gen reaches an Obj parameter: $errors", errors.isNotEmpty())
    }

    @Test
    fun testNoCastControlWithoutCallers() {
        val errors = errorsOf(header + compileNode(guardedBody("")))
        assertTrue("without the cast Gen reaches an Obj parameter: $errors", errors.isNotEmpty())
    }
}
