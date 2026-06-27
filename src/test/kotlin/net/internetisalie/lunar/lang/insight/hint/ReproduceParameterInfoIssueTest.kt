package net.internetisalie.lunar.lang.insight.hint

import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import net.internetisalie.lunar.IndexedDocumentTest
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReproduceParameterInfoIssueTest : IndexedDocumentTest() {

    @Test
    fun testDottedPlatformGlobal() {
        EdtTestUtil.runInEdtAndWait<RuntimeException> {
            // 1. Setup target platform and reload platform library index on EDT.
            // reload() runs WriteAction internally, which requires EDT and no active read lock.
            val settings = LuaProjectSettings.getInstance(myFixture.project)
            val redisVersion = PlatformVersionRegistry.findVersion(LuaPlatform.REDIS, "7+")!!
            settings.state.setTarget(Target(LuaPlatform.REDIS, redisVersion))
            net.internetisalie.lunar.project.PlatformLibraryIndex.reload()

            // 2. Perform file configuration and parameter info lookup under a read action.
            runReadAction {
                val file = configureByText("""
                    cjson.decode(<caret>)
                """.trimIndent())

                val handler = LuaParameterInfoHandler()
                val createCtx = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
                val element = handler.findElementForParameterInfo(createCtx)

                assertNotNull(element, "Should find element for parameter info")
                assertNotNull(createCtx.itemsToShow, "Should have candidates to show")
                assertEquals(1, createCtx.itemsToShow!!.size)

                val candidate = createCtx.itemsToShow!![0] as LuaParameterInfoHandler.LuaParameterInfoCandidate
                assertEquals("cjson.decode", candidate.name)
                assertEquals(listOf("string"), candidate.params)
            }
        }
    }
}
