package net.internetisalie.lunar.lang.insight.unwrap

import com.intellij.codeInsight.unwrap.Unwrapper
import com.intellij.codeInsight.unwrap.UnwrapDescriptorBase

/**
 * Registers the Lua unwrap/remove option set on `com.intellij.lang.unwrapDescriptor` (Ctrl+Shift+Delete).
 * The base [UnwrapDescriptorBase] walks the PSI parent chain from the caret, collecting every applicable
 * [Unwrapper] as a picker option with live preview. Design §2.1. EDITOR-06.
 */
class LuaUnwrapDescriptor : UnwrapDescriptorBase() {

    override fun createUnwrappers(): Array<Unwrapper> = arrayOf(
        LuaBlockUnwrapper(LuaConstruct.IF),
        LuaBlockUnwrapper(LuaConstruct.WHILE),
        LuaBlockUnwrapper(LuaConstruct.FOR),
        LuaBlockUnwrapper(LuaConstruct.DO),
        LuaBlockUnwrapper(LuaConstruct.FUNCTION),
        LuaElseBranchRemover(),
        LuaRemoveConstructUnwrapper(),
    )
}
