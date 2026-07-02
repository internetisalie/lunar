package net.internetisalie.lunar.util

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class LuaFileUtilTest : BasePlatformTestCase() {

    private lateinit var base: Path

    override fun setUp() {
        super.setUp()
        base = Files.createTempDirectory("lunar-fileutil-test")
    }

    override fun tearDown() {
        try {
            base.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testFindLuaFilesInDirReturnsOnlyLuaRecursively() {
        Files.writeString(base.resolve("a.lua"), "local a = 1")
        Files.createDirectories(base.resolve("sub"))
        Files.writeString(base.resolve("sub/b.lua"), "local b = 2")
        Files.writeString(base.resolve("c.txt"), "not lua")

        val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base)
            ?: error("temp dir not found in VFS")
        root.refresh(false, true)

        val found = LuaFileUtil.findLuaFilesInDir(root).map { it.name }.toSet()

        assertEquals(setOf("a.lua", "b.lua"), found)
    }

    fun testGetPluginVirtualDirectoryChildMissingReturnsNull() {
        assertNull(LuaFileUtil.getPluginVirtualDirectoryChild("no-such-child-xyz"))
    }

    fun testFindPsiFilesMapsAndSkipsUnmappable() {
        myFixture.configureByText("m.lua", "local x = 1")
        val luaVirtualFile = myFixture.file.virtualFile
        val unmappableDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(base)
            ?: error("temp dir not found in VFS")

        val psiFiles = LuaFileUtil.findPsiFiles(project, listOf(luaVirtualFile, unmappableDir))

        assertEquals(1, psiFiles.size)
        assertEquals(luaVirtualFile, psiFiles.single().virtualFile)
    }
}
