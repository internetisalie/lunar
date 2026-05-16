package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TestLuaTypeDomainModel : BasePlatformTestCase() {

    @Test
    fun testPrimitiveAssignment() {
        val num = LuaPrimitiveType.NUMBER
        val str = LuaPrimitiveType.STRING
        val any = LuaPrimitiveType.ANY

        assertTrue(num.isAssignableTo(num))
        assertTrue(num.isAssignableTo(any))
        assertFalse(num.isAssignableTo(str))
    }

    @Test
    fun testUnionType() {
        val num = LuaPrimitiveType.NUMBER
        val str = LuaPrimitiveType.STRING
        val union = LuaUnionType(setOf(num, str))

        assertTrue(num.isAssignableTo(union))
        assertTrue(str.isAssignableTo(union))
        assertTrue(union.isAssignableTo(union))
        assertFalse(LuaPrimitiveType.BOOLEAN.isAssignableTo(union))
    }

    @Test
    fun testClassInheritance() {
        val animal = LuaClassType("Animal", localMembers = mapOf("age" to LuaTypeMember("age", LuaPrimitiveType.NUMBER)))
        val dog = LuaClassType("Dog", superTypes = listOf(animal))

        assertTrue(dog.isAssignableTo(animal))
        assertNotNull(dog.resolveMember("age"))
        assertEquals(LuaPrimitiveType.NUMBER, dog.resolveMember("age")?.type)
    }

    @Test
    fun testRecursiveType() {
        // Mock file for context
        val file = myFixture.configureByText("test.lua", "")

        // This is a bit tricky because resolveType is not implemented yet in M1
        // but we can test that it doesn't crash
        val ref = LuaTypeReference("Recursive", file)
        // ref.name is fine
        assertEquals("Recursive", ref.name)
    }
}
