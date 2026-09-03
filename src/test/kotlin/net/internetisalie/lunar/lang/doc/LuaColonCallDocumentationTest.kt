package net.internetisalie.lunar.lang.doc

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 — Quick Documentation on a colon call site **retargets** as well as gains.
 *
 * Where the site had no doc at all it gains the method's; where a same-named global function, local
 * function or parameter was in scope it *retargets* from that declaration's doc to the method's.
 *
 * **The assertion reads the target's anchored element, not its class.** Both declarations yield the
 * same `LuaCatsDocumentationTarget`, which is exactly why DR-05 — which recorded only the class —
 * missed the retargeting and DR-06 had to find it. A case asserting the target class cannot see this
 * change at all.
 *
 * Covers `requirements.md` case 30.
 */
@RunWith(JUnit4::class)
class LuaColonCallDocumentationTest : BasePlatformTestCase() {
    /**
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — one target again, anchored on `function m() end` at offset 0.
     * The target *count* is 1 on both sides, so only the anchor separates them.
     */
    @Test
    fun documentationRetargetsFromASameNamedGlobalToTheMethod() {
        myFixture.configureByText(
            "test.lua",
            "function m() end\nlocal t = {}\nfunction t:m() end\nt:m()\n",
        )

        val targets = LuaDocumentationTargetProvider().documentationTargets(myFixture.file, 51)

        assertEquals("exactly one documentation target at the colon call site", 1, targets.size)
        val anchor = requireNotNull(anchorOf(targets.first())) { "the target anchored on nothing" }
        assertEquals("the method's own declaration is documented", 30, anchor.textRange.startOffset)
        assertEquals("function t:m() end", anchor.text)
    }

    /**
     * The gain half, on case 23's fixture: the call site had **no** doc target before this feature
     * and now has the method's. Kept alongside the retarget so a reader does not take the retarget
     * for the whole effect.
     */
    @Test
    fun documentationIsGainedWhereTheCallSiteHadNone() {
        myFixture.configureByText("test.lua", "local t = {}\nfunction t:m() end\nlocal m = 1\nt:m()\n")

        val targets = LuaDocumentationTargetProvider().documentationTargets(myFixture.file, 46)

        assertEquals("exactly one documentation target", 1, targets.size)
        val anchor = requireNotNull(anchorOf(targets.first())) { "the target anchored on nothing" }
        assertEquals("the method's own declaration is documented", 13, anchor.textRange.startOffset)
    }

    /**
     * The anchored element the target navigates to. `LuaCatsDocumentationTarget.navigatable` is its
     * own `element`, so this reads the documented declaration rather than the target's type.
     */
    private fun anchorOf(target: DocumentationTarget): PsiElement? = target.navigatable as? PsiElement
}
