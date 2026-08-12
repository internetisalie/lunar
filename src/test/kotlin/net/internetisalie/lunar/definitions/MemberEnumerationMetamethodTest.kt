package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.analysis.LuaTypeAssignabilityInspection
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember

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
 * **Mutation-proved, and the third row needed a second door before it could be pinned:**
 *
 * | mutation | result |
 * | :-- | :-- |
 * | drop the `metamethods` contribution (`metamethods = emptySet()`) | **CAUGHT** — the five capability tests go red |
 * | weaken `LuaTypeGraph.implementsOperator` to `value.metamethods.isNotEmpty()` | **CAUGHT** by [testAClassDeclaredAddDoesNotSatisfyConcatenation] |
 * | loosen the name filter from `it in ALL_METAMETHODS` to `it.startsWith("__")` | **SURVIVED** every assignability test above; **CAUGHT** by [testOnlyKnownMetamethodNamesReachTheMetamethodSet] |
 * | drop the name filter entirely (`metamethods = declaredMembers.keys`) | **SURVIVED** every assignability test above, [testAClassWithNoMetamethodIsStillRejectedAtArithmetic] included; **CAUGHT** by [testOnlyKnownMetamethodNamesReachTheMetamethodSet] |
 *
 * Phase 4 first read rows 3 and 4 as "the filter is not independently observable" and deleted the
 * test. That conclusion was wrong, and the paragraph recording it contradicted itself — it justified
 * keeping the filter *because* `metamethods` participates in [LuaGraphType.Table]'s data-class
 * equality, which is an argument that the set **is** observable state. What those two mutations
 * actually establish is narrower: the filter is invisible **through `LuaTypeGraph.implementsOperator`**
 * (the sole production reader), which re-tests the recorded name against the *trait's* own set, so a
 * junk name there satisfies no operator. Reading [LuaGraphType.Table.metamethods] straight off
 * [LuaGraphType.materialize] is a different door, and it observes the filter exactly —
 * [testOnlyKnownMetamethodNamesReachTheMetamethodSet] is that assertion and reddens under both.
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
     * The control for the whole change: it is what keeps the capability **conditional**. Measured —
     * making `LuaTypeGraph.implementsOperator` return `true` for any `Table` reddens it (alongside
     * [testAClassDeclaredAddDoesNotSatisfyConcatenation]; the two are not claimed to be independent).
     *
     * It does **not** catch contributing every member name (`metamethods = declaredMembers.keys`).
     * Measured: under that mutation all nine assignability tests here pass, this one included,
     * because `P`'s only member `x` intersects no trait's metamethod set — the same mechanism that
     * makes `__index` harmless. The rationale this replaces claimed the opposite ("would pass every
     * test above while modelling nothing") and had not been run. That mutation is caught by
     * [testOnlyKnownMetamethodNamesReachTheMetamethodSet], and only there.
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

    // --- the name filter, at the door that can see it ----------------------------------------

    /**
     * `ALL_METAMETHODS` pinned directly, since `implementsOperator` cannot see it.
     *
     * `__index` is the discriminator: it is `__`-prefixed and it is a member, so it survives both the
     * loose `startsWith("__")` reading and the no-filter reading, and reaches [LuaGraphType.Table]
     * under either — where it belongs to no [LuaGraphType.Trait] and so changes no operator verdict.
     * Only a direct read of the recorded set separates the three implementations.
     */
    fun testOnlyKnownMetamethodNamesReachTheMetamethodSet() {
        val declaredClass =
            LuaClassType(
                "V",
                localMembers =
                    mapOf(
                        "x" to LuaTypeMember("x", LuaPrimitiveType.NUMBER),
                        "__add" to LuaTypeMember("__add", LuaPrimitiveType.FUNCTION),
                        "__index" to LuaTypeMember("__index", LuaPrimitiveType.FUNCTION),
                    ),
            )
        assertEquals(setOf("__add"), metamethodsRecordedFor(declaredClass))
    }

    /**
     * The coverage guard `ALL_METAMETHODS` cannot give itself: it hand-lists the three
     * [LuaGraphType.Trait] subobjects, so a fourth would be silently uncovered — harmless only
     * because nothing else reads the set. A class declaring *every* trait's metamethods must record
     * *every* one of them, and [metamethodsOf]'s `when` stops this file compiling if a `Trait` is
     * added, which is what forces [EVERY_TRAIT_METAMETHOD] to grow and this assertion to notice.
     */
    fun testEveryTraitsMetamethodNamesReachTheMetamethodSet() {
        val declaredClass =
            LuaClassType(
                "T",
                localMembers = EVERY_TRAIT_METAMETHOD.associateWith { LuaTypeMember(it, LuaPrimitiveType.FUNCTION) },
            )
        assertEquals(EVERY_TRAIT_METAMETHOD, metamethodsRecordedFor(declaredClass))
    }

    private fun metamethodsRecordedFor(declaredClass: LuaClassType): Set<String> {
        val anchor = myFixture.configureByText("anchor.lua", "local anchor = 1\n")
        val materialized = runReadAction { LuaGraphType.materialize(declaredClass, anchor) }
        return assertInstanceOf(materialized, LuaGraphType.Table::class.java).metamethods
    }

    private companion object {
        /**
         * Deliberately a `when` over the sealed hierarchy rather than `trait.metamethods` inline:
         * the exhaustiveness is the guard, and removing it removes the only thing that reacts to a
         * fourth [LuaGraphType.Trait].
         */
        private fun metamethodsOf(trait: LuaGraphType.Trait): Set<String> =
            when (trait) {
                LuaGraphType.Trait.Numberable -> trait.metamethods
                LuaGraphType.Trait.Stringable -> trait.metamethods
                LuaGraphType.Trait.Lengthable -> trait.metamethods
            }

        val EVERY_TRAIT_METAMETHOD: Set<String> =
            listOf(
                LuaGraphType.Trait.Numberable,
                LuaGraphType.Trait.Stringable,
                LuaGraphType.Trait.Lengthable,
            ).flatMapTo(mutableSetOf(), ::metamethodsOf)

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
