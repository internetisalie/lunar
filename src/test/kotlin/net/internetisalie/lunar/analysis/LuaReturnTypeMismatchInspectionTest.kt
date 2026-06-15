package net.internetisalie.lunar.analysis

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Real-flow tests for [LuaReturnTypeMismatchInspection] — driven through `enableInspections(...)` +
 * `doHighlighting()` rather than reading `LuaTypesSnapshot` directly, so the actual report path
 * (registration, the return-relatedness filter, element pinning) is exercised.
 */
@RunWith(JUnit4::class)
class LuaReturnTypeMismatchInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaReturnTypeMismatchInspection())
    }

    private fun descriptions(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    private fun looksLikeTypeMismatch(d: String): Boolean =
        d.contains("assignable", ignoreCase = true) ||
            (d.contains("number", ignoreCase = true) && d.contains("string", ignoreCase = true))

    /** Returning a string from a `---@return number` function must be reported. */
    @Test
    fun testReturnTypeMismatchReported() {
        val descs = descriptions(
            """
            ---@return number
            local function f()
                return "not a number"
            end
            """.trimIndent(),
        )
        assertTrue(
            "Expected a return-type-mismatch problem, got: $descs",
            descs.any { looksLikeTypeMismatch(it) },
        )
    }

    /** A matching return type must NOT be flagged. */
    @Test
    fun testMatchingReturnNotReported() {
        val descs = descriptions(
            """
            ---@return number
            local function f()
                return 42
            end
            """.trimIndent(),
        )
        assertTrue(
            "A matching return must not be flagged, got: $descs",
            descs.none { looksLikeTypeMismatch(it) },
        )
    }
}
