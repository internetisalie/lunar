package net.internetisalie.lunar.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-lifecycle [CoroutineScope] holder (MAINT-22-02).
 *
 * The IntelliJ Platform injects a project-scoped [CoroutineScope] into this light `@Service`'s
 * constructor and cancels it on project dispose, giving structured-concurrency background work a
 * lifecycle-bound parent scope. Consumers either `launch` on [scope] directly (fire-and-forget)
 * or derive a `childScope` for a bounded sub-lifecycle (e.g. a debug session).
 *
 * Do not retain `Project`/`Editor`/`PsiFile` across a launched job; read PSI/VFS via `readAction`.
 */
@Service(Service.Level.PROJECT)
class LunarCoroutineScopeService(val scope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): LunarCoroutineScopeService = project.service()
    }
}
