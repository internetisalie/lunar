package net.internetisalie.lunar.lang.types

import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeAlgebra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaTypeAlgebraTest {
    @Test
    fun `TC-TYPE-09-P1-01 flattens nested unions`() {
        val nested = LuaGraphType.Union(setOf(LuaGraphType.Number, LuaGraphType.String))
        val result = LuaTypeAlgebra.canonicalize(listOf(nested, LuaGraphType.Boolean))

        assertTrue("Expected a Union, got $result", result is LuaGraphType.Union)
        assertEquals(
            setOf(LuaGraphType.Number, LuaGraphType.String, LuaGraphType.Boolean),
            (result as LuaGraphType.Union).types,
        )
    }

    @Test
    fun `TC-TYPE-09-P1-02 any absorbs scalar members`() {
        val result = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.String, LuaGraphType.Any))
        assertEquals(LuaGraphType.Any, result)

        val manyScalars =
            LuaTypeAlgebra.canonicalize(
                listOf(LuaGraphType.Number, LuaGraphType.Nil, LuaGraphType.Boolean, LuaGraphType.Any),
            )
        assertEquals(LuaGraphType.Any, manyScalars)
    }

    @Test
    fun `BUG-397 any preserves structural arms`() {
        val errTable = LuaGraphType.Table(localMembers = emptyMap())
        val result = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.Any, errTable))

        assertTrue("Expected a Union, got $result", result is LuaGraphType.Union)
        assertEquals(setOf<LuaGraphType>(LuaGraphType.Any, errTable), (result as LuaGraphType.Union).types)
    }

    @Test
    fun `BUG-397 any absorbs scalars but keeps every structural arm`() {
        val table = LuaGraphType.Table(localMembers = emptyMap())
        val array = LuaGraphType.Array(LuaGraphType.String)
        val result =
            LuaTypeAlgebra.canonicalize(
                listOf(LuaGraphType.String, table, LuaGraphType.Any, array, LuaGraphType.Nil),
            )

        assertTrue("Expected a Union, got $result", result is LuaGraphType.Union)
        assertEquals(setOf(LuaGraphType.Any, table, array), (result as LuaGraphType.Union).types)
    }

    @Test
    fun `TC-TYPE-09-P1-03 collapses deduped single member`() {
        val result = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.Number, LuaGraphType.Number))
        assertEquals(LuaGraphType.Number, result)
    }

    @Test
    fun `nil is preserved as a distinct member`() {
        val result = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.String, LuaGraphType.Nil))

        assertTrue("Expected a Union, got $result", result is LuaGraphType.Union)
        val types = (result as LuaGraphType.Union).types
        assertTrue("string missing", types.contains(LuaGraphType.String))
        assertTrue("nil missing", types.contains(LuaGraphType.Nil))
    }

    @Test
    fun `member order does not affect canonical form`() {
        val a = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.String, LuaGraphType.Number))
        val b = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.Number, LuaGraphType.String))
        assertEquals(a, b)
    }

    @Test
    fun `empty members collapse to undefined`() {
        val result = LuaTypeAlgebra.canonicalize(emptyList())
        assertEquals(LuaGraphType.Undefined, result)
    }

    @Test
    fun `undefined is dropped unless it is the only member`() {
        val mixed = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.String, LuaGraphType.Undefined))
        assertEquals(LuaGraphType.String, mixed)

        val solo = LuaTypeAlgebra.canonicalize(listOf(LuaGraphType.Undefined))
        assertEquals(LuaGraphType.Undefined, solo)
    }
}
