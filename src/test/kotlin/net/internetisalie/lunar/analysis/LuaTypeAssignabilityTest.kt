package net.internetisalie.lunar.analysis

import com.intellij.testFramework.EdtTestUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class LuaTypeAssignabilityTest : BaseDocumentTest() {

    @Test
    fun testOptionalFieldInTableConstructor() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText("""
                ---@class User
                ---@field id number
                ---@field username string
                ---@field email string | nil
                local User = {}

                ---@type User
                local current_user = { id = 1, username = "admin" }
            """.trimIndent())

            val types = LuaTypesSnapshot.forFile(myFixture.file)
            val errors = types.getErrors()

            val missingEmailError = errors.any { it.message.contains("Missing required field 'email'") }
            assertTrue(!missingEmailError, "Should not report missing required field 'email' because it is optional")
        }
    }

    @Test
    fun testArrayTypeInlayHintPreservesInferredType() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText("""
                ---@type string[]
                local <caret>tags = { "lua", "intellij", "lunar" }
            """.trimIndent())

            val types = LuaTypesSnapshot.forFile(myFixture.file)
            val tagsElement = myFixture.file.findElementAt(myFixture.caretOffset)!!.parent
            
            val type = types.getValueType(tagsElement)
            // The variable's resolved type now includes both the inferred type { ... } 
            // and the annotation type string[]. This is correct for type flow: when other = tags,
            // other should receive the union type { ... } | string[].
            // The inlay hint provider's hasExplicitAnnotation check prevents showing hints on 
            // the annotated variable itself, while the union type flows to readers.
            assertTrue(type.displayName() == "{ ... } | string[]", "Expected inferred type unioned with annotation type")
        }
    }

    @Test
    fun testTableConstructorAssignabilityToFunction() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            configureByText("""
                ---@class User
                ---@field id number
                ---@field username string
                local User = {}

                ---@type User
                local current_user = { id = 1, username = "admin" }
            """.trimIndent())

            val types = LuaTypesSnapshot.forFile(myFixture.file)
            val errors = types.getErrors()

            val funError = errors.any { it.message.contains("not assignable to fun()") }
            assertTrue(!funError, "Should not report that table constructor is not assignable to fun()")
        }
    }
}
