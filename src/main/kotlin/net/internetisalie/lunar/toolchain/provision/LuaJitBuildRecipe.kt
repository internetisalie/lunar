package net.internetisalie.lunar.toolchain.provision

import java.nio.file.Path

/**
 * Pure LuaJIT git+make build-plan builder (design §3.9, dossier §2c — copied verbatim, not
 * re-derived). Produces the exact git-clone / checkout / `make PREFIX=…` step sequence and the
 * hand-copy install set (LuaJIT ships no relocatable `make install`, so the binaries, static/
 * shared libs, headers and the `src/jit` Lua runtime are copied out of the build tree by hand).
 *
 * The clone is **full** (`.git` retained): LuaJIT's build derives its version string from git,
 * so a shallow clone breaks it (TOOLING-00-03 result). [LuaBuildRecipeInput.version] is the git
 * ref checked out (e.g. `v2.1`). Requires no compiler/git to run; POSIX-only (gated in
 * [SourceBuildStrategy.supports]). The `.so.2` shared-lib copy is best-effort — recorded in the
 * plan but skipped by the strategy when `src/libluajit.so` was not built.
 */
object LuaJitBuildRecipe {
    private const val REPO = "https://github.com/LuaJIT/LuaJIT"

    private val HEADERS = listOf("lua.h", "lauxlib.h", "lualib.h", "luaconf.h", "lua.hpp", "luajit.h")

    fun plan(input: LuaBuildRecipeInput): BuildPlan {
        val buildDir = input.buildDir
        val prefix = input.prefix
        val steps = listOf(
            BuildStep(listOf("git", "clone", REPO, buildDir.toString()), buildDir.parent),
            BuildStep(listOf("git", "-C", buildDir.toString(), "checkout", input.version), buildDir.parent),
            BuildStep(listOf("make", "PREFIX=$prefix"), buildDir, makeEnv(input.os)),
        )
        val binary = prefix.resolve("bin/lua")
        return BuildPlan(steps, installCopies(buildDir, prefix), listOf(binary))
    }

    /** `MACOSX_DEPLOYMENT_TARGET=11.0` only on macOS and only when the variable is unset (dossier §2c). */
    private fun makeEnv(os: LuaOs): Map<String, String> {
        if (os != LuaOs.MACOS) return emptyMap()
        if (System.getenv("MACOSX_DEPLOYMENT_TARGET") != null) return emptyMap()
        return mapOf("MACOSX_DEPLOYMENT_TARGET" to "11.0")
    }

    private fun installCopies(buildDir: Path, prefix: Path): List<Pair<Path, Path>> {
        val src = buildDir.resolve("src")
        val copies = mutableListOf(
            src.resolve("luajit") to prefix.resolve("bin/lua"),
            src.resolve("libluajit.a") to prefix.resolve("lib/libluajit-5.1.a"),
            src.resolve("libluajit.so") to prefix.resolve("lib/libluajit-5.1.so.2"),
        )
        HEADERS.forEach { header -> copies += src.resolve(header) to prefix.resolve("include/$header") }
        return copies
    }

    /** The `src/jit` Lua runtime dir copied into `<prefix>/share/lua/5.1/jit` (design §3.9 step 3). */
    fun jitRuntimeSource(buildDir: Path): Path = buildDir.resolve("src/jit")

    fun jitRuntimeDest(prefix: Path): Path = prefix.resolve("share/lua/5.1/jit")
}
