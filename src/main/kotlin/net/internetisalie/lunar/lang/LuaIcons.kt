package net.internetisalie.lunar.lang

import com.intellij.openapi.util.IconLoader.getIcon
import javax.swing.Icon
import kotlin.jvm.JvmField

/**
 * Lunar's icons. Each SVG has a `_dark` twin, and each tool window icon an `@20x20` pair that the
 * New UI stripe loads in place of scaling the 16 px one.
 */
object LuaIcons {
    @JvmField val FILE: Icon = load("/icons/fileTypes/lua.svg")

    @JvmField val ROCKSPEC: Icon = load("/icons/fileTypes/rockspec.svg")

    @JvmField val LUACHECK_CONFIG: Icon = load("/icons/fileTypes/luacheck.svg")

    @JvmField val BUSTED_CONFIG: Icon = load("/icons/fileTypes/busted.svg")

    @JvmField val STYLUA_CONFIG: Icon = load("/icons/fileTypes/stylua.svg")

    @JvmField val JSON_CONFIG: Icon = load("/icons/fileTypes/luarc.svg")

    @JvmField val COVERAGE_REPORT: Icon = load("/icons/fileTypes/luacov.svg")

    @JvmField val TOOLWINDOW_DEPENDENCIES: Icon = load("/icons/toolwindows/luarocksDependencies.svg")

    @JvmField val TOOLWINDOW_PACKAGES: Icon = load("/icons/toolwindows/luarocksPackages.svg")

    @JvmField val TOOLWINDOW_REDIS_FUNCTIONS: Icon = load("/icons/toolwindows/redisFunctions.svg")

    @JvmField val TOOLWINDOW_TEST_MATRIX: Icon = load("/icons/toolwindows/testMatrix.svg")

    private fun load(path: String): Icon = getIcon(path, LuaIcons::class.java)
}
