package net.internetisalie.lunar.settings

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.platform.target.VersionEntry
import net.internetisalie.lunar.project.PlatformLibraryProvider

/**
 * MAINT-12 Phase 2: change-notification publication (MAINT-12-04) and
 * [PlatformLibraryProvider] output (MAINT-12-05).
 *
 * Light project fixture is required because the notify-methods publish on the project
 * `messageBus` and the provider reads project settings + VFS. Coverage-only, no production
 * change.
 */
class LuaSettingsNotificationTest : BasePlatformTestCase() {
    fun testSetTargetAndNotifyFiresTopic() {
        var fired = false
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    fired = true
                }
            },
        )

        val target = Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4"))
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project).setTargetAndNotify(target)
        }

        assertTrue(fired)
        val stored = LuaProjectSettings.getInstance(project).state.getTarget()
        assertEquals(LuaPlatform.STANDARD, stored.platform)
        assertEquals("5.4", stored.version.label)
    }

    fun testSetProjectToolBindingAndNotifyFiresTopicAndMutatesState() {
        var invocations = 0
        project.messageBus.connect(testRootDisposable).subscribe(
            LuaSettingsChangedListener.TOPIC,
            object : LuaSettingsChangedListener {
                override fun onSettingsChanged() {
                    invocations++
                }
            },
        )

        val settings = LuaProjectSettings.getInstance(project)
        settings.setProjectToolBindingAndNotify("LUACHECK", "uuid-3")
        assertEquals("uuid-3", settings.state.projectToolBindings["LUACHECK"])

        settings.setProjectToolBindingAndNotify("LUACHECK", null)
        assertFalse(settings.state.projectToolBindings.containsKey("LUACHECK"))

        assertEquals(2, invocations)
    }

    fun testGetSupportLibrariesReturnsPlatformLibraryForValidTarget() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project)
                .setTargetAndNotify(Target(LuaPlatform.STANDARD, VersionEntry("5.4", "lua-5.4")))
        }

        val libraries = runReadAction { PlatformLibraryProvider().getSupportLibraries(project) }

        assertEquals(1, libraries.size)
        val library = libraries.single() as PlatformLibraryProvider.PlatformLibrary
        assertTrue(library.sourceRoots.isNotEmpty())
    }

    fun testGetSupportLibrariesEmptyContractHolds() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            LuaProjectSettings.getInstance(project)
                .setTargetAndNotify(Target(LuaPlatform.LUAJIT, VersionEntry("2.0", "luajit-2.0")))
        }

        val libraries = runReadAction { PlatformLibraryProvider().getSupportLibraries(project) }

        assertTrue(libraries.isEmpty())
    }
}
