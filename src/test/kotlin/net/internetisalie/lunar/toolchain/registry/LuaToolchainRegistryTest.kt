package net.internetisalie.lunar.toolchain.registry

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaRuntimeInfo
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.model.isUsable
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.probe.LuaToolProbeResult
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

class FakeLuaToolProbe : LuaToolProbe {
    val results = mutableMapOf<String, LuaToolProbeResult>()
    override fun probe(kind: LuaToolKind, binaryPath: java.nio.file.Path): LuaToolProbeResult {
        return results[binaryPath.toAbsolutePath().toString()] ?: LuaToolProbeResult(
            ok = true,
            version = "1.0.0",
            luaVersion = null,
            runtime = null,
            failure = null
        )
    }
}

@RunWith(JUnit4::class)
class LuaToolchainRegistryTest : BasePlatformTestCase() {

    private lateinit var fakeProbe: FakeLuaToolProbe

    override fun setUp() {
        super.setUp()
        fakeProbe = FakeLuaToolProbe()
        ApplicationManager.getApplication().replaceService(
            LuaToolProbe::class.java,
            fakeProbe,
            testRootDisposable
        )
        // Reset registry state
        LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
    }

    override fun tearDown() {
        try {
            // Restore empty state
            LuaToolchainRegistry.getInstance().loadState(LuaToolchainAppState())
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun testRegisterAndRefreshTool_TC15_16() {
        val file = java.nio.file.Files.createTempFile("stylua", "").toFile()
        file.writeText("#!/bin/sh\n")
        file.setExecutable(true)
        file.deleteOnExit()
        val path = file.absolutePath

        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        // 1. Register tool (TC 15)
        val tool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path, "stylua")
        }.get()

        assertNotNull(tool)
        assertEquals(1, registry.tools().size)
        assertEquals(tool!!.id, registry.tools().first().id)
        assertEquals(Origin.MANUAL, tool.origin)
        assertEquals("stylua", tool.kindId)

        // Verify event
        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.TOOL_REGISTERED, event.change)
            assertEquals(tool.id, event.toolId)
            assertEquals("stylua", event.kindId)
            events.clear()
        }

        // 2. Register again (TC 16)
        fakeProbe.results[file.absolutePath] = LuaToolProbeResult(
            ok = true,
            version = "2.0.0",
            luaVersion = null,
            runtime = null,
            failure = null
        )

        val updatedTool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path, "stylua")
        }.get()

        assertNotNull(updatedTool)
        assertEquals(1, registry.tools().size)
        assertEquals(tool.id, updatedTool!!.id)
        assertEquals("2.0.0", updatedTool.version)

        // Verify event
        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.TOOL_UPDATED, event.change)
            assertEquals(tool.id, event.toolId)
            assertEquals("stylua", event.kindId)
        }
    }

    @Test
    fun testGlobalBindings_TC17() {
        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        val toolId = "test-tool-id-123"

        // 1. setGlobalBinding
        registry.setGlobalBinding("stylua", toolId)
        assertEquals(toolId, registry.globalBindings()["stylua"])

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.GLOBAL_BINDING_CHANGED, event.change)
            assertEquals("stylua", event.kindId)
            assertEquals(toolId, event.toolId)
            events.clear()
        }

        // 2. setGlobalBinding with same value should be no-op (no event)
        registry.setGlobalBinding("stylua", toolId)
        synchronized(events) {
            assertEquals(0, events.size)
        }

        // 3. setGlobalBinding(null) clears it
        registry.setGlobalBinding("stylua", null)
        assertFalse(registry.globalBindings().containsKey("stylua"))

        synchronized(events) {
            assertEquals(1, events.size)
            val event = events.first()
            assertEquals(LuaToolchainChange.GLOBAL_BINDING_CHANGED, event.change)
            assertEquals("stylua", event.kindId)
            assertNull(event.toolId)
        }
    }

    @Test
    fun testStateSerializationRoundTrip_TC18() {
        val originalState = LuaToolchainAppState()

        // 1. RUNTIME tool
        val tool1 = RegisteredToolState().apply {
            id = "id-1"
            kindId = "lua"
            path = "/usr/bin/lua"
            version = "5.4.6"
            luaVersion = ""
            product = "Lua"
            runtimeVersion = "5.4.6"
            languageLevel = "LUA54"
            platform = "STANDARD"
            banner = "Lua 5.4.6  Copyright (C) 199 PUC-Rio"
            origin = Origin.DISCOVERED
            environmentId = "env-123"
            fileExists = true
            executable = true
            probeStatus = ProbeStatus.OK
            probedAtMtime = 123456789L
            reason = ""
        }

        // 2. Probe-failed tool
        val tool2 = RegisteredToolState().apply {
            id = "id-2"
            kindId = "luarocks"
            path = "/usr/bin/luarocks"
            version = ""
            luaVersion = ""
            product = ""
            runtimeVersion = ""
            languageLevel = ""
            platform = ""
            banner = ""
            origin = Origin.MANUAL
            environmentId = ""
            fileExists = true
            executable = true
            probeStatus = ProbeStatus.FAILED
            probedAtMtime = 987654321L
            reason = "Timeout"
        }

        originalState.tools.add(tool1)
        originalState.tools.add(tool2)
        originalState.globalBindings["lua"] = "id-1"
        originalState.kindOptions["lua.arguments"] = "--some-flag"

        // Serialize
        val element = com.intellij.util.xmlb.XmlSerializer.serialize(originalState)
        // Deserialize
        val restoredState = com.intellij.util.xmlb.XmlSerializer.deserialize(element, LuaToolchainAppState::class.java)

        // Verify tools mapping equality
        val originalModels = originalState.tools.map { it.toModel() }
        val restoredModels = restoredState.tools.map { it.toModel() }

        assertEquals(originalModels.size, restoredModels.size)
        for (i in originalModels.indices) {
            val orig = originalModels[i]
            val rest = restoredModels[i]
            assertEquals(orig.id, rest.id)
            assertEquals(orig.kindId, rest.kindId)
            assertEquals(orig.path, rest.path)
            assertEquals(orig.version, rest.version)
            assertEquals(orig.luaVersion, rest.luaVersion)
            assertEquals(orig.origin, rest.origin)
            assertEquals(orig.environmentId, rest.environmentId)
            assertEquals(orig.health, rest.health)
            assertEquals(orig.runtime, rest.runtime)
            assertEquals(orig.isUsable, rest.isUsable)
        }

        assertEquals(originalState.globalBindings, restoredState.globalBindings)
        assertEquals(originalState.kindOptions, restoredState.kindOptions)
    }

    @Test
    fun testDeletedBinaryHealthAndRefresh_TC19() {
        val file = java.nio.file.Files.createTempFile("luacheck", "").toFile()
        file.writeText("#!/bin/sh\n")
        file.setExecutable(true)
        val path = file.absolutePath

        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        // 1. Register tool
        val tool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path, "luacheck")
        }.get()

        assertNotNull(tool)
        assertTrue(tool!!.health.fileExists)
        assertTrue(tool.health.executable)
        synchronized(events) {
            assertEquals(1, events.size)
            events.clear()
        }

        // Delete file from disk
        file.delete()

        // 2. tools() twice: health should remain unchanged
        val t1 = registry.tools().first()
        val t2 = registry.tools().first()
        assertTrue(t1.health.fileExists)
        assertTrue(t2.health.fileExists)
        synchronized(events) {
            assertEquals(0, events.size)
        }

        // Setup fake probe for deleted file
        fakeProbe.results[path] = LuaToolProbeResult(
            ok = false,
            version = null,
            luaVersion = null,
            runtime = null,
            failure = "Not executable"
        )

        // 3. refreshTool(id) -> fileExists=false, TOOL_UPDATED fired
        ApplicationManager.getApplication().executeOnPooledThread {
            registry.refreshTool(tool.id)
        }.get()

        val refreshed = registry.tools().first()
        assertFalse(refreshed.health.fileExists)
        assertFalse(refreshed.health.executable)

        synchronized(events) {
            assertEquals(1, events.size)
            assertEquals(LuaToolchainChange.TOOL_UPDATED, events.first().change)
            assertEquals(tool.id, events.first().toolId)
        }
    }

    @Test
    fun testRegisterUnknownKind_TC20() {
        val file = java.nio.file.Files.createTempFile("unknown_binary", "").toFile()
        file.writeText("#!/bin/sh\n")
        file.setExecutable(true)
        file.deleteOnExit()
        val path = file.absolutePath

        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        val tool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path)
        }.get()

        assertNull(tool)
        assertEquals(0, registry.tools().size)
        synchronized(events) {
            assertEquals(0, events.size)
        }
    }

    @Test
    fun testFindByPath() {
        val file = java.nio.file.Files.createTempFile("stylua", "").toFile()
        file.writeText("#!/bin/sh\n")
        file.setExecutable(true)
        file.deleteOnExit()
        val path = file.absolutePath

        val registry = LuaToolchainRegistry.getInstance()
        val tool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path, "stylua")
        }.get()

        assertNotNull(tool)
        assertEquals(tool, registry.findByPath(path))

        val slashPath = path.replace('/', '\\')
        assertEquals(tool, registry.findByPath(slashPath))

        assertNull(registry.findByPath("/path/does/not/exist/stylua"))
    }

    @Test
    fun testRuntimeToolModelStateRoundTrip_TC24() {
        val runtimeInfo = LuaRuntimeInfo(
            product = "LuaJIT",
            version = "2.1.0",
            languageLevel = LuaLanguageLevel.LUA51,
            platform = LuaPlatform.LUAJIT,
            banner = "LuaJIT 2.1.0"
        )
        val health = LuaToolHealth(
            fileExists = true,
            executable = true,
            probeOk = true,
            probedAtMtime = 123456L,
            reason = null
        )
        val originalModel = LuaRegisteredTool(
            id = "uuid-1234",
            kindId = "luajit",
            path = "/usr/bin/luajit",
            version = "2.1.0",
            luaVersion = null,
            runtime = runtimeInfo,
            origin = Origin.DISCOVERED,
            environmentId = null,
            health = health
        )

        val state = originalModel.toState()
        val restoredModel = state.toModel()

        assertEquals(originalModel.id, restoredModel.id)
        assertEquals(originalModel.kindId, restoredModel.kindId)
        assertEquals(originalModel.path, restoredModel.path)
        assertEquals(originalModel.version, restoredModel.version)
        assertEquals(originalModel.luaVersion, restoredModel.luaVersion)
        assertEquals(originalModel.origin, restoredModel.origin)
        assertEquals(originalModel.environmentId, restoredModel.environmentId)
        assertEquals(originalModel.health, restoredModel.health)
        assertEquals(originalModel.runtime, restoredModel.runtime)
        assertEquals(originalModel.isUsable, restoredModel.isUsable)
    }

    @Test
    fun testUpdateToolCheckOptimized_TC25() {
        val file = java.nio.file.Files.createTempFile("luacov", "").toFile()
        file.writeText("#!/bin/sh\n")
        file.setExecutable(true)
        file.deleteOnExit()
        val path = file.absolutePath

        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        // Register
        val tool = ApplicationManager.getApplication().executeOnPooledThread<LuaRegisteredTool?> {
            registry.registerTool(path, "luacov")
        }.get()

        assertNotNull(tool)
        synchronized(events) {
            assertEquals(1, events.size)
            events.clear()
        }

        // 1. updateToolCheck with identical values
        registry.updateToolCheck(
            toolId = tool!!.id,
            health = tool.health,
            version = tool.version,
            luaVersion = tool.luaVersion,
            runtime = tool.runtime
        )

        synchronized(events) {
            assertEquals(0, events.size)
        }

        // 2. updateToolCheck with changed health
        val newHealth = tool.health.copy(reason = "Some new warning")
        registry.updateToolCheck(
            toolId = tool.id,
            health = newHealth,
            version = tool.version,
            luaVersion = tool.luaVersion,
            runtime = tool.runtime
        )

        synchronized(events) {
            assertEquals(1, events.size)
            assertEquals(LuaToolchainChange.TOOL_UPDATED, events.first().change)
            assertEquals(tool.id, events.first().toolId)
        }
    }

    @Test
    fun testAutoDiscoverE2E() {
        val rootDir = java.nio.file.Files.createTempDirectory("lunar_autodiscover_test")

        val styluaFile = rootDir.resolve("stylua")
        java.nio.file.Files.writeString(styluaFile, "#!/bin/sh\n")
        styluaFile.toFile().setExecutable(true)

        val luarocksFile = rootDir.resolve("luarocks")
        java.nio.file.Files.writeString(luarocksFile, "#!/bin/sh\n")
        luarocksFile.toFile().setExecutable(true)

        val luaFile = rootDir.resolve("lua5.4")
        java.nio.file.Files.writeString(luaFile, "#!/bin/sh\n")
        luaFile.toFile().setExecutable(true)

        fakeProbe.results[styluaFile.toAbsolutePath().toString()] = LuaToolProbeResult(
            ok = true,
            version = "0.20.0",
            luaVersion = null,
            runtime = null,
            failure = null
        )
        fakeProbe.results[luarocksFile.toAbsolutePath().toString()] = LuaToolProbeResult(
            ok = true,
            version = "3.11.0",
            luaVersion = "5.4",
            runtime = null,
            failure = null
        )
        fakeProbe.results[luaFile.toAbsolutePath().toString()] = LuaToolProbeResult(
            ok = true,
            version = "5.4.6",
            luaVersion = null,
            runtime = LuaRuntimeInfo(
                product = "Lua",
                version = "5.4.6",
                languageLevel = LuaLanguageLevel.LUA54,
                platform = LuaPlatform.STANDARD,
                banner = "Lua 5.4.6  Copyright (C) 1994-2023 Lua.org, PUC-Rio"
            ),
            failure = null
        )

        val registry = LuaToolchainRegistry.getInstance()

        val events = mutableListOf<LuaToolchainEvent>()
        val connection = ApplicationManager.getApplication().messageBus.connect(testRootDisposable)
        connection.subscribe(LuaToolchainListener.TOPIC, object : LuaToolchainListener {
            override fun toolchainChanged(event: LuaToolchainEvent) {
                synchronized(events) {
                    events.add(event)
                }
            }
        })

        ApplicationManager.getApplication().executeOnPooledThread {
            registry.autoDiscover(listOf(rootDir))
        }.get()

        val tools = registry.tools()
        val tempTools = tools.filter { it.path.startsWith(rootDir.toAbsolutePath().toString()) }
        assertEquals(3, tempTools.size)

        val styluaTool = tempTools.firstOrNull { it.kindId == "stylua" }
        assertNotNull(styluaTool)
        assertEquals("0.20.0", styluaTool!!.version)
        assertEquals(Origin.DISCOVERED, styluaTool.origin)

        val luarocksTool = tempTools.firstOrNull { it.kindId == "luarocks" }
        assertNotNull(luarocksTool)
        assertEquals("3.11.0", luarocksTool!!.version)
        assertEquals("5.4", luarocksTool.luaVersion)
        assertEquals(Origin.DISCOVERED, luarocksTool.origin)

        val luaTool = tempTools.firstOrNull { it.kindId == "lua" }
        assertNotNull(luaTool)
        assertEquals("5.4.6", luaTool!!.version)
        assertEquals(Origin.DISCOVERED, luaTool.origin)
        val runtime = luaTool.runtime
        assertNotNull(runtime)
        assertEquals("Lua", runtime!!.product)
        assertEquals(LuaLanguageLevel.LUA54, runtime.languageLevel)

        val tempToolIds = tempTools.map { it.id }.toSet()
        synchronized(events) {
            val tempEvents = events.filter { it.toolId in tempToolIds }
            assertEquals(3, tempEvents.size)
            assertTrue(tempEvents.all { it.change == LuaToolchainChange.TOOL_REGISTERED })
        }

        // Clean up files/directory
        styluaFile.toFile().delete()
        luarocksFile.toFile().delete()
        luaFile.toFile().delete()
        rootDir.toFile().delete()
    }
}
