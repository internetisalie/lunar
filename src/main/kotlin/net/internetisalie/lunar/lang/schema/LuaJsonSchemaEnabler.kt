package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler

class LuaJsonSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        // Phase 1: returns false until providers exist
        return false
    }
}
