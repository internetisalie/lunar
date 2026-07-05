package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target

/** Lua flavor a hererocks environment provisions (ROCKS-14-01). */
enum class HererocksFlavor { PUC, LUAJIT }

/**
 * Serializable per-project descriptor of a hererocks-provisioned Lua environment (ROCKS-14-01).
 *
 * Persisted as a nested object on [net.internetisalie.lunar.settings.LuaProjectSettings.State]
 * exactly like `interpreter: LuaInterpreter?`, so every field is a `var` with a default value for
 * the XML serializer. hererocks is a one-shot provisioner; this record captures how the env was
 * built so upgrade/recreate are reproducible.
 */
data class HererocksEnvState(
    var id: String = "",
    var directory: String = "",
    var flavor: HererocksFlavor = HererocksFlavor.PUC,
    var luaVersion: String = "5.4",
    var luarocksVersion: String = "latest",
    var label: String = "",
) {
    fun binDir(): String = if (SystemInfo.isWindows) directory else "$directory/bin"

    fun luaExe(): String {
        val base = if (flavor == HererocksFlavor.LUAJIT) "luajit" else "lua"
        return "${binDir()}/$base${if (SystemInfo.isWindows) ".exe" else ""}"
    }

    fun luarocksExe(): String = "${binDir()}/luarocks${if (SystemInfo.isWindows) ".bat" else ""}"

    fun displayLabel(): String = label.ifBlank { "${flavor.name} $luaVersion" }

    /**
     * Maps this env's [flavor] + [luaVersion] to a project [Target] (ROCKS-16): the authoritative
     * source for the language level + platform libraries when the project is in
     * [net.internetisalie.lunar.settings.InterpreterMode.HEREROCKS_MANAGED]. PUC → STANDARD, LuaJIT →
     * LUAJIT; the version is normalized to a registered `major.minor` label with graceful fallback via
     * [PlatformVersionRegistry.resolveTarget].
     */
    fun toTarget(): Target = when (flavor) {
        HererocksFlavor.PUC ->
            PlatformVersionRegistry.resolveTarget(LuaPlatform.STANDARD, normalizedVersionLabel())
        HererocksFlavor.LUAJIT ->
            PlatformVersionRegistry.resolveTarget(LuaPlatform.LUAJIT, normalizedVersionLabel())
    }

    /**
     * Reduces a hererocks [luaVersion] (which may be a patch level or git ref, e.g. "5.4.6",
     * "2.1.0-beta3", "@v2.1") to the `major.minor` label the [PlatformVersionRegistry] keys on. Falls
     * back to the raw value when no `major.minor` prefix is present, letting `resolveTarget` apply the
     * platform default.
     */
    private fun normalizedVersionLabel(): String =
        Regex("""(\d+\.\d+)""").find(luaVersion)?.groupValues?.get(1) ?: luaVersion
}
