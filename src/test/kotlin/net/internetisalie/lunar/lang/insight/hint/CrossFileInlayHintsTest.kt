package net.internetisalie.lunar.lang.insight.hint

import com.intellij.openapi.application.WriteAction
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

class CrossFileInlayHintsTest : IndexedDeclarativeInlayHintsTest() {
    fun testCrossFileParameterHints() {
        myFixture.addFileToProject("other.lua", """
            function setup_config(speed, force) end

            ---@param speed number
            ---@param force number
            function setup_config2(speed, force) end
        """.trimIndent())

        doLuaTestProvider("test.lua", """
            setup_config(/*<# speed: #>*/5000, /*<# force: #>*/3)
            setup_config2(/*<# speed: #>*/5000, /*<# force: #>*/3)
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
