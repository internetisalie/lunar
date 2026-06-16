package net.internetisalie.lunar.lang.completion.postfix

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaPostfixTemplateTest : BasePlatformTestCase() {

    fun testIfPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = 10
            x > 5.if<caret>
            """.trimIndent()
        )

        myFixture.type("\t") // Trigger postfix template expansion

        // Body indentation is applied by the formatter in the real IDE; the headless template
        // harness (setTemplateTesting) does not reformat, so the caret line is left unindented.
        myFixture.checkResult(
            """
            local x = 10
            if x > 5 then
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testNotPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local ready = true
            ready.not<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local ready = true
            not ready<caret>
            """.trimIndent()
        )
    }

    fun testVarPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            getUser().var<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        // The editable name tab stop is driven by the template harness; assert the committed text.
        myFixture.checkResult(
            """
            local value = getUser()
            """.trimIndent()
        )
    }

    fun testForPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local count = 3
            count.for<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local count = 3
            for i = 1, count do
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testForPairsPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local tbl = {}
            tbl.forp<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local tbl = {}
            for k, v in pairs(tbl) do
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testForIpairsPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local list = {}
            list.fori<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local list = {}
            for i, v in ipairs(list) do
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testIfNotPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local ok = true
            ok.ifnot<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local ok = true
            if not ok then
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testNilPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = nil
            x.nil<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local x = nil
            if x == nil then
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testNotNilPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local x = nil
            x.notnil<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local x = nil
            if x ~= nil then
            <caret>
            end
            """.trimIndent()
        )
    }

    fun testReturnPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local result = 1
            result.return<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local result = 1
            return result<caret>
            """.trimIndent()
        )
    }

    fun testPrintPostfixTemplate() {
        myFixture.configureByText(
            "test.lua",
            """
            local value = 1
            value.print<caret>
            """.trimIndent()
        )

        myFixture.type("\t")

        myFixture.checkResult(
            """
            local value = 1
            print(value)<caret>
            """.trimIndent()
        )
    }

    fun testProviderReturnsAllTemplates() {
        assertEquals(11, LuaPostfixTemplateProvider().getTemplates().size)
    }
}
