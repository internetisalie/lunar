package net.internetisalie.lunar

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LuaPluginDisposableTest : BasePlatformTestCase() {

    /** TC6: both getInstance() overloads resolve unambiguously and return a non-null Disposable. */
    fun testBothGetInstanceOverloadsResolve() {
        assertNotNull(LuaPluginDisposable.getInstance())
        assertNotNull(LuaPluginDisposable.getInstance(project))
    }
}
