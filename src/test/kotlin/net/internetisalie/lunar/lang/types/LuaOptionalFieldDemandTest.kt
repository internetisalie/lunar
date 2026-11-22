package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.ErrorSeverity
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * BUG-118: a member the user declared optional stays optional whatever usage demands merge with it, so
 * an empty table still satisfies a class whose fields are all optional.
 */
class LuaOptionalFieldDemandTest : BasePlatformTestCase() {
    private fun reports(source: String): List<String> {
        var result: List<String> = emptyList()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                result =
                    LuaTypesSnapshot
                        .forFile(myFixture.configureByText("use.lua", source))
                        .getErrors()
                        .filter { it.severity == ErrorSeverity.ERROR }
                        .map { it.message }
            }
        }
        return result
    }

    private fun runWith(fieldLine: String): String =
        """
        local M = {}

        ---@class ManagerOpts
        $fieldLine

        ---@param opts? ManagerOpts
        function M.run(opts)
          opts = opts or {}
          local mode = opts.mode
          local event = mode and ("Lazy" .. mode:sub(1, 1):upper() .. mode:sub(2))
        end
        """.trimIndent()

    fun testOptionalFieldReadByMethodChainAcceptsEmptyTable() {
        assertEquals(emptyList<String>(), reports(runWith("---@field mode? string")))
    }

    fun testRequiredFieldStillReportsEmptyTable() {
        assertTrue(reports(runWith("---@field mode string")).isNotEmpty())
    }

    private fun nodeSource(fieldLine: String): String =
        """
        ---@class ObjR
        ---@field type string
        ---@field start number
        ---@field finish number
        $fieldLine
        ---@field op? ObjR

        local function fin(n) return n.finish + 1 end

        ---@param node ObjR
        local function parseSimple(node)
          if node.type == 'x' then return fin(node.node) end
          return fin(node.op)
        end

        local function peekWord() return nil end
        local function parseName()
          local word = peekWord()
          if not word then return nil end
          return { type = 'name', start = 1, finish = 2 }
        end

        local function f()
          local name = parseName()
          if name then
            parseSimple(name)
          end
        end
        """.trimIndent()

    private fun missingNode(source: String): List<String> =
        reports(source).filter { it.contains("Missing required field 'node'") }

    fun testOptionalMemberPassedToHelperIsNotMissing() {
        assertEquals(emptyList<String>(), missingNode(nodeSource("---@field node? ObjR")))
    }

    fun testRequiredMemberPassedToHelperIsStillMissing() {
        assertTrue(missingNode(nodeSource("---@field node ObjR")).isNotEmpty())
    }
}
