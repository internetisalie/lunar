package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.navigation.LuaMemberFieldNavigation
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REFACT-01 Phase 1 — [LuaDeclarationSite], the single classifier and normaliser for Lua
 * declaration sites (design §2.1, §3.5).
 *
 * TC-21 — `kindOf` over one fixture per row of design §3.5, positives and negatives.
 * TC-22 — `identifierLeafOf` normalises a declaration node and a `LuaNameRef` to the same leaf.
 * TC-30 — `declarationNodeOf` covers the four kinds Phase 1 adds; without it Safe Delete, which
 *   this phase widens in the same commit, deletes the bare identifier and leaves `global  = 1`.
 * TC-40 — the guard that makes DR-05's whole result hold: what member-field resolution hands back,
 *   and `identifierLeafOf`'s refusal of it.
 */
@RunWith(JUnit4::class)
class LuaDeclarationSiteTest : BasePlatformTestCase() {
    private fun configure(text: String) {
        myFixture.configureByText("test.lua", text)
    }

    private fun configureLua55(text: String) {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA55
        myFixture.configureByText("test.lua", text)
    }

    /** Every IDENTIFIER leaf in the configured file whose text is [name], in document order. */
    private fun identifiers(name: String): List<PsiElement> =
        PsiTreeUtil
            .collectElements(myFixture.file) { it.elementType == LuaElementTypes.IDENTIFIER && it.text == name }
            .toList()

    private fun identifier(name: String): PsiElement =
        requireNotNull(identifiers(name).firstOrNull()) { "No IDENTIFIER leaf '$name' in ${myFixture.file.text}" }

    private fun assertKind(
        expected: LuaDeclarationKind?,
        name: String,
    ) = assertEquals(
        "kindOf('$name') in ${myFixture.file.text}",
        expected,
        LuaDeclarationSite.kindOf(identifier(name)),
    )

    // -------------------------------------------------------------------------
    // TC-21 — one fixture per row of design §3.5
    // -------------------------------------------------------------------------

    @Test
    fun testKindOfEveryDeclarationShape() {
        configure("::done::\ngoto done\n")
        val label = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java))
        assertEquals("row 1: a label declaration", LuaDeclarationKind.LABEL, LuaDeclarationSite.kindOf(label))

        configure("local x = 1\n")
        val keyword = requireNotNull(myFixture.file.findElementAt(0))
        assertNull("row 2: a non-IDENTIFIER leaf is never a declaration", LuaDeclarationSite.kindOf(keyword))
        assertKind(LuaDeclarationKind.LOCAL_VARIABLE, "x")

        configure("for i = 1, 3 do end\n")
        assertKind(LuaDeclarationKind.NUMERIC_FOR_VARIABLE, "i")

        configure("local t = { field = 1 }\n")
        assertKind(null, "field")

        configure("local function helper() end\n")
        assertKind(LuaDeclarationKind.LOCAL_FUNCTION, "helper")

        configure("function greet() end\n")
        assertKind(LuaDeclarationKind.GLOBAL_FUNCTION, "greet")

        configure("M = {}\nfunction M.run() end\n")
        assertKind(LuaDeclarationKind.DOTTED_FUNCTION, "run")

        configure("Obj = {}\nfunction Obj:method() end\n")
        assertKind(LuaDeclarationKind.METHOD_FUNCTION, "method")

        configure("local function f(a) return a end\n")
        assertKind(LuaDeclarationKind.PARAMETER, "a")

        configure("for k, v in pairs(t) do end\n")
        assertKind(LuaDeclarationKind.GENERIC_FOR_VARIABLE, "k")
        assertKind(LuaDeclarationKind.GENERIC_FOR_VARIABLE, "v")

        configure("cfg = {}\n")
        assertKind(LuaDeclarationKind.GLOBAL_VARIABLE, "cfg")
    }

    @Test
    fun testKindOfLua55GlobalDeclarations() {
        configureLua55("global count = 0\n")
        assertKind(LuaDeclarationKind.GLOBAL_VARIABLE, "count")

        configureLua55("global function greet() end\n")
        assertKind(LuaDeclarationKind.GLOBAL_FUNCTION, "greet")
    }

    @Test
    fun testKindOfRejectsEveryNonDeclaration() {
        configure("cfg = {}\nprint(cfg)\n")
        assertEquals(
            "the read in print(cfg) must not classify as a declaration",
            null,
            LuaDeclarationSite.kindOf(identifiers("cfg").last()),
        )

        // Row 14 clause 3: an assignment nested inside a function may be writing to an enclosing
        // local, and no rule here attempts scope resolution.
        configure("function g() cfg = 1 end\n")
        assertKind(null, "cfg")

        // Row 14 clause 4: a name also bound by a file-scope `local` is a local write.
        configure("local cfg\ncfg = 2\n")
        assertEquals(
            "an assignment to a file-scope local is not a global declaration",
            null,
            LuaDeclarationSite.kindOf(identifiers("cfg").last()),
        )

        // Clause 1: a dotted target belongs to LuaMemberFieldIndex, not here.
        configure("t.field = 1\n")
        assertKind(null, "t")
    }

    // -------------------------------------------------------------------------
    // TC-22 — normalisation from both directions
    // -------------------------------------------------------------------------

    @Test
    fun testIdentifierLeafOfNormalisesBothDirections() {
        configure("M = {}\nfunction M.run() end\n")
        val funcDecl = requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java))
        val runLeaf = identifier("run")
        val runNameRef = requireNotNull(runLeaf.parent as? LuaNameRef)

        assertSame(
            "identifierLeafOf(LuaFuncDecl) must be the LAST name segment, not the receiver M",
            runLeaf,
            LuaDeclarationSite.identifierLeafOf(funcDecl),
        )
        assertSame(
            "identifierLeafOf(LuaNameRef) must be that name ref's own leaf",
            runLeaf,
            LuaDeclarationSite.identifierLeafOf(runNameRef),
        )
        assertSame("a declaration leaf normalises to itself", runLeaf, LuaDeclarationSite.identifierLeafOf(runLeaf))
    }

    // -------------------------------------------------------------------------
    // TC-30 — declarationNodeOf over the kinds Phase 1 adds
    // -------------------------------------------------------------------------

    @Test
    fun testDeclarationNodeOfCoversTheNewKinds() {
        configureLua55("global x = 1\n")
        assertSame(
            "global x = 1 must elevate to the whole LuaGlobalVarDecl, or Safe Delete leaves `global  = 1`",
            PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalVarDecl::class.java),
            LuaDeclarationSite.declarationNodeOf(identifier("x")),
        )

        configureLua55("global function f() end\n")
        assertSame(
            "global function f() end must elevate to the whole LuaGlobalFuncDecl",
            PsiTreeUtil.findChildOfType(myFixture.file, LuaGlobalFuncDecl::class.java),
            LuaDeclarationSite.declarationNodeOf(identifier("f")),
        )

        configure("M = {}\nfunction M.run() end\n")
        assertSame(
            "a dotted function name must elevate to its LuaFuncDecl",
            PsiTreeUtil.findChildOfType(myFixture.file, LuaFuncDecl::class.java),
            LuaDeclarationSite.declarationNodeOf(identifier("run")),
        )

        configure("cfg = {}\n")
        assertSame(
            "a single-target file-scope assignment must elevate to the whole statement",
            PsiTreeUtil.findChildOfType(myFixture.file, LuaAssignmentStatement::class.java),
            LuaDeclarationSite.declarationNodeOf(identifier("cfg")),
        )
    }

    // -------------------------------------------------------------------------
    // TC-40 — the two-part coupling DR-05 rests on
    // -------------------------------------------------------------------------

    /**
     * TC-40 — REFACT-01 DR-05. A `t.field` member access must NOT normalise to a declaration site,
     * and the reason it does not is a conjunction of two facts in two files. DR-05 measured both
     * and then deleted its probe, leaving the result recorded in prose and enforced by nothing.
     *
     * 1. `LuaMemberFieldNavigation.find` hands back the field's own `LuaNameRef`
     *    (`memberFieldIdentifier`, `LuaMemberFieldNames.kt:23-28`) — not the enclosing `LuaVar`.
     * 2. `identifierLeafOf`'s `LuaNameRef` branch is guarded — `element.identifier.takeIf
     *    { kindOf(it) != null }` — and a member leaf's grandparent is a `LuaIndexExpr`, which
     *    design §3.5 does not classify, so the branch yields null.
     *
     * **Drop the guard in fact 2 and rename stops refusing `t.field`**: `substituteElementToRename`
     * would take the field leaf as a declaration and rename a member access as though it were a
     * global, with no receiver to constrain it. That is what this case reddens on.
     *
     * Fact 1 is asserted rather than assumed because it is what keeps the dangerous neighbouring
     * branch out of reach: `element is LuaVar -> element.nameRef?.identifier` answers with the
     * **receiver** — measured, `identifierLeafOf` of this fixture's `t.field` `LuaVar` is `t` — so
     * a navigation result that arrived as a `LuaVar` would offer to rename `t` when the user asked
     * for `t.field`. If fact 1 ever changes, re-derive fact 2's answer rather than adjusting this
     * expectation.
     */
    @Test
    fun testAMemberFieldAccessNormalisesToNoDeclarationLeaf() {
        configure("local t = {}\nt.field = 1\nprint(t.field)\n")
        val resolved = LuaMemberFieldNavigation.find(project, "t.field", GlobalSearchScope.projectScope(project))

        assertEquals("t.field is assigned exactly once, so navigation has one result", 1, resolved.size)
        val declarationSite = resolved.single()
        assertTrue(
            "member-field navigation must hand back the field's LuaNameRef, not its LuaVar; got " +
                declarationSite.javaClass.simpleName,
            declarationSite is LuaNameRef,
        )
        assertNull(
            "identifierLeafOf must refuse a member access: it names a table field, which is not a " +
                "Lua declaration site, and treating it as one renames it as a global",
            LuaDeclarationSite.identifierLeafOf(declarationSite),
        )
    }
}
