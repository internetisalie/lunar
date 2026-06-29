package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import net.internetisalie.lunar.lang.LuaFileType

class LuaJsonSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        if (file.fileType != LuaFileType) return false
        // Prevent JSON Schema checks on rockspecs which break BuildWorkspaceActionTest via EDT races
        if (file.extension == "rockspec") return false
        return true
    }
}
