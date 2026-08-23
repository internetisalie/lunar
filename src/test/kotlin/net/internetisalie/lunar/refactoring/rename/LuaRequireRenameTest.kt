package net.internetisalie.lunar.refactoring.rename

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 5 — renaming a Lua file rewrites every `require(...)` that names it
 * (REFACT-01-18, design §2.7 / §3.7).
 *
 * Each case asserts the **whole** consuming line, delimiters included, because the delimiters are
 * user data: rewriting `'app.util'` as `"app.helpers"`, or `[[util]]` as `"helpers"`, is an
 * unrequested edit to the user's file. An assertion that merely looked for the substring
 * `helpers` would be green for all three of those corruptions.
 */
@RunWith(JUnit4::class)
class LuaRequireRenameTest : BasePlatformTestCase() {
    /** TC-18a — the parenthesized form, whose module string is a `LuaTerminalExpr`. */
    @Test
    fun testRenameFileRewritesParenthesizedRequire() {
        val consumer = renameModuleFile("util.lua", "local u = require(\"util\")\n")

        assertConsumerText("local u = require(\"helpers\")\n", consumer)
    }

    /**
     * TC-18b — the paren-less single-quoted form, whose module string is a bare STRING leaf under
     * `LuaArgs`. Both the dotted package prefix and the quote style must survive.
     *
     * The fixture carries `app/util.lua`, which the plan's TC-18b row omits: the rename target has
     * to exist for the refactoring to have a subject, and `LuaRequireReference.resolve` has to
     * reach it or `CachesBasedRefSearcher` never reports the usage.
     */
    @Test
    fun testRenameFilePreservesDottedPrefixAndQuoteStyle() {
        val consumer = renameModuleFile("app/util.lua", "local u = require 'app.util'\n")

        assertConsumerText("local u = require 'app.helpers'\n", consumer)
    }

    /** TC-18c — the long-bracket form; the `[[`/`]]` pair is delimiter, not module name. */
    @Test
    fun testRenameFileRewritesLongBracketRequire() {
        val consumer = renameModuleFile("util.lua", "local u = require [[util]]\n")

        assertConsumerText("local u = require [[helpers]]\n", consumer)
    }

    /**
     * TC-18d (added in Phase 5, not in the plan's TC table) — the decline path.
     *
     * A file name is not a Lua string body: `he"lpers.lua` builds the source `local _ = "he"lpers"`,
     * whose first expression parses as the complete-looking `"he"`. Rewriting the caller to
     * `require("he")` would be a silent corruption of a file the user did not ask to edit, so the
     * reference must leave the literal alone. Reachable in practice — a double quote is a legal
     * character in a POSIX file name, and the rename reached `handleElementRename` with it on the
     * parent commit.
     */
    @Test
    fun testRenameToANameThatIsNotALuaStringBodyLeavesTheRequireAlone() {
        val target = myFixture.addFileToProject("util.lua", "return {}\n")
        val consumer = myFixture.addFileToProject("main.lua", "local u = require(\"util\")\n")

        myFixture.renameElement(target, "he\"lpers.lua")

        assertEquals(
            "the file itself must still be renamed — otherwise this case asserts nothing",
            "he\"lpers.lua",
            target.name,
        )
        assertConsumerText("local u = require(\"util\")\n", consumer)
    }

    private fun renameModuleFile(
        modulePath: String,
        consumerText: String,
    ): PsiFile {
        val target = myFixture.addFileToProject(modulePath, "return {}\n")
        val consumer = myFixture.addFileToProject("main.lua", consumerText)

        myFixture.renameElement(target, "helpers.lua")

        return consumer
    }

    private fun assertConsumerText(
        expected: String,
        consumer: PsiFile,
    ) = assertEquals(
        "the require literal must be rewritten in full, delimiters and package prefix included",
        expected,
        consumer.text,
    )
}
