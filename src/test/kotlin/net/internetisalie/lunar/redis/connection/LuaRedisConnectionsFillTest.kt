package net.internetisalie.lunar.redis.connection

import com.intellij.openapi.ui.Splitter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil

/**
 * BUG-448 #4: the page put the connection list in `BorderLayout.WEST`, which hands a component its
 * *preferred* width and never stretches it — the page filled 35% of the content area against a
 * native comparator's 95%. The platform idiom for master/detail is a splitter.
 *
 * The assertions go through `parent`, not through `Splitter.secondComponent`: that getter is a
 * stored field, so a splitter that has *lost* its component to another container still returns it
 * (the BUG-449 defect a first draft of that test could not see).
 */
class LuaRedisConnectionsFillTest : BasePlatformTestCase() {
    fun `test connections page is a splitter that owns both halves (BUG-448 #4)`() {
        val configurable = LuaRedisConnectionsConfigurable(project)
        try {
            val page = configurable.createComponent()
            assertTrue("Master/detail must be a Splitter, not a BorderLayout.WEST panel", page is Splitter)
            val splitter = page as Splitter
            val master = splitter.firstComponent
            val detail = splitter.secondComponent
            assertNotNull("Splitter must have a master half", master)
            assertNotNull("Splitter must have a detail half", detail)
            assertSame("The master half must be parented to the splitter", splitter, master.parent)
            assertSame("The detail half must be parented to the splitter", splitter, detail.parent)
            val list = UIUtil.findComponentOfType(master, JBList::class.java)
            val formField = UIUtil.findComponentOfType(detail, JBTextField::class.java)
            assertNotNull("The master half must hold the connection list", list)
            assertNotNull("The detail half must hold the connection form", formField)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
