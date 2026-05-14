package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
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
        
        val allProviders = listOf(LuaTypeInlayHintProvider(), LuaParameterInlayHintsProvider(), LuaMethodChainInlayHintProvider())
        val providerInfos = allProviders
            .filter { p ->
                val id = when (p) {
                    is LuaTypeInlayHintProvider -> LuaTypeInlayHintProvider.PROVIDER_ID
                    is LuaParameterInlayHintsProvider -> LuaParameterInlayHintsProvider.PROVIDER_ID
                    is LuaMethodChainInlayHintProvider -> LuaMethodChainInlayHintProvider.PROVIDER_ID
                    else -> "unknown"
                }
                declarativeSettings.isProviderEnabled(id) ?: true
            }
            .map { p ->
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
                    is LuaParameterInlayHintsProvider -> emptyList()
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
        
        pass.setContext(file.codeInsightContext)
        
        ActionUtil.underModalProgress(project, "") {
            pass.doCollectInformation(EmptyProgressIndicator())
        }
        pass.applyInformationToEditor()

        val dump = DeclarativeHintsDumpUtil.dumpHints(sourceText, editor = myFixture.editor, renderer = { presentationList ->
            val entries = presentationList.getEntries()
            entries.joinToString(separator = "") { (it as TextInlayPresentationEntry).text }
        })
        
        assertEquals(expectedText.trim(), dump.trim())
    }

    override fun setUp() {
        super.setUp()
        LuaInlayHintsSettings.instance.state.largeFileThreshold = 100000
    }
}
