package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.intellij.psi.impl.DebugUtil

class LuaCompletionBugTest : BaseDocumentTest() {

    private fun doTest(text: String, vararg expected: String) {
        configureByText(text)
        println("PSI before completion:\\n" + DebugUtil.psiToString(myFixture.file, true))
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        println("Lookup elements: $strings")
    }

    @Test
    fun `test l after stmt`() {
        doTest("""
            print(1)
            l<caret>
        """.trimIndent())
    }

    @Test
    fun `test lo after stmt`() {
        doTest("""
            print(1)
            lo<caret>
        """.trimIndent())
    }

    @Test
    fun `test loc after stmt`() {
        doTest("""
            print(1)
            loc<caret>
        """.trimIndent())
    }
}
