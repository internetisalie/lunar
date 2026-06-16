package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaIndexExpr
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for [LuaNameDeriver.baseName] (INTENT-03 §3): raw-name extraction plus prefix
 * stripping. PSI is built with the fixture; the pure function is asserted directly.
 */
@RunWith(JUnit4::class)
class LuaNameDeriverTest : BasePlatformTestCase() {

    private fun firstExpr(rhs: String): LuaExpr {
        myFixture.configureByText("t.lua", "local x = $rhs")
        return runReadAction {
            PsiTreeUtil.findChildrenOfType(myFixture.file, LuaExpr::class.java)
                .first { it.text == rhs }
        }
    }

    private fun deriveFrom(rhs: String): String? = runReadAction { LuaNameDeriver.baseName(firstExpr(rhs)) }

    @Test
    fun testFunctionCallPrefixStripped() {
        assertEquals("user", deriveFrom("getUser()"))
    }

    @Test
    fun testFunctionCallNoPrefix() {
        assertEquals("compute", deriveFrom("compute()"))
    }

    @Test
    fun testMethodCall() {
        assertEquals("name", deriveFrom("obj:getName()"))
    }

    @Test
    fun testDottedCall() {
        assertEquals("user", deriveFrom("db.getUser()"))
    }

    @Test
    fun testFieldAccess() {
        myFixture.configureByText("t.lua", "local x = cfg.timeout")
        val index = runReadAction {
            PsiTreeUtil.findChildrenOfType(myFixture.file, LuaIndexExpr::class.java).last()
        }
        val expr = runReadAction { PsiTreeUtil.getParentOfType(index, LuaExpr::class.java, false)!! }
        assertEquals("timeout", runReadAction { LuaNameDeriver.baseName(expr) })
    }

    @Test
    fun testPrefixFollowedByLowercaseNotStripped() {
        assertEquals("settings", deriveFrom("settings()"))
    }

    @Test
    fun testGetterNotStripped() {
        assertEquals("getter", deriveFrom("getter()"))
    }
}
