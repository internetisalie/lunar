package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LuaBreadcrumbsTest : BaseDocumentTest() {

    @Test
    fun testNestedFunctions() {
        myFixture.configureByText("test.lua", """
            function outer()
                local function inner()
                    <caret>
                end
            end
        """.trimIndent())
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val breadcrumbs = myFixture.breadcrumbsAtCaret
                assertEquals(3, breadcrumbs.size)
                assertEquals("test.lua", breadcrumbs[0].text)
                assertEquals("outer", breadcrumbs[1].text)
                assertEquals("inner", breadcrumbs[2].text)
            }
        }
    }

    @Test
    fun testMethod() {
        myFixture.configureByText("test.lua", """
            local MyClass = {}
            function MyClass:init()
                <caret>
            end
        """.trimIndent())
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            runReadAction {
                val breadcrumbs = myFixture.breadcrumbsAtCaret
                assertEquals(2, breadcrumbs.size)
                assertEquals("test.lua", breadcrumbs[0].text)
                assertEquals("MyClass:init", breadcrumbs[1].text)
            }
        }
    }
}
