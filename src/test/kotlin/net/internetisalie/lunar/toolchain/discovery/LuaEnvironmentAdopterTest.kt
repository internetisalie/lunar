package net.internetisalie.lunar.toolchain.discovery

import com.intellij.openapi.application.ApplicationManager
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.io.File
import java.nio.file.Files

/** TOOLING-02-14 / TC 17: adoption registers every kind binary (with environmentId) + activates. */
class LuaEnvironmentAdopterTest : ToolchainSettingsTestCase() {

    private lateinit var envDir: File

    override fun setUp() {
        super.setUp()
        envDir = Files.createTempDirectory("lunar-adopt").toFile()
        writeExecutable("bin/lua", "#!/bin/sh\necho 'Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio'\n")
        writeExecutable("bin/luarocks", "#!/bin/sh\necho 'luarocks 3.11.0'\n")
    }

    override fun tearDown() {
        try {
            envDir.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testAdoptRegistersBothBinariesAndActivates() {
        val adopted = adoptOffEdt() ?: error("adopt should register both binaries")

        assertEquals(2, adopted.toolIds.size)
        adopted.toolIds.forEach { toolId ->
            val tool = registry.tool(toolId) ?: error("registered tool must be in inventory")
            assertEquals(adopted.id, tool.environmentId)
            assertEquals(Origin.DISCOVERED, tool.origin)
        }
        val kindIds = adopted.toolIds.mapNotNull { registry.tool(it)?.kindId }.toSet()
        assertTrue("luarocks adopted", "luarocks" in kindIds)
        assertTrue("lua adopted", "lua" in kindIds)

        assertEquals(adopted.id, settings.activeEnvironment()?.id)
        assertEquals(envDir.name, adopted.name)
    }

    fun testAdoptReturnsNullWhenNothingRegistered() {
        val emptyDir = Files.createTempDirectory("lunar-empty").toFile()
        try {
            assertNull(adoptOffEdt(emptyDir.absolutePath))
            assertNull(settings.activeEnvironment())
        } finally {
            emptyDir.deleteRecursively()
        }
    }

    private fun adoptOffEdt(directory: String = envDir.absolutePath) =
        ApplicationManager.getApplication()
            .executeOnPooledThread<net.internetisalie.lunar.toolchain.model.LuaEnvironmentState?> {
                LuaEnvironmentAdopter.adopt(project, directory)
            }.get()

    private fun writeExecutable(relative: String, content: String) {
        val target = File(envDir, relative)
        target.parentFile.mkdirs()
        target.writeText(content)
        target.setExecutable(true)
    }
}
