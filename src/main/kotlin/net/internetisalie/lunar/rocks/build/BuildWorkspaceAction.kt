package net.internetisalie.lunar.rocks.build

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.rocks.LuaRockspecDiscoveryService

/**
 * Action to build all discovered rocks in the workspace in topological dependency order.
 */
class BuildWorkspaceAction : DumbAwareAction(
    "Build Workspace (dependency order)",
    "Build all first-party rocks with luarocks make in topological dependency order",
    LuaIcons.ROCKET,
) {
    override fun update(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            event.presentation.isEnabledAndVisible = false
            return
        }
        val count = LuaRockspecDiscoveryService.getInstance(project).discoverRockspecPaths().size
        event.presentation.isVisible = true
        event.presentation.isEnabled = count >= 2
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        ApplicationManager.getApplication().invokeLater {
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val descriptor = RunContentDescriptor(
                console,
                null,
                console.component,
                "Build Workspace",
                LuaIcons.ROCKET
            )

            RunContentManager.getInstance(project).showRunContent(
                DefaultRunExecutor.getRunExecutorInstance(),
                descriptor
            )

            runBuildTask(project, console)
        }
    }

    private fun runBuildTask(project: Project, console: ConsoleView) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Building workspace", true) {
            override fun run(indicator: ProgressIndicator) {
                val plan = WorkspaceBuildOrchestrator.computeBuildOrder(project)
                executePlan(project, plan, console, indicator)
            }
        })
    }

    private fun executePlan(
        project: Project,
        plan: BuildPlan,
        console: ConsoleView,
        indicator: ProgressIndicator
    ) {
        when (plan) {
            BuildPlan.Empty -> {
                console.print("No rockspecs discovered in workspace.\n", ConsoleViewContentType.NORMAL_OUTPUT)
            }
            is BuildPlan.Cycle -> {
                val cycleList = plan.packages.joinToString(", ")
                console.print(
                    "Build aborted: dependency cycle among $cycleList\n",
                    ConsoleViewContentType.ERROR_OUTPUT
                )
            }
            is BuildPlan.Ordered -> {
                val outcome = WorkspaceBuildRunner.run(project, plan.rocks, console, indicator)
                reportOutcome(outcome, console)
            }
        }
    }

    private fun reportOutcome(outcome: WorkspaceBuildRunner.BuildOutcome, console: ConsoleView) {
        val failedRock = outcome.failedRock
        if (failedRock != null) {
            console.print(
                "\nWorkspace build FAILED at ${failedRock.packageName} (exit ${outcome.exitCode})\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
        } else {
            console.print(
                "\nWorkspace build complete: ${outcome.builtCount} rocks\n",
                ConsoleViewContentType.SYSTEM_OUTPUT
            )
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
