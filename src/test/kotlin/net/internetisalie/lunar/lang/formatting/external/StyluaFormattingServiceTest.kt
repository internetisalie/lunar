package net.internetisalie.lunar.lang.formatting.external

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.FormattingNotificationService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.util.containers.ContainerUtil
import net.internetisalie.lunar.settings.LuaApplicationSettings
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.tool.LuaTool
import net.internetisalie.lunar.tool.LuaToolManager
import net.internetisalie.lunar.tool.LuaToolType
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class StyluaFormattingServiceTest : BasePlatformTestCase() {

    private lateinit var mockStyluaBinary: File
    private val recordingNotificationService = RecordingFormattingNotificationService()

    override fun setUp() {
        super.setUp()
        project.replaceService(FormattingNotificationService::class.java, recordingNotificationService, testRootDisposable)

        LuaApplicationSettings.instance.state.toolInventory.clear()
        LuaApplicationSettings.instance.state.globalToolBindings.clear()
        LuaProjectSettings.getInstance(project).state.projectToolBindings.clear()

        mockStyluaBinary = createMockStyluaBinary()
        StyluaFormattingTask.timeoutMs = 30_000
    }

    override fun tearDown() {
        try {
            LuaApplicationSettings.instance.state.toolInventory.clear()
            LuaApplicationSettings.instance.state.globalToolBindings.clear()
            LuaProjectSettings.getInstance(project).state.projectToolBindings.clear()
        } finally {
            super.tearDown()
        }
    }

    private fun createMockStyluaBinary(): File {
        val f = File.createTempFile("stylua", "")
        f.writeText(
            """
            #!/bin/sh
            if [ "$1" = "--version" ]; then
                echo "stylua 0.20.0"
                exit 0
            fi

            stdin_content=$(cat)

            if [ "${'$'}stdin_content" = "syntax_error" ]; then
                echo "syntax error near local" >&2
                exit 1
            fi

            if [ "${'$'}stdin_content" = "timeout" ]; then
                sleep 5
                exit 0
            fi

            if [ "${'$'}stdin_content" = "local x =   1" ] || [ "${'$'}stdin_content" = "local x =   1${'\n'}" ]; then
                echo "local x = 1"
                exit 0
            fi

            if [ "${'$'}stdin_content" = "return  {  a  =  1 ,  b  =  2  }" ] || [ "${'$'}stdin_content" = "return  {  a  =  1 ,  b  =  2  }${'\n'}" ]; then
                echo "return { a = 1, b = 2 }"
                exit 0
            fi

            echo "${'$'}stdin_content"
            exit 0
            """.trimIndent()
        )
        f.setExecutable(true)
        f.deleteOnExit()
        return f
    }

    private fun registerAndBindStylua(path: String = mockStyluaBinary.absolutePath) {
        val tool = LuaTool(
            id = "stylua-test-id",
            type = LuaToolType.STYLUA,
            name = "stylua",
            path = path,
            version = "0.20.0",
            isValid = true
        )
        LuaApplicationSettings.instance.state.toolInventory.add(tool)
        LuaApplicationSettings.instance.state.globalToolBindings[LuaToolType.STYLUA.name] = "stylua-test-id"
    }

    private fun reformat() {
        WriteCommandAction.writeCommandAction(project).run<RuntimeException?> {
            CodeStyleManager.getInstance(project).reformatText(
                myFixture.file, listOf(myFixture.file.textRange)
            )
        }
    }

    @Test
    fun testReformatWithStyluaBound() {
        registerAndBindStylua()
        myFixture.configureByText("test.lua", "local x =   1")
        reformat()
        assertEquals("local x = 1\n", myFixture.file.text)
    }

    @Test
    fun testFallbackToBuiltInWhenNoStyluaBound() {
        myFixture.configureByText("test.lua", "local x = 1")
        reformat()
        assertEquals("local x = 1\n", myFixture.file.text)
    }

    @Test
    fun testSyntaxErrorLeavesDocumentUnchangedAndNotifies() {
        registerAndBindStylua()
        val originalText = "syntax_error"
        myFixture.configureByText("test.lua", originalText)
        reformat()
        assertEquals(originalText, myFixture.file.text)
        assertTrue(recordingNotificationService.errorsReported.any { it.contains("syntax error near local") })
    }

    @Test
    fun testNonExistentBinaryLeavesDocumentUnchangedAndNotifies() {
        registerAndBindStylua(System.getProperty("java.io.tmpdir"))
        val originalText = "local x = 1"
        myFixture.configureByText("test.lua", originalText)
        reformat()
        assertEquals(originalText, myFixture.file.text)
        assertTrue(recordingNotificationService.errorsReported.any { it.contains("Could not execute stylua") })
    }

    @Test
    fun testNonLuaFileDoesNotUseStylua() {
        registerAndBindStylua()
        val originalText = "local x =   1"
        myFixture.configureByText("test.txt", originalText)
        reformat()
        assertEquals(originalText, myFixture.file.text)
    }

    @Test
    fun testReformatComplexStructureWithStylua() {
        registerAndBindStylua()
        myFixture.configureByText("test.lua", "return  {  a  =  1 ,  b  =  2  }")
        reformat()
        assertEquals("return { a = 1, b = 2 }\n", myFixture.file.text)
    }

    @Test
    fun testTimeoutLeavesDocumentUnchangedAndNotifies() {
        registerAndBindStylua()
        StyluaFormattingTask.timeoutMs = 200
        val originalText = "timeout"
        myFixture.configureByText("test.lua", originalText)
        reformat()
        assertEquals(originalText, myFixture.file.text)
        assertTrue(recordingNotificationService.errorsReported.any { it.contains("Stylua did not respond within 30 seconds") })
    }

    private class RecordingFormattingNotificationService : FormattingNotificationService {
        val errorsReported = ContainerUtil.createConcurrentList<String>()

        override fun reportError(
            groupId: String,
            displayId: String?,
            title: @NlsContexts.NotificationTitle String,
            message: @NlsContexts.NotificationContent String,
            vararg actions: AnAction?
        ) {
            errorsReported.add(message)
        }

        override fun reportErrorAndNavigate(
            groupId: String,
            displayId: String?,
            title: @NlsContexts.NotificationTitle String,
            message: @NlsContexts.NotificationContent String,
            context: FormattingContext,
            offset: Int
        ) {
            errorsReported.add(message)
        }
    }
}
