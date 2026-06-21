package net.internetisalie.lunar.lang

import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.Icon
import kotlin.jvm.JvmField

object LuaIcons {
    @JvmField val FILE: Icon = getIcon("/icons/rocket_16.png", LuaIcons::class.java)
    @JvmField val ROCKET: Icon = getIcon("/icons/rocket_16.png", LuaIcons::class.java)
    @JvmField val TEST: Icon = getIcon("/icons/rocket_16.png", LuaIcons::class.java)
    @JvmField val COVERAGE: Icon = getIcon("/icons/rocket_16.png", LuaIcons::class.java)
    @JvmField val COVERAGE_REPORT: Icon = getIcon("/icons/rocket_16.png", LuaIcons::class.java)
}
