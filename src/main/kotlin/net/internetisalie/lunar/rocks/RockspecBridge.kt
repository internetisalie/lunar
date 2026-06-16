package net.internetisalie.lunar.rocks

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LuaProcessUtil
import java.nio.file.Path

/** The rockspec fields ROCKS-03 consumes; all other exported fields are ignored. */
data class RockspecData(
    val packageName: String,
    val version: String?,
    val dependencies: List<String>,
)

/**
 * Runs the bundled `rockspec.lua` bridge over a `.rockspec` and returns its parsed fields.
 *
 * The bridge `print`s a single JSON object (encoder `lua/lunar/json.lua`); this reads only
 * `package`, `version`, and `dependencies`. Background only — [LuaProcessUtil.capture] blocks.
 */
object RockspecBridge {
    private val log = logger<RockspecBridge>()
    private const val TIMEOUT_MS = 10_000
    private const val DEFAULT_INTERPRETER = "lua"
    private const val ENV_LUA_PATH_TEMPLATE = "LUNAR_LUA_PATH_TEMPLATE"

    fun read(project: Project, rockspecPath: Path): RockspecData? {
        val interpreter = LuaProjectSettings.getInstance(project).state.interpreter?.path
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_INTERPRETER
        val command = GeneralCommandLine(
            interpreter,
            LuaRocksBridgeFiles.rockspecScript().toString(),
            rockspecPath.toString(),
        ).withEnvironment(ENV_LUA_PATH_TEMPLATE, LuaRocksBridgeFiles.luaPathTemplate())

        val output = LuaProcessUtil.capture(command, TIMEOUT_MS)
        if (output.exitCode != 0 || output.stdout.isBlank()) {
            log.warn("Rockspec bridge failed for $rockspecPath (exit=${output.exitCode}): ${output.stderr}")
            return null
        }
        return parse(output.stdout, rockspecPath)
    }

    /** Parses one bridge stdout payload. Visible for unit testing the real JSON shape. */
    fun parse(stdout: String, rockspecPath: Path): RockspecData? {
        val root = try {
            JsonParser.parseString(stdout)
        } catch (e: JsonSyntaxException) {
            log.warn("Rockspec bridge emitted invalid JSON for $rockspecPath: ${e.message}")
            return null
        }
        if (!root.isJsonObject) {
            log.warn("Rockspec bridge output for $rockspecPath was not a JSON object")
            return null
        }
        val obj = root.asJsonObject
        val packageName = obj.get("package")?.takeIf { it.isJsonPrimitive }?.asString
        if (packageName.isNullOrBlank()) {
            log.warn("Rockspec bridge output for $rockspecPath is missing a package name")
            return null
        }
        val version = obj.get("version")?.takeIf { it.isJsonPrimitive }?.asString
        return RockspecData(packageName, version, readDependencies(obj.get("dependencies")))
    }

    /**
     * Reads the `dependencies` value as a list of constraint strings. The standard shape is a JSON
     * array; a platform-mapped table comes through as an object whose values are flattened.
     */
    private fun readDependencies(element: com.google.gson.JsonElement?): List<String> = when {
        element == null || element.isJsonNull -> emptyList()
        element.isJsonArray -> element.asJsonArray
            .filter { it.isJsonPrimitive }
            .map { it.asString }
        element.isJsonObject -> element.asJsonObject.entrySet()
            .flatMap { entry ->
                val value = entry.value
                when {
                    value.isJsonPrimitive -> listOf(value.asString)
                    value.isJsonArray -> value.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
                    else -> emptyList()
                }
            }
        else -> emptyList()
    }
}
