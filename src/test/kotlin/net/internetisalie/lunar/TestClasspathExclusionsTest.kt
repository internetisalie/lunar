package net.internetisalie.lunar

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Guards `testIdeBundledPluginsClasspathExcludes` in gradle.properties: the bundled plugins it
 * removes from the unit-test classpath must stay absent from the test application (MAINT-21).
 */
class TestClasspathExclusionsTest : BasePlatformTestCase() {
    fun testExcludedBundledPluginsAreNotLoaded() {
        val loadedExclusions =
            EXCLUDED_PLUGIN_IDS.filter { PluginManagerCore.getPlugin(PluginId.getId(it)) != null }
        assertEmpty("bundled plugins excluded from the test classpath were loaded anyway", loadedExclusions)
    }

    private companion object {
        val EXCLUDED_PLUGIN_IDS = listOf("com.ypwang.plugin.go-linter", "JavaScript")
    }
}
