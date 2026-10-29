package net.internetisalie.lunar.platform.target

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.analysis.inspections.LuaDeprecatedApiInspection
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * BUG-551: `loadstring` is the current API on Lua 5.1 and on both LuaJIT targets (Lua deprecated it
 * in 5.2), so their stubs must not tag it `---@deprecated`. `gcinfo`, deprecated on all three, is the
 * control that proves the inspection ran.
 */
@RunWith(JUnit4::class)
class LoadstringNotDeprecatedTest : IndexedBasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LuaDeprecatedApiInspection())
    }

    override fun tearDown() {
        try {
            useTarget(LuaPlatform.STANDARD, "5.4")
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testLoadstringIsNotDeprecatedOnStandard51() {
        useTarget(LuaPlatform.STANDARD, "5.1")
        assertEquals("names warned as deprecated", listOf("gcinfo"), deprecatedNames())
    }

    @Test
    fun testLoadstringIsNotDeprecatedOnLuaJit20() {
        useTarget(LuaPlatform.LUAJIT, "2.0")
        assertEquals("names warned as deprecated", listOf("gcinfo"), deprecatedNames())
    }

    @Test
    fun testLoadstringIsNotDeprecatedOnLuaJit21() {
        useTarget(LuaPlatform.LUAJIT, "2.1")
        assertEquals("names warned as deprecated", listOf("gcinfo"), deprecatedNames())
    }

    private fun useTarget(
        platform: LuaPlatform,
        label: String,
    ) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(platform, label))
        runInEdtAndWait {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(platform, version))
            RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
                VfsUtil.collectChildrenRecursively(root)
                VfsUtil.markDirtyAndRefresh(false, true, true, root)
            }
            PlatformLibraryIndex.reload()
        }
    }

    /** The names reported as `Deprecated API` in [SOURCE], in document order. */
    private fun deprecatedNames(): List<String> {
        myFixture.configureByText("bug551.lua", SOURCE)
        return myFixture
            .doHighlighting()
            .filter { it.description?.startsWith("Deprecated API") == true }
            .sortedBy { it.startOffset }
            .map { myFixture.file.text.substring(it.startOffset, it.endOffset) }
    }

    private companion object {
        const val SOURCE = "local f = loadstring(\"return 1\")\nprint(f, gcinfo())\n"
    }
}
