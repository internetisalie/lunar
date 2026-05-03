package net.internetisalie.lunar.lang.syntax

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestLuaVarargAnnotator : BasePlatformTestCase() {

    @Test
    fun testValidInMainChunk() {
        myFixture.configureByText("test.lua", """
            local args = {...}
            local y = ...
        """.trimIndent())
        myFixture.checkHighlighting(false, false, false)
    }

    @Test
    fun testValidInVarargFunction() {
        myFixture.configureByText("test.lua", """
            function my_vararg(...)
                local y = ...
            end

            local function local_vararg(a, b, ...)
                return {...}
            end

            local f = function(...)
                local x = ...
            end
        """.trimIndent())
        myFixture.checkHighlighting(false, false, false)
    }

    @Test
    fun testInvalidInFixedFunction() {
        myFixture.configureByText("test.lua", """
            function my_fixed(a)
                local y = <error descr="Cannot use '...' outside a vararg function">...</error>
            end

            local function local_fixed()
                return {<error descr="Cannot use '...' outside a vararg function">...</error>}
            end

            local f = function(a, b)
                local x = <error descr="Cannot use '...' outside a vararg function">...</error>
            end
        """.trimIndent())
        myFixture.checkHighlighting(false, false, false)
    }

    @Test
    fun testInvalidInNestedFunction() {
        myFixture.configureByText("test.lua", """
            function nested(...)
                return function()
                    local y = <error descr="Cannot use '...' outside a vararg function">...</error>
                end
            end

            function nested_valid()
                return function(...)
                    local y = ...
                end
            end
        """.trimIndent())
        myFixture.checkHighlighting(false, false, false)
    }
}
