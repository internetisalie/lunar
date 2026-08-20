package net.internetisalie.lunar.definitions

import com.intellij.testFramework.IndexingTestUtil

/**
 * COMP-09-10 — **Rule S**, one test per binding form (TC 10a–10g, 10i, 10j).
 *
 * The hoisted site (design §4.13) consults the index *before* the in-file type graph exists, which
 * re-opens in-file-versus-global precedence. Rule S (design §4.14) is that precedence restated with the
 * lexical-position refinement dropped, and every test here asserts **the offered set is identical to
 * today's** — because the rule exists to preserve behaviour, not to change it. The one case that takes
 * the index arm is TC 10f, where the consumer binds nothing.
 *
 * **Two of these are mutation proofs, and they must be run separately** (`implementation-plan.md`):
 * delete `LuaLocalBindingScan`'s `LuaParList` clause and **TC 10c** goes red offering `fromLibrary`;
 * delete its `name == "self"` early return and **TC 10i** goes red the same way. One clause deletion
 * reaches one clause, so a proof that only ever deletes the same clause is not testing the other six.
 *
 * TC 10h — the `seedAmbientGlobals` site Rule S deliberately excludes — needs a real Redis target and
 * lives in [MemberEnumerationRedisTargetTest].
 *
 * Every assertion is an **exact set**: a containment assertion cannot see the superset that inventing a
 * member would produce, which is the whole hazard Rule S guards.
 */
class MemberEnumerationShadowingTest : LibraryRootTestCase() {
    /**
     * TC 10a — `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal`'s exact fixture.
     *
     * That test must stay green **untouched**; this one restates it as an exact set, which its
     * `contains`/`not contains` pair cannot express.
     */
    fun testLocalVarBindingKeepsTheGlobalOut() {
        seedShadowLibrary()
        assertOffered("local Shadow = { fromLocal = 1 }\nShadow.<caret>\n", setOf("fromLocal"))
    }

    /** TC 10b — `local function Shadow() end`, the `LuaLocalFuncDecl` clause. */
    fun testLocalFuncBindingKeepsTheGlobalOut() {
        seedShadowLibrary()
        assertOffered("local function Shadow() end\nShadow.<caret>\n", emptySet())
    }

    /**
     * TC 10c — a **parameter** binding, the `LuaParList` clause and the first mutation proof.
     *
     * `LuaFileBindingsIndex` cannot see this: it walks file-scope statements only, so substituting it
     * for `LuaLocalBindingScan` would under-approximate here and offer `fromLibrary` on a name the file
     * demonstrably binds — the member-inventing direction design §4.14 rejects it for.
     */
    fun testParameterBindingKeepsTheGlobalOut() {
        seedShadowLibrary()
        assertOffered("local function f(Shadow)\n    Shadow.<caret>\nend\n", emptySet())
    }

    /**
     * TC 10d — both loop forms: `LuaGenericForStatement.nameList` and `LuaNumericForStatement`.
     *
     * `end` is offered because the keyword provider is registered on the bare `psiElement()` pattern and
     * a `do` block is open at the caret; it is **today's** output, and DR-21 measured the same
     * `today=[end] hoisted=[end]` for both loop forms (design §1.10.4). What matters is that no member
     * appears beside it: `fromLibrary` here would be a member invented on a loop variable.
     */
    fun testForLoopBindingsKeepTheGlobalOut() {
        seedShadowLibrary()
        assertOffered("for Shadow in pairs({}) do\n    Shadow.<caret>\nend\n", setOf("end"))
        assertOffered("for Shadow = 1, 2 do\n    Shadow.<caret>\nend\n", setOf("end"))
    }

    /**
     * TC 10e — the consumer file's **own** global write wins, in both of its shapes.
     *
     * `Shadow = { … }` is `declareFileGlobals`' bare assignment target and `function Shadow.here()` is
     * the `LuaFuncDecl` clause binding a fresh receiver. Under Rule S these decline to the existing
     * path; the `exclude = parameters.originalFile` argument is belt and braces on the same case.
     */
    fun testTheConsumersOwnGlobalWritesWin() {
        seedShadowLibrary()
        assertOffered("Shadow = { fromThisFile = 1 }\nShadow.<caret>\n", setOf("fromThisFile"))
        assertOffered("function Shadow.here() end\nShadow.<caret>\n", setOf("here"))
    }

    /** TC 10f — the consumer binds nothing, so this is the one case that takes the index arm. */
    fun testAnUnboundReceiverReachesTheLibrary() {
        seedShadowLibrary()
        assertOffered("Shadow.<caret>\n", setOf("fromLibrary"))
    }

    /**
     * TC 10g — a **bundled stdlib** receiver the consumer file does not bind.
     *
     * `table` is bound by `LuaTypesVisitor.freeGlobalSeed` → `resolveGlobal` off
     * `runtime/standard/lua-5.4/table.lua` (`table = {}`), **not** by `seedAmbientGlobals`: that reads
     * only files named `global.lua` and no `global.lua` exists under any `runtime/standard` root
     * (design §1.10.8), so on this target that site declares nothing at all. This pins that Rule S asks
     * about the **consumer** file only, for a receiver another file declares.
     *
     * It is also `LuaGlobalMemberCompletionTest.stdlibGlobalCompletesItsMembers` as an exact set.
     */
    fun testABundledStdlibReceiverIsUnchanged() {
        assertOffered("table.<caret>\n", setOf("concat", "insert", "move", "pack", "remove", "sort", "unpack"))
    }

    /**
     * TC 10i — `self`, the clause with no PSI shape, and the **second** mutation proof.
     *
     * `self` is bound by every method body (`LuaTypesVisitor.kt:1414`) and is never a global, so no
     * clause deletion can express it and the `LuaParList` proof cannot reach it. The library here
     * deliberately declares a global literally named `self`, which is what makes the missing early
     * return observable: without it the index arm offers `fromLibrary` on a receiver that is the
     * method's own instance.
     */
    fun testSelfIsNeverTheIndexedGlobal() {
        myFixture.addFileToProject("selflib.lua", "self = {}\nfunction self.fromLibrary() end\n")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        assertOffered("function C:m()\n    self.<caret>\nend\n", emptySet())
    }

    /**
     * TC 10j — the type-guard narrowing at `LuaTypesVisitor.kt:462`, which re-declares an already-bound
     * name.
     *
     * §4.14's table calls that site "covered transitively"; this **measures** the claim rather than
     * asserting it. The verdict is `fromLibrary`'s presence, and it is **absent**: `:462` re-declares a
     * name the `LuaLocalVarDecl` clause already bound, Rule S sees it, the arm declines, and no eighth
     * clause is needed.
     *
     * **The other half was a finding, and [[BUG-435]] has since fixed it.** This method used to pin
     * `[else, elseif, end]` — the three keywords an open `if` block offers, with no member at all —
     * against TC 10j's own prediction of `[fromLocal]`. That set was the *defect*, recorded here and
     * in risks-and-gaps.md as out of COMP-09's scope: the narrowed variable node carried the guard's
     * bare `Table(localMembers={})` instead of the literal's `Table(localMembers={fromLocal=…})`, so
     * narrowing to `table` removed every member. Confirmed by reading the node, not inferred.
     *
     * The set is now `[fromLocal, else, elseif, end]` — TC 10j's original prediction plus the
     * keywords. **The verdict is untouched**: `fromLibrary` is still absent, Rule S still sees the
     * `LuaLocalVarDecl` binding, the arm still declines, and no eighth clause is needed. Only the
     * half this method recorded as broken has changed, which is what makes it safe to update.
     */
    fun testTypeGuardNarrowingIsCoveredTransitively() {
        seedShadowLibrary()
        assertOffered(
            "local Shadow = { fromLocal = 1 }\nif type(Shadow) == \"table\" then\n    Shadow.<caret>\nend\n",
            setOf("fromLocal", "else", "elseif", "end"),
        )
    }

    /** `LuaGlobalMemberCompletionTest.aLocalShadowsTheCrossFileGlobal`'s library file, verbatim. */
    private fun seedShadowLibrary() {
        myFixture.addFileToProject("shadowed.lua", "Shadow = {}\nfunction Shadow.fromLibrary() end\n")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    private fun assertOffered(
        consumerText: String,
        expected: Set<String>,
    ) {
        val found = completionsFor(consumerText).toSet()
        println("COMP-09 Rule S | ${consumerText.replace("\n", "\\n")} | offered=$found")
        assertEquals("the offered set must be identical to today's", expected, found)
    }
}
