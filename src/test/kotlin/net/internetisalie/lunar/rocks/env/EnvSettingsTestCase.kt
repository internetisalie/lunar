package net.internetisalie.lunar.rocks.env

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.settings.InterpreterMode
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Base for env/matrix tests that mutate the project-level [LuaProjectSettings] env state. The
 * light-test fixture shares one in-memory project across methods/classes, so leftover envs from a
 * prior test leak into the next one (ROCKS-15 test-isolation fix). Resetting the env set in both
 * [setUp] and [tearDown] makes every env test order-independent.
 */
abstract class EnvSettingsTestCase : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        resetEnvState()
    }

    override fun tearDown() {
        try {
            resetEnvState()
        } finally {
            super.tearDown()
        }
    }

    @Suppress("DEPRECATION")
    private fun resetEnvState() {
        val state = LuaProjectSettings.getInstance(project).state
        state.hererocksEnvs.clear()
        state.activeEnvId = ""
        state.hererocksEnv = null
        // ROCKS-16: the interpreter mode + its overlay also live on the shared project state.
        state.interpreterMode = InterpreterMode.EXPLICIT
        state.interpreter = null
        state.target = null
        state.explicitInterpreter = null
        state.explicitTarget = null
    }
}
