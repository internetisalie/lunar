package net.internetisalie.lunar.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager

fun newAppBackgroundTask(
    description: String,
    action : (ProgressIndicator)->Unit,
) : Task.Backgroundable {
    return newProjectBackgroundTask(description, ProjectManager.getInstance().defaultProject, action)
}

fun newProjectBackgroundTask(
    description: String,
    project: Project,
    action: (ProgressIndicator) -> Unit,
) : Task.Backgroundable {
    return object : Task.Backgroundable(project, description, false) {
        override fun run(indicator: ProgressIndicator) {
            action(indicator)
        }
    }
}
