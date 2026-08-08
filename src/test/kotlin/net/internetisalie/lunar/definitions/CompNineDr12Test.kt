package net.internetisalie.lunar.definitions

/**
 * THROWAWAY — COMP-09 DR-12. Two checklist questions that have been argued in prose across two Step 9
 * reviews and never run.
 *
 * A. Does a `---@class`-declared `__add` appear in member completion **today**?
 *    `human-verification-checklists.md` and `implementation-plan.md` say it must not; design §4.7
 *    says it already does, because the class-to-graph conversion copies every member into
 *    `localMembers`. Three documents, one unexecuted question.
 *
 * B. Does `Foo.` offer `baz` for `Foo.bar.baz = 1` **today**? Checklist scenario 2.2 expects not,
 *    citing `memberNameOf`. DR-09b measured the opposite through the global door (BUG-430), so the
 *    scenario as written would fail on today's code for a reason that is not the feature's fault.
 */
class CompNineDr12Test : LibraryRootTestCase() {
    fun testDr12MetamethodInCompletionToday() {
        registerLibraryRoot(
            mapOf(
                "vec.lua" to
                    """
                    ---@meta

                    ---@class Vec
                    ---@field x number
                    ---@field __add fun(a: Vec, b: Vec): Vec
                    local Vec = {}

                    ---@return number
                    function Vec:len() end
                    """.trimIndent(),
            ),
        )
        val offered = completionsFor("---@type Vec\nlocal v = nil\nv.<caret>\n")
        println("DR-12 A  v. offers $offered")
        println("DR-12 A  __add present today = ${offered.contains("__add")}")
        val colon = completionsFor("---@type Vec\nlocal v = nil\nv:<caret>\n")
        println("DR-12 A  v: offers $colon")
    }

    fun testDr12NestedQualifierInCompletionToday() {
        registerLibraryRoot(
            mapOf(
                "foo.lua" to
                    """
                    ---@meta

                    Foo = {}
                    Foo.bar = {}
                    Foo.bar.baz = 1
                    Foo.direct = 2

                    return Foo
                    """.trimIndent(),
            ),
        )
        val onFoo = completionsFor("Foo.<caret>\n")
        println("DR-12 B  Foo. offers $onFoo")
        println("DR-12 B  'baz' offered on Foo today = ${onFoo.contains("baz")}  (BUG-430 predicts true)")
        val onBar = completionsFor("Foo.bar.<caret>\n")
        println("DR-12 B  Foo.bar. offers $onBar  (BUG-430 predicts baz is MISSING here)")
    }
}
