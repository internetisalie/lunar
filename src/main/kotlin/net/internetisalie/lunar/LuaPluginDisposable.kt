package net.internetisalie.lunar

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.APP, Service.Level.PROJECT)
class LuaPluginDisposable : Disposable {
    override fun dispose() = Unit

    companion object {
        @JvmStatic
        fun getInstance(): Disposable =
            ApplicationManager.getApplication().getService(LuaPluginDisposable::class.java)

        @JvmStatic
        fun getInstance(project: Project): Disposable =
            project.getService(LuaPluginDisposable::class.java)
    }
}
