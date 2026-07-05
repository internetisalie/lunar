package net.internetisalie.lunar.rocks.init

import net.internetisalie.lunar.rocks.env.HererocksFlavor

enum class RockType { LIBRARY, APPLICATION }

data class LuaRocksProjectSettings(
    var name: String = "",
    var type: RockType = RockType.LIBRARY,
    var loaderSetup: Boolean = false,
    var bustedConfig: Boolean = false,
    var makefile: Boolean = false,

    // --- Interpreter (ROCKS-17: hererocks integration in the New Project wizard) -----------------
    /**
     * The Lua flavor + version the new project targets. Always defines the project [Target]
     * (platform + language level); when [provisionHererocks] is true they also parameterize the
     * hererocks provision.
     */
    var flavor: HererocksFlavor = HererocksFlavor.PUC,
    var luaVersion: String = "5.4",
    /**
     * When true, provision a project-scoped isolated environment (`<projectDir>/.lua`) with
     * hererocks and let it drive the interpreter (Managed mode). When false, the project uses
     * [interpreterPath] (an existing, globally-registered interpreter) in Explicit mode.
     */
    var provisionHererocks: Boolean = false,
    /** Path of the chosen existing interpreter when [provisionHererocks] is false; blank = none. */
    var interpreterPath: String = "",
)
