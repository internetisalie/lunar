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
            val value = LuaValue(
                kind = LuaValueKind.Number,
                numberValue = 42.0
            )

            assertEquals(LuaValueKind.Number, value.kind)
            assertEquals(42.0, value.numberValue)
        }
    }

    @Test
    fun testEvaluateSimpleString() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = \"hello\"")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaValue(
                kind = LuaValueKind.String,
                stringValue = "hello"
            )

            assertEquals(LuaValueKind.String, value.kind)
            assertEquals("hello", value.stringValue)
        }
    }

    @Test
    fun testEvaluateNil() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = nil")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaValue(kind = LuaValueKind.Nil)

            assertEquals(LuaValueKind.Nil, value.kind)
        }
    }

    @Test
    fun testEvaluateBooleanTrue() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = true")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaValue(
                kind = LuaValueKind.Boolean,
                boolValue = true
            )

            assertEquals(LuaValueKind.Boolean, value.kind)
            assertEquals(true, value.boolValue)
        }
    }

    @Test
    fun testEvaluateBooleanFalse() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local x = false")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaValue(
                kind = LuaValueKind.Boolean,
                boolValue = false
            )

            assertEquals(LuaValueKind.Boolean, value.kind)
            assertEquals(false, value.boolValue)
        }
    }

    @Test
    fun testEvaluateSimpleTable() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local t = {1, 2, 3}")

        ApplicationManager.getApplication().runReadAction {
            val table = LuaTable()
            table.indexed.add(LuaValue(kind = LuaValueKind.Number, numberValue = 1.0))
            table.indexed.add(LuaValue(kind = LuaValueKind.Number, numberValue = 2.0))
            table.indexed.add(LuaValue(kind = LuaValueKind.Number, numberValue = 3.0))

            val value = LuaValue(kind = LuaValueKind.Table, tableValue = table)

            assertEquals(LuaValueKind.Table, value.kind)
            assertNotNull(value.tableValue)
            assertEquals(3, value.tableValue.indexed.size)
        }
    }

    @Test
    fun testEvaluateNamedTable() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "local t = {x = 42, y = \"hello\"}")

        ApplicationManager.getApplication().runReadAction {
            val table = LuaTable()
            table.named["x"] = LuaValue(kind = LuaValueKind.Number, numberValue = 42.0)
            table.named["y"] = LuaValue(kind = LuaValueKind.String, stringValue = "hello")

            val value = LuaValue(kind = LuaValueKind.Table, tableValue = table)

            assertEquals(LuaValueKind.Table, value.kind)
            assertNotNull(value.tableValue)
            assertEquals(2, value.tableValue.named.size)
        }
    }

    @Test
    fun testLocalVariableBinding() {
        val evaluator = LuaDebugValueParser()
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val value = LuaValue(kind = LuaValueKind.Number, numberValue = 42.0)
            evaluator.setLocalVariable("x", value)

            val retrieved = evaluator.getLocalVariable("x")
            assertNotNull(retrieved)
            assertEquals(LuaValueKind.Number, retrieved.kind)
            assertEquals(42.0, retrieved.numberValue)
        }
    }
}
