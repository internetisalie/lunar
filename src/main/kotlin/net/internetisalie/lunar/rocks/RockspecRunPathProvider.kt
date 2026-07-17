package net.internetisalie.lunar.rocks

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import net.internetisalie.lunar.settings.LuaProjectSettings

object RockspecRunPathProvider {
    /** Local roots, '?'-expanded, ending ';' — to PREPEND before the existing project LUA_PATH. */
    fun luaPathPrefix(project: Project): String {
        val patterns = RockspecSourcePathProvider.getInstance(project).derivedPatterns()
        return patterns.joinToString("") { it.spec + ";" }
    }

    /** "<treeRoot>/lib/lua/<X.Y>/?.<ext>;;" when any builtin C module exists; else null. */
    fun luaCPath(project: Project): String? {
        val cRocks = RockspecSourcePathProvider.getInstance(project).cModuleRockspecs()
        if (cRocks.none { it.hasCModules }) return null

        val treeRoot = LuaRocksTreeLocator.treeRoot(project) ?: return null
        val languageLevel = LuaProjectSettings.getInstance(project).state.getTarget()
            .getImplicitLanguageLevel().version
        val nativeExtension = nativeModuleExtension()
        return "$treeRoot/lib/lua/$languageLevel/?.$nativeExtension;;".replace('\\', '/')
    }

    internal fun nativeModuleExtension(): String = if (SystemInfo.isWindows) "dll" else "so"
}
