package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Quick fix that adds the undeclared name to the project's "Additional Globals" allowlist,
 * so subsequent reads of the name are no longer flagged (INSP-01-07).
 */
class LuaAddToGlobalsQuickFix(private val name: String) : LocalQuickFix {

    override fun getFamilyName(): String = "Add '$name' to additional globals"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        WriteAction.run<RuntimeException> {
            val globals = LuaProjectSettings.getInstance(project).state.additionalGlobals
            if (name !in globals) globals.add(name)
        }
    }
}
