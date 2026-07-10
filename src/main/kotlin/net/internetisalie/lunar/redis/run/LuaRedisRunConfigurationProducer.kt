package net.internetisalie.lunar.redis.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Offers a "Redis Script" run configuration for a `.lua` file only when the project target is Redis
 * (design §7, RISK-R12). Target-gated and **additive** — it never replaces the standard Lua producer;
 * on a non-Redis target [setupConfigurationFromContext] returns `false` (TC-PROD-1). Shape mirrors
 * `run/test/LuaTestRunConfigurationProducer`.
 */
class LuaRedisRunConfigurationProducer : LazyRunConfigurationProducer<LuaRedisRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        LuaRedisRunConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: LuaRedisRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val targetLocation = context.location ?: return false
        val targetPsiElement = targetLocation.psiElement
        val targetVirtualFile = targetLocation.virtualFile ?: return false
        val targetFile = targetPsiElement.containingFile ?: return false
        if (targetFile.fileType.name != "Lua") return false
        if (!isRedisTarget(context)) return false

        configuration.scriptPath = targetVirtualFile.path
        configuration.name = "Redis Script: ${targetVirtualFile.name}"
        prefillConnection(configuration, context)
        sourceElement.set(targetFile)
        return true
    }

    override fun isConfigurationFromContext(
        configuration: LuaRedisRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val targetVirtualFile = context.location?.virtualFile ?: return false
        return configuration.scriptPath == targetVirtualFile.path
    }

    private fun isRedisTarget(context: ConfigurationContext): Boolean =
        LuaProjectSettings.getInstance(context.project).state.getTarget().platform == LuaPlatform.REDIS

    private fun prefillConnection(configuration: LuaRedisRunConfiguration, context: ConfigurationContext) {
        if (!configuration.connectionId.isNullOrBlank()) return
        val firstConnection =
            net.internetisalie.lunar.redis.connection.LuaRedisConnectionSettings.getInstance(context.project)
                .connections().firstOrNull() ?: return
        configuration.connectionId = firstConnection.id
    }
}
