package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import java.io.File

/**
 * Base class for inlay hints tests that require specific settings to be enabled.
 */
abstract class LuaInlayHintsTestCase : DeclarativeInlayHintsProviderTestCase() {
    
    @JvmOverloads
    fun doLuaTestProvider(
        fileName: String,
        expectedText: String,
        provider: InlayHintsProvider,
        enabledOptions: Map<String, Boolean> = emptyMap(),
        expectedFile: File? = null,
        verifyHintsPresence: Boolean = false,
        testMode: ProviderTestMode = ProviderTestMode.SIMPLE,
    ) {
        val sourceText = InlayDumpUtil.removeInlays(expectedText)
        myFixture.configureByText(fileName, sourceText)
        
        val declarativeSettings = DeclarativeInlayHintsSettings.getInstance()
        
        val providers = listOf(LuaTypeInlayHintProvider(), LuaParameterInlayHintsProvider(), LuaMethodChainInlayHintProvider())
        val providerInfos = providers.map { p ->
            val providerId = when (p) {
                is LuaTypeInlayHintProvider -> LuaTypeInlayHintProvider.PROVIDER_ID
                is LuaParameterInlayHintsProvider -> LuaParameterInlayHintsProvider.PROVIDER_ID
                is LuaMethodChainInlayHintProvider -> LuaMethodChainInlayHintProvider.PROVIDER_ID
                else -> "unknown"
            }
            
            val options = mutableMapOf<String, Boolean>()
            val optionIds = when (p) {
                is LuaTypeInlayHintProvider -> listOf(
                    LuaTypeInlayHintProvider.LOCAL_VARIABLE_TYPE_OPTION_ID,
                    LuaTypeInlayHintProvider.RETURN_TYPE_OPTION_ID,
                    LuaTypeInlayHintProvider.RESPECT_ANNOTATIONS_OPTION_ID
                )
                is LuaParameterInlayHintsProvider -> listOf(
                    LuaParameterInlayHintsProvider.PARAMETER_NAME_OPTION_ID
                )
                is LuaMethodChainInlayHintProvider -> listOf(
                    LuaMethodChainInlayHintProvider.METHOD_CHAIN_OPTION_ID
                )
                else -> emptyList()
            }
            
            optionIds.forEach { id ->
                options[id] = enabledOptions[id] ?: declarativeSettings.isOptionEnabled(id, providerId) ?: true
            }
            
            InlayProviderPassInfo(p, providerId, options)
        }
        
        val file = myFixture.file!!
        val editor = myFixture.editor
        
        val pass = ActionUtil.underModalProgress(project, "") {
            DeclarativeInlayHintsPass(file, editor, providerInfos, isPreview = false)
        }
        
        // Use the extension property directly
        pass.setContext(file.codeInsightContext)
        
        val method = DeclarativeInlayHintsProviderTestCase::class.java.getDeclaredMethod(
            "applyPassAndCheckResult",
            DeclarativeInlayHintsPass::class.java,
            File::class.java,
            String::class.java,
            String::class.java,
            ProviderTestMode::class.java
        )
        method.isAccessible = true
        method.invoke(this, pass, expectedFile, sourceText, expectedText, testMode)
    }

    override fun setUp() {
        super.setUp()
        LuaInlayHintsSettings.instance.state.largeFileThreshold = 100000
    }
}
