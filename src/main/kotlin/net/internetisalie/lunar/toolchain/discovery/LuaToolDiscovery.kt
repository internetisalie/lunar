package net.internetisalie.lunar.toolchain.discovery

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

object LuaToolDiscovery {
    private val LOG = logger<LuaToolDiscovery>()
    private val envVarPattern: Pattern = Pattern.compile(".*\\$\\{([^}]+)}.*")

    data class DiscoveredBinary(val kind: LuaToolKind, val file: File)

    internal val WELL_KNOWN_UNIX: List<String> = listOf(
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
        "/home/linuxbrew/.linuxbrew/bin"
    )

    internal val WELL_KNOWN_WINDOWS: List<String> = listOf(
        "C:\\Program Files\\Lua 5.*",
        "C:\\Program Files (x86)\\Lua 5.*",
        "C:\\Program Files\\LuaRocks",
        "C:\\Program Files\\Lua",
        "C:\\ProgramData\\chocolatey\\bin",
        "\${APPDATA}\\LuaRocks\\bin",
        "\${USERPROFILE}\\scoop\\shims"
    )

    fun discoverAll(
        kinds: List<LuaToolKind> = LuaToolKindRegistry.all(),
        extraRoots: List<Path> = emptyList()
    ): List<DiscoveredBinary> {
        val context = ScanContext(
            resolvedRoots = resolveSearchRoots(extraRoots),
            pathDirsSet = getPathDirsSet(),
            seen = LinkedHashSet(),
            results = mutableListOf()
        )
        scanExactMatches(kinds, context)
        scanGlobMatches(kinds, context)
        return context.results
    }

    fun platformCandidates(name: String, windows: Boolean = SystemInfo.isWindows): List<String> {
        return if (windows) {
            listOf("$name.bat", "$name.exe", "$name.cmd", name)
        } else {
            listOf(name)
        }
    }

    internal fun substituteEnvVars(from: String): String {
        var into = from
        var m = envVarPattern.matcher(into)
        while (m.matches()) {
            val varName = m.group(1)
            val varValue = System.getenv(varName) ?: ""
            into = into.replace("\${$varName}", varValue)
            m = envVarPattern.matcher(into)
        }
        return into
    }

    fun expandSearchPath(spec: String): List<Path> {
        if (!isGlob(spec)) return listOf(Path.of(spec))

        val rawSegments = spec.split('/', '\\')
        var frontier = when {
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
            LOG.debug("Cannot list $base: ${e.message}")
            emptyList()
        }
    }

    private fun getMatchName(fileName: String, windows: Boolean): String {
        val name = fileName.lowercase()
        return if (windows) {
            when {
                name.endsWith(".exe") -> name.substring(0, name.length - 4)
                name.endsWith(".bat") -> name.substring(0, name.length - 4)
                name.endsWith(".cmd") -> name.substring(0, name.length - 4)
                else -> name
            }
        } else {
            name
        }
    }

    private fun resolveSearchRoots(extraRoots: List<Path>): List<Path> {
        val pathDirs = PathEnvironmentVariableUtil.getPathVariableValue()?.let {
            PathEnvironmentVariableUtil.getPathDirs(it)
        } ?: emptyList()

        val rawRoots = mutableListOf<String>().apply {
            addAll(pathDirs)
            if (SystemInfo.isWindows) {
                addAll(WELL_KNOWN_WINDOWS)
            } else {
                addAll(WELL_KNOWN_UNIX)
            }
        }

        val expandedRoots = rawRoots.flatMap { raw ->
            val substituted = substituteEnvVars(raw)
            if (substituted.isNotEmpty()) expandSearchPath(substituted) else emptyList()
        } + extraRoots

        return expandedRoots.filter { Files.isDirectory(it) }
    }

    private fun getPathDirsSet(): Set<Path> {
        val pathDirs = PathEnvironmentVariableUtil.getPathVariableValue()?.let {
            PathEnvironmentVariableUtil.getPathDirs(it)
        } ?: emptyList()

        return pathDirs.mapNotNull {
            try {
                Path.of(it).toAbsolutePath().normalize()
            } catch (_: Exception) {
                null
            }
        }.toSet()
    }

    private fun scanExactMatches(
        kinds: List<LuaToolKind>,
        context: ScanContext
    ) {
        for (kind in kinds) {
            ProgressManager.checkCanceled()
            for (binaryName in kind.binaryNames) {
                if (!isGlob(binaryName)) {
                    scanExactCandidates(kind, binaryName, context)
                }
            }
        }
    }

    private fun scanExactCandidates(
        kind: LuaToolKind,
        binaryName: String,
        context: ScanContext
    ) {
        val candidates = platformCandidates(binaryName, SystemInfo.isWindows)
        for (candidate in candidates) {
            val pathFiles = PathEnvironmentVariableUtil.findAllExeFilesInPath(candidate)
            for (file in pathFiles) {
                addIfExecutableAndNew(kind, file, context)
            }

            for (root in context.resolvedRoots) {
                val rootNormalized = try {
                    root.toAbsolutePath().normalize()
                } catch (_: Exception) {
                    root
                }
                if (!context.pathDirsSet.contains(rootNormalized)) {
                    val file = File(root.toFile(), candidate)
                    addIfExecutableAndNew(kind, file, context)
                }
            }
        }
    }

    private fun scanGlobMatches(
        kinds: List<LuaToolKind>,
        context: ScanContext
    ) {
        for (kind in kinds) {
            ProgressManager.checkCanceled()
            for (binaryName in kind.binaryNames) {
                if (isGlob(binaryName)) {
                    scanGlobCandidates(kind, binaryName, context)
                }
            }
        }
    }

    private fun scanGlobCandidates(
        kind: LuaToolKind,
        binaryName: String,
        context: ScanContext
    ) {
        val pattern = patternFromGlob(binaryName.lowercase())
        for (root in context.resolvedRoots) {
            val files = try {
                root.toFile().listFiles()?.sortedBy { it.name }
            } catch (_: Exception) {
                null
            } ?: continue

            for (file in files) {
                if (file.isFile && file.canExecute()) {
                    val matchName = getMatchName(file.name, SystemInfo.isWindows)
                    if (pattern.matcher(matchName).matches()) {
                        addIfExecutableAndNew(kind, file, context)
                    }
                }
            }
        }
    }

    private fun addIfExecutableAndNew(
        kind: LuaToolKind,
        file: File,
        context: ScanContext
    ) {
        if (file.exists() && file.isFile && file.canExecute()) {
            val canonical = try {
                file.canonicalPath
            } catch (_: Exception) {
                file.absolutePath
            }
            if (context.seen.add(canonical)) {
                context.results.add(DiscoveredBinary(kind, file))
            }
        }
    }

    private class ScanContext(
        val resolvedRoots: List<Path>,
        val pathDirsSet: Set<Path>,
        val seen: MutableSet<String>,
        val results: MutableList<DiscoveredBinary>
    )
}
