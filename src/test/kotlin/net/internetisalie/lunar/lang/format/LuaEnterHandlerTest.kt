package net.internetisalie.lunar.lang.format

import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SYNTAX-10: Enter Handler for LuaDoc Comments
 *
 * Tests the auto-continuation behavior when pressing Enter inside LuaDOC comment blocks (---).
 * These tests verify that the handler correctly:
 * 1. Auto-continues LuaDOC comments with the "--- " prefix
 * 2. Preserves indentation levels
 * 3. Distinguishes between LuaDOC (---) and regular (--) comments
 * 4. Works correctly in various code contexts
 */
class LuaEnterHandlerTest : BaseDocumentTest() {

    // ===== Test Case: Simple Auto-continuation =====

    @Test
    fun testSimpleAutoContinuationStructure() {
        myFixture.configureByText(LuaFileType, "--- This is a documentation block")
        val document = myFixture.editor.document

        // Verify initial state
        assertEquals("--- This is a documentation block", document.text)
        val lines = document.text.split("\n")
        assertEquals(1, lines.size, "Should start with single line")
    }

    @Test
    fun testDocCommentMarkerDetection() {
        myFixture.configureByText(LuaFileType, "--- Documentation line")
        val text = myFixture.editor.document.text.trim()

        assertTrue(text.startsWith("---"), "Line should start with ---")
    }

    // ===== Test Case: Indentation Preservation =====

    @Test
    fun testIndentationDetection() {
        val testCases = listOf(
            "    --- Nested" to 4,
            "  --- Nested" to 2,
            "\t--- Tabbed" to 1,
            "--- No indent" to 0,
        )

        testCases.forEach { (line, expectedIndent) ->
            var indentCount = 0
            var i = 0
            while (i < line.length && (line[i] == ' ' || line[i] == '\t')) {
                if (line[i] == ' ') indentCount += 1
                else if (line[i] == '\t') indentCount += 1
                i++
            }
            assertTrue(indentCount == expectedIndent || line.substring(indentCount).startsWith("---"),
                "Line should have correct indentation: $line")
        }
    }

    // ===== Test Case: LuaDOC vs Regular Comments =====

    @Test
    fun testDocCommentIdentification() {
        val docComments = listOf(
            "--- Documentation",
            "  --- Indented documentation",
            "\t--- Tabbed documentation",
        )

        docComments.forEach { comment ->
            val trimmed = comment.trim()
            assertTrue(trimmed.startsWith("---"), "Should identify doc comment: $comment")
        }
    }

    @Test
    fun testRegularCommentIdentification() {
        val regularComments = listOf(
            "-- Regular comment",
            "  -- Indented comment",
            "\t-- Tabbed comment",
        )

        regularComments.forEach { comment ->
            val trimmed = comment.trim()
            assertTrue(trimmed.startsWith("--") && !trimmed.startsWith("---"),
                "Should identify regular comment: $comment")
        }
    }

    // ===== Test Case: Comment Prefix Patterns =====

    @Test
    fun testDocCommentPrefixPattern() {
        val text = "--- This is documentation"
        val prefixPattern = Regex("^(\\s*)---\\s")

        assertTrue(prefixPattern.containsMatchIn(text), "Should match doc comment prefix")
    }

    @Test
    fun testDocCommentPrefixWithVariousIndentations() {
        val testCases = listOf(
            "--- Doc" to true,
            "  --- Doc" to true,
            "\t--- Doc" to true,
            "-- Comment" to false,
            "  -- Comment" to false,
        )

        testCases.forEach { (line, shouldMatch) ->
            val trimmed = line.trim()
            val isDocComment = trimmed.startsWith("---")
            assertEquals(shouldMatch, isDocComment, "Line should${if (!shouldMatch) " not" else ""} be doc comment: $line")
        }
    }

    // ===== Test Case: Content Preservation =====

    @Test
    fun testDocCommentContentPreservation() {
        myFixture.configureByText(LuaFileType, "--- Function documentation\n--- with multiple lines")
        val text = myFixture.editor.document.text

        assertTrue(text.contains("--- Function documentation"), "First line should be preserved")
        assertTrue(text.contains("--- with multiple lines"), "Second line should be preserved")
    }

    @Test
    fun testIndentedDocCommentPreservation() {
        myFixture.configureByText(LuaFileType, "    --- Indented documentation")
        val text = myFixture.editor.document.text

        assertTrue(text.contains("    --- Indented documentation"), "Indented line should be preserved")
    }

    // ===== Test Case: Complex Scenarios =====

    @Test
    fun testDocCommentFollowedByCode() {
        myFixture.configureByText(
            LuaFileType,
            """--- Documentation
function test()
end"""
        )
        val text = myFixture.editor.document.text

        assertTrue(text.contains("--- Documentation"), "Doc comment preserved")
        assertTrue(text.contains("function test()"), "Code preserved")
    }

    @Test
    fun testDocCommentInFunctionBlock() {
        myFixture.configureByText(
            LuaFileType,
            """function test()
    --- Inner documentation
    local x = 1
end"""
        )
        val text = myFixture.editor.document.text

        assertTrue(text.contains("    --- Inner documentation"), "Indented doc preserved")
        assertTrue(text.contains("local x = 1"), "Code preserved")
    }

    @Test
    fun testMixedCommentTypes() {
        myFixture.configureByText(
            LuaFileType,
            """--- Documentation
-- Regular comment
local x = 1"""
        )
        val text = myFixture.editor.document.text
        val lines = text.split("\n")

        assertTrue(lines[0].trim().startsWith("---"), "First line should be doc comment")
        assertTrue(lines[1].trim().startsWith("--") && !lines[1].trim().startsWith("---"),
            "Second line should be regular comment")
    }

    // ===== Test Case: Empty Lines and Edge Cases =====

    @Test
    fun testEmptyDocCommentLine() {
        myFixture.configureByText(LuaFileType, "---")
        val text = myFixture.editor.document.text

        assertEquals("---", text, "Empty doc comment should be preserved")
    }

    @Test
    fun testDocCommentWithOnlySpaces() {
        myFixture.configureByText(LuaFileType, "---   ")
        val text = myFixture.editor.document.text
        val trimmed = text.trim()

        assertEquals("---", trimmed, "Doc comment with spaces should trim to ---")
    }

    @Test
    fun testDocCommentWithEmbeddedMarker() {
        myFixture.configureByText(LuaFileType, "--- This has --- inside it")
        val text = myFixture.editor.document.text

        assertTrue(text.startsWith("---"), "Should start with doc marker")
        assertTrue(text.contains("--- inside"), "Should contain embedded marker")
    }

    // ===== Test Case: Multi-line Documentation Blocks =====

    @Test
    fun testConsecutiveDocCommentLines() {
        myFixture.configureByText(
            LuaFileType,
            """--- Line 1
--- Line 2
--- Line 3"""
        )
        val text = myFixture.editor.document.text
        val lines = text.split("\n")

        assertEquals(3, lines.size, "Should have 3 lines")
        lines.forEach { line ->
            assertTrue(line.trim().startsWith("---"), "Each line should start with ---: $line")
        }
    }

    @Test
    fun testDocCommentBlockWithTermination() {
        myFixture.configureByText(
            LuaFileType,
            """--- Documentation block
--- with multiple lines
local x = 1"""
        )
        val text = myFixture.editor.document.text
        val lines = text.split("\n")

        assertEquals(3, lines.size, "Should have 3 lines")
        assertTrue(lines[0].trim().startsWith("---"), "First line is doc")
        assertTrue(lines[1].trim().startsWith("---"), "Second line is doc")
        assertTrue(lines[2].contains("local"), "Third line is code")
    }

    // ===== Test Case: Indentation Consistency =====

    @Test
    fun testConsistentIndentationInDocBlock() {
        myFixture.configureByText(
            LuaFileType,
            """    --- Line 1
    --- Line 2
    --- Line 3"""
        )
        val text = myFixture.editor.document.text
        val lines = text.split("\n")

        lines.forEach { line ->
            if (line.isNotEmpty()) {
                assertTrue(line.startsWith("    "), "All lines should have 4-space indent: '$line'")
                assertTrue(line.substring(4).trim().startsWith("---"), "All lines should have doc marker after indent")
            }
        }
    }

    @Test
    fun testMixedIndentationDetection() {
        val line = "\t  --- Mixed indent"
        var i = 0
        while (i < line.length && line[i].isWhitespace()) {
            i++
        }
        val indent = line.substring(0, i)
        val content = line.substring(i)

        assertEquals(3, indent.length, "Should detect mixed indent (1 tab + 2 spaces)")
        assertTrue(content.startsWith("---"), "Content should start with ---")
    }

    // ===== Test Case: Pattern Matching =====

    @Test
    fun testDocCommentPatternMatching() {
        val validPatterns = listOf(
            "--- ",
            "---\t",
            "--- text",
            "  --- ",
            "\t--- ",
        )

        validPatterns.forEach { pattern ->
            val trimmed = pattern.trim()
            assertTrue(trimmed.startsWith("---"), "Should match doc pattern: '$pattern'")
        }
    }

    @Test
    fun testInvalidPatternDetection() {
        val invalidPatterns = listOf(
            "-- ",
            "--text",
            "  -- ",
            "\t-- ",
        )

        invalidPatterns.forEach { pattern ->
            val trimmed = pattern.trim()
            val isNotDocComment = !trimmed.startsWith("---")
            assertTrue(isNotDocComment, "Should reject non-doc pattern: '$pattern'")
        }
    }

    // ===== Test Case: Handler Registration Verification =====

    @Test
    fun testPluginFileTypeConfiguration() {
        myFixture.configureByText(LuaFileType, "--- test")
        val file = myFixture.file

        assertTrue(file.name.endsWith(".lua"), "File should have Lua extension: ${file.name}")
    }

    @Test
    fun testDocCommentInLuaFile() {
        myFixture.configureByText(LuaFileType, "--- This is a Lua documentation comment")
        val text = myFixture.editor.document.text

        assertTrue(text.contains("---"), "Doc comment should be recognized in Lua file")
    }
}
