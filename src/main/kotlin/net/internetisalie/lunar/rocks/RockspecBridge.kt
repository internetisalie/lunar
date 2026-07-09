package net.internetisalie.lunar.rocks

import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.toolchain.exec.LuaExecResult
import net.internetisalie.lunar.toolchain.exec.LuaExecTimeout
import net.internetisalie.lunar.toolchain.exec.LuaToolExecutionService
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path
import java.util.concurrent.Callable

/** The rockspec fields ROCKS-03 consumes; all other exported fields are ignored. */
data class RockspecData(
    val packageName: String,
    val version: String?,
    val dependencies: List<String>,
    val buildType: String?,
    val luaModules: Map<String, String>,
    val cModules: Map<String, List<String>>,
)

/**
 * Runs the bundled `rockspec.lua` bridge over a `.rockspec` and returns its parsed fields.
 *
 * The bridge `print`s a single JSON object (encoder `lua/lunar/json.lua`); this reads only
 * `package`, `version`, and `dependencies`. Background only — [LuaToolExecutionService.capture] blocks.
 */
object RockspecBridge {
    private val log = logger<RockspecBridge>()
    private const val ENV_LUA_PATH_TEMPLATE = "LUNAR_LUA_PATH_TEMPLATE"

    fun read(project: Project, rockspecPath: Path): RockspecData? {
        val interpreter = LuaToolResolver.getInstance().resolveRuntime(project)?.path
            ?.takeIf { it.isNotBlank() }
        if (interpreter == null) {
            log.warn("Rockspec bridge skipped for $rockspecPath: no Lua runtime is configured")
            return null
        }
        val command = GeneralCommandLine(
            interpreter,
            LuaRocksBridgeFiles.rockspecScript().toString(),
            rockspecPath.toString(),
        ).withEnvironment(ENV_LUA_PATH_TEMPLATE, LuaRocksBridgeFiles.luaPathTemplate())

        val output = captureOffEdt(command)
        if (output.exitCode != 0 || output.stdout.isBlank()) {
            log.warn("Rockspec bridge failed for $rockspecPath (exit=${output.exitCode}): ${output.stderr}")
            return null
        }
        return parse(output.stdout, rockspecPath)
    }

    /**
     * Runs the bridge capture, offloading to a pooled thread when invoked on the EDT (the exec
     * service refuses to launch a process on the dispatch thread — contract §1/§10). The light
     * fixtures drive [read] on the test/EDT thread, so this keeps the capture off the UI thread.
     */
    private fun captureOffEdt(command: GeneralCommandLine): LuaExecResult {
        val service = LuaToolExecutionService.getInstance()
        val application = ApplicationManager.getApplication()
        return if (application != null && application.isDispatchThread) {
            application.executeOnPooledThread(Callable { service.capture(command, LuaExecTimeout.PROBE) }).get()
        } else {
            service.capture(command, LuaExecTimeout.PROBE)
        }
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
        
        val buildObj = obj.get("build")?.takeIf { it.isJsonObject }?.asJsonObject
        val buildType = buildObj?.get("type")?.takeIf { it.isJsonPrimitive }?.asString
        val modulesObj = buildObj?.get("modules")?.takeIf { it.isJsonObject }?.asJsonObject
        val luaModules = LinkedHashMap<String, String>()
        val cModules = LinkedHashMap<String, List<String>>()
        modulesObj?.entrySet()?.forEach { (name, value) ->
            when {
                value.isJsonPrimitive -> luaModules[name] = value.asString
                value.isJsonArray -> cModules[name] =
                    value.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
                else -> Unit
            }
        }
        
        return RockspecData(
            packageName,
            version,
            readDependencies(obj.get("dependencies")),
            buildType,
            luaModules,
            cModules,
        )
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
