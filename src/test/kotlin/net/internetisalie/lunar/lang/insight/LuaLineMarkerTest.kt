package net.internetisalie.lunar.lang.insight

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.LuaBundle
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LuaLineMarkerTest : BaseDocumentTest() {

    @Test
    fun testRecursiveCall() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", """
                    local function f()
                        f()
                    end
                """.trimIndent())
                val gutters = myFixture.findAllGutters()
                val recursiveTooltip = LuaBundle.message("gutter.recursive.call")
                assertTrue(gutters.any { it.tooltipText == recursiveTooltip }, "Recursive call gutter marker not found")
            }
        }
    }

    @Test
    fun testTailCall() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", """
                    local function g()
                    end
                    local function f()
                        return g()
                    end
                """.trimIndent())
                val gutters = myFixture.findAllGutters()
                val tailCallTooltip = LuaBundle.message("gutter.tail.call")
                assertTrue(gutters.any { it.tooltipText == tailCallTooltip }, "Tail call gutter marker not found")
            }
        }
    }

    @Test
    fun testRecursiveTailCall() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", """
                    local function f()
                        return f()
                    end
                """.trimIndent())
                val gutters = myFixture.findAllGutters()
                val recursiveTooltip = LuaBundle.message("gutter.recursive.call")
                val tailCallTooltip = LuaBundle.message("gutter.tail.call")

                assertTrue(gutters.any { it.tooltipText == recursiveTooltip }, "Recursive call gutter marker not found")
                assertTrue(gutters.any { it.tooltipText == tailCallTooltip }, "Tail call gutter marker not found")
            }
        }
    }

    @Test
    fun testNonTailCall() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                myFixture.configureByText("test.lua", """
                    local function g()
                    end
                    local function f()
                        return 1 + g()
                    end
                """.trimIndent())
                val gutters = myFixture.findAllGutters()
                val tailCallTooltip = LuaBundle.message("gutter.tail.call")
                assertTrue(gutters.none { it.tooltipText == tailCallTooltip }, "Tail call gutter marker found where it shouldn't be")
            }
        }
    }
}
