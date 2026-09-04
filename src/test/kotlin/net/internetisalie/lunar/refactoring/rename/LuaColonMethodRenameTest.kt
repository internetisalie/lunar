package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaColonCallResolution
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename.Spelling
import net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename.Undecided
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

/**
 * REFACT-09 Phase 1: the occurrence scan, driven directly.
 *
 * Every method here calls [LuaColonMethodRename.undecidedOccurrences] with a usage set built from
 * `ReferencesSearch.search(declarationLeaf, projectScope)` — the same set
 * `LuaRenameProcessor.findReferences` returns, measured unchanged by `risks-and-gaps.md` DR-02
 * Finding 1. Nothing here renames anything: Phase 2 turns rename on and Phase 3 wires the scan into
 * the conflicts dialog, and each re-drives these fixtures end to end.
 *
 * The cases are `risks-and-gaps.md` DR-03's control table `c01`–`c14` and DR-05's field-scan
 * fixtures, so a verdict that moves is visible against a recorded measurement rather than against
 * this file's own expectations.
 *
 * **One `configureByText` per method, except the controls whose fixture names a second file.**
 * `LuaTypeManagerImpl` searches `GlobalSearchScope.allScope(project)`, so a stray sibling declaring
 * the same class name binds an arm to the wrong file and manufactures a result.
 *
 * **No assertion depends on the order occurrences come out in** (design §3.2 forbids it): every
 * reporting case asserts a single occurrence identified by the source line it sits on, and every
 * accepting case asserts the list is empty.
 */
@RunWith(JUnit4::class)
class LuaColonMethodRenameTest : BasePlatformTestCase() {
    // ---------------------------------------------------------------- DR-03 controls, accepted

    /** `c01` — the plain local table with two call sites: both are usages, so nothing is left. */
    @Test
    fun aPlainLocalTableWithTwoCallSitesIsComplete() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\nt:m()\n")

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `c04` — `requirements.md` row 3's scan half, and the control for design §3.4's clause (b).
     * `q:m()` is not in `t:m`'s usage set, and is decided only because it *resolves elsewhere*.
     *
     * Mutation: dismiss an occurrence only when it is in the usage set (drop the `declarationLeafOf`
     * clause) and this reddens while `c01` stays green.
     */
    @Test
    fun twoReceiversSharingTheMemberNameStayIndependent() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal q = {}\nfunction q:m() end\nq:m()\n",
        )

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `c07` — `requirements.md` row 12's scan half. Two files with identical text: `b.lua`'s call
     * resolves to `b.lua`'s own declaration, so it is decided and no conflict is manufactured for a
     * rename that is in fact file-local and correct.
     */
    @Test
    fun anIdenticalDeclarationInAnotherFileIsNotAnOccurrence() {
        myFixture.addFileToProject("b.lua", "local t = {}\nfunction t:m() end\nt:m()\n")
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\n")

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `c09` — a `---@field m` naming the same member is **not** seen, and that is a stated residual
     * rather than an oversight: `LuaCatsLazyCommentImpl` is a `LazyParseablePsiElement`, not a
     * `PsiComment`, and a tag is not a `LuaNameRef`. `risks-and-gaps.md` Gap 2.3 owns it.
     */
    @Test
    fun aCatsFieldTagNamingTheMemberIsNotSeen() {
        myFixture.addFileToProject("b.lua", "---@class Other\n---@field m fun()\n")
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\n")

        assertAccepted(scanFor("m", "n"))
    }

    /** `c10` — `requirements.md` row 5's scan half: no call sites, no occurrences, no conflict. */
    @Test
    fun aDeclarationWithNoCallSitesIsComplete() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\n")

        val declaration = methodDeclarationLeaf("m")
        assertEmpty(usagesOf(declaration))
        assertAccepted(LuaColonMethodRename.undecidedOccurrences(targetOf(declaration, "n"), usagesOf(declaration)))
    }

    /**
     * `c11` — `requirements.md` row 4's scan half. The aliased `local b = Builder` call site is in
     * the usage set (NAV-13 resolves it through the `---@class` arm), so nothing is left behind.
     */
    @Test
    fun anAnnotatedReceiverReachedThroughAnAliasIsComplete() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(x) end\n" +
                "local b = Builder\nb:setName(\"x\")\n",
        )

        assertAccepted(scanFor("setName", "withName"))
    }

    /**
     * `c12` — `requirements.md` row 21. The superseded design **refused** this shape as an escaping
     * receiver; it is accepted now because nothing in the project names `m` undecidedly. What a
     * consumer outside the project does with the returned table is Gap 2.4.
     */
    @Test
    fun aModuleThatReturnsItsTableIsComplete() {
        myFixture.configureByText("a.lua", "local M = {}\nfunction M:m() end\nM:m()\nreturn M\n")

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `c13` — `requirements.md` row 20. A dynamically indexed member is **not** an occurrence and
     * does not block the rename. This row is the *cost* of that Out-of-Scope decision rather than
     * its benefit, pinned so the residual is visible rather than discovered (Gap 2.2).
     *
     * Falsifier in the other direction: treat any bracket step as an occurrence — this reddens while
     * `c08` and `c01` stay green.
     */
    @Test
    fun aDynamicallyIndexedMemberIsNotAnOccurrence() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal k = 'm'\nprint(t[k])\n",
        )

        assertAccepted(scanFor("m", "n"))
    }

    // ------------------------------------------------------- DR-03 controls, occurrence reported

    /**
     * `c02` / `requirements.md` row 6 — the `self:` call, the first half-rename the feature's
     * Overview transcribes. `self` reaches no declaration, so the site cannot be decided.
     *
     * Mutation: delete the `LuaMethodExpr` row from `undecidedIn`'s `when` → this reddens and the
     * rename becomes `R09PROBE[R05]`, the silent half-rename.
     */
    @Test
    fun aSelfCallThatCannotBeBoundIsReported() {
        myFixture.configureByText(
            "a.lua",
            "local C = {}\nfunction C:m() end\nfunction C:a() self:m() end\nC:m()\n",
        )

        assertSingle(scanFor("m", "n"), Expected(Spelling.COLON_CALL, "function C:a() self:m() end"))
    }

    /**
     * `c03` / `requirements.md` row 7 — the dotted spelling in an expression names the same member
     * and this rename does not rewrite it (design §9 Alternative D).
     *
     * Mutation: delete the `LuaIndexExpr` row from `undecidedIn`'s `when`.
     */
    @Test
    fun theDottedSpellingInAnExpressionIsReported() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\nprint(t.m)\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.DOTTED, "print(t.m)"))
    }

    /**
     * `c14` / `requirements.md` row 8 — the dotted **declaration**, which reaches a different clause
     * from row 7: `t.m` in an expression is a `LuaIndexExpr`, `function t.m()` is a
     * `LuaFuncNameProperty`, and `funcNameProperty`/`indexExpr` are different `lua.bnf` rules.
     *
     * Mutation: delete the `LuaFuncNameProperty` row → this reddens while row 7 stays green, which
     * is what proves the two clauses are not one clause.
     */
    @Test
    fun theDottedDeclarationOfTheSameMemberIsReported() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nfunction t.m() end\nt:m()\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.DOTTED, "function t.m() end"))
    }

    /**
     * `c08` / `requirements.md` row 9 — the bracket spelling, which has no `LuaNameRef` at all and
     * so needs its own walk. This is also why `candidateFiles` uses `UsageSearchContext.ANY`: the
     * name is inside a string token.
     *
     * Mutation: delete `bracketOccurrences` from `undecidedIn`.
     */
    @Test
    fun aStringKeyNamingTheMemberIsReported() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\nprint(t[\"m\"])\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.BRACKET, "print(t[\"m\"])"))
    }

    /**
     * `c05` / `requirements.md` row 10 — a global receiver called from another file. `b.lua`'s call
     * does not resolve (the receiver has no type there), so it is exactly the cross-file half-rename
     * `R09PROBE[R10]` produced with the refusal merely lifted.
     *
     * Mutation: scan only the declaring file instead of the refactoring scope (design §3.3 step 1).
     */
    @Test
    fun aCrossFileGlobalCallSiteIsReported() {
        myFixture.addFileToProject("b.lua", "Obj:m()\n")
        myFixture.configureByText("a.lua", "Obj = {}\nfunction Obj:m() end\nObj:m()\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.COLON_CALL, "Obj:m()", "b.lua"))
    }

    /**
     * `c06` / `requirements.md` row 11 — a parameter receiver in another file, which NAV-13 resolves
     * nothing for. Same mutation as row 10, and the reason design §9 Alternative E ("scan only the
     * declaring file") is rejected: one file cannot decide this.
     */
    @Test
    fun aParameterReceiverCallInAnotherFileIsReported() {
        myFixture.addFileToProject("b.lua", "local function f(x) x:m() end\nf(nil)\n")
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.COLON_CALL, "local function f(x) x:m() end", "b.lua"))
    }

    // ------------------------------------------------------------- DR-05 field-scan fixtures

    /**
     * `requirements.md` row 23 — `fieldKeyOnSameTable`. A constructor key on the receiver's **own**
     * table still declares the old member after the rename. DR-01 Finding 3 measured this clause as
     * the sole blocker for 8 of 941 corpus declarations, i.e. it changes verdicts and is therefore
     * not optional.
     *
     * Mutation: delete `fieldOccurrences` from `undecidedIn` → this reports nothing and the rename
     * completes silently, which is the BUG-457 class arriving on the receiver's own table.
     */
    @Test
    fun aConstructorKeyOnTheReceiversOwnTableIsReported() {
        myFixture.configureByText("a.lua", "local t = { m = 1 }\nfunction t:m() end\nt:m()\n")

        assertSingle(scanFor("m", "n"), Expected(Spelling.FIELD_KEY, "local t = { m = 1 }"))
    }

    /**
     * `requirements.md` row 24 — `fieldKeyOtherTable`. The scan deliberately does **not** ask whose
     * table it is; it looks for the old name in any member position. This is the row that keeps the
     * field clause honest in the reporting direction, paired with row 26 in the other.
     */
    @Test
    fun aConstructorKeyOnAnUnrelatedTableIsReported() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal u = { m = 1 }\n",
        )

        assertSingle(scanFor("m", "n"), Expected(Spelling.FIELD_KEY, "local u = { m = 1 }"))
    }

    /**
     * `requirements.md` row 25 — `bracketKeyInConstructor`, the `field`'s bracketed alternative,
     * which has a null identifier and reaches the member name only through the literal reader.
     *
     * Mutation: make `fieldKeyName` read only `field.identifier` → this reddens while rows 23 and 24
     * stay green.
     */
    @Test
    fun aBracketedConstructorKeyIsReported() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal u = { [\"m\"] = 1 }\n",
        )

        assertSingle(scanFor("m", "n"), Expected(Spelling.FIELD_KEY, "local u = { [\"m\"] = 1 }"))
    }

    /**
     * `requirements.md` row 26, first half — `controlPositionalValue`. A positional value spelled
     * like the member is not a member spelling: the field has one expression and no identifier, and
     * the bare `m` inside it is a `LuaVar` head, not an index or a key.
     *
     * Mutation: treat every `LuaField` as an occurrence → this reddens while row 1's control stays
     * green. This is the row that stops the field clause from becoming the bracket clause's mistake.
     */
    @Test
    fun aPositionalValueSpelledLikeTheMemberIsNotAnOccurrence() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal m = 1\nlocal u = { m }\n",
        )

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `requirements.md` row 26, second half — `controlOtherFieldName`. A different name, a computed
     * key and a literal that cannot spell an identifier are each not an occurrence, for three
     * different reasons in `fieldKeyName`.
     */
    @Test
    fun aComputedKeyAndANonIdentifierLiteralAreNotOccurrences() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal k = 1\n" +
                "local u = { mm = 1, [k] = 2, 3, [\"m m\"] = 4 }\n",
        )

        assertAccepted(scanFor("m", "n"))
    }

    // --------------------------------------------------------------- the two stated exclusions

    /**
     * `requirements.md` row 27 — a redefinition of the same member on the same receiver is neither
     * rewritten nor reported, because `undecidedIn`'s `when` has no `LuaFuncNameMethod` branch.
     *
     * This is the **cost** of the exclusion rows 3 and 12 require, pinned so the residual is visible
     * rather than discovered. DR-06 measured the call site binding to the *first* declaration, so
     * renaming it rewrites the call and leaves the second definition on the old name; Gap 2.10 sizes
     * that at 3 same-file occurrences in luacheck and none in the other four trees.
     *
     * Falsifier in the other direction: add a `LuaFuncNameMethod` branch → this reports a conflict,
     * **and `c04` and `c07` redden with it**, which is why the branch is not added.
     */
    @Test
    fun aSecondDeclarationOfTheSameMemberIsNotAnOccurrence() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nfunction t:m() end\nt:m()\n",
        )

        assertAccepted(scanFor("m", "n"))
    }

    /**
     * `requirements.md` row 28 — design §3.4's clause (a), whose only reachable falsifier is a
     * synthetic usage set.
     *
     * Over both measured trees no colon occurrence was ever in the usage set while failing to
     * resolve (`R09R[<tree>] clauseAliveDecls project=0 all=0`, DR-01 Finding 4), so no Lua fixture
     * can drive this clause. The test therefore takes the occurrence the scan itself reports — the
     * `self:m()` site, asserted here to be genuinely unresolvable — and hands it back *as a usage*.
     * A usage the platform is about to rewrite must not also be reported as left behind.
     *
     * Mutation: delete `if (nameRef in usages) return null` from `colonCallVerdict` → this reddens.
     */
    @Test
    fun aColonOccurrenceAlreadyInTheUsageSetIsNotReported() {
        myFixture.configureByText(
            "a.lua",
            "local C = {}\nfunction C:m() end\nfunction C:a() self:m() end\nC:m()\n",
        )
        val declaration = methodDeclarationLeaf("m")
        val target = targetOf(declaration, "n")
        val collected = usagesOf(declaration)

        val selfCall = LuaColonMethodRename.undecidedOccurrences(target, collected).single().occurrence
        assertNull(
            "the fixture only falsifies clause (a) if this occurrence genuinely does not resolve",
            LuaColonCallResolution.declarationLeafOf(selfCall),
        )

        assertAccepted(LuaColonMethodRename.undecidedOccurrences(target, collected + selfCall))
    }

    /**
     * Design §3.2 step 1 — the scan is entered only for `METHOD_FUNCTION`. Without the guard every
     * local-variable rename would pay for a project-wide word scan and report the receiver's own
     * sites, which is `requirements.md` row 15's failure mode one layer down.
     */
    @Test
    fun aKindOtherThanAColonMethodIsNotScanned() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\nprint(t.m)\n")
        val declaration = methodDeclarationLeaf("m")

        val scan =
            LuaColonMethodRename.undecidedOccurrences(
                LuaRenameTarget(declaration, LuaDeclarationKind.LOCAL_VARIABLE, "n"),
                usagesOf(declaration),
            )

        assertAccepted(scan)
    }

    // ------------------------------------------- receiverAlreadyHasNewName (design §3.7, DR-05)

    /** DR-05 `R09F[local]` — the receiver's type already carries `n`, so the rename would merge. */
    @Test
    fun aLocalReceiverThatAlreadyHasTheNewNameIsDetected() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nfunction t:n() end\nt:m()\nt:n()\n",
        )

        assertTrue("the receiver's type carries n", receiverHasNewName("m", "n"))
    }

    /** DR-05 `R09F[localNegative]` — the same fixture without `function t:n()`. */
    @Test
    fun aLocalReceiverWithoutTheNewNameIsNotReported() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:m()\n")

        assertFalse("nothing declares n on this receiver", receiverHasNewName("m", "n"))
    }

    /**
     * DR-05 `R09F[annotated]` / `R09E[annotated]` — **the union-arm loop's falsifier.**
     *
     * A `---@class`-annotated receiver types as `{ … } | Builder`, and `LuaUnionType.resolveMember`
     * returns null unless *every* arm carries the name — the anonymous table arm does not. So the
     * plain lookup answers `false` here and only the per-arm loop answers `true`.
     *
     * Mutation: drop the union-arm loop from `hasMember` → this reddens alone.
     */
    @Test
    fun anAnnotatedReceiverThatAlreadyHasTheNewNameIsDetected() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(x) end\n" +
                "function Builder:withName(x) end\nBuilder:setName(\"x\")\n",
        )

        assertTrue("the Builder arm carries withName", receiverHasNewName("setName", "withName"))
    }

    /** DR-05 `R09F[annotatedNegative]` — the same fixture without `function Builder:withName`. */
    @Test
    fun anAnnotatedReceiverWithoutTheNewNameIsNotReported() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(x) end\nBuilder:setName(\"x\")\n",
        )

        assertFalse("nothing declares withName on Builder", receiverHasNewName("setName", "withName"))
    }

    /**
     * DR-05 `R09F[fieldKeyOther]` / `requirements.md` row 17c — another table's key is not this
     * receiver's member. This rule asks about the **new** name on **this receiver's type** only,
     * which is the opposite scope from `undecidedOccurrences`; an implementation that answered
     * either question with the other's scope fails this row or row 24.
     */
    @Test
    fun anotherTablesKeyIsNotThisReceiversMember() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nt:m()\nlocal u = { n = 1 }\n",
        )

        assertFalse("u's key is not t's member", receiverHasNewName("m", "n"))
    }

    /**
     * DR-05 `R09F[noCallSites]` / `requirements.md` row 29 — with no bound call site there is no
     * receiver handle, so the merge is **not** reported. This is a measured miss, accepted because
     * every miss is in the "report nothing" direction while a false conflict on a correct rename is
     * what row 12 exists to prevent. Gap 2.8 records what would close it.
     */
    @Test
    fun aDeclarationWithNoBoundCallSiteHasNoReceiverHandle() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nfunction t:n() end\n")

        assertFalse("no usage means no receiver to ask", receiverHasNewName("m", "n"))
    }

    // ------------------------------------------------------------------------- cancellation

    /**
     * The scan is the most expensive rule the conflict detector will reach, and it walks whole files.
     * A user who cancels must not wait for every name ref in every candidate file to be classified.
     *
     * **Differential over the occurrence count**, in the shape
     * `LuaRenameConflictTest.testCancellationIsCheckedPerFileNameRefNotPerCollisionsCall` uses, so
     * it cannot be satisfied by an entry check: both runs make exactly one `undecidedOccurrences`
     * call and differ only in how much file there is to walk. Ten more `print(t.m)` lines are 30
     * more name refs and 10 more index steps.
     *
     * Mutation: delete the `ProgressManager.checkCanceled()` from `undecidedIn`'s `mapNotNull` and
     * from `bracketOccurrences` → the delta collapses and this reddens.
     */
    @Test
    fun cancellationIsCheckedPerOccurrenceNotPerScanCall() {
        myFixture.configureByText("small.lua", "local t = {}\nfunction t:m() end\nt:m()\n")
        val small = scanCancellationChecks()
        myFixture.configureByText(
            "large.lua",
            "local t = {}\nfunction t:m() end\nt:m()\n" + "print(t.m)\n".repeat(10),
        )
        val large = scanCancellationChecks()

        assertTrue(
            "ten more occurrence-bearing lines must cost at least thirty more cancellation checks, " +
                "but cost ${large - small}, so the scan walks whole files without offering to stop",
            large - small >= 30,
        )
    }

    // ----------------------------------------------------------------------------- helpers

    /** What a reporting case asserts: one occurrence, its spelling, and the line it sits on. */
    private data class Expected(
        val spelling: Spelling,
        val line: String,
        val file: String = "a.lua",
    )

    /** The scan for the `function X:[name]()` in the configured file, renaming it to [newName]. */
    private fun scanFor(
        name: String,
        newName: String,
    ): List<Undecided> {
        val declaration = methodDeclarationLeaf(name)
        return LuaColonMethodRename.undecidedOccurrences(targetOf(declaration, newName), usagesOf(declaration))
    }

    private fun receiverHasNewName(
        name: String,
        newName: String,
    ): Boolean {
        val declaration = methodDeclarationLeaf(name)
        return LuaColonMethodRename.receiverAlreadyHasNewName(targetOf(declaration, newName), usagesOf(declaration))
    }

    private fun targetOf(
        declaration: PsiElement,
        newName: String,
    ): LuaRenameTarget = LuaRenameTarget(declaration, LuaDeclarationKind.METHOD_FUNCTION, newName)

    /**
     * The IDENTIFIER leaf of the first `function <receiver>:[name]()` in the configured file — the
     * element `LuaDeclarationSite.identifierLeafOf` hands the processor, reached without a caret
     * marker so that fixtures stay readable as Lua.
     */
    private fun methodDeclarationLeaf(name: String): PsiElement =
        PsiTreeUtil
            .findChildrenOfType(myFixture.file, LuaFuncNameMethod::class.java)
            .map { it.nameRef.identifier }
            .firstOrNull { it.text == name }
            ?: throw AssertionError("no `function X:$name()` in\n${myFixture.file.text}")

    /**
     * The usage set the platform collects, exactly as `LuaRenameProcessor.findReferences` does for a
     * `METHOD_FUNCTION` — `isFileLocal` is false for that kind, so it takes the plain
     * `ReferencesSearch` branch over the refactoring scope.
     */
    private fun usagesOf(declaration: PsiElement): Set<PsiElement> =
        ReferencesSearch
            .search(declaration, GlobalSearchScope.projectScope(project))
            .findAll()
            .mapNotNull { it.element }
            .toSet()

    private fun assertAccepted(scan: List<Undecided>) =
        assertTrue(
            "expected a complete usage set, but the rename would leave behind\n  " +
                scan.joinToString("\n  ") { "${it.spelling} at '${lineOf(it.occurrence)}'" },
            scan.isEmpty(),
        )

    private fun assertSingle(
        scan: List<Undecided>,
        expected: Expected,
    ) {
        val described = scan.map { Expected(it.spelling, lineOf(it.occurrence), it.occurrence.containingFile.name) }
        assertEquals("the reported occurrences", listOf(expected), described)
    }

    /** The trimmed source line an occurrence sits on — which occurrence it is, without an offset. */
    private fun lineOf(element: PsiElement): String {
        val text = element.containingFile.text
        val start = text.lastIndexOf('\n', element.textOffset) + 1
        val end = text.indexOf('\n', element.textOffset).takeIf { it >= 0 } ?: text.length
        return text.substring(start, end).trim()
    }

    /**
     * How many `ProgressManager.checkCanceled()` calls one scan makes **from the scan itself**.
     * `ProgressManagerImpl`'s hook is the only observation point that does not require a cancelled
     * indicator: with none on the thread, `CoreProgressManager.doCheckCanceled` takes its
     * `ONLY_HOOKS` branch and a counting `ProgressIndicator` would never be consulted.
     */
    private fun scanCancellationChecks(): Int {
        val declaration = methodDeclarationLeaf("m")
        val target = targetOf(declaration, "n")
        val usages = usagesOf(declaration)
        val checks = AtomicInteger()
        val hook =
            CoreProgressManager.CheckCanceledHook {
                if (calledDirectlyByTheScan()) checks.incrementAndGet()
                false
            }
        val progressManager = ProgressManager.getInstance() as ProgressManagerImpl
        progressManager.runWithHook(hook) {
            LuaColonMethodRename.undecidedOccurrences(target, usages)
        }
        return checks.get()
    }

    /**
     * True when the innermost frame below this test's own hook and the platform's progress plumbing
     * is the scan — i.e. the scan called `checkCanceled` itself, rather than some platform routine
     * running underneath it doing so.
     */
    private fun calledDirectlyByTheScan(): Boolean =
        StackWalker.getInstance().walk { frames ->
            frames
                .limit(FRAME_SEARCH_DEPTH)
                .filter { !it.className.startsWith(javaClass.name) && !it.className.startsWith(PROGRESS_PACKAGE) }
                .findFirst()
                .map { it.className == SCAN }
                .orElse(false)
        }

    private companion object {
        const val SCAN = "net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename"
        const val PROGRESS_PACKAGE = "com.intellij.openapi.progress"
        const val FRAME_SEARCH_DEPTH = 20L
    }
}
