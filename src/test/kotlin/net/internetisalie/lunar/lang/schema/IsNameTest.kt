package net.internetisalie.lunar.lang.schema
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.schema.LuaJsonLikePsiWalker

class IsNameTest : BasePlatformTestCase() {
    fun testIsName() {
        val file = myFixture.configureByText("test.lua", "op<caret>")
        val leaf = file.findElementAt(myFixture.caretOffset - 1)
        assertNotNull(leaf)
        val walker = LuaJsonLikePsiWalker.INSTANCE
        val checkable = leaf?.let { walker.findElementToCheck(it) }
        assertNotNull(checkable)
        assertEquals("op", checkable?.text)
        assertTrue(checkable is net.internetisalie.lunar.lang.psi.LuaExpr)
    }
}
