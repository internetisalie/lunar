package net.internetisalie.lunar.analysis.controlflow

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

object ControlFlowCache {
    fun getControlFlow(owner: ScopeOwner): LuaControlFlow {
        return CachedValuesManager.getCachedValue(owner) {
            val builder = LuaControlFlowBuilder()
            val flow = builder.build(owner)
            CachedValueProvider.Result.create(flow as LuaControlFlow, owner.containingFile)
        }
    }
}
