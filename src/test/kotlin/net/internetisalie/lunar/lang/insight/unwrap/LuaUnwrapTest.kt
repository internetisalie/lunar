package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Drives the real Unwrap/Remove framework (Ctrl+Shift+Delete) over the EDITOR-06 unwrappers. To stay
 * deterministic (the platform shows a popup when several options apply), each test resolves the option set
 * via [LuaUnwrapDescriptor.collectUnwrappers] and invokes the chosen [com.intellij.codeInsight.unwrap.Unwrapper]
 * by its description under a write command — the documented `UnwrapTestCase` pattern (TC-01..TC-09).
 */
class LuaUnwrapTest : BasePlatformTestCase() {

    private fun options(): List<Pair<PsiElement, com.intellij.codeInsight.unwrap.Unwrapper>> {
        val collected = LuaUnwrapDescriptor().collectUnwrappers(project, myFixture.editor, myFixture.file)
        return collected.map { it.first to it.second }
    }

    private fun assertUnwrapped(before: String, after: String, option: String) {
        myFixture.configureByText("a.lua", before)
        val opts = options()
        val chosen = opts.firstOrNull { it.second.getDescription(it.first) == option }
            ?: throw AssertionError("option '$option' not offered; got ${opts.map { it.second.getDescription(it.first) }}")
        WriteCommandAction.runWriteCommandAction(project) {
            chosen.second.unwrap(myFixture.editor, chosen.first)
        }
        myFixture.checkResult(after)
    }

    private fun offeredDescriptions(before: String): List<String> {
        myFixture.configureByText("a.lua", before)
        return options().map { it.second.getDescription(it.first) }
    }

    // TC-01 (06-01) — unwrap plain if hoists the body.
    fun testUnwrapIf() =
        assertUnwrapped("if x then\n  a()\n  b()<caret>\nend", "a()\nb()", "Unwrap 'if'")

    // TC-05 (06-01) — unwrap while.
    fun testUnwrapWhile() =
        assertUnwrapped("while c do\n  step()<caret>\nend", "step()", "Unwrap 'while'")

    // TC-06 (06-01) — unwrap numeric for.
    fun testUnwrapNumericFor() =
        assertUnwrapped("for i = 1, 3 do\n  use(i)<caret>\nend", "use(i)", "Unwrap 'for'")

    // TC-07 (06-01) — unwrap do.
    fun testUnwrapDo() =
        assertUnwrapped("do\n  scoped()<caret>\nend", "scoped()", "Unwrap 'do'")

    // TC-08 (06-01) — unwrap function decl.
    fun testUnwrapFunction() =
        assertUnwrapped("function f()\n  body()<caret>\nend", "body()", "Unwrap 'function'")

    // TC-02 (06-02) — collapse the else branch, keep the then body.
    fun testCollapseElse() =
        assertUnwrapped("if x then\n  a()\nelse\n  b()<caret>\nend", "if x then\n  a()\nend", "Remove 'else' branch")

    // TC-09 (06-02) — three-way if drops only the trailing else, keeps the elseif.
    fun testThreeWayIfDropsElse() =
        assertUnwrapped(
            "if a then\n  p()\nelseif b then\n  q()\nelse\n  r()<caret>\nend",
            "if a then\n  p()\nelseif b then\n  q()\nend",
            "Remove 'else' branch",
        )

    // TC-03 (06-03) — remove the whole enclosing while.
    fun testRemoveWhile() =
        assertUnwrapped("while c do\n  work()<caret>\nend", "", "Remove enclosing block")

    // TC-04 (06-04) — nested function/do/if offers unwrap options for each enclosing construct.
    fun testNestedConstructsOfferAllOptions() {
        val offered = offeredDescriptions("function f()\n  do\n    if q then g()<caret> end\n  end\nend")
        for (expected in listOf("Unwrap 'if'", "Unwrap 'do'", "Unwrap 'function'")) {
            assertTrue("expected '$expected' offered, got $offered", offered.contains(expected))
        }
    }

    // A plain if offers "Unwrap 'if'"; an if/else does NOT (block-unwrap refuses multi-branch).
    fun testMultiBranchIfDoesNotOfferBlockUnwrap() {
        assertTrue(offeredDescriptions("if x then\n  a()<caret>\nend").contains("Unwrap 'if'"))
        val withElse = offeredDescriptions("if x then\n  a()<caret>\nelse\n  b()\nend")
        assertFalse("if/else must not offer plain Unwrap 'if', got $withElse", withElse.contains("Unwrap 'if'"))
        assertTrue("if/else should offer else-collapse, got $withElse", withElse.contains("Remove 'else' branch"))
    }
}
