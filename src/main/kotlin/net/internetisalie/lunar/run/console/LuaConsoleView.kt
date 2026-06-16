package net.internetisalie.lunar.run.console

import com.intellij.execution.console.LanguageConsoleImpl
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.lang.LuaLanguage

/**
 * Lua-highlighted REPL console view (RUN-03-01/04). The input editor is backed by [LuaLanguage],
 * so syntax highlighting and the existing completion contributor (RUN-03-06) apply for free.
 */
class LuaConsoleView(project: Project) :
    LanguageConsoleImpl(project, "Lua Console", LuaLanguage)
