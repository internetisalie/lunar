package net.internetisalie.lunar.tool

import java.util.UUID

/**
 * Persistent descriptor for a registered external Lua ecosystem tool binary.
 *
 * Serialised into [net.internetisalie.lunar.settings.LuaApplicationSettings.State.toolInventory]
 * via IntelliJ's `PersistentStateComponent` XML serializer. All fields must have default values
 * so that the no-arg constructor works (required by the serializer).
 *
 * NOTE: TOOL-03 will add two additional fields to this class (health-check state and last-check
 * timestamp). Keep it open for extension — do not add a `sealed` modifier.
 */
data class LuaTool(
    /** Unique stable identifier (UUID string) assigned at registration time. */
    var id: String = UUID.randomUUID().toString(),

    /** Tool category — drives which version regex and version-flag to use. */
    var type: LuaToolType = LuaToolType.LUACHECK,

    /** Display name (e.g. "LuaRocks", "luacheck"). Derived from the binary output at registration. */
    var name: String = "",

    /** Absolute path to the binary on disk. */
    var path: String = "",

    /** Extracted version string (e.g. "3.9.2"). Empty if extraction failed. */
    var version: String = "",

    /**
     * Lua version compatibility hint reported by the tool (e.g. "5.4"). May be empty if the tool
     * does not report this or if detection failed. Used for TOOL-01-06 compatibility checks.
     */
    var luaVersion: String = "",

    /**
     * Whether the binary passed validation at registration time (file exists, executable, version
     * extraction succeeded). Lazy re-validation against disk happens in [LuaToolManager] on access.
     */
    var isValid: Boolean = false,
)
