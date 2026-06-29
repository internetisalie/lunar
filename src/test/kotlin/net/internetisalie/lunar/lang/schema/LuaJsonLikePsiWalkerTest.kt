package net.internetisalie.lunar.lang.schema

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import com.intellij.psi.util.PsiTreeUtil

class LuaJsonLikePsiWalkerTest : BasePlatformTestCase() {

    fun testIsObjectTable() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "return { a = 1, ['b'] = 2, 3 }") as? LuaFile)
        val table = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaTableConstructor::class.java))
        assertTrue(LuaValueAdapter.isObjectTable(table))
    }

    fun testIsArrayTable() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "return { 1, 2, 3 }") as? LuaFile)
        val table = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaTableConstructor::class.java))
        assertFalse(LuaValueAdapter.isObjectTable(table))
    }

    fun testGetRootsShapeA() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "a = 1\nb = 2") as? LuaFile)
        val roots = LuaJsonLikePsiWalker.INSTANCE.getRoots(file)
        assertSize(1, roots)
        assertEquals(file, roots.first())
    }

    fun testGetRootsShapeB() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "return { a = 1 }") as? LuaFile)
        val roots = LuaJsonLikePsiWalker.INSTANCE.getRoots(file)
        assertSize(1, roots)
        assertTrue(roots.first() is LuaTableConstructor)
    }

    fun testFindPositionInTable() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "return { a = { b = 42 } }") as? LuaFile)
        val number = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaTerminalExpr::class.java))
        
        val position = requireNotNull(LuaJsonLikePsiWalker.INSTANCE.findPosition(number, true))
        assertEquals("/a/b", position.toJsonPointer())
    }

    fun testFindPositionInFile() {
        val file = requireNotNull(myFixture.configureByText("test.lua", "a = { b = 42 }") as? LuaFile)
        val number = requireNotNull(PsiTreeUtil.findChildOfType(file, LuaTerminalExpr::class.java))
        
        val position = requireNotNull(LuaJsonLikePsiWalker.INSTANCE.findPosition(number, true))
        assertEquals("/a/b", position.toJsonPointer())
    }
}
