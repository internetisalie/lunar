package net.internetisalie.lunar.toolchain.provision

/**
 * Pure LuaRocks POSIX source-build-plan builder (design §3.5 step L, dossier §2d). Produces
 * the `configure` / `make build` / `make install` step sequence; the post-install CFLAGS
 * config append and the `bin/luarocks` presence check are performed by the strategy from the
 * data this plan carries. Requires `make` from the compiler probe.
 *
 * All shipped LuaRocks versions are ≥ 3.0.0, so the 2.0.x plain-`make` variant is not modelled.
 */
object LuaRocksBuildRecipe {
    /** The config line appended to `<prefix>/etc/luarocks/config-{X.Y}.lua` (design §3.5 step L.4). */
    val CONFIG_APPEND: String = """
        |variables = {
        |   CFLAGS = "-O2 -fPIC",
        |}
    """.trimMargin()

    fun plan(input: LuaBuildRecipeInput): BuildPlan {
        val prefix = input.prefix.toString()
        val buildDir = input.buildDir
        val steps = listOf(
            BuildStep(listOf("./configure", "--prefix=$prefix", "--with-lua=$prefix"), buildDir),
            BuildStep(listOf("make", "build"), buildDir),
            BuildStep(listOf("make", "install"), buildDir),
        )
        return BuildPlan(steps, installCopies = emptyList(), executables = listOf(input.prefix.resolve("bin/luarocks")))
    }
}
