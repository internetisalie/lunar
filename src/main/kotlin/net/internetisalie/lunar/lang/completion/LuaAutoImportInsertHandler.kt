package net.internetisalie.lunar.lang.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import net.internetisalie.lunar.lang.path.LuaModulePathResolver
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.settings.AutoImportStyle
import net.internetisalie.lunar.settings.LuaProjectSettings

/**
 * On acceptance of a non-imported cross-file symbol, inserts the appropriate `require`
 * statement (COMP-03-03). Attached only to lookup elements with `isImported = false`.
 *
 * Threading: [handleInsert] runs on the EDT. All PSI/VFS reads happen in [runReadActionBlocking];
 * the single document mutation runs in one [WriteCommandAction] (one undoable command).
 */
class LuaAutoImportInsertHandler(
    private val targetFile: VirtualFile,
    private val modulePathResolver: LuaModulePathResolver,
    private val exportStyleDetector: LuaExportStyleDetector,
    private val importNameResolver: LuaImportNameResolver,
) : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val project = context.project
        val currentFile = context.file as? LuaFile ?: return
        if (!targetFile.isValid) return

        ProgressManager.checkCanceled()

        val modulePath = runReadActionBlocking { modulePathResolver.resolve(targetFile, project) } ?: return

        val alreadyRequired = runReadActionBlocking {
            LuaDeduplicationChecker.isAlreadyRequired(currentFile, modulePath)
        }
        if (alreadyRequired) return

        val exportStyle = resolveExportStyle(project)
        val localName = runReadActionBlocking {
            importNameResolver.resolve(targetFile, exportStyle, currentFile, project)
        }
        val importStatement = buildImportStatement(modulePath, exportStyle, localName)

        WriteCommandAction.runWriteCommandAction(
            project,
            "Auto-import $modulePath",
            null,
            { LuaImportInserter.insert(context.editor, currentFile, importStatement) },
        )
    }

    private fun resolveExportStyle(project: Project): LuaExportStyle {
        val override = LuaProjectSettings.getInstance(project).autoImportStyle
        return when (override) {
            AutoImportStyle.FORCE_LOCAL_ASSIGN -> LuaExportStyle.RETURN_STYLE
            AutoImportStyle.FORCE_GLOBAL -> LuaExportStyle.GLOBAL_STYLE
            AutoImportStyle.AUTO_DETECT -> runReadActionBlocking { exportStyleDetector.detect(targetFile, project) }
        }
    }

    private fun buildImportStatement(
        modulePath: String,
        exportStyle: LuaExportStyle,
        localName: String?,
    ): String = when (exportStyle) {
        LuaExportStyle.RETURN_STYLE ->
            "local ${localName ?: modulePath.substringAfterLast('.')} = require(\"$modulePath\")"
        LuaExportStyle.GLOBAL_STYLE ->
            "require(\"$modulePath\")"
    }
}
