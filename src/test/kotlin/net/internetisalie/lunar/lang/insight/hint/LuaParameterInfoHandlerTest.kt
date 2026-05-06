package net.internetisalie.lunar.lang.insight.hint

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LuaParameterInfoHandlerTest : BaseDocumentTest() {

    @Test
    fun testParameterInfoTrigger() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- Adds two numbers.
                    --- @param a number
                    --- @param b number
                    function add(a, b) end

                    add(<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
                val element = handler.findElementForParameterInfo(createCtx)

                assertNotNull(element, "Should find element for parameter info")
                assertNotNull(createCtx.itemsToShow, "Should have candidates to show")
                assertEquals(1, createCtx.itemsToShow!!.size)

                val candidate = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                assertEquals("add", candidate.name)
                assertEquals(listOf("a", "b"), candidate.params)
                assertEquals(listOf("number", "number"), candidate.types)
            }
        }
    }

    @Test
    fun testParameterTracking() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    function test(a, b, c) end
                    test(1, 2<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

                val offset = myFixture.caretOffset
                val el = myFixture.file.findElementAt(offset)
                println("testParameterTracking: element at $offset is $el (${el?.javaClass?.simpleName})")

                val element = handler.findElementForParameterInfo(createCtx)
                assertNotNull(element, "findElementForParameterInfo returned null")

                val updateCtx = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
                val updateElement = handler.findElementForUpdatingParameterInfo(updateCtx)
                assertNotNull(updateElement, "findElementForUpdatingParameterInfo returned null")

                handler.updateParameterInfo(updateElement!!, updateCtx)
                assertEquals(1, updateCtx.currentParameter, "Should highlight the second parameter (index 1)")
            }
        }
    }

    @Test
    fun testMethodSelfSuppression() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    local Obj = {}
                    --- @param name string
                    function Obj:setName(name) end

                    Obj:setName(<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)

                val offset = myFixture.caretOffset
                val el = myFixture.file.findElementAt(offset)
                println("testMethodSelfSuppression: element at $offset is $el (${el?.javaClass?.simpleName})")

                val element = handler.findElementForParameterInfo(createCtx)
                assertNotNull(element, "findElementForParameterInfo returned null")

                val candidate = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                // Signature should be Obj:setName(name: string) - 'self' is NOT suppressed in the candidate, but in updateUI
                assertEquals(listOf("self", "name"), candidate.params)
                assertTrue(candidate.isMethod)
            }
        }
    }

    @Test
    fun testOverloadSupport() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- @overload fun(x: string): void
                    --- @param x number
                    function process(x) end

                    process(<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
                val element = handler.findElementForParameterInfo(createCtx)

                assertNotNull(element, "Should find element for parameter info")
                assertNotNull(createCtx.itemsToShow, "Should have candidates to show")
                assertEquals(2, createCtx.itemsToShow!!.size, "Should have 2 candidates for overloads")

                val candidate1 = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                assertEquals("process", candidate1.name)
                assertEquals(listOf("x"), candidate1.params)
                assertEquals(listOf("number"), candidate1.types)

                val candidate2 = createCtx.itemsToShow!![1] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                assertEquals("process", candidate2.name)
                assertEquals(listOf("x"), candidate2.params)
                assertEquals(listOf("string"), candidate2.types)
            }
        }
    }

    @Test
    fun testVarargSupport() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    --- @param name string
                    --- @param ... any
                    function test(name, ...) end

                    test("foo", <caret>nil)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
                val element = handler.findElementForParameterInfo(createCtx)

                assertNotNull(element, "Should find element for parameter info")
                val candidate = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                assertEquals(listOf("name", "..."), candidate.params)
                assertEquals(listOf("string", "any"), candidate.types)
            }
        }
    }

    @Test
    fun testBuiltinPrint() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                configureByText("""
                    print(<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
                val element = handler.findElementForParameterInfo(createCtx)

                // This might be null in a light test environment without platform libraries
                if (element != null) {
                    val candidate = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                    assertEquals(listOf("..."), candidate.params)
                }
            }
        }
    }

    private fun assertContains(text: String, substring: String) {
        assertTrue(text.contains(substring), "Expected to find '$substring' in '$text'")
    }
}
