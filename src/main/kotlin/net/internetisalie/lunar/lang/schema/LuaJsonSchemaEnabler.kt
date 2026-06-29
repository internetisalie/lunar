package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import net.internetisalie.lunar.lang.LuaFileType

class LuaJsonSchemaEnabler : JsonSchemaEnabler {
    private val isChecking = ThreadLocal.withInitial { false }

    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        if (file.fileType != LuaFileType) return false
        if (project == null) return false

        if (isChecking.get()) return false
        isChecking.set(true)
        try {
            return JsonSchemaService.Impl.get(project).getSchemaFilesForFile(file).isNotEmpty()
        } finally {
            isChecking.set(false)
        }
    }
}
