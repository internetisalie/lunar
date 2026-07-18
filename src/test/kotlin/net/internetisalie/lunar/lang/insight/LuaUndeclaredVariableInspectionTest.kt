package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaUndeclaredVariableInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    private fun undeclaredWarnings(text: String): List<String> {
        myFixture.configureByText("test.lua", text)
        return myFixture.doHighlighting()
            .filter { it.description?.startsWith("Undeclared variable") == true }
            .map { it.description }
    }

    private fun assertNoUndeclared(text: String) {
        val warnings = undeclaredWarnings(text)
        assertTrue("Expected no undeclared warnings but found: $warnings", warnings.isEmpty())
    }

    private fun assertUndeclared(text: String, vararg names: String) {
        val warnings = undeclaredWarnings(text)
        assertEquals("Warnings: $warnings", names.size, warnings.size)
        for (name in names) {
            assertTrue(
                "Expected warning for '$name' but found: $warnings",
                warnings.contains("Undeclared variable '$name'"),
            )
        }
    }

    // TC-01: Simple Local Resolution (INSP-01-01)
    @Test
    fun testLocalResolves() {
        assertNoUndeclared(
            """
            local x = 10
            print(x)
            """.trimIndent(),
        )
    }

    // TC-02: Undeclared Global (INSP-01-04)
    @Test
    fun testUndeclaredGlobalFlagged() {
        assertUndeclared("print(undeclaredVar)", "undeclaredVar")
    }

    // TC-03: Used Before Local Declaration (INSP-01-05)
    @Test
    fun testUsedBeforeLocalFlaggedOnce() {
        assertUndeclared(
            """
            print(x)
            local x = 10
            """.trimIndent(),
            "x",
        )
    }

    // TC-04: Standard Library Global (INSP-01-03)
    @Test
    fun testStandardLibraryNotFlagged() {
        assertNoUndeclared("print(math.abs(-10))")
    }

    // TC-05: Cross-File Global (INSP-01-02)
    @Test
    fun testCrossFileGlobalNotFlagged() {
        myFixture.addFileToProject("a.lua", "function Helper() end")
        assertNoUndeclared("Helper()")
    }

    // TC-06: Write Target Excluded (INSP-01-06)
    @Test
    fun testWriteTargetExcludedIndexBaseFlagged() {
        assertUndeclared(
            """
            newGlobal = 5
            existing.field = 6
            """.trimIndent(),
            "existing",
        )
    }

    // TC-07: Additional Globals Allowlist (INSP-01-07)
    @Test
    fun testAdditionalGlobalsAllowlist() {
        LuaProjectSettings.getInstance(project).state.additionalGlobals.add("love")
        try {
            assertNoUndeclared("""love.graphics.print("hi")""")
        } finally {
            LuaProjectSettings.getInstance(project).state.additionalGlobals.remove("love")
        }
    }

    // TC-08: Diagnostic Suppression (INSP-01-08)
    @Test
    fun testDiagnosticDisableNextLineSuppression() {
        assertNoUndeclared(
            """
            ---@diagnostic disable-next-line: undefined-global
            print(mysteryGlobal)
            """.trimIndent(),
        )
    }

    // TC-09: Luacheck Suppression (INSP-01-08)
    @Test
    fun testLuacheckIgnoreSuppression() {
        assertNoUndeclared("print(mysteryGlobal) -- luacheck: ignore mysteryGlobal")
    }

    // MAINT-26-04 / TC7: an inline `-- luacheck: ignore` scopes to its own line only; an
    // undeclared global on the FOLLOWING line is still flagged.
    @Test
    fun testInlineLuacheckIgnoreDoesNotSuppressNextLine() {
        assertUndeclared(
            """
            local y = 1 -- luacheck: ignore
            print(mysteryGlobal)
            """.trimIndent(),
            "mysteryGlobal",
        )
    }

    // MAINT-26-04 / TC6: a `disable: undefined-global` block is NOT closed by an
    // `enable` naming an unrelated diagnostic — the block stays open past it.
    @Test
    fun testEnableUnrelatedNameLeavesUndefinedGlobalBlockOpen() {
        assertNoUndeclared(
            """
            ---@diagnostic disable: undefined-global
            print(firstGlobal)
            ---@diagnostic enable: unused
            print(secondGlobal)
            """.trimIndent(),
        )
    }

    // MAINT-26-04 / TC6 (complement): a matching `enable: undefined-global` DOES close the
    // block, so a global after the enable is flagged again.
    @Test
    fun testEnableMatchingNameClosesUndefinedGlobalBlock() {
        assertUndeclared(
            """
            ---@diagnostic disable: undefined-global
            print(firstGlobal)
            ---@diagnostic enable: undefined-global
            print(secondGlobal)
            """.trimIndent(),
            "secondGlobal",
        )
    }

    // TC-10: Underscore-Prefixed Globals (INSP-01-04)
    @Test
    fun testUnderscorePrefixedSuppressed() {
        assertNoUndeclared("print(_ENV_PLACEHOLDER)")
    }

    // TC-11: Function-Name Head (INSP-01-06)
    // Plain global function declarations are never flagged.
    //
    // DEVIATION FROM DESIGN: the design's TC-11 also expects the head of a dotted
    // declaration (`function undeclaredTable.method() end`) to be flagged as a read of the
    // table being indexed. The existing `LuaNameReference` resolves a func-name head ONLY to
    // itself in every case (verified: a declared `local t` / global `t = {}` base resolves to
    // the head leaf, not to the real declaration). There is therefore no resolver signal that
    // distinguishes a truly-undeclared dotted base from a legitimately-declared one. To keep
    // this enabled-by-default WARNING free of false positives on `function declared.m() end`,
    // the func-name head is classified as a non-read and never flagged. Flagging an
    // undeclared dotted-function base is deferred (see requirements Future Work).
    @Test
    fun testFunctionNameHead() {
        assertNoUndeclared(
            """
            function PlainGlobal() end
            function undeclaredTable.method() end
            """.trimIndent(),
        )
    }

    // Regression guard: for-loop variables resolve in the body (numeric + generic).
    @Test
    fun testForLoopVariablesNotFlagged() {
        assertNoUndeclared(
            """
            for k, v in pairs({}) do print(v) print(k) end
            for i = 1, 10 do print(i) end
            """.trimIndent(),
        )
    }
}
