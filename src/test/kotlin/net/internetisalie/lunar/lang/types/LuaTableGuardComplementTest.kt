package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * BUG-111 — the complement of `type(v) == "table"` must drop every table-shaped member, arrays included.
 *
 * Subtracting the guard's bare table removed only an equal member, so the `else` branch of a
 * `string | string[]` parameter still held `string[]` and `self.cmd = cmd` reported
 * `string | string[] is not assignable to string` (lazy.nvim `manage/process.lua`).
 */
class LuaTableGuardComplementTest : BaseDocumentTest() {
    @Test
    fun theElseBranchOfATableGuardIsNoLongerAnArray() {
        assertEquals(emptyList(), assignabilityErrors(processShape()))
    }

    @Test
    fun theElseBranchOfATableGuardOnALocalIsNoLongerAnArray() {
        assertEquals(emptyList(), assignabilityErrors(guarded("type(c) == \"table\"", "out = c[1]", "out = c")))
    }

    @Test
    fun theMatchBranchOfANotTableGuardGetsTheComplement() {
        assertEquals(emptyList(), assignabilityErrors(guarded("type(c) ~= \"table\"", "out = c", "out = c[1]")))
    }

    @Test
    fun anElseifChainAccumulatesTheTableExclusion() {
        val source =
            """
            |---@param c string|string[]|number
            |local function f(c)
            |  ---@type string
            |  local out
            |  if type(c) == "number" then
            |    out = "n"
            |  elseif type(c) == "table" then
            |    out = c[1]
            |  else
            |    out = c
            |  end
            |  return out
            |end
            """.trimMargin()
        assertEquals(emptyList(), assignabilityErrors(source))
    }

    @Test
    fun aClassMemberIsRemovedByATableGuardToo() {
        val source =
            """
            |---@class Opts
            |---@field n integer
            |
            |---@param c string|Opts
            |local function f(c)
            |  ---@type string
            |  local out
            |  if type(c) == "table" then
            |    out = "t"
            |  else
            |    out = c
            |  end
            |  return out
            |end
            """.trimMargin()
        assertEquals(emptyList(), assignabilityErrors(source))
    }

    @Test
    fun aTableLiteralMemberIsRemovedByATableGuardToo() {
        val source =
            """
            |---@param c string|{ n: integer }
            |local function f(c)
            |  ---@type string
            |  local out
            |  if type(c) == "table" then
            |    out = "t"
            |  else
            |    out = c
            |  end
            |  return out
            |end
            """.trimMargin()
        assertEquals(emptyList(), assignabilityErrors(source))
    }

    @Test
    fun aGuardForAnotherTypeStillLeavesTheArrayInTheElseBranch() {
        val reported = assignabilityErrors(guarded("type(c) == \"number\"", "out = \"n\"", "out = c"))
        assertEquals(true, reported.any { it.contains("string[]") }, "the array must survive a number guard: $reported")
    }

    @Test
    fun aMemberTheGuardDoesNotCoverIsStillReportedInTheElseBranch() {
        val source =
            """
            |---@param c string|string[]|number
            |local function f(c)
            |  ---@type string
            |  local out
            |  if type(c) == "table" then
            |    out = c[1]
            |  else
            |    out = c
            |  end
            |  return out
            |end
            """.trimMargin()
        val reported = assignabilityErrors(source)
        assertEquals(true, reported.any { it.contains("number") && !it.contains("string[]") }, "reported: $reported")
    }

    private fun guarded(
        condition: String,
        matchBody: String,
        elseBody: String,
    ): String =
        """
        |---@param c string|string[]
        |local function f(c)
        |  ---@type string
        |  local out
        |  if $condition then
        |    $matchBody
        |  else
        |    $elseBody
        |  end
        |  return out
        |end
        """.trimMargin()

    private fun processShape(): String =
        """
        |---@class LazyProcess
        |---@field cmd string
        |local Process = {}
        |
        |---@param cmd string|string[]
        |function Process.new(cmd)
        |  local self = setmetatable({}, { __index = Process })
        |  if type(cmd) == "table" then
        |    self.cmd = cmd[1]
        |  else
        |    self.cmd = cmd
        |  end
        |  return self
        |end
        """.trimMargin()

    private fun assignabilityErrors(source: String): List<String> {
        var messages = emptyList<String>()
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText(source)
            messages =
                LuaTypesSnapshot
                    .forFile(myFixture.file)
                    .getErrors()
                    .filter { it.message.contains("not assignable") }
                    .map { "${it.severity}|${it.message}" }
        }
        return messages
    }
}
