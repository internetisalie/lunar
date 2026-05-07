package net.internetisalie.lunar.lang

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LuaNavigationTest : BaseDocumentTest() {
    @Test
    fun `test local variable navigation`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = configureByText("""
                local data = {value = 42}
                print(<caret>data)
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "Local variable 'data' not resolved")

                val expectedOffset = file.text.indexOf("data =")
                assertEquals(expectedOffset, resolved?.textOffset, "Resolved to wrong element")
            }
        }
    }

    @Test
    fun `test multiple local variable navigation`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = configureByText("""
                local x, y = 1, 2
                print(x, <caret>y)
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "Local variable 'y' not resolved")

                val expectedOffset = file.text.indexOf("y =")
                if (expectedOffset == -1) {
                    val altOffset = file.text.indexOf("y,")
                    assertEquals(altOffset, resolved?.textOffset, "Resolved to wrong element")
                } else {
                    assertEquals(expectedOffset, resolved?.textOffset, "Resolved to wrong element")
                }
            }
        }
    }

    @Test
    fun `test local variable in nested function`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = configureByText("""
                local function outer()
                    local data = 42
                    local function inner()
                        print(<caret>data)
                    end
                end
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "Local variable 'data' not resolved in nested function")

                val expectedOffset = file.text.indexOf("data = 42")
                assertEquals(expectedOffset, resolved?.textOffset, "Resolved to wrong element")
            }
        }
    }

    @Test
    fun `test function parameter navigation`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = configureByText("""
                local function foo(param)
                    print(<caret>param)
                end
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "Function parameter 'param' not resolved")

                val expectedOffset = file.text.indexOf("param)")
                assertEquals(expectedOffset, resolved?.textOffset, "Resolved to wrong element")
            }
        }
    }

    @Test
    fun `test self resolution in method`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText("""
                local obj = {}
                function obj:method()
                    print(<caret>self)
                end
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "'self' not resolved in method")
            }
        }
    }

    @Test
    fun `test recursive function resolution`() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            val file = configureByText("""
                function fib(n)
                    if n <= 1 then return n end
                    return <caret>fib(n-1)
                end
            """.trimIndent())

            runReadAction {
                val ref = myFixture.file.findReferenceAt(myFixture.caretOffset)
                assertNotNull(ref, "Reference not found at caret")
                val resolved = ref?.resolve()
                assertNotNull(resolved, "Recursive function call not resolved")

                val expectedOffset = file.text.indexOf("fib(n)")
                assertEquals(expectedOffset, resolved?.textOffset, "Resolved to wrong element")
            }
        }
    }
}
