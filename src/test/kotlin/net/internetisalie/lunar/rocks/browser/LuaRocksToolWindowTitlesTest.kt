package net.internetisalie.lunar.rocks.browser

import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import net.internetisalie.lunar.rocks.ui.LuaRocksToolWindowFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * TC-ROCKS-16-12: the two LuaRocks tool windows carry unambiguous roles (BUG-366, design §7).
 *
 * The headless test `ToolWindow` mock swallows `setStripeTitle`/`setTitle` (both no-ops returning
 * ""), so titles cannot be read back from the manager. Instead this test records the setter calls
 * through a recording proxy `ToolWindow` and asserts each factory applies the intended stripe/title.
 * (Live stripe rendering is the integration/VNC check; the titles themselves are asserted here.)
 */
class LuaRocksToolWindowTitlesTest : BasePlatformTestCase() {

    fun `test browser factory sets the LuaRocks Packages stripe and title`() {
        val recorder = TitleRecorder()
        LuaRocksBrowserToolWindowFactory().createToolWindowContent(project, recorder.toolWindow)

        assertEquals("LuaRocks Packages", recorder.stripeTitle)
        assertEquals("LuaRocks Packages", recorder.title)
    }

    fun `test dependency factory sets the LuaRocks Dependencies stripe and title`() {
        val recorder = TitleRecorder()
        LuaRocksToolWindowFactory().createToolWindowContent(project, recorder.toolWindow)

        assertEquals("LuaRocks Dependencies", recorder.stripeTitle)
        assertEquals("LuaRocks Dependencies", recorder.title)
    }

    /**
     * A recording [ToolWindow] proxy: captures `setStripeTitle`/`setTitle`, exposes a real
     * [ContentManager] via the platform's headless tool window so `addContent`/`setDisposer` work,
     * and returns benign defaults for everything else.
     */
    private inner class TitleRecorder {
        var stripeTitle: String? = null
        var title: String? = null

        private val delegate: ToolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)

        val toolWindow: ToolWindow = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ToolWindow::class.java),
            InvocationHandler { _, method, args -> dispatch(method, args) },
        ) as ToolWindow

        private fun dispatch(method: Method, args: Array<out Any?>?): Any? = when (method.name) {
            "setStripeTitle" -> { stripeTitle = args?.get(0) as? String; null }
            "setTitle" -> { title = args?.get(0) as? String; null }
            else -> method.invoke(delegate, *(args ?: emptyArray()))
        }
    }
}
