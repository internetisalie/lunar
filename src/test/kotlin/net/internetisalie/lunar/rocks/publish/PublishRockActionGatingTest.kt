package net.internetisalie.lunar.rocks.publish

import com.intellij.testFramework.LightVirtualFile
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TC-ROCKS-08-01: the publish action is gated to `.rockspec` files (design §3.1). */
class PublishRockActionGatingTest {
    private fun file(name: String) = LightVirtualFile(name, "")

    @Test
    fun acceptsRockspec() {
        assertTrue(PublishRockAction.isRockspec(file("app-scm-1.rockspec")))
    }

    @Test
    fun rejectsLuaFile() {
        assertFalse(PublishRockAction.isRockspec(file("init.lua")))
    }

    @Test
    fun rejectsExtensionlessFile() {
        assertFalse(PublishRockAction.isRockspec(file("README")))
    }
}
