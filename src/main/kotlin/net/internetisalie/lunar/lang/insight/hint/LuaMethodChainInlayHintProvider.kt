package net.internetisalie.lunar.lang.insight.hint

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.LuaFile

class LuaMethodChainInlayHintProvider : InlayHintsProvider {
    companion object {
        const val PROVIDER_ID = "lua.method.chain.hints"
        const val METHOD_CHAIN_OPTION_ID = "lua.method.chain"
    }

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        val settings = LuaInlayHintsSettings.instance.state

        // Check large file threshold
        val document = editor.document
        if (document.lineCount > settings.largeFileThreshold) {
            return null
        }

        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (file !is LuaFile) return
                
                // Logic for SYNTAX-07-07 will be implemented here
                // For now, this is a placeholder satisfying the separate provider requirement.
            }
        }
    }
}
