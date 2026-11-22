package net.internetisalie.lunar.analysis

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-117. A method's parameters follow its signature; the order its `---@param` tags were written
 * in decides nothing, because LuaCATS binds a tag to a parameter by name.
 */
class LuaParamTagOrderTest : BasePlatformTestCase() {
    private fun allErrors(source: String): List<String> {
        var messages: List<String> = emptyList()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("use.lua", source)
                messages =
                    LuaTypesSnapshot
                        .forFile(myFixture.file)
                        .getErrors()
                        .map { "${it.severity} ${it.message}  @[${it.element.text.take(40)}]" }
            }
        }
        return messages
    }

    private fun assignabilityErrors(source: String): List<String> =
        allErrors(source).filter { it.contains("is not assignable") }

    private fun taskSource(
        tags: String,
        call: String = "",
    ): String =
        """
        ---@class TO
        ---@field on_done? fun(x: string)
        ---@alias TFn fun(x: string)
        local Task = {}
        function Task:_run(task) task('a') end

        $tags
        function Task.new(name, task, opts)
          local self = setmetatable({}, { __index = Task })
          self:_run(task)
          return self
        end
        $call
        """.trimIndent()

    private val tagsOutOfOrder = "---@param name string\n---@param opts? TO\n---@param task TFn"
    private val tagsInOrder = "---@param name string\n---@param task TFn\n---@param opts? TO"

    fun testTagsOutOfSignatureOrderBindByName() {
        val errors = assignabilityErrors(taskSource(tagsOutOfOrder))
        assertTrue("expected no assignability error, got:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    fun testTagsInSignatureOrderControl() {
        val errors = assignabilityErrors(taskSource(tagsInOrder))
        assertTrue("expected no assignability error, got:\n${errors.joinToString("\n")}", errors.isEmpty())
    }

    private fun callErrors(
        tags: String,
        call: String,
    ): List<String> =
        allErrors(
            """
            ---@class Task
            local Task = {}

            $tags
            function Task.new(name, task, opts) end
            $call
            """.trimIndent(),
        ).filter { it.startsWith("ERROR") }

    private val typedOutOfOrder = "---@param name string\n---@param opts number\n---@param task boolean"
    private val typedInOrder = "---@param name string\n---@param task boolean\n---@param opts number"

    fun testArgumentsOfAConformingCallMeetTheirParametersWhateverTheTagOrder() {
        assertEquals(emptyList<String>(), callErrors(typedInOrder, "Task.new('n', true, 5)"))
        assertEquals(emptyList<String>(), callErrors(typedOutOfOrder, "Task.new('n', true, 5)"))
    }

    fun testArgumentsOfANonConformingCallAreReportedAtTheSameParametersWhateverTheTagOrder() {
        val call = "Task.new('n', 5, true)"
        val inOrder = callErrors(typedInOrder, call)
        assertTrue(
            "the in-order control must report the arguments, got:\n${inOrder.joinToString("\n")}",
            inOrder.isNotEmpty(),
        )
        assertEquals(inOrder.sorted(), callErrors(typedOutOfOrder, call).sorted())
    }
}
