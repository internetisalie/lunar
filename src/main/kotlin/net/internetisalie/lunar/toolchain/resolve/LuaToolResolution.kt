package net.internetisalie.lunar.toolchain.resolve

import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool

sealed interface LuaToolResolution {
    data class Resolved(val tool: LuaRegisteredTool, val source: ResolutionSource) : LuaToolResolution

    data class Unresolved(val kindId: String, val skipped: List<SkippedBinding>) : LuaToolResolution
}

enum class ResolutionSource {
    ACTIVE_ENVIRONMENT,
    PROJECT_BINDING,
    GLOBAL_BINDING,
    INVENTORY_FALLBACK
}

data class SkippedBinding(val tier: ResolutionSource, val toolId: String, val reason: SkipReason)

enum class SkipReason {
    NOT_IN_INVENTORY,
    WRONG_KIND,
    UNUSABLE
}
