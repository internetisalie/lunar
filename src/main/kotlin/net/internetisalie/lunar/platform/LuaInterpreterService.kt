package net.internetisalie.lunar.platform

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.command.newLuaInterpreterCommandLine
import net.internetisalie.lunar.util.LuaProcessUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

@Service(Service.Level.APP)
class LuaInterpreterService() {

    fun findInterpreters(): List<LuaInterpreter> {
        val interpreters =
            (if (SystemInfo.isWindows) PATHS_WINDOWS else PATHS_UNIX)
                // Inject environment variables
                .map { it -> pathFromEnvVarString(it) }
                // Search each search path after env var substitution
                .flatMap { searchPath -> find(searchPath) }

        interpreters.forEach { identify(it) }
        return interpreters
    }

    private fun find(directoryName: Path): List<LuaInterpreter> {
        val directory = directoryName.directoryAsVirtualFile() ?: return emptyList()
        val results = ArrayList<LuaInterpreter>()

        for (family in LuaInterpreterFamily.FAMILIES.values) {
            val exeName = family.platformExecutableName

            if (isGlob(exeName)) {
                // Match the glob
                val globPattern = patternFromGlob(exeName)
                for (executable in directory.children) {
                    if (!globPattern.matcher(executable.name).matches()) continue

                    val result = validate(executable, family)
                    if (result != null) results.add(result)
                }
            } else {
                LOG.debug("Checking lua binary ${directoryName}/${exeName}")
                val executable = directory.findChild(exeName)
                val result = validate(executable, family)
                if (result != null) {
                    results.add(result)
                    LOG.debug("Found interpreter: ${result.path} ${result.product} ${result.version}")
                }
            }
        }

        return results
    }

    private fun validate(executable: VirtualFile?, family: LuaInterpreterFamily): LuaInterpreter? {
        if (executable == null) return null

        val possibleResult = LuaInterpreter()
        possibleResult.path = executable.path
        identify(possibleResult)
        if (family.productName == possibleResult.family!!.productName) {
            possibleResult.product = family.productName
            return possibleResult
        }

        return null
    }

    fun identify(interpreter: LuaInterpreter) {
        // Reset the interpreter to invalid
        interpreter.product = LuaInterpreterFamily.INVALID_PRODUCT
        interpreter.version = null
        interpreter.banner = null

        // Request its version string
        val cmd = newLuaInterpreterCommandLine(interpreter) ?: error("Could not create a command line")
        cmd.addParameters("-v")

        val processOutput = LuaProcessUtil.capture(cmd)
        if (processOutput.exitCode != 0) {
            interpreter.banner = processOutput.stderr
            LOG.debug("Error inspecting ${interpreter.path}: ${interpreter.banner}")
            return
        }

        // Locate the binary as a VirtualFile
        val executable = interpreter.executable ?: return

        // Parse the version banner
        val banner = Banner.create(processOutput) ?: return
        interpreter.banner = banner.full
        LOG.debug("Received banner from ${interpreter.path}: ${interpreter.banner}")

        // Find a matching family
        val interpreterFamily = LuaInterpreterFamily.find(banner.product, executable.name) ?: return

        // Mark the interpreter as valid
        interpreter.product = interpreterFamily.productName
        interpreter.version = banner.version
        interpreter.path = executable.path
        interpreter.languageLevel = interpreterFamily.languageLevel(banner.version)?.version
        interpreter.platform = interpreterFamily.platform.label

        LOG.debug("Identified ${interpreter.path}: product=${interpreter.product} version=${interpreter.version}")
    }

    private fun pathFromEnvVarString(from: String): Path {
        return Path.of(substituteEnvVars(from))
    }

    private fun substituteEnvVars(from: String): String {
        var into = from
        var m = envVarPattern.matcher(into)
        while (m.matches()) {
            val varName = m.group(1)
            var varValue = System.getenv(varName)
            if (varValue == null) varValue = ""
            into = into.replace("\${$varName}", varValue)
            m = envVarPattern.matcher(into)
        }
        return into
    }

    companion object {
        private val LOG = logger<LuaInterpreterService>()

        val PATHS_UNIX: Array<String> = arrayOf(
            "/bin",
            "/sbin",
            "/usr/bin",
            "/usr/sbin",
            "/usr/local/bin",
            "/usr/local/sbin",
            "/opt/bin",
            "/opt/sbin",
            "/opt/local/bin",
            "/opt/local/sbin",
            "\${HOME}/bin",
            "\${HOME}/sbin",
//            "\${HOME}/torch/install/bin",
        )

        // TODO: Search Path Globs
        val PATHS_WINDOWS: Array<String> = arrayOf(
            "C:\\Program Files\\Lua 5.1",
            "C:\\Program Files\\Lua 5.2",
            "C:\\Program Files\\Lua 5.3",
            "C:\\Program Files\\Lua 5.4",
            "C:\\Program Files (x86)\\Lua 5.1",
            "C:\\Program Files (x86)\\Lua 5.2",
            "C:\\Program Files (x86)\\Lua 5.3",
            "C:\\Program Files (x86)\\Lua 5.4",
        )

        val envVarPattern: Pattern = Pattern.compile(".*\\$\\{([^\\}]+)\\}.*")

        fun getInstance(): LuaInterpreterService {
            return ApplicationManager.getApplication().getService(LuaInterpreterService::class.java)
        }
    }
}

fun Path.directoryAsVirtualFile(): VirtualFile? {
    val virtualFile = VfsUtil.findFile(this, true)
    return if ((virtualFile != null && virtualFile.exists()
                && virtualFile.isDirectory)
    )
        virtualFile
    else
        null
}

data class Banner(
    val product: String,
    val version: String,
    val full: String,
) {
    companion object {
        val VERSION_PATTERN = Pattern.compile("^(\\S+)\\s+(\\S+).*$")

        fun create(banner : String) : Banner? {
            val matcher = VERSION_PATTERN.matcher(banner)
            if (!matcher.matches()) return null
            return Banner(
                matcher.group(1),
                matcher.group(2),
                banner,
            )
        }

        fun create(processOutput: ProcessOutput) : Banner? {
            // Find the process output from STDOUT or STDERR
            var outputText = processOutput.stderr.ifEmpty { processOutput.stdout }
            outputText = outputText.trim(' ', '\n', '\t')
            if (outputText.contains('\n')) {
                outputText = outputText.substringBefore('\n')
            }

            return create(outputText)
        }
    }
}

private val EXPANSION_LOG = logger<LuaInterpreterService>()

fun expandSearchPath(spec: String): List<Path> {
    if (!isGlob(spec)) return listOf(Path.of(spec))

    val rawSegments = spec.split('/', '\\')
    var frontier =
        when {
            spec.startsWith("/") -> listOf(Path.of("/"))
            rawSegments[0].matches(Regex("^[A-Za-z]:$")) -> listOf(Path.of(rawSegments[0] + "\\"))
            else -> listOf(Path.of(rawSegments[0]))
        }

    for (index in 1..rawSegments.lastIndex) {
        val segment = rawSegments[index]
        if (segment.isEmpty()) continue
        ProgressManager.checkCanceled()
        frontier = frontier.flatMap { expandSegment(it, segment) }
        if (frontier.isEmpty()) return emptyList()
    }
    return frontier
}

private fun expandSegment(base: Path, segment: String): List<Path> {
    if (!isGlob(segment)) {
        val child = base.resolve(segment)
        return if (Files.isDirectory(child)) listOf(child) else emptyList()
    }

    val pattern = patternFromGlob(segment)
    return try {
        Files.newDirectoryStream(base).use { stream ->
            stream
                .filter { Files.isDirectory(it) && pattern.matcher(it.fileName.toString()).matches() }
                .sortedBy { it.fileName.toString() }
        }
    } catch (e: IOException) {
        EXPANSION_LOG.debug("Cannot list $base: ${e.message}")
        emptyList()
    }
}

fun isGlob(filename: String): Boolean {
    return filename.contains("*") || filename.contains("?")
}

fun matchesGlob(glob: String, filename: String): Boolean {
    val p = patternFromGlob(glob)
    return p.matcher(filename).matches()
}

// http://stackoverflow.com/questions/1247772
fun patternFromGlob(glob: String): Pattern {
    var out = "^"
    for (i in 0..<glob.length) {
        val c = glob.get(i)
        when (c) {
            '*' -> out += ".*"
            '?' -> out += '.'
            '.' -> out += "\\."
            '\\' -> out += "\\\\"
            else -> out += c
        }
    }
    out += '$'
    return Pattern.compile(out)
}
