package net.internetisalie.lunar.run

import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Acceptance tests for DEBUG-04: Expression Evaluation
 *
 * Tests verify that expression evaluation correctly:
 * - Parses remote debugger responses
 * - Recovers type information from stringified values
 * - Displays results with correct types
 * - Handles all Lua value types
 * - Handles errors gracefully
 *
 * These tests directly verify the parseStringAsLuaValue logic which is the core
 * mechanism for type recovery from the Mobdebug remote debugger.
 */
class TestDebug04AcceptanceCriteria : BaseDocumentTest() {

    /**
     * AC-1: Simple arithmetic: `2 + 3` → `5` (number)
     * Remote returns: `do local _={"5"}; return _; end`
     * After re-parsing: `5` should be Number(5.0)
     */
    @Test
    fun testSimpleArithmetic() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // Simulate what remote debugger returns for "2 + 3"
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "5")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(5.0, result.numberValue)
        }
    }

    /**
     * AC-2: Variable reference: `x` where x=100
     * Remote returns: `do local _={"100"}; return _; end`
     * After re-parsing: Should be Number(100.0)
     */
    @Test
    fun testVariableReference() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "100")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(100.0, result.numberValue)
        }
    }

    /**
     * AC-3: Table field access: `t.x` where t={x=50}
     * Remote returns: `do local _={"50"}; return _; end`
     * After re-parsing: Should be Number(50.0)
     */
    @Test
    fun testTableFieldAccess() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "50")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(50.0, result.numberValue)
        }
    }

    /**
     * AC-4: Function call: `func()` where func returns 42
     * Remote returns: `do local _={"42"}; return _; end`
     * After re-parsing: Should be Number(42.0)
     */
    @Test
    fun testFunctionCall() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "42")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(42.0, result.numberValue)
        }
    }

    /**
     * AC-5: Array indexing: `arr[2]` where arr={10, 20, 30}
     * Remote returns: `do local _={"20"}; return _; end`
     * After re-parsing: Should be Number(20.0)
     */
    @Test
    fun testArrayIndexing() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "20")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(20.0, result.numberValue)
        }
    }

    /**
     * AC-6: String concatenation: `"hello" .. " " .. "world"`
     * Remote returns: `do local _={"\"hello world\""}; return _; end`
     * After re-parsing: Should be String("hello world")
     */
    @Test
    fun testStringConcatenation() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "\"hello world\"")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.String, result.kind)
            assertEquals("hello world", result.stringValue)
        }
    }

    /**
     * AC-7: Empty table: `{}`
     * Remote returns: `do local _={"{"}; return _; end` (wrapped)
     * After re-parsing: Should be Table([])
     */
    @Test
    fun testEmptyTable() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "{}")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Table, result.kind)
            assertNotNull(result.tableValue)
            assertEquals(0, result.tableValue?.indexed?.size)
            assertEquals(0, result.tableValue?.named?.size)
        }
    }

    /**
     * AC-8: Mixed type table: `{1, "text", true}`
     * Remote returns stringified table with mixed types
     * After re-parsing: Should recover individual types from each string element
     */
    @Test
    fun testMixedTypeTable() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // Test the table structure: {1, "text", true}
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "{1, \"text\", true}")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Table, result.kind)
            val table = result.tableValue
            assertNotNull(table)
            assertEquals(3, table.indexed.size)
            
            // Element 1: number
            assertEquals(LuaValueKind.Number, table.indexed[0].kind)
            assertEquals(1.0, table.indexed[0].numberValue)
            
            // Element 2: string
            assertEquals(LuaValueKind.String, table.indexed[1].kind)
            assertEquals("text", table.indexed[1].stringValue)
            
            // Element 3: boolean
            assertEquals(LuaValueKind.Boolean, table.indexed[2].kind)
            assertEquals(true, table.indexed[2].boolValue)
        }
    }

    /**
     * AC-9: Nested table: `{a=1, b={c=2}}`
     * Remote returns stringified nested table
     * After re-parsing: Should preserve nested table structure
     */
    @Test
    fun testNestedTable() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "{a=1, b={c=2}}")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Table, result.kind)
            val outerTable = result.tableValue
            assertNotNull(outerTable)
            assertEquals(2, outerTable.named.size)
            
            // Check a=1
            val aPair = outerTable.getByName("a")
            assertNotNull(aPair, "Expected 'a' field")
            assertEquals(LuaValueKind.Number, aPair.second.kind)
            assertEquals(1.0, aPair.second.numberValue)
            
            // Check b={c=2}
            val bPair = outerTable.getByName("b")
            assertNotNull(bPair, "Expected 'b' field")
            assertEquals(LuaValueKind.Table, bPair.second.kind)
            
            val innerTable = bPair.second.tableValue
            assertNotNull(innerTable)
            val cPair = innerTable.getByName("c")
            assertNotNull(cPair, "Expected 'c' field in nested table")
            assertEquals(LuaValueKind.Number, cPair.second.kind)
            assertEquals(2.0, cPair.second.numberValue)
        }
    }

    /**
     * AC-10: Statement mode: `x = 10; return x`
     * Remote returns: `do local _={"10"}; return _; end`
     * After re-parsing: Should be Number(10.0)
     */
    @Test
    fun testStatementMode() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "10")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(10.0, result.numberValue)
        }
    }

    /**
     * AC-11: Error handling: Invalid expression should be handled gracefully
     * Remote returns: Error message or empty response
     * Should not crash, should return Nil or appropriate error value
     */
    @Test
    fun testErrorHandling() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // Test parsing a syntax error - should not crash
            try {
                val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "not valid lua syntax !!!@#$")
                // If it doesn't crash, test passes (it might return Nil or error value)
                assertTrue(true)
            } catch (e: Exception) {
                // Should be caught and logged, not thrown
                assertTrue(false, "Should not throw exception: ${e.message}")
            }
        }
    }

    /**
     * AC-12: Boolean values: `true` and `false`
     */
    @Test
    fun testBooleanValues() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // Test true
            val trueResult = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "true")
            assertNotNull(trueResult)
            assertEquals(LuaValueKind.Boolean, trueResult.kind)
            assertEquals(true, trueResult.boolValue)
            
            // Test false
            val falseResult = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "false")
            assertNotNull(falseResult)
            assertEquals(LuaValueKind.Boolean, falseResult.kind)
            assertEquals(false, falseResult.boolValue)
        }
    }

    /**
     * AC-13: Nil value: `nil`
     */
    @Test
    fun testNilValue() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "nil")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Nil, result.kind)
        }
    }

    /**
     * AC-14: Floating point numbers: `3.14`
     * Tests that decimal numbers are properly parsed and typed
     */
    @Test
    fun testFloatingPointNumbers() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "3.14")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(3.14, result.numberValue)
        }
    }

    /**
     * AC-15: Unary minus operator: `-5`
     * Tests that negative numbers are properly evaluated
     */
    @Test
    fun testUnaryMinus() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "-5")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(-5.0, result.numberValue)
        }
    }

    /**
     * AC-16: Unary NOT operator: `not true`
     * Tests that logical NOT is properly evaluated
     */
    @Test
    fun testUnaryNot() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            // Test `not true` → false
            val resultTrue = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "not true")
            assertNotNull(resultTrue)
            assertEquals(LuaValueKind.Boolean, resultTrue.kind)
            assertEquals(false, resultTrue.boolValue)
            
            // Test `not false` → true
            val resultFalse = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "not false")
            assertNotNull(resultFalse)
            assertEquals(LuaValueKind.Boolean, resultFalse.kind)
            assertEquals(true, resultFalse.boolValue)
        }
    }

    /**
     * AC-17: Unary length operator: `#{1, 2, 3}`
     * Tests that length operator is properly evaluated for tables
     */
    @Test
    fun testUnaryLength() {
        myFixture.configureByText(LuaFileType, "")

        ApplicationManager.getApplication().runReadAction {
            val result = LuaDebugValueParser.parseStringAsLuaValue(myFixture.project, "#{1, 2, 3}")
            
            assertNotNull(result)
            assertEquals(LuaValueKind.Number, result.kind)
            assertEquals(3.0, result.numberValue)
        }
    }
}
