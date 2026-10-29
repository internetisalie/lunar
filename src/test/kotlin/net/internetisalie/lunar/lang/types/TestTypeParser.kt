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

    /** BUG-542: an optional `fun` parameter marker reaches `LuaParameter.isOptional`. */
    @Test
    fun testOptionalFunParameter() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(opts?: table)", file) as LuaFunctionType
        assertEquals("opts", func.params[0].name)
        assertTrue(func.params[0].isOptional)
    }

    /** BUG-542: an optional table key admits nil, as `@field name?` does (BUG-401). */
    @Test
    fun testOptionalTableKeyAdmitsNil() {
        val file = myFixture.configureByText("dummy.lua", "")

        val table = TypeParser.parse("{a?: string}", file) as LuaTableLiteralType
        val member = requireNotNull(table.localMembers["a"]) { "no member a in ${table.name}" }
        assertEquals(setOf(LuaPrimitiveType.STRING, LuaPrimitiveType.NIL), (member.type as LuaUnionType).types)
    }

    /** BUG-543: a bracket key has no name, so it must not collapse into a member named "unknown". */
    @Test
    fun testBracketKeyedEntriesAreNotMembers() {
        val file = myFixture.configureByText("dummy.lua", "")

        val table = TypeParser.parse("{[1]: string, a: number}", file) as LuaTableLiteralType
        assertEquals(setOf("a"), table.localMembers.keys)
    }

    /** BUG-544: a nil-admitting parameter may be omitted. */
    @Test
    fun testNilableFunParameterIsOptional() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(a: string?)", file) as LuaFunctionType
        assertTrue(func.params[0].isOptional)
    }

    /** BUG-545: the nominal layer models one return, so a return list contributes its first value. */
    @Test
    fun testParenReturnListTypesTheFirstValue() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(): (string, number)", file) as LuaFunctionType
        assertEquals(LuaPrimitiveType.STRING, func.returnType)
    }

    /** BUG-547: a type-only parameter is positional, named `_`. */
    @Test
    fun testUnnamedFunParameterIsPositional() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(table)", file) as LuaFunctionType
        assertEquals("_", func.params[0].name)
        assertEquals(LuaPrimitiveType.TABLE, func.params[0].type)
    }

    /** BUG-514: `...` in the name slot is a vararg, as `isVararg = argName == "..."` always intended. */
    @Test
    fun testVarargFunParameter() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(...: any)", file) as LuaFunctionType
        assertTrue(func.params[0].isVararg)
    }

    @Test
    fun testUntypedVarargFunParameter() {
        val file = myFixture.configureByText("dummy.lua", "")

        val func = TypeParser.parse("fun(...)", file) as LuaFunctionType
        assertTrue(func.params[0].isVararg)
        assertEquals(LuaPrimitiveType.ANY, func.params[0].type)
    }
}
