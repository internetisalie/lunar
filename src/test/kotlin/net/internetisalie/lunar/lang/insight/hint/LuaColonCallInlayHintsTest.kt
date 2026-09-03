package net.internetisalie.lunar.lang.insight.hint

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 — parameter-name inlay hints appear at a colon call site where a same-named **stdlib
 * global** used to suppress them.
 *
 * `LuaParameterInlayHintsProvider.isStdlibCall` (`:191`) resolves the callee and asks whether the
 * answer is a stdlib declaration. Pre-feature, `t:print(…)`'s member name resolved lexically to the
 * global `print`, so the call was classified stdlib and its hints suppressed; the member name is a
 * table key on `t` and never named that global.
 *
 * The `t:emit` control separates the stdlib clause from the hint machinery: it is the same fixture
 * with a member name no stdlib global shares, and its hints are identical on both sides of the
 * change. Without it, a mutation that broke hint rendering outright would look like this feature.
 *
 * Covers `requirements.md` case 24.
 */
@RunWith(JUnit4::class)
class LuaColonCallInlayHintsTest : BasePlatformTestCase() {
    /**
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — the offsets fall from `[7, 55, 58]` to `[7]`, because
     * `isStdlibCall` classifies the call stdlib again by the same-named global.
     */
    @Test
    fun aColonCallShadowedByAStdlibGlobalRegainsItsParameterHints() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:print(alpha, beta) end\nt:print(1, 2)\n",
        )

        assertEquals(
            "hints must appear at both argument offsets of t:print(1, 2)",
            listOf(7, 55, 58),
            inlineInlayOffsets(),
        )
    }

    /**
     * The control. `emit` is not a stdlib global, so `isStdlibCall` answered `false` before this
     * feature as well and these offsets are unchanged by it — which is what makes the case above an
     * observation about the stdlib clause rather than about hints in general.
     */
    @Test
    fun aColonCallWithNoSameNamedGlobalKeepsItsHintsOnBothSides() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:emit(alpha, beta) end\nt:emit(1, 2)\n",
        )

        assertEquals(
            "the control's hints are the same before and after the change",
            listOf(7, 53, 56),
            inlineInlayOffsets(),
        )
    }

    private fun inlineInlayOffsets(): List<Int> {
        myFixture.doHighlighting()
        return myFixture.editor.inlayModel
            .getInlineElementsInRange(0, myFixture.file.textLength)
            .map { it.offset }
            .sorted()
    }
}
