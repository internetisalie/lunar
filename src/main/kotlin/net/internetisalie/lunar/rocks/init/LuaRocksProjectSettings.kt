package net.internetisalie.lunar.rocks.init

enum class RockType { LIBRARY, APPLICATION }

/** Runtime kind ids offered by the New Project wizard (TOOLING-05 §2.8). */
object WizardRuntimeKinds {
    const val LUA = "lua"
    const val LUAJIT = "luajit"
}

data class LuaRocksProjectSettings(
    var name: String = "",
    var type: RockType = RockType.LIBRARY,
    var loaderSetup: Boolean = false,
    var bustedConfig: Boolean = false,
    var makefile: Boolean = false,

    // --- Interpreter (TOOLING-05 §2.8: toolchain integration in the New Project wizard) --------------
    /**
     * The runtime kind id the new project targets (`"lua"` or `"luajit"`, [WizardRuntimeKinds]). With
     * [luaVersion] this defines the project `Target` (platform + language level); when
     * [provisionEnvironment] is true they also parameterize the provisioning request.
     */
    var kindId: String = WizardRuntimeKinds.LUA,
    var luaVersion: String = "5.4",
    /**
     * When true, provision a project-scoped isolated environment (`<projectDir>/.lua`) via the
     * TOOLING-04 provisioner and activate it. When false, the project binds [interpreterPath] (an
     * existing, registered runtime tool).
     */
    var provisionEnvironment: Boolean = false,
    /** Path of the chosen existing interpreter when [provisionEnvironment] is false; blank = none. */
    var interpreterPath: String = "",
)
