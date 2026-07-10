package net.internetisalie.lunar.redis.console

import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertNotNull as ktAssertNotNull

/**
 * Error-link filtering coverage for [LuaRedisErrorLinkFilter] (design §3.6, TC-CON-3).
 *
 * The `user_script:<N>` / `@user_script: <N>` references hyperlink to the run's script file, converting
 * the 1-based server line to the 0-based editor line; an unresolvable URL yields `null` (no `!!`). A
 * light fixture provides a real `VirtualFile` whose URL the filter resolves.
 */
class TestLuaRedisErrorLinkFilter : BasePlatformTestCase() {

    /** TC-CON-3: `user_script:12:` links to editor line 11 (12 → 0-based). */
    fun testColonFormLinksToConvertedLine() {
        val psiFile = myFixture.configureByText("script.lua", "return 1")
        val filter = LuaRedisErrorLinkFilter(project, psiFile.virtualFile.url)

        val line = "user_script:12: bad arg\n"
        val result = filter.applyFilter(line, line.length)

        val nonNullResult = ktAssertNotNull(result, "expected a hyperlink result")
        assertTrue(
            "hyperlink resolves to a file",
            nonNullResult.firstHyperlinkInfo is OpenFileHyperlinkInfo,
        )
        // Offsets cover the matched reference within the line.
        val range = nonNullResult.resultItems.first()
        val matched = line.substring(range.highlightStartOffset, range.highlightEndOffset)
        assertEquals("user_script:12", matched)
    }

    /** TC-CON-3: `@user_script: 7` links to editor line 6 (7 → 0-based); the `@`/whitespace form matches. */
    fun testAtWhitespaceFormMatches() {
        val psiFile = myFixture.configureByText("script.lua", "return 1")
        val filter = LuaRedisErrorLinkFilter(project, psiFile.virtualFile.url)

        val line = "error @user_script: 7 boom\n"
        val result = filter.applyFilter(line, line.length)

        val nonNullResult = ktAssertNotNull(result)
        val range = nonNullResult.resultItems.first()
        val matched = line.substring(range.highlightStartOffset, range.highlightEndOffset)
        assertEquals("@user_script: 7", matched)
    }

    /** TC-CON-3: an unresolvable file URL yields no link (null), never a `!!` dereference. */
    fun testUnresolvableUrlReturnsNull() {
        val filter = LuaRedisErrorLinkFilter(project, "file:///no/such/file-xyz.lua")
        val line = "user_script:3: nope\n"
        assertNull(filter.applyFilter(line, line.length))
    }

    /** A line with no reference yields no link. */
    fun testNoReferenceReturnsNull() {
        val psiFile = myFixture.configureByText("script.lua", "return 1")
        val filter = LuaRedisErrorLinkFilter(project, psiFile.virtualFile.url)
        val line = "just some output\n"
        assertNull(filter.applyFilter(line, line.length))
    }
}
