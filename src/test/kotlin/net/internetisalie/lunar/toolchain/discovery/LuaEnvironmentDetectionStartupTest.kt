package net.internetisalie.lunar.toolchain.discovery

import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.provision.LuaEnvManifest
import net.internetisalie.lunar.toolchain.provision.LuaManifestComponent
import net.internetisalie.lunar.toolchain.provision.LuaProvisionItem
import net.internetisalie.lunar.toolchain.provision.LuaProvisionRequest
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.io.File
import java.nio.file.Files

/**
 * TOOLING-02-14: the Adopt-on-open offer decision, incl. the dedup against
 * [net.internetisalie.lunar.toolchain.provision.LuaEnvRedetectionStartup] (a `.lunar-env.json`
 * manifest hands the tree to re-registration, so this startup stays silent for it).
 */
class LuaEnvironmentDetectionStartupTest : ToolchainSettingsTestCase() {

    private val startup = LuaEnvironmentDetectionStartup()
    private lateinit var envDir: File

    override fun setUp() {
        super.setUp()
        envDir = Files.createTempDirectory("lunar-detect").toFile()
    }

    override fun tearDown() {
        try {
            envDir.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testOffersAdoptForForeignUnrecordedDirectory() {
        assertTrue(startup.shouldOfferAdopt(project, envDir.absolutePath))
    }

    fun testSkipsLunarProvisionedTree() {
        LuaEnvManifest.write(envDir.toPath(), wellFormedManifest())

        assertFalse(
            "a .lunar-env.json tree is LuaEnvRedetectionStartup's to prompt",
            startup.shouldOfferAdopt(project, envDir.absolutePath)
        )
    }

    fun testSkipsAlreadyRecordedDirectory() {
        settings.upsertEnvironmentAndActivate(
            LuaEnvironmentState(id = "R", name = "Recorded", rootDir = envDir.absolutePath)
        )

        assertFalse(startup.shouldOfferAdopt(project, envDir.absolutePath))
    }

    private fun wellFormedManifest() = LuaEnvManifest(
        manifestVersion = 1,
        environmentId = "E1",
        environmentName = "Env",
        request = LuaProvisionRequest("Env", envDir.absolutePath, listOf(LuaProvisionItem("lua", "5.4"))),
        components = mapOf(
            "lua" to LuaManifestComponent("5.4.6", "release", "hash", listOf("bin/lua"), 1L)
        )
    )
}
