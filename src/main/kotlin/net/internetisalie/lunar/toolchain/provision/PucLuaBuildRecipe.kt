package net.internetisalie.lunar.toolchain.provision

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Inputs to a source-build recipe (design §2.9). Bundled into one value object so
 * [PucLuaBuildRecipe.plan] / [LuaRocksBuildRecipe.plan] stay one-argument functions under the
 * engineering-contract §3 parameter cap (`ProgressIndicator`/`String`/`Path` all count).
 */
data class LuaBuildRecipeInput(
    val version: String,
    val os: LuaOs,
    val toolchain: LuaCompilerProbe.Toolchain,
    val buildDir: Path,
    val prefix: Path,
)

/**
 * Resolved build context derived from [LuaBuildRecipeInput]. Bundles all pre-computed values
 * so private recipe helpers stay under the 3-arg contract without unpacking them individually.
 */
private data class RecipeContext(
    val cc: String,
    val ar: String,
    val ranlib: String,
    val cflags: List<String>,
    val ldflags: List<String>,
    val libName: String,
    val srcDir: Path,
    val prefix: Path,
)

/**
 * Pure PUC-Lua POSIX build-plan builder (design §3.5, dossier §2a — copied verbatim, not
 * re-derived). Produces the exact compile/archive/link/install command sequence for a given
 * version + OS; requires no compiler to run. The `luaconf.h` splice is [patchLuaconf].
 *
 * All build steps run in `<buildDir>/src`; install copies target `<prefix>/{bin,include,lib}`.
 */
object PucLuaBuildRecipe {
    fun plan(input: LuaBuildRecipeInput): BuildPlan {
        val rc = RecipeContext(
            cc = input.toolchain.cc.toString(),
            ar = input.toolchain.ar.toString(),
            ranlib = input.toolchain.ranlib.toString(),
            cflags = cflags(input.version),
            ldflags = ldflags(input.os),
            libName = "liblua${digits(input.version)}.a",
            srcDir = input.buildDir.resolve("src"),
            prefix = input.prefix,
        )
        val sources = sourceFiles(rc.srcDir)
        val steps = compileSteps(rc, sources) + archiveSteps(rc, sources) + linkSteps(rc, sources)
        return BuildPlan(steps, installCopies(rc, sources), executables(rc, sources))
    }

    fun patchLuaconf(luaconfText: String, version: String, prefix: Path): String {
        val lines = luaconfText.split("\n")
        val lastEndif = lines.indexOfLast { it.trim().startsWith("#endif") }
        if (lastEndif < 0) throw LuaProvisionException("Unrecognized luaconf.h: no #endif found")
        val block = spliceBlock(version, prefix)
        val patched = lines.subList(0, lastEndif) + block + lines.subList(lastEndif, lines.size)
        return patched.joinToString("\n")
    }

    private fun spliceBlock(version: String, prefix: Path): List<String> = listOf(
        "/* patched by Lunar provisioner */",
        "#undef LUA_PATH_DEFAULT",
        "#define LUA_PATH_DEFAULT \"${pathDefault(version, prefix)}\"",
        "#undef LUA_CPATH_DEFAULT",
        "#define LUA_CPATH_DEFAULT \"${cpathDefault(version, prefix)}\"",
    )

    private fun pathDefault(version: String, prefix: Path): String {
        val label = label(version)
        val p = prefix.toString()
        return when (majorMinor(version)) {
            "5.1" -> "./?.lua;$p/share/lua/5.1/?.lua;$p/share/lua/5.1/?/init.lua"
            "5.2" -> "$p/share/lua/5.2/?.lua;$p/share/lua/5.2/?/init.lua;./?.lua"
            else -> "$p/share/lua/$label/?.lua;$p/share/lua/$label/?/init.lua;./?.lua;./?/init.lua"
        }
    }

    private fun cpathDefault(version: String, prefix: Path): String {
        val label = label(version)
        val p = prefix.toString()
        return when (majorMinor(version)) {
            "5.1" -> "./?.so;$p/lib/lua/5.1/?.so;$p/lib/lua/5.1/loadall.so"
            "5.2" -> "$p/lib/lua/5.2/?.so;$p/lib/lua/5.2/loadall.so;./?.so"
            else -> "$p/lib/lua/$label/?.so;$p/lib/lua/$label/loadall.so;./?.so"
        }
    }

    private fun compileSteps(rc: RecipeContext, sources: List<String>): List<BuildStep> =
        sources.map { source ->
            val obj = source.removeSuffix(".c") + ".o"
            BuildStep(listOf(rc.cc) + rc.cflags + listOf("-c", "-o", obj, source), rc.srcDir)
        }

    private fun archiveSteps(rc: RecipeContext, sources: List<String>): List<BuildStep> {
        val libObjs = sources.map { it.removeSuffix(".c") + ".o" }
            .filter { it != "lua.o" && it != "luac.o" && it != "print.o" }
        return listOf(
            BuildStep(listOf(rc.ar, "rcu", rc.libName) + libObjs, rc.srcDir),
            BuildStep(listOf(rc.ranlib, rc.libName), rc.srcDir),
        )
    }

    private fun linkSteps(rc: RecipeContext, sources: List<String>): List<BuildStep> {
        val steps = mutableListOf<BuildStep>()
        if (sources.contains("luac.c")) {
            val printObj = if (sources.contains("print.c")) listOf("print.o") else emptyList()
            steps += BuildStep(listOf(rc.cc, "-o", "luac", "luac.o") + printObj + listOf(rc.libName) + rc.ldflags, rc.srcDir)
        }
        steps += BuildStep(listOf(rc.cc, "-o", "lua", "lua.o", rc.libName) + rc.ldflags, rc.srcDir)
        return steps
    }

    private fun installCopies(rc: RecipeContext, sources: List<String>): List<Pair<Path, Path>> {
        val copies = mutableListOf<Pair<Path, Path>>()
        copies += rc.srcDir.resolve("lua") to rc.prefix.resolve("bin/lua")
        if (sources.contains("luac.c")) copies += rc.srcDir.resolve("luac") to rc.prefix.resolve("bin/luac")
        for (header in listOf("lua.h", "luaconf.h", "lualib.h", "lauxlib.h")) {
            copies += rc.srcDir.resolve(header) to rc.prefix.resolve("include/$header")
        }
        val hpp = if (rc.srcDir.resolve("lua.hpp").exists()) rc.srcDir.resolve("lua.hpp") else rc.srcDir.parent.resolve("etc/lua.hpp")
        copies += hpp to rc.prefix.resolve("include/lua.hpp")
        copies += rc.srcDir.resolve(rc.libName) to rc.prefix.resolve("lib/${rc.libName}")
        return copies
    }

    /** The two empty LuaRocks install-target dirs the strategy must create after install (design §3.5 step 7). */
    fun installDirs(prefix: Path, version: String): List<Path> {
        val label = label(version)
        return listOf(prefix.resolve("share/lua/$label"), prefix.resolve("lib/lua/$label"))
    }

    private fun executables(rc: RecipeContext, sources: List<String>): List<Path> {
        val execs = mutableListOf<Path>(rc.prefix.resolve("bin/lua"))
        if (sources.contains("luac.c")) execs.add(rc.prefix.resolve("bin/luac"))
        return execs
    }

    private fun sourceFiles(srcDir: Path): List<String> =
        srcDir.listDirectoryEntries()
            .filter { it.isRegularFile() && it.name.endsWith(".c") && it.name != "onelua.c" }
            .map { it.name }
            .sorted()

    private fun cflags(version: String): List<String> {
        val flags = mutableListOf("-O2", "-Wall", "-Wextra")
        if (majorMinor(version) in setOf("5.3", "5.4", "5.5")) flags += "-std=gnu99"
        flags += listOf("-DLUA_USE_POSIX", "-DLUA_USE_DLOPEN")
        if (majorMinor(version) == "5.2") flags += listOf("-DLUA_USE_STRTODHEX", "-DLUA_USE_AFORMAT", "-DLUA_USE_LONGLONG")
        flags += compatDefines(version)
        return flags
    }

    /** The compat `-D` flags for [version] (design §3.5 compat table); also feeds the identifiers hash. */
    fun compatDefines(version: String): List<String> = when (majorMinor(version)) {
        "5.1" -> emptyList()
        "5.2" -> listOf("-DLUA_COMPAT_ALL")
        "5.3" -> listOf("-DLUA_COMPAT_5_1", "-DLUA_COMPAT_5_2")
        "5.4" -> listOf("-DLUA_COMPAT_5_3")
        "5.5" -> listOf("-DLUA_COMPAT_MATHLIB")
        else -> emptyList()
    }

    private fun ldflags(os: LuaOs): List<String> = when (os) {
        LuaOs.LINUX -> listOf("-Wl,-E", "-ldl", "-lm")
        LuaOs.MACOS -> listOf("-lm")
        LuaOs.WINDOWS -> throw LuaProvisionException("PUC-Lua source build is not supported on Windows")
    }

    private fun majorMinor(version: String): String {
        val parts = version.split(".")
        if (parts.size < 2) throw LuaProvisionException("Unrecognized Lua version: '$version'")
        return "${parts[0]}.${parts[1]}"
    }

    private fun label(version: String): String = majorMinor(version)

    private fun digits(version: String): String = majorMinor(version).replace(".", "")
}
