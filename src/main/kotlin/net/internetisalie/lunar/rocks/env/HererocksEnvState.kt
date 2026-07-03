package net.internetisalie.lunar.rocks.env

import com.intellij.openapi.util.SystemInfo

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
}
