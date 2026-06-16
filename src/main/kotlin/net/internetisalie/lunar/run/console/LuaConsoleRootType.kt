package net.internetisalie.lunar.run.console

import com.intellij.execution.console.ConsoleRootType
import com.intellij.ide.scratch.RootType

/**
 * Scratch root type backing persistent REPL history (RUN-03-05). Registered as a
 * `scratch.rootType` extension and resolved via [instance].
 */
class LuaConsoleRootType internal constructor() : ConsoleRootType("lua", "Lua Console") {
    companion object {
        val instance: LuaConsoleRootType
            get() = RootType.findByClass(LuaConsoleRootType::class.java)
    }
}
