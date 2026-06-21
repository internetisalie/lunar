package net.internetisalie.lunar.lang.formatting.external

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.LuaLanguage
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType

class StyluaFormattingService : AsyncDocumentFormattingService() {

    override fun canFormat(psiFile: PsiFile): Boolean {
        if (psiFile.language !is LuaLanguage) return false
        val project = psiFile.project
        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.STYLUA)
        return tool != null && tool.isValid
    }

    override fun getFeatures(): Set<FormattingService.Feature> {
        return emptySet()
    }

    override fun prepareForFormatting(document: Document, formattingContext: FormattingContext) {
        super.prepareForFormatting(document, formattingContext)
    }

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val context = request.context
        val project = context.project
        val tool = LuaToolManager.getInstance().getEffectiveTool(project, LuaToolType.STYLUA) ?: return null
        if (!tool.isValid) return null

        val ioFile = request.ioFile ?: return null
        val fileName = ioFile.name
        val parentDir = ioFile.parentFile ?: return null
        val workingDirectory = parentDir.absolutePath

        val config = StyluaExecutionConfig(
            styluaPath = tool.path,
            fileName = fileName,
            workingDirectory = workingDirectory
        )

        return StyluaFormattingTask(
            request = request,
            config = config
        )
    }

    override fun getNotificationGroupId(): String {
        return "notification.group.lunar.stylua"
    }

    override fun getName(): String {
        return "StyLua"
    }
}
