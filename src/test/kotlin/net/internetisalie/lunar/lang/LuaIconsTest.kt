package net.internetisalie.lunar.lang

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Lunar's icons resolve, with their dark and 20 px variants, and tooling files get their own. */
class LuaIconsTest : BasePlatformTestCase() {
    fun `test every SVG icon has a dark variant on the classpath`() {
        val missing =
            (FILE_TYPE_ICONS + TOOLWINDOW_ICONS + PLUGIN_ICONS).filter {
                resource(it.replace(".svg", "_dark.svg")) ==
                    null
            }

        assertEquals(emptyList<String>(), missing)
    }

    fun `test every tool window icon has a light and dark 20 px variant`() {
        val variants =
            TOOLWINDOW_ICONS.flatMap {
                listOf(it.replace(".svg", "@20x20.svg"), it.replace(".svg", "@20x20_dark.svg"))
            }

        assertEquals(emptyList<String>(), variants.filter { resource(it) == null })
    }

    fun `test every icon a plugin xml attribute names is a field of LuaIcons`() {
        val pluginXml = resource("/META-INF/plugin.xml")?.readText().orEmpty()
        val named = ICON_ATTRIBUTE.findAll(pluginXml).map { it.groupValues[1] }.toSet()
        val fields =
            LuaIcons::class.java.fields
                .map { it.name }
                .toSet()

        assertTrue(named.isNotEmpty())
        assertEquals(emptySet<String>(), named - fields)
    }

    fun `test tooling files are matched by name`() {
        assertSame(LuaIcons.ROCKSPEC, LuaFileIconProvider.iconFor("argparse-0.7.2-1.rockspec"))
        assertSame(LuaIcons.LUACHECK_CONFIG, LuaFileIconProvider.iconFor(".luacheckrc"))
        assertSame(LuaIcons.BUSTED_CONFIG, LuaFileIconProvider.iconFor(".busted"))
        assertSame(LuaIcons.STYLUA_CONFIG, LuaFileIconProvider.iconFor(".stylua.toml"))
        assertSame(LuaIcons.JSON_CONFIG, LuaFileIconProvider.iconFor(".luarc.json"))
    }

    fun `test other files are left to their file type`() {
        assertNull(LuaFileIconProvider.iconFor("main.lua"))
        assertNull(LuaFileIconProvider.iconFor("package.json"))
    }

    fun `test the provider is consulted for a project file`() {
        val file = myFixture.addFileToProject("dkjson-2.8-1.rockspec", "package = \"dkjson\"").virtualFile

        assertSame(LuaIcons.ROCKSPEC, LuaFileIconProvider().getIcon(file, 0, project))
    }

    private fun resource(path: String) = LuaIcons::class.java.getResource(path)

    private companion object {
        val ICON_ATTRIBUTE = Regex("""icon="net\.internetisalie\.lunar\.lang\.LuaIcons\.(\w+)"""")

        val FILE_TYPE_ICONS =
            listOf(
                "lua",
                "rockspec",
                "luacheck",
                "busted",
                "stylua",
                "luarc",
                "luacov",
            ).map { "/icons/fileTypes/$it.svg" }

        val TOOLWINDOW_ICONS =
            listOf(
                "luarocksDependencies",
                "luarocksPackages",
                "redisFunctions",
                "testMatrix",
            ).map { "/icons/toolwindows/$it.svg" }

        val PLUGIN_ICONS = listOf("/META-INF/pluginIcon.svg")
    }
}
