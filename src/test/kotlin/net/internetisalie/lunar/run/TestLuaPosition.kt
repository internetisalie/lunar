package net.internetisalie.lunar.run

import com.intellij.xdebugger.XDebuggerUtil
import net.internetisalie.lunar.BaseDocumentTest
import net.internetisalie.lunar.lang.LuaFileType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestLuaPosition : BaseDocumentTest() {

    @Test
    fun testCreateRemotePosition() {
        val psiFile = myFixture.configureByText(LuaFileType, "-- test file")
        val virtualFile = psiFile.virtualFile
        
        val xSourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, 10)
        assertNotNull(xSourcePosition)
        
        val workingDir = File(myFixture.project.basePath!!)
        
        val remotePos = LuaPosition.createRemotePosition(xSourcePosition, workingDir)
        
        println("virtualFile.name: ${virtualFile.name}")
        println("virtualFile.path: ${virtualFile.path}")
        println("workingDir: ${workingDir.path}")
        println("remotePos.path: ${remotePos.path}")
        
        // The path will be relative to the project base path in LightProjectDescriptor
        // In LightTempDirTestFixtureImpl, the file is usually at the root of the temp VFS
        assertTrue(remotePos.path.endsWith(virtualFile.name), "Expected remote path to end with ${virtualFile.name}, but was ${remotePos.path}")
        assertEquals(11, remotePos.line) // 1-indexed in Lua/Mobdebug
    }

    @Test
    fun testCreateLocalPosition() {
        val psiFile = myFixture.configureByText(LuaFileType, "-- test file")
        val virtualFile = psiFile.virtualFile
        
        val localPos = LuaPosition.createLocalPosition(virtualFile, 11) // 1-indexed in Lua
        assertNotNull(localPos)
        
        assertEquals(virtualFile, localPos.file)
        assertEquals(10, localPos.line) // 0-indexed in IDE
    }

    /**
     * TC-02b (#16): a null working directory makes `FileUtil.getRelativePath` return null; the
     * fallback must yield the absolute path (previously an NPE from the `!!`).
     */
    @Test
    fun testCreateRemotePositionFallsBackToAbsolutePath() {
        val psiFile = myFixture.configureByText(LuaFileType, "-- test file")
        val virtualFile = psiFile.virtualFile

        val xSourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, 0)
        assertNotNull(xSourcePosition)

        val remotePos = LuaPosition.createRemotePosition(xSourcePosition, null)

        assertEquals(virtualFile.path.replace('\\', '/'), remotePos.path)
        assertEquals(1, remotePos.line)
    }

    /** TC 12: args() emits [path, line-as-string]. */
    @Test
    fun testArgs() {
        assertEquals(listOf("main.lua", "5"), LuaPosition("main.lua", 5).args())
    }

    /** TC 11, 13: IDE (0-based) ↔ remote (1-based) line conversions round-trip. */
    @Test
    fun testRoundTripLineConversion() {
        val psiFile = myFixture.configureByText(LuaFileType, "-- test file")
        val virtualFile = psiFile.virtualFile

        val ideLine = 10
        val xSourcePosition = XDebuggerUtil.getInstance().createPosition(virtualFile, ideLine)
        assertNotNull(xSourcePosition)

        val workingDir = File(myFixture.project.basePath!!)
        val remotePos = LuaPosition.createRemotePosition(xSourcePosition, workingDir)
        assertEquals(ideLine + 1, remotePos.line) // 1-based on the wire

        val localPos = LuaPosition.createLocalPosition(virtualFile, remotePos.line)
        assertNotNull(localPos)
        assertEquals(ideLine, localPos.line) // back to the original 0-based IDE line
    }
}
