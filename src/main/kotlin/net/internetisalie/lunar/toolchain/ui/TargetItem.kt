package net.internetisalie.lunar.toolchain.ui

import net.internetisalie.lunar.platform.LuaPlatform

/**
 * TOOLING-08 §2.3. Combo item model for the *Platform target* combo on the *Lua Project* page.
 * [Auto] follows the resolved runtime (synchronizer-owned); [Platform] pins a concrete platform in
 * explicit mode.
 */
sealed interface TargetItem {
    data object Auto : TargetItem

    data class Platform(val platform: LuaPlatform) : TargetItem
}
