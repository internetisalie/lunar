package net.internetisalie.lunar.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.isActive

class LunarCoroutineScopeServiceTest : BasePlatformTestCase() {

    /** MAINT-22-02: the project-level scope service resolves and hands out an active scope. */
    fun testServiceResolvesWithActiveScope() {
        val service = LunarCoroutineScopeService.getInstance(project)
        assertNotNull(service)
        assertTrue("injected scope should be active", service.scope.isActive)
    }

    /** getInstance is a stable singleton per project. */
    fun testGetInstanceIsSingleton() {
        assertSame(
            LunarCoroutineScopeService.getInstance(project),
            LunarCoroutineScopeService.getInstance(project),
        )
    }
}
