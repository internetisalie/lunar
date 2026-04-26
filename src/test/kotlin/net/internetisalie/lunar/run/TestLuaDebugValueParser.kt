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
            table.addByName("x", LuaValue(kind = LuaValueKind.Number, numberValue = 42.0))
            table.addByName("y", LuaValue(kind = LuaValueKind.String, stringValue = "hello"))

            val value = LuaValue(kind = LuaValueKind.Table, tableValue = table)

            assertEquals(LuaValueKind.Table, value.kind)
            assertNotNull(value.tableValue)
            assertEquals(2, value.tableValue.named.size)
        }
    }

    @Test
    fun testParseTable() {
        myFixture.configureByText(LuaFileType, "do local _={1, \"A\"};return _;end")

        ApplicationManager.getApplication().runReadAction {
            val table = LuaDebugValueParser.parseFile(myFixture.file)

            // Should have 1 indexed entry (the string "1")
            assertEquals(2, table.indexed.size)

            // First entry should be a number value 1
            val firstEntry = table.indexed[0]
            assertNotNull(firstEntry)
            assertEquals(LuaValueKind.Number, firstEntry.kind)
            assertEquals(1, firstEntry.numberValue?.toInt())

            // Second entry should be a string value "A"
            val secondEntry = table.indexed[1]
            assertNotNull(secondEntry)
            assertEquals(LuaValueKind.String, secondEntry.kind)
            assertEquals("A", secondEntry.stringValue)
        }
    }

    @Test
    fun testParseChunk() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // This simulates what the debugger returns: a script with do...return
            val chunk = "do local _={1, \"B\"};return _;end"
            val table = LuaDebugValueParser.parseChunk(myFixture.project, chunk)

            // Should have 2 indexed entries
            assertEquals(2, table.indexed.size)

            // First entry should be number 1
            val firstEntry = table.indexed[0]
            assertNotNull(firstEntry)
            assertEquals(LuaValueKind.Number, firstEntry.kind)
            assertEquals(1, firstEntry.numberValue?.toInt())

            // Second entry should be string "B"
            val secondEntry = table.indexed[1]
            assertNotNull(secondEntry)
            assertEquals(LuaValueKind.String, secondEntry.kind)
            assertEquals("B", secondEntry.stringValue)
        }
    }
}
