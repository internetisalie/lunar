package net.internetisalie.lunar.lang.insight

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-478: the Find Usages **action**, driven from the editor at a declaration caret.
 *
 * `ReferencesSearch` already answered these fixtures correctly before the fix
 * ([LuaColonCallFindUsagesTest]), so a test that calls the search API would pass with the defect
 * present and prove nothing. This drives `IdeActions.ACTION_FIND_USAGES` through the editor's own
 * data context, which is the layer that decided to refuse: `targetVariants` reads
 * `UsageView.USAGE_TARGETS_KEY`, whose `DefaultUsageTargetProvider` gates on
 * `FindManager.canFindUsages(<the element TargetElementUtil produced>)` — measured to be the
 * `LuaNameRef` composite at a colon or dotted declaration, which [LuaFindUsagesProvider] rejects.
 */
@RunWith(JUnit4::class)
class LuaDeclarationFindUsagesActionTest : BasePlatformTestCase() {
    private fun usageOffsets(
        text: String,
        caret: Int,
    ): List<Int> {
        myFixture.configureByText("test.lua", text)
        myFixture.editor.caretModel.moveToOffset(caret)
        val usages: Collection<Usage> = myFixture.testFindUsagesUsingAction()
        return ReadAction.compute<List<Int>, RuntimeException> {
            usages
                .filterIsInstance<UsageInfo2UsageAdapter>()
                .map { it.usageInfo.navigationOffset }
                .sorted()
        }
    }

    /** The defect: the declaration caret was refused with "Cannot search for usages from this location." */
    @Test
    fun aColonMethodDeclarationFindsItsCallSiteThroughTheAction() {
        assertEquals(
            "function t:m() must find the t:m() call site from its own declaration caret",
            listOf(34),
            usageOffsets("local t = {}\nfunction t:m() end\nt:m()\n", 24),
        )
    }

    /** The same refusal, on the other function-name shape that resolves to nothing at its declaration. */
    @Test
    fun aDottedFunctionDeclarationFindsItsCallSiteThroughTheAction() {
        assertEquals(
            "function M.run() must find the M.run() call site from its own declaration caret",
            listOf(36),
            usageOffsets("local M = {}\nfunction M.run() end\nM.run()\n", 24),
        )
    }

    /**
     * The control. A global function declaration searched fine before the fix and must keep doing
     * so — without it a green run cannot tell "this declaration is special" from "the action works".
     */
    @Test
    fun aGlobalFunctionDeclarationKeepsFindingItsCallSitesThroughTheAction() {
        assertEquals(
            "function gfun() must find both call sites from its declaration caret",
            listOf(20, 27),
            usageOffsets("function gfun() end\ngfun()\ngfun()\n", 9),
        )
    }
}
