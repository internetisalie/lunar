package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaNameSuggestionProvider.getSuggestedNames] (INTENT-03). The EP method is a
 * pure PSI function, so it is invoked directly without driving the Rename UI.
 */
@RunWith(JUnit4::class)
class LuaNameSuggestionProviderTest : BasePlatformTestCase() {

    private val provider = LuaNameSuggestionProvider()

    private fun suggestForExpr(rhs: String): Set<String> {
        myFixture.configureByText("t.lua", "local x = $rhs")
        return runReadAction {
            val expr = PsiTreeUtil.findChildrenOfType(myFixture.file, LuaExpr::class.java)
                .first { it.text == rhs }
            val result = mutableSetOf<String>()
            provider.getSuggestedNames(expr, null, result)
            result
        }
    }

    @Test
    fun testNoPrefixCall() {
        assertTrue(suggestForExpr("compute()").contains("compute"))
    }

    @Test
    fun testMethodCall() {
        assertTrue(suggestForExpr("obj:getName()").contains("name"))
    }

    @Test
    fun testDottedCall() {
        assertTrue(suggestForExpr("db.getUser()").contains("user"))
    }

    @Test
    fun testFieldAccess() {
        assertTrue(suggestForExpr("cfg.timeout").contains("timeout"))
    }

    @Test
    fun testPrefixFollowedByLowercase() {
        assertTrue(suggestForExpr("settings()").contains("settings"))
    }

    @Test
    fun testRenameElementShape() {
        myFixture.configureByText("t.lua", "local y = getUser()")
        val nameElement = runReadAction {
            PsiTreeUtil.collectElementsOfType(myFixture.file, LuaNameRef::class.java)
                .first { it.identifier.text == "y" }
        }
        val result = runReadAction {
            val acc = mutableSetOf<String>()
            provider.getSuggestedNames(nameElement, null, acc)
            acc
        }
        assertTrue("expected 'user' from RHS, got $result", result.contains("user"))
    }
}
