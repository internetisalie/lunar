package net.internetisalie.lunar.rocks.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import net.internetisalie.lunar.lang.path.PathConfiguration
import java.nio.file.Paths

class LuaRockSourceRootDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val dir = node.virtualFile ?: return
        if (!dir.isDirectory || !dir.isValid) return
        val base = project.basePath ?: return
        val roots = sourceRootDirs(project, base)
        if (dir.path.trimEnd('/') in roots) {
            data.addText(" rock source root", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }

    private fun sourceRootDirs(project: Project, base: String): Set<String> {
        val patterns = PathConfiguration.getProjectSourcePathPatterns(project)
        return patterns.asSequence()
            .map { it.leadingPath }
            .map { Paths.get(it) }
            .filter { it.isAbsolute && it.startsWith(base) }
            .map { it.toString().replace('\\', '/').trimEnd('/') }
            .toSet()
    }
}
