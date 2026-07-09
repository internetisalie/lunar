package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool

/**
 * TOOLING-06 §2.4. Typed item for a per-kind binding combo on the *Lua Project* page.
 * [Inherit] means "no project binding" — resolution falls through to the global tiers;
 * [Tool] pins a concrete inventory entry as the project binding for that kind.
 */
sealed interface LuaBindingItem {
    data object Inherit : LuaBindingItem

    data class Tool(val tool: LuaRegisteredTool) : LuaBindingItem
}

/**
 * TOOLING-06 §2.4. Typed item for the active-environment combo. [None] clears the active
 * environment (bindings-only resolution); [Env] activates a named project environment.
 */
sealed interface LuaEnvironmentItem {
    data object None : LuaEnvironmentItem

    data class Env(val env: LuaEnvironmentState) : LuaEnvironmentItem
}
