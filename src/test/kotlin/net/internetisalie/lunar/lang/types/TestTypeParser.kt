package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestTypeParser : BasePlatformTestCase() {

    @Test
    fun testParsePrimitives() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t1 = TypeParser.parse("string", file)
        assertEquals(LuaPrimitiveType.STRING, t1)

        val t2 = TypeParser.parse("number", file)
        assertEquals(LuaPrimitiveType.NUMBER, t2)
    }

    @Test
    fun testParseUnion() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t = TypeParser.parse("string | number | nil", file)
        assertTrue(t is LuaUnionType)
        val union = t as LuaUnionType
        assertEquals(3, union.types.size)
        assertTrue(union.types.any { it.name == "string" })
        assertTrue(union.types.any { it.name == "number" })
        assertTrue(union.types.any { it.name == "nil" })
    }

    @Test
    fun testParseArray() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t = TypeParser.parse("string[]", file)
        assertTrue(t is LuaArrayType)
        val arr = t as LuaArrayType
        assertEquals("string", arr.elementType.name)
    }

    @Test
    fun testParseParameterized() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t = TypeParser.parse("Map<K, V>", file)
        assertTrue(t is LuaParameterizedType)
        val pType = t as LuaParameterizedType
        assertEquals("Map", pType.baseType.name)
        assertEquals(2, pType.arguments.size)
        assertEquals("K", pType.arguments[0].name)
        assertEquals("V", pType.arguments[1].name)
    }

    @Test
    fun testParseDictionary() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t = TypeParser.parse("{[string]: number}", file)
        assertTrue(t is LuaParameterizedType)
        val dict = t as LuaParameterizedType
        assertEquals("table", dict.baseType.name)
        assertEquals(2, dict.arguments.size)
        assertEquals("string", dict.arguments[0].name)
        assertEquals("number", dict.arguments[1].name)
    }

    @Test
    fun testParseFunctionSignature() {
        val file = myFixture.configureByText("dummy.lua", "")

        val t = TypeParser.parse("fun(name: string, age: number): boolean", file)
        println("PARSED TYPE IS: " + t.javaClass.simpleName + " text=" + t.name)
        assertTrue(t is LuaFunctionType)
        val func = t as LuaFunctionType
        assertEquals("boolean", func.returnType.name)
        assertEquals(2, func.params.size)

        assertEquals("string", func.params[0].type.name)
        assertEquals("number", func.params[1].type.name)

        // BUG-357: parameter NAMES must survive parsing of a fun(...) signature, not collapse to "p".
        assertEquals("name", func.params[0].name)
        assertEquals("age", func.params[1].name)
    }
}
