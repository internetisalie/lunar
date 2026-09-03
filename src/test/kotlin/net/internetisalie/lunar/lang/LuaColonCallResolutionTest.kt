package net.internetisalie.lunar.lang

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaMethodExpr
import net.internetisalie.lunar.lang.psi.LuaNameRef
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-01 / NAV-13-04 / NAV-13-05: what a colon call site's member name resolves to.
 *
 * `t:m(...)` is sugar for `t.m(t, ...)`, so the member name is a table key and never a variable —
 * lookup goes through the *receiver's* inferred type, and a shape nobody enumerated resolves to
 * **null** rather than to a plausible-but-wrong target.
 *
 * Covers `requirements.md` cases 1–6 (accepted shapes), 9–14 (refusals with a reachable falsifier)
 * and 15 (the reach pins, which have none — see [selfReceiverDoesNotResolve]).
 *
 * **One `configureByText` per test method.** `LuaTypeManagerImpl` searches
 * `GlobalSearchScope.allScope(project)`, so a sibling fixture declaring the same class name binds a
 * union arm to the wrong file and manufactures a false result (TYPE-13 requirements case 17). The
 * exceptions are the two cross-file rows, which need a second file by construction.
 *
 * Every offset asserted here is transcribed from the prototype run recorded in `risks-and-gaps.md`
 * DR-02, not chosen — see each row of `requirements.md`'s test-case table.
 */
@RunWith(JUnit4::class)
class LuaColonCallResolutionTest : BasePlatformTestCase() {
    // -------------------------------------------------------------------------
    // Accepted shapes — requirements.md cases 1-6
    // -------------------------------------------------------------------------

    /**
     * Case 1 — a plain local table. Falsifier: delete the colon branch from
     * `LuaNameReference.multiResolve`; executed, `resolve = null`.
     */
    @Test
    fun plainLocalTableReceiverResolvesToItsMethodDeclaration() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nt:m()\n",
        )
        assertColonMemberResolvesToLeafAt(24)
    }

    /**
     * Case 2 — an in-file global table. A **reach pin**, not independent acceptance: no mutation
     * separates it from case 1, because a colon call site resolves through the *call receiver* and
     * TYPE-13 Gap 2.9 measured that handle surviving `declareFileGlobals`-as-a-no-op. Recorded under
     * `risks-and-gaps.md` "Test Case Gaps"; it must not be "simplified" away as a duplicate of case 1.
     */
    @Test
    fun inFileGlobalTableReceiverResolvesToItsMethodDeclaration() {
        myFixture.configureByText(
            "test.lua",
            "Obj = {}\nfunction Obj:m() end\nObj:m()\n",
        )
        assertColonMemberResolvesToLeafAt(22)
    }

    /**
     * Case 3 — `setmetatable`-based OO, reached through the supertype chain. The mint mutation
     * (`declaresMember = false`) reddens this row with cases 1 and 2 and leaves case 4 green, so it
     * separates the structural route from the nominal one.
     */
    @Test
    fun setmetatableReceiverResolvesThroughTheSupertypeChain() {
        myFixture.configureByText(
            "test.lua",
            "local Class = {}\n" +
                "Class.__index = Class\n" +
                "function Class:m() end\n" +
                "local o = setmetatable({}, Class)\n" +
                "o:m()\n",
        )
        assertColonMemberResolvesToLeafAt(54)
    }

    /**
     * Case 4 — an aliased `---@class` receiver, which types as `{ … } | Builder`. Falsifier: drop
     * the union-arm loop from `declarationLeaves`; `LuaUnionType.resolveMember` requires **every**
     * arm to carry the name, and the anonymous table arm has no `setName`, so the plain lookup
     * misses. Executed: the plain route reports `resolveMember=MISS` on this exact fixture.
     */
    @Test
    fun aliasedAnnotatedClassReceiverResolvesThroughTheUnionArm() {
        myFixture.configureByText(
            "test.lua",
            "---@class Builder\n" +
                "local Builder = {}\n" +
                "function Builder:setName(n) end\n" +
                "local b = Builder\n" +
                "b:setName(\"x\")\n",
        )
        assertColonMemberResolvesToLeafAt(54)
    }

    /**
     * Case 5 — a `---@type`-declared receiver. A different receiver spelling from case 4's alias;
     * only case 4 was measured to need the union-arm loop, and this row pins that the declared-type
     * spelling resolves too.
     */
    @Test
    fun declaredTypeReceiverResolvesToItsMethodDeclaration() {
        myFixture.configureByText(
            "test.lua",
            "---@class Builder\n" +
                "local Builder = {}\n" +
                "function Builder:setName(n) end\n" +
                "---@type Builder\n" +
                "local b\n" +
                "b:setName(\"x\")\n",
        )
        assertColonMemberResolvesToLeafAt(54)
    }

    /**
     * Case 6 — the only row that crosses a file, and it does so through `LuaTypeManagerImpl`'s
     * `allScope` lookup rather than through the per-file `LuaTypesSnapshot`. The un-annotated
     * counterpart cannot cross a file at all — see [crossFileGlobalTableReceiverDoesNotResolve].
     */
    @Test
    fun annotatedReceiverResolvesAcrossFiles() {
        myFixture.addFileToProject(
            "cls.lua",
            "---@class Builder\n" +
                "local Builder = {}\n" +
                "function Builder:setName(n) end\n" +
                "return Builder\n",
        )
        myFixture.configureByText(
            "useb.lua",
            "---@type Builder\nlocal b\nb:setName(\"x\")\n",
        )
        assertColonMemberResolvesToLeafAt(54, "cls.lua")
    }

    // -------------------------------------------------------------------------
    // Refusals with a reachable falsifier — requirements.md cases 9-14
    // -------------------------------------------------------------------------

    /**
     * Case 9 — a chain's second segment. Falsifier: drop `receiverOf`'s first-segment test; the
     * receiver becomes `A`, whose own `go` is `function A:go()` — a plausible-but-wrong target for a
     * call whose real target is `function B:go()`. The first segment still resolves, at `next@77`.
     */
    @Test
    fun chainSecondSegmentDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local A = {}\n" +
                "function A:go() end\n" +
                "local B = {}\n" +
                "function B:go() end\n" +
                "function A:next() return B end\n" +
                "A:next():go()\n",
        )
        val segments = colonMemberNames()
        assertEquals("expected two colon segments in the chain", 2, segments.size)
        assertEquals("the first segment must still resolve", 77, resolutionOf(segments[0])?.textOffset)
        assertNull("a chain's second segment must resolve to nothing", resolutionOf(segments[1]))
    }

    /**
     * Case 10 — a suffixed receiver. Falsifier: drop `receiverOf`'s `varSuffixList.isEmpty()` test;
     * the receiver becomes the bare head `a`, whose own `m` is `function a:m()` — again plausible
     * and wrong. The graph anchors every suffix of a `var` on that `var`'s bare head (TYPE-13 Gap 2.8).
     */
    @Test
    fun suffixedReceiverDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local a = {}\n" +
                "a.b = {}\n" +
                "function a:m() end\n" +
                "function a.b:m() end\n" +
                "a.b:m()\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 11 — a table-constructor field. Falsifier: drop `methodNameLeafOf`'s `as? LuaFuncDecl`
     * cast and return `declarationOf`'s value, which is `LuaFieldImpl@12`. Executed:
     * `LuaDeclarationSite.kindOf` of that field's name leaf is null, so `LuaNameReferenceSearcher`
     * would refuse it as a search target and the site would navigate with no usage set.
     */
    @Test
    fun tableConstructorFieldMemberDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local t = { m = function() end }\nt:m()\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 12 — a dotted assignment. The same falsifier as case 11, yielding
     * `LuaAssignmentStatementImpl@13`, whose `LuaDeclarationSite.identifierLeafOf` is the
     * **receiver** `t`, not `m`.
     */
    @Test
    fun dottedAssignmentMemberDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nt.m = function() end\nt:m()\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 13 — a parameter receiver with a same-named global in scope. Falsifier: make the colon
     * branch fall through to the ordinary two-phase resolution when it finds nothing; the global
     * `function m()` is then offered as the declaration of a parameter receiver's method.
     */
    @Test
    fun parameterReceiverDoesNotFallThroughToASameNamedGlobal() {
        myFixture.configureByText(
            "test.lua",
            "function m() end\nlocal function f(x) x:m() end\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 14 — NAV-13-05's withdrawal. The call site resolves to the method at `m@24`, **not** to
     * the local `m@38` it bound to before this feature (executed pre-change: `LeafPsiElement@38`).
     * Same falsifier as case 13.
     */
    @Test
    fun aSameNamedLocalDoesNotCaptureTheColonMemberName() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal m = 1\nt:m()\nprint(m)\n",
        )
        val resolved = assertColonMemberResolvesToLeafAt(24)
        assertFalse("the member name must not bind to the local m@38", resolved.textOffset == 38)
    }

    // -------------------------------------------------------------------------
    // Reach pins — requirements.md case 15
    //
    // These shapes resolve to nothing because TYPE-13's `declarationOf` reports no declaration for
    // them (Gaps 2.7, 2.11), NOT because any NAV-13 clause refuses them. **They have no NAV-13-side
    // falsifier, deliberately.** The obvious candidate — TYPE-13 case 6's "make `declaringNodeOf`
    // return the start node when the walk finds nothing" — was applied and every NAV-13 outcome was
    // byte-identical to the unmutated run (`risks-and-gaps.md` DR-02 Finding 6): the start node is a
    // `LuaMethodExpr`, which `methodNameLeafOf`'s `as? LuaFuncDecl` cast already refuses, so mutant
    // and correct code both return null. They are kept as reach pins — if a later engine change
    // gives these shapes a declaration, this feature starts resolving them and the diff is visible.
    // -------------------------------------------------------------------------

    /** Case 15 — a `self` receiver. Reach pin; no NAV-13-side falsifier (see the block comment above). */
    @Test
    fun selfReceiverDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local C = {}\nfunction C:b() end\nfunction C:a() self:b() end\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /** Case 15 — a factory-returned receiver. Reach pin; no NAV-13-side falsifier. */
    @Test
    fun factoryReturnedReceiverDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local function make() local t = {} function t:m() end return t end\n" +
                "local o = make()\n" +
                "o:m()\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /** Case 15 — an aliased local receiver. Reach pin; no NAV-13-side falsifier. */
    @Test
    fun aliasedLocalReceiverDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal u = t\nu:m()\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 15 — a bare parameter receiver, with no same-named global to fall through to. Reach pin;
     * no NAV-13-side falsifier. Case 13 is the *falsifiable* parameter row: it adds the collision
     * that the fall-through mutation would bind to.
     */
    @Test
    fun bareParameterReceiverDoesNotResolve() {
        myFixture.configureByText(
            "test.lua",
            "local t = {}\nfunction t:m() end\nlocal function f(x) x:m() end\n",
        )
        assertColonMemberDoesNotResolve()
    }

    /**
     * Case 15 — a `require`d module receiver. Reach pin; no NAV-13-side falsifier. TYPE-13 Gap 2.11
     * measured `moduleType` carrying zero members, so the member has no `sourceElement` to reach.
     */
    @Test
    fun requiredModuleReceiverDoesNotResolve() {
        myFixture.addFileToProject("mod.lua", "local M = {}\nfunction M:m() end\nreturn M\n")
        myFixture.configureByText("a.lua", "local M = require('mod')\nM:m()\n")
        assertColonMemberDoesNotResolve()
    }

    /**
     * The cross-file **un-annotated** control for case 6. `LuaTypesSnapshot` is per file, so a global
     * table's methods are unreachable from another file (Gap 2.2, executed `references=0`) — where the
     * annotated receiver of case 6 crosses freely. The two routes differ in scope by construction.
     */
    @Test
    fun crossFileGlobalTableReceiverDoesNotResolve() {
        myFixture.addFileToProject("decl.lua", "Obj = {}\nfunction Obj:m() end\n")
        myFixture.configureByText("use.lua", "Obj:m()\n")
        assertColonMemberDoesNotResolve()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Every colon call site's member name in the configured file, in document order. */
    private fun colonMemberNames(): List<LuaNameRef> =
        PsiTreeUtil
            .findChildrenOfType(myFixture.file, LuaNameRef::class.java)
            .filter { it.parent is LuaMethodExpr }

    private fun resolutionOf(memberName: LuaNameRef): PsiElement? = memberName.reference?.resolve()

    /** The configured file's sole colon member name — asserted sole, so a fixture drift is loud. */
    private fun soleColonMemberName(): LuaNameRef {
        val names = colonMemberNames()
        assertEquals("expected exactly one colon call site in the fixture", 1, names.size)
        return names.first()
    }

    /**
     * Asserts the sole colon member name resolves to the IDENTIFIER leaf at [offset], optionally in
     * [inFile]. The resolved leaf's text is asserted to equal the call site's own — `methodNameLeafOf`'s
     * `takeIf` ties the two, and `isReferenceTo` compares text before resolving, so a divergence would
     * give a Go-to target Find Usages cannot see.
     */
    private fun assertColonMemberResolvesToLeafAt(
        offset: Int,
        inFile: String? = null,
    ): PsiElement {
        val memberName = soleColonMemberName()
        val resolved =
            requireNotNull(resolutionOf(memberName)) {
                "expected '${memberName.text}' to resolve to its method declaration"
            }
        assertEquals("the declaration leaf's text", memberName.text, resolved.text)
        assertEquals("the declaration leaf's offset", offset, resolved.textOffset)
        if (inFile != null) {
            assertEquals("the declaration's file", inFile, resolved.containingFile.name)
        }
        return resolved
    }

    private fun assertColonMemberDoesNotResolve() {
        val memberName = soleColonMemberName()
        assertNull(
            "an out-of-scope receiver shape must resolve to nothing, not to a plausible-but-wrong target",
            resolutionOf(memberName),
        )
    }
}
