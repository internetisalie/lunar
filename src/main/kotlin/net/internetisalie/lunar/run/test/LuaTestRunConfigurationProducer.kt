package net.internetisalie.lunar.run.test

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.settings.LuaProjectSettings

class LuaTestRunConfigurationProducer : LazyRunConfigurationProducer<LuaTestRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        LuaTestRunConfigurationType.getInstance().configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: LuaTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val targetLocation = context.location ?: return false
        val targetPsiElement = targetLocation.psiElement
        val targetVirtualFile = targetLocation.virtualFile ?: return false

        if (targetPsiElement is PsiDirectory) {
            setupDirectoryConfiguration(configuration, targetPsiElement, targetVirtualFile)
            sourceElement.set(targetPsiElement)
            return true
        }

        val targetFile = targetPsiElement.containingFile ?: return false
        if (targetFile.fileType.name != "Lua") return false
        if (!isTestFile(targetFile)) return false

        setupFileOrPatternConfiguration(configuration, context, sourceElement)
        return true
    }

    private fun setupDirectoryConfiguration(
        configuration: LuaTestRunConfiguration,
        directory: PsiDirectory,
        virtualFile: VirtualFile
    ) {
        configuration.testTargetType = "DIRECTORY"
        configuration.testTarget = virtualFile.path
        configuration.name = "Lua Tests in ${virtualFile.name}"
        val defaultInterpreter = LuaProjectSettings.getInstance(directory.project).state.interpreter
        if (defaultInterpreter != null) {
            configuration.interpreter = defaultInterpreter
        }
        configuration.workingDirectory = virtualFile.path
    }

    private fun setupFileOrPatternConfiguration(
        configuration: LuaTestRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ) {
        val targetLocation = context.location ?: return
        val targetPsiElement = targetLocation.psiElement
        val targetVirtualFile = targetLocation.virtualFile ?: return
        val targetFile = targetPsiElement.containingFile ?: return

        val framework = detectFramework(targetFile)
        configuration.testFramework = framework
        
        val targetProject = context.project
        val defaultInterpreter = LuaProjectSettings.getInstance(targetProject).state.interpreter
        if (defaultInterpreter != null) {
            configuration.interpreter = defaultInterpreter
        }
        configuration.workingDirectory = targetVirtualFile.parent?.path ?: targetProject.basePath

        val targetCall = runReadActionBlocking {
            PsiTreeUtil.getParentOfType(targetPsiElement, LuaFuncCall::class.java, false)
        }
        val targetTestName = targetCall?.let { getFirstStringArgument(it) }
        val targetCalleeName = targetCall?.let { getCalleeName(it) }

        if (framework == LuaTestFramework.BUSTED && targetTestName != null &&
            (targetCalleeName == "describe" || targetCalleeName == "it" || targetCalleeName == "context")
        ) {
            configuration.testTargetType = "PATTERN"
            configuration.testTarget = targetTestName
            configuration.name = "Lua Tests: $targetTestName"
            sourceElement.set(targetCall)
        } else {
            configuration.testTargetType = "FILE"
            configuration.testTarget = targetVirtualFile.path
            configuration.name = "Lua Tests: ${targetVirtualFile.name}"
            sourceElement.set(targetFile)
        }
    }

    override fun isConfigurationFromContext(
        configuration: LuaTestRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val targetLocation = context.location ?: return false
        val targetPsiElement = targetLocation.psiElement
        val targetVirtualFile = targetLocation.virtualFile ?: return false

        if (targetPsiElement is PsiDirectory) {
            return configuration.testTargetType == "DIRECTORY" && configuration.testTarget == targetVirtualFile.path
        }

        val targetFile = targetPsiElement.containingFile ?: return false
        if (targetFile.fileType.name != "Lua") return false

        if (configuration.testTargetType == "FILE") {
            return configuration.testTarget == targetVirtualFile.path
        }

        if (configuration.testTargetType == "PATTERN") {
            val targetCall = runReadActionBlocking {
                PsiTreeUtil.getParentOfType(targetPsiElement, LuaFuncCall::class.java, false)
            }
            val targetTestName = targetCall?.let { getFirstStringArgument(it) }
            return configuration.testTarget == targetTestName
        }

        return false
    }

    private fun detectFramework(file: PsiFile): LuaTestFramework {
        val text = runReadActionBlocking { file.text }
        if (text.contains("busted") || text.contains("describe") || text.contains("it(")) {
            return LuaTestFramework.BUSTED
        }
        return LuaTestFramework.LUNITY
    }

    private fun isTestFile(file: PsiFile): Boolean {
        val name = file.name
        if (name.endsWith("_spec.lua") || name.endsWith("_test.lua") || name.contains("spec") || name.contains("test")) {
            return true
        }
        val text = runReadActionBlocking { file.text }
        return text.contains("lunity") || text.contains("busted")
    }

    private fun getFirstStringArgument(call: LuaFuncCall): String? {
        val args = call.nameAndArgsList.firstOrNull()?.args ?: return null
        val firstArg = args.exprList?.exprList?.firstOrNull() ?: return null
        val token = firstArg.firstChild ?: return null
        val tokenType = token.node.elementType
        if (tokenType == LuaElementTypes.STRING) {
            val text = token.text
            if (text.length >= 2) {
                return text.substring(1, text.length - 1)
            }
        }
        return null
    }

    private fun getCalleeName(call: LuaFuncCall): String? {
        val varOrExp = call.varOrExp
        val nameRef = varOrExp.`var`?.nameRef ?: return null
        return nameRef.text
    }
}
