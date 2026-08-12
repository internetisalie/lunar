package net.internetisalie.lunar.definitions

import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection

/**
 * COMP-09-05 — **`@class`-declared metamethods**, closing BUG-426's Known limitation and
 * COMP-04-DR-01 (requirements TC 6 / 6a / 6b, design §4.7).
 *
 * BUG-424 taught the engine that a table implementing an operator satisfies that operator's
 * position, and BUG-426 made `setmetatable(t, mt)` produce a typed table so there was something for
 * it to consult. Both recorded metamethods from a **real metatable only**, so
 * `---@class V` + `---@field __add …` — the way an annotated library declares the same capability —
 * left `a + b` reported. `LuaOperatorTraitTest` covers the `setmetatable` half; this file covers the
 * `@class` half, and the two mechanisms meet in `LuaTypeGraph.implementsOperator`.
 *
 * **Two things here are measurements, not intentions, and both were wrong somewhere first.**
 *
 * 1. *The offered sets do not move* (TC 6a). A `@class`-declared `__add` has **always** completed on
 *    the instance — `v.` offers `[__add, len, x]` today and did before this change — because the
 *    class-to-graph conversion copies every member into `localMembers`. `LuaGraphType.Table`'s
 *    `metamethods` KDoc describes an intent ("merging them would make `t.__add` complete on the
 *    instance, which is not what Lua exposes") that this path has never implemented. COMP-09-05
 *    contributes the name to `metamethods` **as well as** leaving it in `localMembers`, so the
 *    operator position gains it and completion is untouched. The plan and the human checklist both
 *    expected `__add` *absent* from completion; DR-12 measured otherwise and this test is the guard
 *    that the conservative reading stays true. Sets are asserted **exactly** — a subset passes every
 *    other gate in this area.
 * 2. *The requirements' literal TC 6 fixture cannot fail.* TC 6 is written `local a, b = V(), V()`,
 *    and measured on the pre-change code that reports **nothing**: `V()` calls a class that declares
 *    no `__call`, infers `Undefined`, and `Undefined` absorbs every check. A test in that shape
 *    would have passed with no implementation at all — the exact trap BUG-424's own fixture fell
 *    into before BUG-426 landed. The asserted form binds the operands with `---@type V` instead, and
 *    [testAClassWithNoMetamethodIsStillRejectedAtArithmetic] is the control that keeps it honest.
 *
 * **Mutation-proved, three ways, and one of them came back negative:**
 *
 * | mutation | result |
 * | :-- | :-- |
 * | drop the `metamethods` contribution (`metamethods = emptySet()`) | **CAUGHT** — the five capability tests go red |
 * | weaken `LuaTypeGraph.implementsOperator` to `value.metamethods.isNotEmpty()` | **CAUGHT** by [testAClassDeclaredAddDoesNotSatisfyConcatenation] |
 * | loosen the name filter from `it in ALL_METAMETHODS` to `it.startsWith("__")` | **SURVIVED** |
 *
 * The third is a real finding and is recorded rather than papered over: the narrow filter is **not
 * independently observable**, because `implementsOperator` tests the recorded name against the
 * *trait's* set anyway, so a junk name in `metamethods` satisfies nothing. A test written to pin the
 * filter (an `---@class` whose only `__`-prefixed member is `__index`) is therefore indistinguishable
 * from the plain control above, and was removed rather than kept as coverage it does not provide.
 * The filter stays because design §4.7 specifies it and because `metamethods` participates in
 * `Table`'s data-class equality, which is a memoization key — but no test claims to prove it.
 */
class MemberEnumerationMetamethodTest : LibraryRootTestCase() {
    private val vectorClass =
        """
        ---@class V
        ---@field x number
        ---@field __add fun(a: V, b: V): V
        local V = {}
        """.trimIndent()

    private fun assignabilityProblems(text: String): List<String> {
        myFixture.enableInspections(LuaTypeAssignabilityInspection())
        myFixture.configureByText("consumer.lua", text)
        return myFixture
            .doHighlighting()
            .mapNotNull { it.description }
            .filter { it.contains("not assignable") }
    }

    private fun assertNoProblem(
        why: String,
        text: String,
    ) {
        val problems = assignabilityProblems(text)
        assertTrue("$why — got: $problems", problems.isEmpty())
    }

    private fun assertProblem(
        why: String,
        text: String,
    ) {
        assertFalse(
            "$why — got no assignability problem",
            assignabilityProblems(text).isEmpty(),
        )
    }

    // --- TC 6: the capability ----------------------------------------------------------------

    /** TC 6. Pre-change this reported `V is not assignable to number`. */
    fun testAClassDeclaredAddMakesItsInstancesArithmeticCapable() =
        assertNoProblem(
            "a @class declaring __add implements arithmetic",
            "$vectorClass\n---@type V\nlocal a\n---@type V\nlocal b\nlocal total = a + b\n",
        )

    /** The mixed-operand form: the capability must survive the value/variable hop BUG-419 shipped. */
    fun testAClassDeclaredAddSurvivesAMixedOperand() =
        assertNoProblem(
            "a @class declaring __add implements arithmetic against a number literal",
            "$vectorClass\n---@type V\nlocal a\nlocal total = a + 2\n",
        )

    /**
     * The same class declared on a **global** rather than a local. Phase 2 hoisted global receivers
     * onto an index arm, so the two are no longer the same route to a type and the capability is
     * asserted on both rather than assumed to transfer.
     */
    fun testAGlobalDeclaredClassIsArithmeticCapableToo() =
        assertNoProblem(
            "a @class on a global declaring __add implements arithmetic",
            "---@class G\n---@field __add fun(a: G, b: G): G\nG = {}\n---@type G\nlocal a\nlocal total = a + 2\n",
        )

    /** `__concat` is the same arm at the other trait. Pre-change: `C is not assignable to string`. */
    fun testAClassDeclaredConcatMakesItsInstancesConcatenable() =
        assertNoProblem(
            "a @class declaring __concat implements concatenation",
            "---@class C\n---@field __concat fun(a: C, b: C): C\nlocal C = {}\n---@type C\nlocal a\nlocal joined = a .. \"s\"\n",
        )

    /**
     * TC 6b. No separate supertype walk exists or is needed: `LuaClassType.getMembers()` merges
     * supertypes, so `D` reaches `Base`'s `__add` through the same expression that finds its own.
     * Pre-change this reported `D is not assignable to number`.
     */
    fun testAnInheritedClassDeclaredAddIsAlsoArithmeticCapable() =
        assertNoProblem(
            "@class D : Base inherits Base's declared __add",
            "---@class Base\n---@field __add fun(a: Base, b: Base): Base\nlocal Base = {}\n" +
                "---@class D : Base\nlocal D = {}\n---@type D\nlocal a\n---@type D\nlocal b\nlocal total = a + b\n",
        )

    // --- …and what must still be rejected ----------------------------------------------------

    /**
     * The control for the whole change. Without it, contributing *every* member name to
     * `metamethods` would pass every test above while modelling nothing.
     */
    fun testAClassWithNoMetamethodIsStillRejectedAtArithmetic() =
        assertProblem(
            "a @class with no arithmetic metamethod is a genuine Lua error",
            "---@class P\n---@field x number\nlocal P = {}\n---@type P\nlocal a\nlocal total = a + 2\n",
        )

    /** Per-trait precision: `__add` does not make a value concatenable. */
    fun testAClassDeclaredAddDoesNotSatisfyConcatenation() =
        assertProblem(
            "__add must not make a @class concatenable",
            "$vectorClass\n---@type V\nlocal a\nlocal joined = a .. \"s\"\n",
        )

    // --- TC 6a: the offered sets do not move -------------------------------------------------

    /**
     * TC 6a, the local-declared class — DR-12's fixture verbatim, re-measured at this head. `v.`
     * offers `__add`; `v:` does not, because the graph arm's colon filter is semantic and `__add`'s
     * declared type is not a method of `V`.
     */
    fun testTheOfferedSetsOfALocalDeclaredClassDoNotMove() {
        registerLibraryRoot(mapOf("vec.lua" to LIBRARY_CLASS.replace("@RECEIVER@", "local Vec")))
        assertEquals(listOf("__add", "len", "x"), completionsFor(INSTANCE_DOT).sorted())
        assertEquals(listOf("len"), completionsFor(INSTANCE_COLON).sorted())
    }

    /**
     * TC 6a re-checked at the Phase 2 site. A `@class` on a **global** can route the receiver-name
     * carets through the index arm, whose `isColon` filter is *syntactic* — it asks whether the
     * member's indexed `Kind` is `FUNCTION`, not whether the type says method — so the two doors are
     * expected to disagree here and both are pinned.
     *
     * `Vec:` offering `__add` is that divergence, **not** a metamethod-visibility change: the field's
     * type text starts with `fun(`, so design §4.3 indexes it as `Kind.FUNCTION` and the syntactic
     * filter keeps it. It is the same mechanism requirements TC 7e already fixes for `onClose`. The
     * instance carets `v.` / `v:` — which are what TC 6a is about — are identical to the
     * local-declared class and to DR-12's pre-Phase-2 measurement.
     */
    fun testTheOfferedSetsOfAGlobalDeclaredClassDoNotMove() {
        registerLibraryRoot(mapOf("vecg.lua" to LIBRARY_CLASS.replace("@RECEIVER@", "Vec")))
        assertEquals(listOf("__add", "len", "x"), completionsFor("Vec.<caret>\n").sorted())
        assertEquals(listOf("__add", "len"), completionsFor("Vec:<caret>\n").sorted())
        assertEquals(listOf("__add", "len", "x"), completionsFor(INSTANCE_DOT).sorted())
        assertEquals(listOf("len"), completionsFor(INSTANCE_COLON).sorted())
    }

    private companion object {
        const val LIBRARY_CLASS =
            "---@meta\n\n" +
                "---@class Vec\n" +
                "---@field x number\n" +
                "---@field __add fun(a: Vec, b: Vec): Vec\n" +
                "@RECEIVER@ = {}\n\n" +
                "---@return number\n" +
                "function Vec:len() end\n"

        const val INSTANCE_DOT = "---@type Vec\nlocal v = nil\nv.<caret>\n"
        const val INSTANCE_COLON = "---@type Vec\nlocal v = nil\nv:<caret>\n"
    }
}
