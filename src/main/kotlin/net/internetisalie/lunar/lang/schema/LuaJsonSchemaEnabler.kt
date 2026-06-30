package net.internetisalie.lunar.lang.schema

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler
import net.internetisalie.lunar.lang.LuaFileType

class LuaJsonSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile, project: Project?): Boolean {
        // Coarse, index-free language gate — the JsonSchemaEnabler contract says this method MUST NOT
        // address indexes, so per-file schema selection is left to provider.isAvailable + the engine
        // (which no-ops when no provider claims the file). Mirrors the platform's own YamlJsonEnabler,
        // which likewise enables for every file of its language.
        if (file.fileType != LuaFileType) return false
        // SCHEMA-02 TODO: enabling the engine on .rockspec triggers JsonSchemaService activity that
        // races the EDT in BuildWorkspaceActionTest. Excluded until SCHEMA-02 maps rockspecs and
        // resolves that race; remove this line then.
        if (file.extension == "rockspec") return false
        return true
    }
}
