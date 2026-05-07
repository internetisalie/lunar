package net.internetisalie.lunar.lang.types

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

class UnionBugTest : BasePlatformTestCase() {
    fun testUnionBug() {
        myFixture.configureByText("main.lua", """
            ---@param input string | number
            local function handle(input)
                if type(input) == "string" then
                    return input:upper()
                else
                    return input + 1
                end
            end

            local z = handle("hello")
            local u = handle(1234)
        """.trimIndent())

        val snapshot = LuaTypesSnapshot.forFile(myFixture.file)
        val errors = snapshot.getErrors()
        assertTrue("Expected no errors, but found: \${errors.map { it.message }}", errors.isEmpty())
    }
}
