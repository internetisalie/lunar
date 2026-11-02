package net.internetisalie.lunar.lang.types

import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-14 Phase 1: the DR-01 fixtures whose shapes used to report a symbolic generic as a defect
 * (requirements "What the engine does today", the "false error" rows). Fixtures B, C, D, F, G, I.
 * Each asserts only that no diagnostic names a generic parameter; what the call infers is later phases.
 *
 * B and I declare `xs` without the DR-01 `= {}` initialiser: a table constructor assigned to an array type
 * is a false error of its own (BUG-481) whose message, with a generic parameter in the demand, would name
 * `T[]` without being a symbolic-generic defect. Fixture B's `wrap` returns a constructor `{ v }` against
 * `T[]`, the same defect on the return side, so exactly that message is set aside.
 */
@RunWith(JUnit4::class)
class Type14FalseErrorRegressionTest : IndexedBasePlatformTestCase() {
    private fun genericMessages(file: PsiFile): List<String> =
        LuaTypesSnapshot
            .forFile(file)
            .getErrors()
            .map { it.message }
            .filterNot { it == "{ ... } is not assignable to T[]" }
            .filter { Regex("\\b[TKV]\\b").containsMatchIn(it) }

    private fun assertNoGenericDiagnostics(text: String) {
        val file = myFixture.configureByText("main.lua", text.trimIndent())
        assertEquals(emptyList<String>(), genericMessages(file))
    }

    @Test
    fun testFixtureBNestedPositions() =
        assertNoGenericDiagnostics(
            """
            ---@generic T
            ---@param xs T[]
            ---@return T
            local function first(xs) return xs[1] end
            ---@generic T
            ---@param v T
            ---@return T[]
            local function wrap(v) return { v } end
            ---@generic T
            ---@param v T|nil
            ---@return T
            local function must(v) return v end
            ---@type string[]
            local xs
            local a = first(xs)
            local w = wrap(1)
            local m = must("q")
            """,
        )

    @Test
    fun testFixtureCCrossFileGlobal() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@generic T
            ---@param v T
            ---@return T
            function ident(v) return v end
            """.trimIndent(),
        )
        assertNoGenericDiagnostics(
            """
            local s = ident("x")
            local n = ident(1)
            """,
        )
    }

    @Test
    fun testFixtureDGenericMethod() =
        assertNoGenericDiagnostics(
            """
            ---@class Box
            local Box = {}
            ---@generic T
            ---@param v T
            ---@return T
            function Box:echo(v) return v end
            ---@type Box
            local b
            local r = b:echo("x")
            local q = Box:echo(1)
            """,
        )

    @Test
    fun testFixtureFConstraint() =
        assertNoGenericDiagnostics(
            """
            ---@class Base
            ---@field k string
            local Base = {}
            ---@generic T : Base
            ---@param p T
            ---@return T
            local function f(p)
              local m = p.k
              return p
            end
            local bad = f(1)
            ---@type Base
            local base
            local good = f(base)
            """,
        )

    @Test
    fun testFixtureGBacktickCapture() =
        assertNoGenericDiagnostics(
            """
            ---@class Widget
            ---@field w number
            local Widget = {}
            ---@generic T
            ---@param cls `T`
            ---@return T
            local function new(cls) return {} end
            local w = new("Widget")
            """,
        )

    @Test
    fun testFixtureIFilterShape() =
        assertNoGenericDiagnostics(
            """
            ---@generic T
            ---@param fn fun(a: T)
            ---@param t T[]
            ---@return T[]
            local function filter(fn, t) return t end
            ---@type string[]
            local xs
            local r = filter(function(a) end, xs)
            """,
        )
}
