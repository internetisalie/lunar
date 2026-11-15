package net.internetisalie.lunar.lang.psi.types

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import net.internetisalie.lunar.lang.psi.LuaFile

/**
 * MAINT-63 design §2.3 / §3.4 — feeds [LuaSourceChangeTracker] from every PSI event that ticks
 * `MODIFICATION_COUNT`: a Lua file's event bumps that file's counter, and an event that names no
 * file bumps the structure epoch. A preprocessor rather than a listener because the platform calls
 * preprocessors unconditionally. No index or PSI reads, and an event never creates the service.
 */
class LuaSourceChangePreprocessor : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        if (!PsiModificationTrackerImpl.canAffectPsi(event)) return
        val manager = event.source as? PsiManager ?: return
        val tracker = manager.project.serviceIfCreated<LuaSourceChangeTracker>() ?: return
        val file = event.file
        if (file != null && file !is LuaFile) return
        val url = file?.originalFile?.virtualFile?.url
        if (url == null) tracker.structureChanged() else tracker.fileChanged(url)
    }
}
