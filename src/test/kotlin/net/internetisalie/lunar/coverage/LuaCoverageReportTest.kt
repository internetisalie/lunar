package net.internetisalie.lunar.coverage

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.EditorNotificationPanel
import net.internetisalie.lunar.coverage.report.LuaCovReportFileType
import net.internetisalie.lunar.coverage.report.LuaCovReportLexer
import net.internetisalie.lunar.coverage.report.LuaCovReportNotificationProvider
import org.junit.Test
import java.io.File

class LuaCoverageReportTest : BasePlatformTestCase() {

    @Test
    fun testLexer() {
        val reportText = """
            ==============================================================================
            initrd/usr/bin/tests.lua
            ==============================================================================
                 -- comment line
               1 local test_modules = {}
            ***0 local fs = require("runtime.fs")
        """.trimIndent()

        val lexer = LuaCovReportLexer()
        lexer.start(reportText, 0, reportText.length)

        val tokens = mutableListOf<String>()
        while (lexer.tokenType != null) {
            tokens.add("${lexer.tokenType}: '${reportText.substring(lexer.tokenStart, lexer.tokenEnd)}'")
            lexer.advance()
        }

        assertTrue(tokens.isNotEmpty())
        
        // 1st token: HEADER_BOUNDARY
        assertTrue(tokens[0].startsWith("HEADER_BOUNDARY"))
        
        // 2nd token: NEWLINE
        assertTrue(tokens[1].startsWith("NEWLINE"))
        
        // 3rd token: FILE_PATH
        assertTrue(tokens[2].startsWith("FILE_PATH: 'initrd/usr/bin/tests.lua'"))
        
        // 4th token: NEWLINE
        assertTrue(tokens[3].startsWith("NEWLINE"))
        
        // 5th token: HEADER_BOUNDARY
        assertTrue(tokens[4].startsWith("HEADER_BOUNDARY"))
        
        // 6th token: NEWLINE
        assertTrue(tokens[5].startsWith("NEWLINE"))
        
        // 7th token: HIT_NONE: '     '
        assertTrue(tokens[6].startsWith("HIT_NONE: '     '"))
        
        // 8th token: LUA_CODE: '-- comment line'
        assertTrue(tokens[7].startsWith("LUA_CODE: '-- comment line'"))
    }

    @Test
    fun testParserIntegration() {
        val reportContent = """
            ==============================================================================
            initrd/usr/bin/tests.lua
            ==============================================================================
                 -- comment line
               2 local test_modules = {}
            ***0 local fs = require("runtime.fs")
              12 for line in p:lines() do
        """.trimIndent()

        val tempFile = File.createTempFile("luacov.report", "out")
        try {
            tempFile.writeText(reportContent)
            val results = LuaCovReportParser.parse(tempFile)
            assertEquals(1, results.size)
            val fileCoverage = results[0]
            assertEquals("initrd/usr/bin/tests.lua", fileCoverage.filePath)
            assertEquals(2, fileCoverage.lineHits[2])
            assertEquals(0, fileCoverage.lineHits[3])
            assertEquals(12, fileCoverage.lineHits[4])

            // Test toProjectData with path resolution
            val projectData = LuaCovReportParser.toProjectData(results, project)
            val expectedPath = File(project.basePath ?: "", "initrd/usr/bin/tests.lua").absolutePath
            val classData = projectData.getClassData(expectedPath)
            assertNotNull(classData)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testNotificationProvider() {
        val provider = LuaCovReportNotificationProvider()
        
        // Test non-report file
        val txtFile = myFixture.configureByText("dummy.txt", "hello")
        val txtPanelFun = provider.collectNotificationData(project, txtFile.virtualFile)
        assertNull(txtPanelFun)

        // Test report file
        val reportFile = myFixture.configureByText("luacov.report.out", "=======\ntests.lua\n=======\n")
        
        // Check file type is registered correctly
        assertEquals(LuaCovReportFileType, reportFile.virtualFile.fileType)

        val panelFun = provider.collectNotificationData(project, reportFile.virtualFile)
        assertNotNull(panelFun)

        val editors = FileEditorManager.getInstance(project).openFile(reportFile.virtualFile, false)
        val editor = editors.firstOrNull()
        assertNotNull(editor)
        val component = editor?.let { panelFun?.apply(it) } as? EditorNotificationPanel
        assertNotNull(component)
        assertEquals("This is a LuaCov coverage report.", component?.text)
    }

    @Test
    fun testActionRegistration() {
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("Lunar.ImportLuaCovReport")
        assertNotNull(action)

        val analyzeGroup = actionManager.getAction("AnalyzePlatformMenu") as? DefaultActionGroup
        assertNotNull(analyzeGroup)

        val children = analyzeGroup?.getChildren(null) ?: emptyArray()
        assertTrue(children.contains(action))
    }
}
