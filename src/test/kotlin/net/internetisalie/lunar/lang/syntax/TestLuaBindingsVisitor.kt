package net.internetisalie.lunar.lang.syntax

import com.intellij.openapi.command.WriteCommandAction
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import net.internetisalie.lunar.lang.insight.LuaBindings
import net.internetisalie.lunar.lang.insight.LuaBindingsVisitor
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TestLuaBindingsVisitor : BaseDocumentTest() {
    fun getBindings() : LuaBindings {
        var bindings : LuaBindings? = null
        WriteCommandAction.writeCommandAction(myFixture.project).run<RuntimeException?> {
            bindings = LuaBindingsVisitor.getBindings(myFixture.file)
        }
        Assertions.assertNotNull(bindings)
        return bindings!!
    }

    @Test
    fun testLocalVariables() {
        myFixture.configureByText(LuaFileType,
            """
                local a = 1
                local b = 2
                print(a + b)
            """.trimIndent()
        )

        val bindings = getBindings()

        val refA = bindings.references[30]
        Assertions.assertNotNull(refA)
        Assertions.assertTrue(refA!!.defined)
        Assertions.assertEquals("a", refA.name.joinToString("."))

        val refB = bindings.references[34]
        Assertions.assertNotNull(refB)
        Assertions.assertTrue(refB!!.defined)
        Assertions.assertEquals("b", refB.name.joinToString("."))
    }

    @Test
    fun testRequire() {
        myFixture.configureByText(LuaFileType,
            """
                require("os")
            """.trimIndent())

        val bindings = getBindings()
        Assertions.assertEquals(1, bindings.requires.size)

        val require = bindings.requires[0]
        Assertions.assertEquals("os", require)
    }
}