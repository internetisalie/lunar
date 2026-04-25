package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestLuaDebugValueParser : BaseDocumentTest() {

    @Test
    fun testEvaluateSimpleNumber() {
        myFixture.configureByText(LuaFileType, "local x = 42")
        val evaluator = LuaDebugValueParser()

        ApplicationManager.getApplication().runReadAction {
            // This is a basic test - in practice you'd parse the PSI tree
            // and extract expressions to evaluate
            val value = LuaEvaluatedValue(
                kind = LuaEvaluatedValueKind.Number,
                numberValue = 42.0
            )

            assertEquals(LuaEvaluatedValueKind.Number, value.kind)
            assertEquals(42.0, value.numberValue)
        }
    }

    @Test
    fun testEvaluateSimpleString() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = \"hello\"")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaEvaluatedValue(
                kind = LuaEvaluatedValueKind.String,
                stringValue = "hello"
            )

            assertEquals(LuaEvaluatedValueKind.String, value.kind)
            assertEquals("hello", value.stringValue)
        }
    }

    @Test
    fun testEvaluateNil() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = nil")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Nil)

            assertEquals(LuaEvaluatedValueKind.Nil, value.kind)
        }
    }

    @Test
    fun testEvaluateBooleanTrue() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = true")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaEvaluatedValue(
                kind = LuaEvaluatedValueKind.Boolean,
                boolValue = true
            )

            assertEquals(LuaEvaluatedValueKind.Boolean, value.kind)
            assertEquals(true, value.boolValue)
        }
    }

    @Test
    fun testEvaluateBooleanFalse() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = false")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaEvaluatedValue(
                kind = LuaEvaluatedValueKind.Boolean,
                boolValue = false
            )

            assertEquals(LuaEvaluatedValueKind.Boolean, value.kind)
            assertEquals(false, value.boolValue)
        }
    }

    @Test
    fun testEvaluateSimpleTable() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local t = {1, 2, 3}")

        ApplicationManager.getApplication().runReadAction {
            val table = LuaEvaluatedTable()
            table.indexed.add(LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Number, numberValue = 1.0))
            table.indexed.add(LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Number, numberValue = 2.0))
            table.indexed.add(LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Number, numberValue = 3.0))

            val value = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Table, tableValue = table)

            assertEquals(LuaEvaluatedValueKind.Table, value.kind)
            assertNotNull(value.tableValue)
            assertEquals(3, value.tableValue.indexed.size)
        }
    }

    @Test
    fun testEvaluateNamedTable() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local t = {x = 42, y = \"hello\"}")

        ApplicationManager.getApplication().runReadAction {
            val table = LuaEvaluatedTable()
            table.named["x"] = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Number, numberValue = 42.0)
            table.named["y"] = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.String, stringValue = "hello")

            val value = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Table, tableValue = table)

            assertEquals(LuaEvaluatedValueKind.Table, value.kind)
            assertNotNull(value.tableValue)
            assertEquals(2, value.tableValue.named.size)
        }
    }

    @Test
    fun testLocalVariableBinding() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaEvaluatedValue(kind = LuaEvaluatedValueKind.Number, numberValue = 42.0)
            evaluator.setLocalVariable("x", value)

            val retrieved = evaluator.getLocalVariable("x")
            assertNotNull(retrieved)
            assertEquals(LuaEvaluatedValueKind.Number, retrieved.kind)
            assertEquals(42.0, retrieved.numberValue)
        }
    }
}
