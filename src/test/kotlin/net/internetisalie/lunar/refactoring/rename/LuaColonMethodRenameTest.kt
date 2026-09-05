package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.LuaBundle
import net.internetisalie.lunar.lang.psi.LuaColonCallResolution
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaFuncNameMethod
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename.Spelling
import net.internetisalie.lunar.refactoring.rename.LuaColonMethodRename.Undecided
import net.internetisalie.lunar.settings.LuaProjectSettings
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

    // ------------------------------------------------- BUG-479: the identity half of clause (b)

    /**
     * **BUG-479.** The same `c11` fixture, scanned against a usage set that does **not** carry the
     * aliased call site — the condition the defect was reported under. The occurrence resolves to
     * the declaration being renamed, so it is *ours*; being absent from the usage set means the
     * rename will not rewrite it, so it must be reported.
     *
     * The empty set is not a hypothetical: `undecidedOccurrences` is handed whatever
     * `RenameUtil.processUsages` collected, and design §3.1 is written *decided only if*. Before
     * the fix clause (b) asked only "does it resolve?", so this occurrence was declared decided,
     * was not rewritten (the rename rewrites `usages`) and was not reported (the scan reports only
     * *undecided* occurrences) — the silent half-rename of the BUG-457 class, in the shape the
     * feature reaches most.
     *
     * Measured before the fix: `B479 scanEmptyUsages=ACCEPTED`. Mutation: restore
     * `if (LuaColonCallResolution.declarationLeafOf(nameRef) != null) return null` and this reddens
     * while [anOccurrenceBoundToAnotherReceiverStaysDecidedWithNoUsageSet] stays green — which is
     * what proves the clause now compares identity rather than mere resolvability.
     */
    @Test
    fun anOccurrenceBoundToTheRenamedDeclarationButMissingFromTheUsageSetIsReported() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(x) end\n" +
                "local b = Builder\nb:setName(\"x\")\n",
        )
        val declaration = methodDeclarationLeaf("setName")

        val scan = LuaColonMethodRename.undecidedOccurrences(targetOf(declaration, "withName"), emptySet())

        assertSingle(scan, Expected(Spelling.COLON_CALL, """b:setName("x")"""))
    }

    /**
     * **BUG-479's control, and the falsifier for over-reporting.** `q:m()` resolves to `q`'s own
     * declaration, not to `t:m`, so it stays decided even with no usage set at all: clause (b) must
     * dismiss a site that resolves *elsewhere*, which is what design §3.4 specifies.
     *
     * [twoReceiversSharingTheMemberNameStayIndependent] asks the same question with the real usage
     * set, where clause (a) can answer first. Emptying the set forces the identity comparison to be
     * the thing under test. Mutation: drop the `!==` guard and report every resolving site — this
     * reddens while the BUG-479 case above stays green.
     */
    @Test
    fun anOccurrenceBoundToAnotherReceiverStaysDecidedWithNoUsageSet() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:m() end\nlocal q = {}\nfunction q:m() end\nq:m()\n",
        )
        val declaration = methodDeclarationLeaf("m")

        assertAccepted(LuaColonMethodRename.undecidedOccurrences(targetOf(declaration, "n"), emptySet()))
    }

    /**
     * **The premise `c11` states in prose and nothing pinned.** Its KDoc claims the aliased call
     * site "is in the usage set", but `assertAccepted` is satisfied either way — by clause (a) if
     * the search found it and by the old clause (b) if it did not. BUG-479 was reported as the
     * search failing to return this occurrence, so the claim is asserted here directly rather than
     * inferred from a verdict that cannot distinguish the two.
     *
     * Measured: `ReferencesSearch` returns exactly this one call site, identically under
     * `projectScope` and `allScope` (`B479 projectScopeUsages=1 allScopeUsages=1
     * callSiteInProjectUsages=true`).
     */
    @Test
    fun theAliasedAnnotatedCallSiteIsInTheUsageSet() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:setName(x) end\n" +
                "local b = Builder\nb:setName(\"x\")\n",
        )

        val usages = usagesOf(methodDeclarationLeaf("setName"))

        assertEquals(
            "the lines the collected usages sit on",
            listOf("""b:setName("x")"""),
            usages.map { lineOf(it) },
        )
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

    // ------------------------------------------------- Phase 2: the rename, end to end

    /**
     * `requirements.md` row 1 — `REFACT-09-01`, the shape the feature exists for. Driven end to end
     * through `myFixture.renameElementAtCaret`, so it exercises the substitution, `findReferences`
     * and `renameElement` together rather than the scan alone.
     *
     * Mutation: restore the `METHOD_FUNCTION -> refuse` clause of
     * `LuaRenameProcessor.substituteElementToRename` -> refused and the file byte-identical. The
     * positive outcome is `risks-and-gaps.md` DR-02 `R09PROBE[R01] RENAMED`.
     */
    @Test
    fun aColonMethodRenamesFromItsDeclarationWithEveryCallSite() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nt:m()\nt:m()\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\nt:n()\n")
    }

    /**
     * `requirements.md` row 2 — `REFACT-09-02`, rename from a CALL SITE. The caret sits on `m` in
     * `t:m()`, so `resolvedDeclarationLeaf` reaches the declaration through [[NAV-13]]'s colon-call
     * resolution before the kind is classified.
     *
     * This is also the case that fixes the discriminator of design §3.6's caret guard: here
     * `caret.text == leaf.text == "m"`, so the guard must NOT fire. A guard written against the
     * literal `"self"`, or against "the caret is not the declaration", would redden this row.
     *
     * Mutation: as row 1. `R09PROBE[R02] RENAMED`.
     */
    @Test
    fun aColonMethodRenamesFromOneOfItsCallSites() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\nt:<caret>m()\nt:m()\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\nt:n()\n")
    }

    /**
     * `requirements.md` row 3 — two receivers carrying the same member name stay independent:
     * `q`'s declaration and call site are left on `m`, and no conflict is reported.
     *
     * The scan half is pinned by [twoReceiversSharingTheMemberNameStayIndependent]; this is the
     * same fixture driven end to end, which is what proves the independence survives the rewrite
     * and not merely the verdict. `R09PROBE[R06] RENAMED`.
     */
    @Test
    fun renamingOneReceiversMethodLeavesTheOtherReceiversAlone() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nt:m()\nlocal q = {}\nfunction q:m() end\nq:m()\n",
        )

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\nlocal q = {}\nfunction q:m() end\nq:m()\n")
    }

    /**
     * `requirements.md` row 4 — an `---@class`-annotated receiver reached through an alias. The
     * call site `b:setName("x")` binds through the type engine rather than through the receiver's
     * text, which is what makes it a usage at all. `R09PROBE[R07] RENAMED`.
     */
    @Test
    fun anAnnotatedReceiverRenamesThroughAnAliasedCallSite() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:<caret>setName(x) end\n" +
                "local b = Builder\nb:setName(\"x\")\n",
        )

        myFixture.renameElementAtCaret("withName")

        myFixture.checkResult(
            "---@class Builder\nlocal Builder = {}\nfunction Builder:withName(x) end\n" +
                "local b = Builder\nb:withName(\"x\")\n",
        )
    }

    /**
     * `requirements.md` row 5 — `REFACT-09-01` and `REFACT-09-07` for a declaration with no call
     * sites at all: it renames, and an empty usage set is not mistaken for an incomplete one.
     * `R09PRED[c10] verdict=acceptedNoCallSites`, `R09PROBE[R14] RENAMED`.
     */
    @Test
    fun aColonMethodWithNoCallSitesRenamesWithoutAConflict() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\n")
    }

    /**
     * `requirements.md` row 21 — `REFACT-09-03`: the module that returns its own table renames with
     * no conflict. The row exists because the SUPERSEDED design refused this shape as an escaping
     * receiver; it is accepted now (`R09PRED[c12] verdict=accepted`), and `risks-and-gaps.md`
     * Gap 2.4 states the residual — a consumer outside the project.
     *
     * The scan half is [aModuleThatReturnsItsTableIsComplete]; this drives the same shape through
     * the rename. Mutation: as row 1.
     */
    @Test
    fun aModuleThatReturnsItsTableRenamesWithoutAConflict() {
        myFixture.configureByText("a.lua", "local M = {}\nfunction M:<caret>m() end\nM:m()\nreturn M\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local M = {}\nfunction M:n() end\nM:n()\nreturn M\n")
    }

    /**
     * `requirements.md` row 13 — `REFACT-09-04`, design §3.6's caret guard.
     *
     * **Driven through `myFixture.renameElementAtCaret`, and it cannot be driven any other way.**
     * `LuaRenameTest.assertRefusedWith` calls `substituteElementToRename(target, null)`, and a null
     * editor means there is no caret to read, so the guard returns null and the case would pass
     * against a processor that has no guard at all.
     *
     * Measured without the guard (`risks-and-gaps.md` DR-02 Finding 4):
     * `R09PROBE[R04] RENAMED | local C = {} / function C:m() end / function C:n() self:m() end /
     * C:m()` — the ENCLOSING method `a` was renamed, because `LuaScopeProcessor` resolves `self` to
     * `funcName.funcNameMethod.nameRef.identifier`. That is a silent wrong rename, not a failure,
     * which is why the refusal has to name the caret token back to the user.
     *
     * Mutation: delete the `caretRefusal` call from `colonMethodSubstitution` -> this reddens while
     * rows 1 and 2 stay green.
     */
    @Test
    fun aCaretOnSelfInsideAMethodIsRefusedAndNamesTheCaretToken() {
        myFixture.configureByText(
            "a.lua",
            "local C = {}\nfunction C:m() end\nfunction C:a() se<caret>lf:m() end\nC:m()\n",
        )

        assertRenameRefusedNaming("self", "n")
    }

    /**
     * `requirements.md` row 14 — `REFACT-09-05`, design §3.6's out-of-project refusal.
     *
     * `f:write` resolves into the plugin's OWN bundled stub, measured
     * `R09PRED[j01] resolved=write file=...lunar-0.18.0.jar!/runtime/standard/lua-5.4/io.lua
     * writable=false`, and the discriminator is
     * `GlobalSearchScope.projectScope(project).contains(virtualFile)` —
     * `R09SCOPE projectScopeContainsStub=false projectScopeContainsOwnFile=true`.
     *
     * Mutation: delete the `outOfProjectRefusal` call from `colonMethodSubstitution` -> the
     * substitution hands back a leaf in another file and this reddens with
     * `AssertionError: element not found in file`, which `risks-and-gaps.md` DR-04 point 2 records
     * as a fixture artifact rather than a production verdict — and is exactly why an explicit
     * refusal is specified instead of relying on the platform's read-only check.
     */
    @Test
    fun aMethodDeclaredInABundledStubIsRefusedAndNamesTheDeclaringFile() {
        establishStandardLua54()
        myFixture.configureByText("a.lua", "local f = io.open(\"x\")\nf:<caret>write(\"y\")\n")

        assertRenameRefusedNaming("io.lua", "emit")
    }

    /**
     * `requirements.md` row 15 — `REFACT-09-06`: a caret on the RECEIVER renames the receiver, not
     * the method. The colon path is entered only for [LuaDeclarationKind.METHOD_FUNCTION], and `t`
     * classifies as a `LOCAL_VARIABLE`, so this is an unchanged route and the row is a regression
     * guard on that boundary.
     *
     * **The `function t:m()` receiver segment is deliberately asserted as LEFT BEHIND.** That is
     * `risks-and-gaps.md` Gap 2.1 / [[BUG-476]], a pre-existing receiver-segment defect that this
     * feature neither causes nor repairs; asserting the true text rather than the desired one is
     * what keeps this row a boundary check instead of a second report of that bug.
     *
     * Its named mutation — broadening the `METHOD_FUNCTION` arm to every kind — is **inert in
     * Phase 2** and is deferred to Phase 3: with no occurrence scan on the substitution path, a
     * `LOCAL_VARIABLE` routed through `colonMethodSubstitution` passes both O(1) guards
     * (`caret.text == leaf.text == "t"`, and the file is in the project) and renames identically.
     * Phase 3 wires the scan into `LuaRenameConflictDetector`, which is where that mutation becomes
     * observable.
     */
    @Test
    fun aCaretOnTheReceiverRenamesTheReceiverAndNotTheMethod() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:m() end\n<caret>t:m()\n")

        myFixture.renameElementAtCaret("renamedTable")

        assertEquals(
            "the method name must be untouched and the receiver's own sites rewritten",
            "local renamedTable = {}\nfunction t:m() end\nrenamedTable:m()\n",
            myFixture.file.text,
        )
    }

    /**
     * `requirements.md` row 16 — `REFACT-09-07`: a committed colon rename is a SINGLE undoable
     * command, so one undo restores the file.
     *
     * The idiom is `LuaRenameUndoTest.undoAfterRenameRestoresTheDocument`
     * ([LuaRenameUndoTest.kt:43-49](../../../../src/test/kotlin/net/internetisalie/lunar/refactoring/LuaRenameUndoTest.kt)),
     * which is the existing gate for the mechanism on the local-variable route; this extends it to
     * the colon form. The property is inherited from `LuaRenameProcessor.renameElement`'s single
     * non-cancelable section (REFACT-01 design §3.3) and is easy to lose silently, since
     * `renameElement` opens no write action of its own.
     */
    @Test
    fun undoAfterAColonRenameRestoresTheDocumentInOneStep() {
        val before = "local t = {}\nfunction t:m() end\nt:m()\nt:m()\n"
        myFixture.configureByText("a.lua", before.replace("t:m() end", "t:<caret>m() end"))

        myFixture.renameElementAtCaret("n")
        assertEquals(
            "the rename must apply before undo is meaningful",
            "local t = {}\nfunction t:n() end\nt:n()\nt:n()\n",
            myFixture.file.text,
        )

        val editor = FileEditorManager.getInstance(project).getSelectedEditor(myFixture.file.virtualFile)
        UndoManager.getInstance(project).undo(editor as? TextEditor)

        assertEquals("one undo must restore the pre-rename text", before, myFixture.editor.document.text)
    }

    /**
     * `requirements.md` row 19 — `REFACT-09-10`: the DOTTED form is unchanged by this feature.
     * `function M.run()` classifies as a plain function, never enters the colon arm, and keeps
     * renaming declaration and call site together.
     *
     * This is the control for row 1's mutation: restoring the `METHOD_FUNCTION -> refuse` clause
     * reddens rows 1-5 and 21 while this row stays green, which is what shows the mutation is
     * scoped to the colon kind rather than breaking rename outright. `LuaRenameTest`'s BUG-465
     * cases are the wider gate.
     */
    @Test
    fun theDottedFormIsUnaffectedByTheColonArm() {
        myFixture.configureByText("a.lua", "local M = {}\nfunction M.<caret>run() end\nM.run()\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local M = {}\nfunction M.n() end\nM.n()\n")
    }

    // ------------------------------------------- Phase 3: the conflict arm, end to end (design §5)

    /**
     * `requirements.md` row 6 — `REFACT-09-03`. `self:m()` reaches no declaration ([[NAV-13]] Out of
     * Scope), so it is neither a usage nor resolvable, and the rename would leave it bound to the
     * old name. Reported as a `COLON_CALL`.
     *
     * Measured without design §3.3's `LuaMethodExpr` occurrence row:
     * `R09PROBE[R05] RENAMED | local C = {} / function C:n() end / function C:a() self:m() end /
     * C:n()` — the half-applied rename this whole arm exists against.
     */
    @Test
    fun aSelfCallThatCannotBeBoundIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local C = {}\nfunction C:<caret>m() end\nfunction C:a() self:m() end\nC:m()\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.undecidedCall", "m")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 7 — the DOTTED spelling of the same member, read as an expression.
     * `print(t.m)` is a `LuaIndexExpr` and this rename does not rewrite it.
     *
     * Measured without design §3.3's `LuaIndexExpr` row: `R09PROBE[R08] RENAMED | … function t:n()
     * end / t:n() / print(t.m)`.
     */
    @Test
    fun aDottedReadOfTheSameMemberIsReportedAsAConflict() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nt:m()\nprint(t.m)\n")

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.dottedSpelling", "m")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 8 — the dotted *declaration* `function t.m()`, which is a
     * `LuaFuncNameProperty` and not the `LuaIndexExpr` row 7 exercises. The two are different
     * `lua.bnf` rules, so one clause passing says nothing about the other; this row is why design
     * §3.3 carries both.
     */
    @Test
    fun aDottedDeclarationOfTheSameMemberIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nfunction t.m() end\nt:m()\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.dottedSpelling", "m")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 9 — the bracket spelling `t["m"]`, which has no `LuaNameRef` at all and
     * so cannot reach the name-ref walk. Its own message, because reading a member through a string
     * key is a different thing to tell the user than failing to bind a call.
     */
    @Test
    fun aBracketedReadOfTheSameMemberIsReportedAsAConflict() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nt:m()\nprint(t[\"m\"])\n")

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.bracketSpelling", "m")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 24 — a table-constructor key naming the same member, on a table that is
     * **not** the receiver.
     *
     * This is one half of design §5's stated contrast and it is the half that decides the scope of
     * the occurrence scan: `undecidedOccurrences` looks for the OLD name in any member position and
     * never asks whose table it is, so `local u = { m = 1 }` is reported.
     * [anotherTablesMemberNamedLikeTheNewNameIsNotAConflict] is the other half, and an
     * implementation that answered either question with the other's scope fails one of the two.
     */
    @Test
    fun anotherTablesConstructorKeyNamingTheOldMemberIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nt:m()\nlocal u = { m = 1 }\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.fieldKey", "m")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 10 — the occurrence is in ANOTHER FILE, and the conflict must still be
     * reported: `b.lua`'s `Obj:m()` does not resolve, so the rename would leave it behind.
     *
     * Mutation: scan only the declaring file instead of the refactoring scope (design §3.3 step 1)
     * → `R09PROBE[R10] RENAMED` in the caret's file with `b.lua` still reading `Obj:m()`. This row
     * and [aColonCallOnAParameterInAnotherFileIsReportedAsAConflict] are the only two that hold
     * `candidateFiles` to the project scope.
     */
    @Test
    fun aColonCallOnAGlobalInAnotherFileIsReportedAsAConflict() {
        val other = myFixture.addFileToProject("b.lua", "Obj:m()\n")
        myFixture.configureByText("a.lua", "Obj = {}\nfunction Obj:<caret>m() end\nObj:m()\n")

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.undecidedCall", "m")),
            conflictsFromRenamingTo("n"),
        )
        assertEquals("a declined rename must leave the other file byte-identical", "Obj:m()\n", other.text)
    }

    /**
     * `requirements.md` row 11 — a PARAMETER receiver in another file. `x:m()` inside
     * `local function f(x)` reaches no declaration, so it is undecided rather than decided-elsewhere.
     *
     * Mutation: as row 10 → `R09PROBE[R09] RENAMED`, `b.lua` still reading `x:m()`.
     */
    @Test
    fun aColonCallOnAParameterInAnotherFileIsReportedAsAConflict() {
        val other = myFixture.addFileToProject("b.lua", "local function f(x) x:m() end\nf(nil)\n")
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nt:m()\n")

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.colonMethod.undecidedCall", "m")),
            conflictsFromRenamingTo("n"),
        )
        assertEquals(
            "a declined rename must leave the other file byte-identical",
            "local function f(x) x:m() end\nf(nil)\n",
            other.text,
        )
    }

    // ------------------------------- Phase 3: receiverAlreadyHasNewName through the conflict path

    /**
     * `requirements.md` row 17 — `REFACT-09-08`: the receiver already has a member called `n`, so
     * the rename would MERGE two members rather than move one.
     *
     * **The assertion is on the exact message set, not on "a conflict was raised".** Three of the
     * four pre-existing rules fire on colon fixtures (`risks-and-gaps.md` DR-02 Finding 6), and two
     * of them fire on *this* one — so "an exception was thrown" is green for an arm that reports the
     * wrong rule, and green for one that never excluded `globalNameTaken` at all.
     */
    @Test
    fun aLocalReceiverThatAlreadyHasTheNewMemberIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nfunction t:n() end\nt:m()\nt:n()\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.conflict.memberExists", "n")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 17a — the same verdict for a GLOBAL receiver, and the row that shows the
     * rule is not `globalNameTaken` wearing a new message: that rule searches the project-wide key
     * `"Obj:n"` and would answer here too, but it also answers on any unrelated `Obj` one file away
     * (design §5, `risks-and-gaps.md` Gap 2.9). `R09F[global] MECHANISM=true`.
     */
    @Test
    fun aGlobalReceiverThatAlreadyHasTheNewMemberIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "Obj = {}\nfunction Obj:<caret>m() end\nfunction Obj:n() end\nObj:m()\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.conflict.memberExists", "n")),
            conflictsFromRenamingTo("n"),
        )
    }

    /**
     * `requirements.md` row 17b — an `---@class`-annotated receiver, which types as `{ … } | Builder`
     * and is the shape `REFACT-09-08` is most likely to meet.
     *
     * **This row is the falsifier for design §3.7's union-arm loop.** `LuaUnionType.resolveMember`
     * returns null unless EVERY arm carries the name and the anonymous arm carries nothing, so
     * dropping the loop reddens this row while rows 17 and 17a — whose receivers type as a plain
     * `{ }` — stay green: `R09E[annotated] plain=false unionAware=true` against
     * `R09E[local] plain=true`.
     */
    @Test
    fun anAnnotatedReceiverThatAlreadyHasTheNewMemberIsReportedAsAConflict() {
        myFixture.configureByText(
            "a.lua",
            "---@class Builder\nlocal Builder = {}\nfunction Builder:<caret>setName(x) end\n" +
                "function Builder:withName(x) end\nBuilder:setName(\"x\")\n",
        )

        assertConflictsAre(
            listOf(LuaBundle.message("refactoring.rename.conflict.memberExists", "withName")),
            conflictsFromRenamingTo("withName"),
        )
    }

    // ------------------------------------ Phase 3: the rules that must NOT fire for this kind

    /**
     * `requirements.md` row 12 — `REFACT-09-10`, and the falsifier for **C4** [ambiguousGlobal].
     * Two files each declare `function t:m()` on their own `local t`; each renames correctly and
     * independently, because a colon call resolves through the receiver's type per file rather than
     * through a project-wide index.
     *
     * Mutation — keep `ambiguousGlobal` in the rule set for this kind → `R09PROBE[R11] THREW
     * ConflictsInTestsException: 't:m' is declared in 2 places; while more than one declaration
     * exists its usages do not resolve, so they will not be rewritten`, a conflict whose premise
     * [[NAV-13]] falsified. The scan half is [anIdenticalDeclarationInAnotherFileIsNotAnOccurrence];
     * what this adds is that the *detector* stays silent too.
     */
    @Test
    fun anIdenticalDeclarationInAnotherFileIsNotAConflict() {
        val other = myFixture.addFileToProject("b.lua", "local t = {}\nfunction t:m() end\nt:m()\n")
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nt:m()\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\n")
        assertEquals(
            "the other file's own declaration must be untouched",
            "local t = {}\nfunction t:m() end\nt:m()\n",
            other.text,
        )
    }

    /**
     * `requirements.md` row 18 — `REFACT-09-10`, and the falsifier for **C1** [captures].
     *
     * A `local n` is lexically visible at `t:m()`, but since [[NAV-13]] a colon member name has no
     * lexical binding at all (`NAV-13-05`), so nothing about `t:n` can be captured by it. Left
     * running, `captures` calls `visibleDeclarationOf("n", <the t:m() site>)`, finds the `local n`
     * and reports a capture that cannot happen — on **every** colon call site in scope of any
     * same-named local.
     *
     * Mutation — let `captures` run for this kind → this reddens with
     * `refactoring.rename.conflict.capture`.
     */
    @Test
    fun aVisibleLocalNamedLikeTheNewMemberIsNotACaptureConflict() {
        myFixture.configureByText(
            "a.lua",
            "local n = 1\nlocal t = {}\nfunction t:<caret>m() end\nt:m()\nprint(n)\n",
        )

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local n = 1\nlocal t = {}\nfunction t:n() end\nt:n()\nprint(n)\n")
    }

    /**
     * `requirements.md` row 17c — the other half of design §5's stated contrast.
     * `receiverAlreadyHasNewName` asks for the NEW name on **this receiver's type** only, so
     * another table's `n` is not a conflict. `R09F[fieldKeyOther] MECHANISM=false`.
     *
     * Mutation: ask the *file* for a member named `n` instead of the usage's receiver type → this
     * row reddens while row 17 stays green.
     */
    @Test
    fun anotherTablesMemberNamedLikeTheNewNameIsNotAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nt:m()\nlocal u = { n = 1 }\n",
        )

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\nlocal u = { n = 1 }\n")
    }

    /**
     * `requirements.md` row 17d — a SHADOWING receiver spelled the same. The inner `do local t = {}`
     * is a different table that happens to share the outer's name, and its `n` is not the outer
     * `t`'s member.
     *
     * **This is the row a receiver-TEXT rule gets wrong**, which is the substantive difference
     * between the removed `globalNameTaken` (which searches the key `"t:n"`) and design §3.7 (which
     * asks the usage receiver's inferred type). `R09F[shadowed] MECHANISM=false`.
     */
    @Test
    fun aShadowingReceiverWithTheNewMemberIsNotAConflict() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nt:m()\ndo local t = {} function t:n() end t:n() end\n",
        )

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult(
            "local t = {}\nfunction t:n() end\nt:n()\ndo local t = {} function t:n() end t:n() end\n",
        )
    }

    /**
     * `requirements.md` row 29 — the **measured cost** of keying `REFACT-09-08` on a usage's
     * receiver: with no bound call site there is no receiver to ask, so the merge is not reported
     * and the file ends with two `function t:n()`.
     *
     * `R09F[noCallSites] usages=[] MECHANISM receiverAlreadyHasNewName=false`. Pinned as a property
     * rather than left to be discovered; `risks-and-gaps.md` Gap 2.8 names what would close it.
     * Falsifier in the other direction — fall back to the first `local <receiver>` in the file →
     * [aShadowingReceiverWithTheNewMemberIsNotAConflict] reddens, because the fallback picks the
     * wrong `t`.
     */
    @Test
    fun aDeclarationWithNoCallSiteCannotSeeTheMemberItWouldMerge() {
        myFixture.configureByText("a.lua", "local t = {}\nfunction t:<caret>m() end\nfunction t:n() end\n")

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nfunction t:n() end\n")
    }

    /**
     * `requirements.md` row 20 — a dynamically indexed member does not block the rename. `t[k]`
     * names nothing in the PSI, and the pinned corpus carries 5 424 such steps, so treating any of
     * them as an occurrence would refuse every rename in it (`risks-and-gaps.md` Gap 2.2).
     *
     * Driven here rather than at the scan because the cost of the Out-of-Scope decision is a
     * property of the *rename*, not of the predicate.
     */
    @Test
    fun aDynamicallyIndexedMemberDoesNotBlockTheRename() {
        myFixture.configureByText(
            "a.lua",
            "local t = {}\nfunction t:<caret>m() end\nt:m()\nlocal k = 'm'\nprint(t[k])\n",
        )

        myFixture.renameElementAtCaret("n")

        myFixture.checkResult("local t = {}\nfunction t:n() end\nt:n()\nlocal k = 'm'\nprint(t[k])\n")
    }

    // ----------------------------------------------------------------------------- helpers

    /**
     * Attaches the STANDARD Lua 5.4 runtime library, which is the only thing in this class that
     * depends on a bundled stub resolving.
     *
     * **This is not ceremony — without it the case is order-dependent, and it was measured failing
     * that way.** The attached library is chosen by
     * `PlatformLibraryProvider.getPlatformLibrary`, which reads
     * `LuaProjectSettings.getInstance(project).state.getTarget()`, and several suites change that
     * target on the shared light project without restoring it — `LibraryProviderTest` leaves Lua
     * 5.1 and `StubGlobalSeedTypeTest` leaves Redis 7, neither having a `tearDown`. Under a Redis
     * target there is no `io.lua` at all, so `f:write` resolves to nothing and the processor
     * refuses one step earlier with `refactoring.rename.unresolved` — a refusal, so the file is
     * still byte-identical, but not the out-of-project refusal this row exists to pin.
     *
     * Observed: green filtered to this class, red in the full suite with
     * `Cannot determine which declaration this name refers to`.
     *
     * `setTargetAndNotify` rather than `state.setTarget` because only the former publishes the
     * roots change that reloads the library (`LibraryLoadingAfterTargetChangeTest` is the gate for
     * that mechanism). No `tearDown` is needed: this sets the target every other suite already
     * assumes, so it repairs leaked state rather than adding more.
     */
    private fun establishStandardLua54() {
        val version =
            requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, "5.4")) {
                "no STANDARD 5.4 version in the registry"
            }
        LuaProjectSettings.getInstance(project).setTargetAndNotify(Target(LuaPlatform.STANDARD, version))
    }

    /**
     * Drives `myFixture.renameElementAtCaret` on the configured file, asserts it was REFUSED with a
     * message naming [fragment], and asserts the file is byte-identical afterwards.
     *
     * Headlessly `CommonRefactoringUtil.showErrorHint` throws
     * `RefactoringErrorHintException` instead of painting a balloon, so a refusal arrives here
     * as a `RuntimeException`.
     */
    private fun assertRenameRefusedNaming(
        fragment: String,
        newName: String,
    ) {
        val before = myFixture.file.text
        val failure =
            try {
                myFixture.renameElementAtCaret(newName)
                null
            } catch (thrown: RuntimeException) {
                thrown
            }

        assertNotNull("the rename must be refused, not applied or half-applied", failure)
        assertTrue(
            "the refusal must name '$fragment', not merely abort: " + failure?.message,
            failure?.message.orEmpty().contains(fragment),
        )
        assertEquals("a refused rename must leave the file byte-identical", before, myFixture.file.text)
    }

    /**
     * The messages the conflicts dialog would have shown for a rename at the caret, HTML-free, and
     * the assertion that **nothing was written** while they were being collected.
     *
     * A rename that applies instead of reporting fails here rather than at an assertion further
     * down, because a silently half-applied rename is the BUG-457 defect itself and not a detail of
     * it. `ConflictsInTestsException` is what the platform raises in place of the dialog headlessly.
     */
    private fun conflictsFromRenamingTo(newName: String): List<String> {
        val before = myFixture.file.text
        try {
            myFixture.renameElementAtCaret(newName)
        } catch (conflicts: BaseRefactoringProcessor.ConflictsInTestsException) {
            assertEquals("no file may be written before a conflict is acknowledged", before, myFixture.file.text)
            return conflicts.messages.toList()
        }
        throw AssertionError(
            "renaming to '$newName' applied silently; a colon rename that leaves occurrences on the " +
                "old name without reporting them is the defect REFACT-09-03 exists against. File is now:\n" +
                myFixture.file.text,
        )
    }

    /**
     * **Exact set, not `contains`.** Three of the four pre-existing conflict rules were measured
     * firing on colon fixtures before design §5 excluded them (`risks-and-gaps.md` DR-02 Finding 6),
     * so an assertion that merely finds the expected message among others is green for an arm that
     * never excluded them — which is precisely the mutation `requirements.md` rows 12, 17 and 18
     * name. Sorted because neither the scan (design §3.2) nor the dialog fixes an order.
     */
    private fun assertConflictsAre(
        expected: List<String>,
        reported: List<String>,
    ) = assertEquals("the conflicts the dialog would have shown", expected.sorted(), reported.sorted())

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
