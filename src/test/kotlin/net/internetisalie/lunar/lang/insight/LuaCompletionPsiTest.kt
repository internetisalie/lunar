package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.BaseDocumentTest
import org.junit.jupiter.api.Test
import com.intellij.psi.impl.DebugUtil

class LuaCompletionPsiTest : BaseDocumentTest() {

    @Test
    fun `test l`() {
        configureByText("local a = 1\nl<caret>")
        println("PSI l:\\n" + DebugUtil.psiToString(myFixture.file, true))
        println("Lookup l: " + myFixture.completeBasic()?.map { it.lookupString })
    }

    @Test
    fun `test lo`() {
        configureByText("local a = 1\nlo<caret>")
        println("PSI lo:\\n" + DebugUtil.psiToString(myFixture.file, true))
        println("Lookup lo: " + myFixture.completeBasic()?.map { it.lookupString })
    }

    @Test
    fun `test loc`() {
        configureByText("local a = 1\nloc<caret>")
        println("PSI loc:\\n" + DebugUtil.psiToString(myFixture.file, true))
        println("Lookup loc: " + myFixture.completeBasic()?.map { it.lookupString })
    }
}
