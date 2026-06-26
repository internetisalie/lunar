package net.internetisalie.lunar.lang

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import net.internetisalie.lunar.lang.psi.LuaLabel
import net.internetisalie.lunar.lang.psi.LuaLabelName

class LuaLabelScopeProcessor(val name: String) : PsiScopeProcessor {
    var result: LuaLabelName? = null
        private set
    private var found = false

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if (found) return false
        if (element is LuaLabel) {
            val labelName = element.labelName
            if (labelName.identifier.text == name) {
                result = labelName
                found = true
                return false // stop the walk
            }
        }
        return true
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}

class LuaLabelCompletionScopeProcessor : PsiScopeProcessor {
    val results: MutableMap<String, LuaLabelName> = LinkedHashMap()

    override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if (element is LuaLabel) {
            val labelName = element.labelName
            results.putIfAbsent(labelName.identifier.text, labelName) // nearest scope wins
        }
        return true // always continue
    }

    override fun <T : Any?> getHint(hintKey: Key<T>): T? = null
    override fun handleEvent(event: PsiScopeProcessor.Event, associated: Any?) {}
}
