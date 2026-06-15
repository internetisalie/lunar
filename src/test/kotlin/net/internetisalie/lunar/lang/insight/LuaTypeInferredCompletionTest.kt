package net.internetisalie.lunar.lang.insight

import net.internetisalie.lunar.IndexedDocumentTest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * COMP-04: Type-Inferred Completion. Covers the TC-01..TC-06 matrix from the requirements:
 * member completion after `.`/`:`, inheritance, LuaCATS `@field`/`@class`, `setmetatable __index`
 * (TC-05), and `self` typing in methods (TC-06).
 */
class LuaTypeInferredCompletionTest : IndexedDocumentTest() {

    private fun doContains(text: String, vararg expected: String) {
        configureByText(text)
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings
        assertNotNull(strings, "Completion lookup should not be null")
        for (s in expected) {
            assertTrue(strings.contains(s), "Completion should contain '$s'. Found: $strings")
        }
    }

    private fun doNotContains(text: String, vararg unexpected: String) {
        configureByText(text)
        myFixture.completeBasic()
        val strings = myFixture.lookupElementStrings ?: return
        for (s in unexpected) {
            assertTrue(!strings.contains(s), "Completion should NOT contain '$s'. Found: $strings")
        }
    }

    @Test
    fun `TC-01 literal table dot member`() {
        doContains("local t = { name = \"Lua\" }\nt.<caret>", "name")
    }

    @Test
    fun `TC-02 inherited LuaCATS field`() {
        doContains(
            """
            ---@class A
            ---@field x number
            local A = {}

            ---@class B : A
            local B = {}

            ---@type B
            local b
            b.<caret>
            """.trimIndent(),
            "x",
        )
    }

    @Test
    fun `TC-03 colon method completion`() {
        doContains(
            """
            ---@class C
            ---@field test function
            local C = {}

            ---@type C
            local c
            c:<caret>
            """.trimIndent(),
            "test",
        )
    }

    @Test
    fun `TC-03 colon filters out plain fields`() {
        doNotContains("local t = { name = \"Lua\" }\nt:<caret>", "name")
    }

    @Test
    fun `TC-05 setmetatable __index members`() {
        doContains(
            "local t = setmetatable({}, { __index = { x = 1 } })\nt.<caret>",
            "x",
        )
    }

    @Test
    fun `TC-06 self typing in method`() {
        doContains(
            """
            ---@class MyClass
            ---@field value number
            local MyClass = {}

            function MyClass:method()
                self.<caret>
            end
            """.trimIndent(),
            "value",
        )
    }

    /**
     * TYPE-02 × COMP-04 integration: implicit `@class` fields (from `self.field =` inside a method
     * and `Class.field =`) must reach member completion, not just the type manager. The implicit-
     * fields unit test checks `resolveType(...).getMembers()` directly; this checks the real flow.
     */
    @Test
    fun `implicit class fields appear in completion`() {
        doContains(
            """
            ---@class Player
            local Player = {}
            function Player:heal() self.hp = 1 end
            Player.maxHp = 100

            ---@type Player
            local p
            p.<caret>
            """.trimIndent(),
            "hp",
            "maxHp",
        )
    }

    // TC-04 (union completion, COMP-04-06 S-priority) is deferred — see requirements Future Work.
}
