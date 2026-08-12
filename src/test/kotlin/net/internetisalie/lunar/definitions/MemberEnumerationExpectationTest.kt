package net.internetisalie.lunar.definitions

import com.intellij.codeInsight.lookup.LookupElementPresentation

/**
 * COMP-09 Phase 2 — **the declared behaviour changes at the hoisted completion site**, E1–E7.
 *
 * Each is an *expectation*, not a discovery: design §1.10.5 and §4.5a measured every one of these on
 * the armed prototype through a real `myFixture.completeBasic()` before the site was written, and the
 * "today" column each was red against is recorded in `implementation-plan.md`'s E-table.
 *
 * **Every assertion is an exact set** (`assertEquals`, never `assertTrue(contains)`). Half the value
 * here is that nothing *extra* is offered, and a containment assertion cannot see a superset — which is
 * defect D2, the failure mode this feature has hit three times.
 *
 * E1 and E4 register their own single-file library root; the consumer is the second file, which
 * `completionsFor` writes into the *project*. The rest run against [Comp09GoldenFixture].
 */
class MemberEnumerationExpectationTest : LibraryRootTestCase() {
    /**
     * E1 — every one of design §4.3's indexer sources reaches the door, TC 5.
     *
     * The recorded red is `fromFieldTag` **absent**: source 3 (`---@field`) is the one today's
     * `resolveGlobal` path cannot see, because it builds a `LuaTableLiteralType` from the assignment and
     * never reads the `@class` comment.
     *
     * **`fromMethod` is in the dot set, and that is a preservation rather than a change.** §4.13's
     * `emitIndexed` filters on one predicate — `isColon && kind != FUNCTION` — and has **no separator
     * filter**, so a `Separator.COLON` member is offered at a `.` caret exactly as today's path offers
     * `Base|completion|Show` for a `function Base:Show()`.
     */
    fun testE1EveryIndexerSourceReachesTheReceiver() {
        registerLibraryRoot(mapOf("sources.lua" to SOURCES))
        assertOffered("Sources.", setOf("assigned", "fromFieldTag", "fromFunc", "fromMethod"))
        assertOffered("Sources:", setOf("fromFunc", "fromMethod"))
    }

    /** E2 — `Derived.` gains its own `@field`, TC 7c. Today `[Show, ownFn]`. */
    fun testE2DerivedOffersItsOwnFieldTag() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        assertOffered("Derived.", setOf("Show", "ownField", "ownFn"))
    }

    /**
     * E3 — `Shapes.` no longer offers `deep`, TC 3.
     *
     * `Shapes.nested.deep = 1` is a **grandchild**: BUG-430 is today's global door flattening it onto
     * `Shapes`, where it is offered at a path it does not exist on. The index applies
     * `LuaTypeManagerImpl.memberNameOf`'s nested-qualifier rule — a member has exactly one separator —
     * so `a.b.c` contributes nothing to `a`.
     */
    fun testE3ShapesNoLongerHoistsTheGrandchild() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        assertOffered("Shapes.", setOf("direct", "nested", "plain"))
    }

    /**
     * E4 — design §4.3's D3 residual, made visible rather than assumed away.
     *
     * `Residue.aliased = impl` cannot be classified as a function without resolution, so it is recorded
     * `Kind.FIELD` and the **syntactic** colon filter drops it; `Residue.direct = function() end` is
     * literally a `LuaFuncDef` and survives. The dot door is unchanged either way. Today both are
     * offered at `:`, which is the recorded red.
     */
    fun testE4AnIndirectlyAssignedFunctionIsAFieldAtTheColonDoor() {
        registerLibraryRoot(mapOf("residue.lua" to RESIDUE))
        assertOffered("Residue.", setOf("aliased", "direct"))
        assertOffered("Residue:", setOf("direct"))
    }

    /**
     * E5 — the index arm renders **no type text**, design §4.13.
     *
     * Read off a rendered `LookupElementPresentation`, never off the lookup string: the lookup string
     * never carried the type in the first place, so a string-level assertion would pass with the type
     * text present. Today `wx.`'s elements render `wxFileExists=fun(filename)`.
     */
    fun testE5TheIndexArmRendersNoTypeText() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        myFixture.configureByText("consumer.lua", "wx.<caret>\n")
        val elements = myFixture.completeBasic()
        assertNotNull("`wx.` must offer both members rather than auto-inserting one", elements)
        val withTypeText =
            elements
                .orEmpty()
                .filter { element ->
                    val presentation = LookupElementPresentation()
                    element.renderElement(presentation)
                    presentation.typeText != null
                }.map { it.lookupString }
                .toSet()
        assertEquals(
            "the index arm carries no member types, so every element's type text must be absent",
            emptySet<String>(),
            withTypeText,
        )
    }

    /**
     * E6 — `Base.` gains **its own** `@field`s, TC 7d.
     *
     * DR-21's finding: §4.5a had declared the superset for `Derived` only. `Base` owns both
     * `---@field inheritedField string` and `---@field onClose fun(): nil`; `Derived` gains neither,
     * because inherited `@field`s belong to `Base`'s index key — the flat list behaving exactly as
     * §4.5a's B5 paragraph says it should.
     */
    fun testE6BaseGainsItsOwnFieldTags() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        assertOffered("Base.", setOf("Show", "inheritedField", "inheritedFn", "onClose"))
    }

    /**
     * E7 — `Base:` gains `onClose`, **TC 7e**, and it is the named gate for that case.
     *
     * `---@field onClose fun(): nil` indexes `Kind.FUNCTION` under design §4.3's `startsWith("fun(")`
     * rule, so it survives the syntactic colon filter. This is the one place the index arm's syntactic
     * `isColon` filter and the graph arm's semantic one visibly diverge — the mirror image of the ten
     * Redis constants, whose `@field` type text is `number`/`string` and which do not survive.
     */
    fun testE7BaseColonGainsTheFunctionShapedFieldTag() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        assertOffered("Base:", setOf("Show", "inheritedFn", "onClose"))
    }

    private fun assertOffered(
        receiverAndSeparator: String,
        expected: Set<String>,
    ) {
        val found = completionsFor("$receiverAndSeparator<caret>\n").toSet()
        assertEquals("`$receiverAndSeparator<caret>` offered $found", expected, found)
    }

    private companion object {
        /**
         * E1's library, modelled on `Comp09GoldenFixture.BASE`'s layout: `---@meta`, the `---@class` +
         * `---@field` block **immediately above** the declaration it annotates, then the members.
         */
        val SOURCES =
            """
            ---@meta

            ---@class Sources
            ---@field fromFieldTag string
            Sources = {}

            Sources.assigned = 1

            function Sources.fromFunc() end

            function Sources:fromMethod() end

            return Sources
            """.trimIndent()

        val RESIDUE =
            """
            ---@meta

            Residue = {}

            local function impl() end

            Residue.aliased = impl

            Residue.direct = function() end

            return Residue
            """.trimIndent()
    }
}
