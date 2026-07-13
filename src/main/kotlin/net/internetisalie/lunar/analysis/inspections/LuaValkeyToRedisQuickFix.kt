package net.internetisalie.lunar.analysis.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.psi.LuaElementFactory
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * Quick fix that rewrites a flagged `server.<member>` access to `redis.<member>` by replacing
 * the base `server` identifier leaf with a `redis` identifier (REDIS-03 AC-6, design §2.8, §3.6).
 *
 * Offered only for the `server.<member>` case (a 1:1 compat rename exists); never for the
 * `SERVER_*` globals, which have no `redis` equivalent.
 */
class LuaValkeyToRedisQuickFix : LocalQuickFix {

    override fun getFamilyName(): String = "Replace 'server' with 'redis'"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val serverRef = descriptor.psiElement as? LuaNameRef ?: return
        val identifierLeaf = serverRef.identifier
        WriteAction.run<RuntimeException> {
            val redisIdentifier = LuaElementFactory.createIdentifier(project, "redis") ?: return@run
            identifierLeaf.replace(redisIdentifier)
        }
    }
}
