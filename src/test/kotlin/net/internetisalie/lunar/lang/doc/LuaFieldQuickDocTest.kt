package net.internetisalie.lunar.lang.doc

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * BUG-440 — Quick Doc must find a member declared as `---@field`, not only one declared as
 * `function X.y()`.
 *
 * The bug was reported as "openresty fails, love2d works", and that framing was wrong: measured with
 * the real catalog files, a love2d `---@field` fails exactly as an openresty one does. The live
 * checklist happened to test a **function** on love2d and a **field** on openresty. The variable was
 * never the library — it is the declaration form.
 *
 * `resolveDocumentationTarget` obtains its target through `reference.resolve()`, and a `---@field`
 * has no declaration PSI to resolve to: the field exists only as a tag inside a LuaCATS comment
 * (the `AGENTS.md` invariant — LuaCATS tags are not stubbed, they ride a host declaration's stub).
 * So `resolve()` returned null, no target was produced, and Quick Doc rendered
 * "No documentation found".
 *
 * The two controls are what make the other two mean anything: a fix that returned a target for
 * *anything* would satisfy the first two assertions and fail [testAnUnknownMemberStillHasNoTarget].
 */
class LuaFieldQuickDocTest : BasePlatformTestCase() {
    fun testAFieldMemberHasADocumentationTarget() {
        assertEquals(
            "a `---@field` member is documented by its tag — this is BUG-440",
            1,
            targetsFor("local v = Cfg.identi<caret>ty\n"),
        )
    }

    fun testTheRenderedDocCarriesTheFieldsTypeAndProse() {
        val html = htmlFor("local v = Cfg.identi<caret>ty\n")
        assertNotNull("BUG-440: no documentation was produced at all", html)
        assertTrue("the declared type must appear — got: $html", html!!.contains("string"))
        assertTrue("the field's own prose must appear — got: $html", html.contains("The save directory"))
    }

    /** Control 1: the `function X.y()` form already worked and must keep working. */
    fun testAFunctionMemberStillHasADocumentationTarget() {
        assertEquals(1, targetsFor("local v = Cfg.sav<caret>e\n"))
    }

    /**
     * Control 2, and the load-bearing one. A fix that hands back a target for any member access —
     * or that resolves to an arbitrary same-named symbol — passes every assertion above.
     */
    fun testAnUnknownMemberStillHasNoTarget() {
        assertEquals(
            "an undeclared member has nothing to document; showing something would be worse than nothing",
            0,
            targetsFor("local v = Cfg.nosuchme<caret>mber\n"),
        )
    }

    private fun seed() {
        myFixture.addFileToProject(
            "lib.lua",
            """
            ---@class Cfg
            --- The save directory used by the game.
            ---@field identity string
            Cfg = {}

            --- Persists the config.
            function Cfg.save() end
            """.trimIndent(),
        )
    }

    private fun targetsFor(source: String): Int = probe(source).first

    private fun htmlFor(source: String): String? = probe(source).second

    private fun probe(source: String): Pair<Int, String?> {
        seed()
        myFixture.configureByText("consumer.lua", source)
        return runReadAction {
            val offset = myFixture.caretOffset
            val targets = LuaDocumentationTargetProvider().documentationTargets(myFixture.file, offset)
            val html = targets.firstOrNull()?.computeDocumentation()?.toString()
            println("BUG-440 targets=${targets.size} at offset=$offset html=${html?.take(160)}")
            targets.size to html
        }
    }

    @Suppress("unused")
    private fun unusedImportAnchor(): LuaNameRef? = PsiTreeUtil.findChildOfType(myFixture.file, LuaNameRef::class.java)
}
