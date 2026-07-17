package net.internetisalie.lunar.analysis.redis

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * Provides quick documentation for Redis/Valkey command-name string literals inside
 * `redis.call("CMD")` / `redis.pcall("CMD")` call sites (design §2.5, §3.6, AC-5).
 *
 * When the caret is on a command-name literal that resolves against the bundled spec,
 * returns a [RedisCommandDocumentationTarget] carrying the [RedisCommandInfo] for that
 * command. All PSI access is done transiently; no PSI is stored on the target.
 *
 * Registered as a sibling to [net.internetisalie.lunar.lang.doc.LuaDocumentationTargetProvider]
 * (plugin.xml §7).
 */
class RedisCommandDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val element = file.findElementAt(offset) ?: return emptyList()
        if (element.elementType != LuaElementTypes.STRING) return emptyList()
        val site = RedisCallSiteMatcher.match(element) ?: return emptyList()
        if (site.nameLiteral?.string !== element) return emptyList()
        val name = site.commandName ?: return emptyList()
        val project = file.project
        val target = LuaProjectSettings.getInstance(project).state.getTarget()
        if (target.platform != LuaPlatform.REDIS && target.platform != LuaPlatform.VALKEY) return emptyList()
        val info = RedisCommandSpecService.getInstance().specFor(target).lookup(name)
            ?: return emptyList()
        return listOf(RedisCommandDocumentationTarget(info))
    }
}

/**
 * Documentation target for a single Redis command (design §3.6, TC-DOC-1).
 *
 * Holds only a plain [RedisCommandInfo] data record — no PSI, no Project — so
 * [createPointer] can reconstruct it from the info alone (no `SmartPsiElementPointer`
 * needed). The HTML body contains the command name, summary, since-version, and arity.
 */
class RedisCommandDocumentationTarget(private val info: RedisCommandInfo) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val captured = info
        return Pointer { RedisCommandDocumentationTarget(captured) }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(info.name)
            .icon(com.intellij.icons.AllIcons.Nodes.Method)
            .presentation()

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(buildDocHtml(info))

    companion object {
        /** Builds the HTML body for a Redis command's quick documentation. */
        fun buildDocHtml(info: RedisCommandInfo): String = buildString {
            append("<b>")
            append(info.name)
            append("</b><br>")
            append(info.summary)
            append("<br>Since ")
            append(info.since)
            append("<br>Arity ")
            append(info.arity)
        }
    }
}
