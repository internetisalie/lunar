package net.internetisalie.lunar.refactoring

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-475: the platform must be able to name a Lua declaration, or it de-camel-cases the PSI
 * implementation class into user-visible text ("Lua Name Ref Impl").
 */
@RunWith(JUnit4::class)
class LuaElementDescriptionProviderTest : BasePlatformTestCase() {
    private fun describeFirstNameRef(
        source: String,
        name: String,
    ): Pair<String, String> {
        val file = myFixture.configureByText("desc.lua", source)
        return runReadAction {
            val ref =
                PsiTreeUtil
                    .findChildrenOfType(file, LuaNameRef::class.java)
                    .first { it.text == name }
            val type = ElementDescriptionUtil.getElementDescription(ref, UsageViewTypeLocation.INSTANCE)
            val short = ElementDescriptionUtil.getElementDescription(ref, UsageViewShortNameLocation.INSTANCE)
            type to short
        }
    }

    @Test
    fun aLocalIsDescribedAsALocalVariable() {
        val (type, short) = describeFirstNameRef("local config = 2\nprint(config)", "config")
        assertEquals("local variable", type)
        assertEquals("config", short)
    }

    @Test
    fun aGlobalIsDescribedAsAGlobalVariable() {
        val (type, _) = describeFirstNameRef("gconfig = 1\nprint(gconfig)", "gconfig")
        assertEquals("global variable", type)
    }

    @Test
    fun aParameterIsDescribedAsAParameter() {
        val (type, _) = describeFirstNameRef("local function f(arg)\n  return arg\nend", "arg")
        assertEquals("parameter", type)
    }

    /** The defect itself: no description must ever be the de-camel-cased implementation class. */
    @Test
    fun noDescriptionLeaksThePsiImplementationClassName() {
        val (type, short) = describeFirstNameRef("local config = 2\nprint(config)", "config")
        assertFalse("type leaked the PSI class: $type", type.contains("Lua Name Ref"))
        assertFalse("short name leaked the PSI class: $short", short.contains("Lua Name Ref"))
    }
}
