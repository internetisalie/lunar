package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase

/**
 * Tests for Lua inlay hints settings functionality.
 */
class LuaInlayHintsSettingsTest : LuaInlayHintsTestCase() {

    override fun setUp() {
        super.setUp()
        // Enable all inlay hint categories for testing by default
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, true)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, true)
        setOptionEnabled(LuaMethodChainInlayHintProvider.METHOD_CHAIN_OPTION_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.RESPECT_ANNOTATIONS_OPTION_ID, true)
        LuaInlayHintsSettings.instance.state.largeFileThreshold = 100000
    }

    private fun setOptionEnabled(optionId: String, enabled: Boolean) {
        val providerId = when (optionId) {
            LuaMethodChainInlayHintProvider.METHOD_CHAIN_OPTION_ID -> LuaMethodChainInlayHintProvider.PROVIDER_ID
            else -> LuaTypeInlayHintProvider.PROVIDER_ID
        }
        WriteAction.run<RuntimeException> {
            DeclarativeInlayHintsSettings.getInstance().setOptionEnabled(optionId, providerId, enabled)
        }
    }

    fun testLocalVariableHintsDisabled() {
        // Disable local variable hints
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, false)

        doLuaTestProvider("test.lua", """
            local x = 42
            local function greet(name) end
            greet("hello")
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testLocalVariableHintsEnabled() {
        // Enable local variable hints
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, true)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)

        doLuaTestProvider("test.lua", """
            local x/*<# : number #>*/ = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testParameterHintsDisabled() {
        // Disable parameter hints provider
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)

        doLuaTestProvider("test.lua", """
            local function move(posX/*<# : number #>*/, posY/*<# : number #>*/) end
            move(10, 20)
        """.trimIndent(), LuaParameterInlayHintsProvider())
    }

    fun testParameterHintsEnabled() {
        // Enable parameter hints provider
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)

        doLuaTestProvider("test.lua", """
            local function move(posX, posY) end
            move(/*<# posX: #>*/10, /*<# posY: #>*/20)
        """.trimIndent(), LuaParameterInlayHintsProvider())
    }

    fun testReturnTypeHintsDisabled() {
        // Disable return type hints
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, false)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)

        doLuaTestProvider("test.lua", """
            local function double()
                return 42
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testReturnTypeHintsEnabled() {
        // Enable return type hints
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, false)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)

        doLuaTestProvider("test.lua", """
            local function double()/*<# : number #>*/
                return 42
            end
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testRespectAnnotationsEnabled() {
        // Enable annotation respect - hints should be suppressed
        setOptionEnabled(LuaTypeInlayHintProvider.RESPECT_ANNOTATIONS_OPTION_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, true)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)

        doLuaTestProvider("test.lua", """
            ---@type number
            local x = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testRespectAnnotationsDisabled() {
        // Disable annotation respect - hints should show even with explicit type
        setOptionEnabled(LuaTypeInlayHintProvider.RESPECT_ANNOTATIONS_OPTION_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, true)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)

        doLuaTestProvider("test.lua", """
            ---@type number
            local x/*<# : number #>*/ = 42
        """.trimIndent(), LuaTypeInlayHintProvider())
    }

    fun testMethodChainHintsEnabled() {
        // Enable method chain hints
        setOptionEnabled(LuaMethodChainInlayHintProvider.METHOD_CHAIN_OPTION_ID, true)
        setOptionEnabled(LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID, false)
        setProviderEnabled(LuaParameterInlayHintsProvider.PROVIDER_ID, false)
        setOptionEnabled(LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID, false)

        // Note: Currently no logic in the provider, so this just verifies the provider is called without error
        doLuaTestProvider("test.lua", """
            obj:m1()
               :m2()
        """.trimIndent(), LuaMethodChainInlayHintProvider())
    }

    fun testLargeFileThreshold() {
        // Set a very low threshold - file should be skipped
        LuaInlayHintsSettings.instance.state.largeFileThreshold = 1 // File has more than 1 line

        doLuaTestProvider("test.lua", """
            local x = 42
            local y = 10
        """.trimIndent(), LuaTypeInlayHintProvider())
    }
}
