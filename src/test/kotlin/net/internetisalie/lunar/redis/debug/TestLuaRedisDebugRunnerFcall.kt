package net.internetisalie.lunar.redis.debug

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.run.LuaRedisExecMode
import net.internetisalie.lunar.redis.run.LuaRedisRunConfiguration
import net.internetisalie.lunar.redis.run.LuaRedisRunConfigurationType

/**
 * TC-DBG-1 (REDIS-05 Phase 4, AC-9): the Debug executor is disabled for FCALL-mode
 * configurations (LDB cannot step Function invocations, REDIS epic RISK-R05), while
 * REDIS-02's Debug support for EVAL/EVALSHA modes is preserved (design §3.11).
 */
class TestLuaRedisDebugRunnerFcall : BasePlatformTestCase() {

    private fun newConfig(): LuaRedisRunConfiguration {
        val type = LuaRedisRunConfigurationType.getInstance()
        val factory = type.configurationFactories[0]
        return factory.createTemplateConfiguration(project) as LuaRedisRunConfiguration
    }

    /** FCALL mode → Debug executor cannot run (greyed; tooltip via human-verification §4). */
    fun testDebugDisabledForFcall() {
        val config = newConfig()
        config.execMode = LuaRedisExecMode.FCALL
        assertFalse(
            "Debug executor must be disabled for FCALL mode",
            LuaRedisDebugRunner().canRun(DefaultDebugExecutor.EXECUTOR_ID, config),
        )
    }

    /** EVAL mode → Debug executor still runs (REDIS-02 behavior preserved). */
    fun testDebugEnabledForEval() {
        val config = newConfig()
        config.execMode = LuaRedisExecMode.EVAL
        assertTrue(
            "Debug executor must remain enabled for EVAL mode",
            LuaRedisDebugRunner().canRun(DefaultDebugExecutor.EXECUTOR_ID, config),
        )
    }
}
