package net.internetisalie.lunar.toolchain.provision

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import net.internetisalie.lunar.toolchain.model.LuaEnvironmentState
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedKind
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedRock
import net.internetisalie.lunar.toolchain.provision.feed.LuaFeedVersion
import net.internetisalie.lunar.toolchain.provision.feed.LuaToolchainFeed
import net.internetisalie.lunar.toolchain.registry.LuaToolchainAppState
import net.internetisalie.lunar.toolchain.registry.ToolchainSettingsTestCase
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * BUG-533: a provisioned environment's manifest id, its registered record's id and its tools'
 * `environmentId` must be one value, whichever entry point provisions the directory. Drives the
 * real [RegistryProvisionResultSink] and project settings with a fake strategy (no network).
 */
class LuaProvisionEnvironmentIdTest : ToolchainSettingsTestCase() {
    private lateinit var rootDir: Path
    private val platform = LuaHostPlatform(LuaOs.LINUX, LuaArch.X86_64)

    override fun setUp() {
        super.setUp()
        rootDir = createTempDirectory("lunar-env-id-test")
    }

    override fun tearDown() {
        try {
            FileUtil.delete(rootDir.toFile())
        } finally {
            super.tearDown()
        }
    }

    private class FakeReleaseStrategy : LuaProvisioningStrategy {
        override val id = "release-binary"

        override fun supports(
            item: LuaProvisionItem,
            platform: LuaHostPlatform,
            feed: LuaToolchainFeed,
        ) = item.kindId in KINDS

        override fun identityHash(
            context: LuaProvisionContext,
            item: LuaProvisionItem,
        ): String = "hash-${item.kindId}"

        override fun provision(
            context: LuaProvisionContext,
            item: LuaProvisionItem,
        ): LuaProvisionedComponent {
            val binary = context.rootDir.resolve("bin/${item.kindId}")
            binary.parent?.createDirectories()
            binary.writeText("#!/bin/sh\n")
            binary.toFile().setExecutable(true)
            return LuaProvisionedComponent(
                item.kindId,
                item.versionSpec,
                id,
                binary,
                emptyList(),
                "hash-${item.kindId}",
            )
        }

        companion object {
            val KINDS = setOf("lua", "luarocks")
        }
    }

    private object SilentNotifier : LuaProvisionNotifier {
        override fun notify(
            project: Project,
            message: String,
            type: NotificationType,
        ) = Unit
    }

    private val feed: LuaToolchainFeed
        get() {
            val rock = LuaFeedRock("r", null, "tool", needsCToolchain = false)
            val version = { spec: String -> LuaFeedVersion(spec, null, null, emptyList(), rock) }
            return LuaToolchainFeed(
                1,
                mapOf(
                    "lua" to LuaFeedKind(emptyMap(), listOf(version("5.4"))),
                    "luarocks" to LuaFeedKind(emptyMap(), listOf(version("latest"))),
                ),
            )
        }

    private fun provision() {
        val request =
            LuaProvisionRequest(
                "lua 5.4",
                rootDir.toString(),
                listOf(LuaProvisionItem("lua", "5.4"), LuaProvisionItem("luarocks", "latest")),
            )
        val engine = LuaProvisionEngine(listOf(FakeReleaseStrategy()), RegistryProvisionResultSink(), SilentNotifier)
        engine.run(LuaProvisionJob(project, request, EmptyProgressIndicator()), platform, feed)
    }

    private fun manifestId(): String? = LuaEnvManifest.read(rootDir)?.environmentId

    private fun assertOneId(expectedId: String) {
        val record = settings.environments().single()
        assertEquals("the registered record keeps its id", expectedId, record.id)
        assertEquals("the manifest records the registered id", expectedId, manifestId())
        val toolEnvIds = registry.tools().map { it.environmentId }.toSet()
        assertEquals("every tool belongs to the registered record", setOf(expectedId), toolEnvIds)
        assertNull(
            "reopen finds nothing to re-register",
            LuaEnvRedetection.findOrphan(rootDir, settings.environments().map { it.id }.toSet()),
        )
    }

    fun testWizardRegisteredEnvironmentKeepsOneIdAcrossManifestRecordAndTools() {
        val wizardRecord =
            settings.upsertEnvironmentAndActivate(LuaEnvironmentState(name = "lua 5.4", rootDir = rootDir.toString()))

        provision()

        assertOneId(wizardRecord.id)
    }

    fun testRecreateWithoutManifestAdoptsTheRegisteredId() {
        settings.upsertEnvironmentAndActivate(LuaEnvironmentState("registered-id", "lua 5.4", rootDir.toString()))

        provision()

        assertOneId("registered-id")
    }

    fun testReProvisionRepairsAManifestCarryingAStaleId() {
        provision()
        val stale = LuaEnvManifest.read(rootDir) ?: error("first provision wrote no manifest")
        LuaEnvManifest.write(rootDir, stale.copy(environmentId = "stale-id"))

        provision()

        assertOneId(settings.environments().single().id)
        assertFalse("the stale id is gone", manifestId() == "stale-id")
    }

    fun testReRegisterRepairsATreeRegisteredUnderAnotherId() {
        provision()
        val registeredId = settings.environments().single().id
        val provisioned = LuaEnvManifest.read(rootDir) ?: error("provision wrote no manifest")
        val mismatched = provisioned.copy(environmentId = "orphan-id")
        LuaEnvManifest.write(rootDir, mismatched)
        registry.loadState(LuaToolchainAppState())

        LuaEnvReRegistration.reRegister(project, rootDir, mismatched)

        assertOneId(registeredId)
    }

    fun testNewDirectoryRegistersTheManifestId() {
        provision()

        val id = manifestId() ?: error("provision wrote no manifest")
        assertOneId(id)
    }
}
