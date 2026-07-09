package net.internetisalie.lunar.toolchain.provision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.io.path.createTempDirectory

/**
 * Pure re-detection tests (TOOLING-04-16): an orphaned `.lunar-env.json` (no matching TOOLING-02
 * record) is detected and its registration payload rebuilt from the manifest; a tree whose
 * environment id is already registered is ignored. No project/notification/Swing involved.
 */
class LuaEnvRedetectionTest {
    private fun writeManifest(): Pair<java.nio.file.Path, LuaEnvManifest> {
        val rootDir = createTempDirectory("lunar-redetect")
        val manifest = LuaEnvManifest(
            manifestVersion = 1,
            environmentId = "env-orphan-1",
            environmentName = "env54",
            request = LuaProvisionRequest("env54", rootDir.toString(), listOf(LuaProvisionItem("lua", "5.4.8"))),
            components = mapOf(
                "lua" to LuaManifestComponent("5.4.8", "source-build", "hash-lua", listOf("bin/lua", "bin/luac"), 0L),
            ),
        )
        LuaEnvManifest.write(rootDir, manifest)
        return rootDir to manifest
    }

    @Test
    fun orphanTreeIsDetectedWhenNoMatchingEnvId() {
        val (rootDir, _) = writeManifest()
        val found = LuaEnvRedetection.findOrphan(rootDir, registeredEnvIds = setOf("some-other-env"))
        assertEquals("env-orphan-1", found?.environmentId)
    }

    @Test
    fun alreadyRegisteredTreeIsIgnored() {
        val (rootDir, _) = writeManifest()
        assertNull(LuaEnvRedetection.findOrphan(rootDir, registeredEnvIds = setOf("env-orphan-1")))
    }

    @Test
    fun absentManifestYieldsNoOrphan() {
        val empty = createTempDirectory("lunar-redetect-empty")
        assertNull(LuaEnvRedetection.findOrphan(empty, registeredEnvIds = emptySet()))
    }

    @Test
    fun toResultRebuildsComponentsWithAbsoluteBinaries() {
        val (rootDir, manifest) = writeManifest()
        val result = LuaEnvRedetection.toResult(rootDir, manifest)
        assertEquals("env-orphan-1", result.environmentId)
        assertEquals("env54", result.environmentName)
        val component = result.components.single()
        assertEquals("lua", component.kindId)
        assertEquals(rootDir.resolve("bin/lua"), component.primaryBinary)
        assertEquals(listOf(rootDir.resolve("bin/luac")), component.extraBinaries)
    }

    @Test
    fun corruptOrEmptyDirReturnsNull() {
        val empty = createTempDirectory("lunar-redetect-corrupt")
        assertSame(null, LuaEnvRedetection.findOrphan(empty, emptySet()))
    }
}
