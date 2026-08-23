package net.internetisalie.lunar.lang.insight

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.analysis.inspections.LuaUndeclaredVariableInspection
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 1, and the executed answer to `REFACT-01-00-DR-07`: a Lua 5.5 `global`
 * declaration made in one file must resolve from every other file.
 *
 * `LuaDeclarationSite` classifies both 5.5 forms as project-wide (`isFileLocal = false`), which is
 * what stops a rename narrowing its search to one file. That classification is only safe if
 * resolution can actually see the declaration across files — a name classified project-wide whose
 * usages are unresolvable elsewhere is exactly the half-applied rename Risk 1.1 describes. Design
 * §2.10 is the half that makes it true: `LuaGlobalAssignmentIndex` records both forms and
 * [net.internetisalie.lunar.lang.navigation.LuaGlobalAssignmentNavigation] re-collects them.
 *
 * The sibling [LuaCrossFileGlobalResolutionTest] covers the pre-5.5 bare-assignment form (BUG-391);
 * neither of its declaration shapes exercises §2.10's two new collectors.
 */
@RunWith(JUnit4::class)
class LuaLua55GlobalCrossFileResolutionTest : BasePlatformTestCase() {
    private var previousLevel: LuaLanguageLevel? = null

    override fun setUp() {
        super.setUp()
        val settings = LuaProjectSettings.getInstance(project).state
        previousLevel = settings.languageLevel
        settings.languageLevel = LuaLanguageLevel.LUA55
        myFixture.enableInspections(LuaUndeclaredVariableInspection())
    }

    override fun tearDown() {
        try {
            // The light project is shared with the rest of the suite; leaving it on 5.5 would make
            // `global` a keyword for every later test that happens to use the name.
            previousLevel?.let { LuaProjectSettings.getInstance(project).state.languageLevel = it }
        } finally {
            super.tearDown()
        }
    }

    private fun undeclaredIn(text: String): List<String> {
        myFixture.configureByText("consumer.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.startsWith("Undeclared variable") }
    }

    @Test
    fun globalVariableDeclaredInAnotherFileResolves() {
        myFixture.addFileToProject("declarer.lua", "global count = 0\n")
        assertEmpty(
            "`global count = 0` elsewhere must not be reported undeclared",
            undeclaredIn("print(count)\n"),
        )
    }

    @Test
    fun everyTargetOfAMultiNameGlobalDeclarationResolves() {
        myFixture.addFileToProject("declarer.lua", "global alpha, beta = 1, 2\n")
        assertEmpty(undeclaredIn("print(alpha)\nprint(beta)\n"))
    }

    @Test
    fun globalFunctionDeclaredInAnotherFileResolves() {
        myFixture.addFileToProject("declarer.lua", "global function greet() end\n")
        assertEmpty(undeclaredIn("greet()\n"))
    }

    @Test
    fun trulyUndeclaredNameIsStillReportedAt55() {
        // The collectors must not silence the inspection wholesale.
        myFixture.addFileToProject("declarer.lua", "global count = 0\n")
        assertEquals(
            "Undeclared variable 'neverDeclaredAnywhere'",
            undeclaredIn("print(neverDeclaredAnywhere)\n").singleOrNull(),
        )
    }

    @Test
    fun aFileScopeLocalOfTheSameNameStillWins() {
        // `local shadowed` then `global shadowed = 2` is not a shape the indexer may record as a
        // project-wide declaration — the same exclusion the bare-assignment form applies.
        myFixture.addFileToProject("declarer.lua", "local shadowed\nglobal shadowed = 2\n")
        assertEquals(
            "Undeclared variable 'shadowed'",
            undeclaredIn("print(shadowed)\n").singleOrNull(),
        )
    }
}
