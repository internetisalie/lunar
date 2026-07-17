package net.internetisalie.lunar.coverage

import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.openapi.project.Project

class LuaCoverageAnnotator(project: Project) : SimpleCoverageAnnotator(project) {

    companion object {
        fun getInstance(project: Project): LuaCoverageAnnotator =
            project.getService(LuaCoverageAnnotator::class.java)
    }
}
