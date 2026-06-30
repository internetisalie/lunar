package net.internetisalie.lunar.lang.schema.providers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.schema.LuaSchemaFileProvider

/**
 * Maps `.rockspec` files to a bundled rockspec JSON schema (SCHEMA-02), selecting the v3.0 or v3.1
 * schema by the file's `rockspec_format` (design §3.1). Registered as application-level
 * `schemaFileProvider` extensions, so no [com.intellij.openapi.project.Project] is injected; the
 * project is obtained at call time via [ProjectLocator].
 */
sealed class RockspecSchemaProvider(
    private val displayName: String,
    private val schemaResourcePath: String,
) : LuaSchemaFileProvider() {

    /** Provider for v3.0-and-below rockspecs (the LuaRocks default when `rockspec_format` is absent). */
    class V30 : RockspecSchemaProvider("Rockspec v3.0", "/jsonschema/rockspec-schema-v30.json") {
        override fun isAvailable(file: VirtualFile): Boolean = isRockspec(file) && !isFormat31(file)
    }

    /** Provider for rockspecs declaring `rockspec_format = "3.1"`. */
    class V31 : RockspecSchemaProvider("Rockspec v3.1", "/jsonschema/rockspec-schema-v31.json") {
        override fun isAvailable(file: VirtualFile): Boolean = isRockspec(file) && isFormat31(file)
    }

    override fun getName(): String = displayName

    override fun getSchemaFile(): VirtualFile? =
        JsonSchemaProviderFactory.getResourceFile(this::class.java, schemaResourcePath)

    protected fun isRockspec(file: VirtualFile): Boolean = file.extension == ROCKSPEC_EXTENSION

    /** True when the file's top-level `rockspec_format` string starts with `"3.1"`; false otherwise. */
    protected fun isFormat31(file: VirtualFile): Boolean {
        val format = readRockspecFormat(file) ?: return false
        return format.startsWith(FORMAT_31_PREFIX)
    }

    companion object {
        private const val ROCKSPEC_EXTENSION = "rockspec"
        private const val FORMAT_31_PREFIX = "3.1"
        private const val FORMAT_VARIABLE = "rockspec_format"

        private fun readRockspecFormat(file: VirtualFile): String? {
            if (!file.isValid) return null
            val targetProject = ProjectLocator.getInstance().guessProjectForFile(file) ?: return null
            return ApplicationManager.getApplication().runReadAction<String?> {
                val luaFile = PsiManager.getInstance(targetProject).findFile(file) as? LuaFile
                luaFile?.let { extractFormatValue(it) }
            }
        }

        /** The unquoted string assigned to the first top-level `rockspec_format = "…"`, or null. */
        private fun extractFormatValue(luaFile: LuaFile): String? {
            val assignment = PsiTreeUtil.findChildrenOfType(luaFile, LuaAssignmentStatement::class.java)
                .firstOrNull { it.varList.varList.firstOrNull()?.nameRef?.text == FORMAT_VARIABLE }
                ?: return null
            val valueExpr = assignment.exprList.exprList.firstOrNull() as? LuaTerminalExpr
            val literal = valueExpr?.string?.text ?: return null
            return literal.trim('"', '\'')
        }
    }
}
