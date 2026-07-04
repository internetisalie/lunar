package net.internetisalie.lunar.rocks.env.matrix

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import net.internetisalie.lunar.rocks.env.EnvSettingsTestCase
import net.internetisalie.lunar.rocks.env.HererocksFlavor
import net.internetisalie.lunar.settings.LuaProjectSettings
import java.io.File
import java.nio.file.Files

/**
 * ROCKS-15 remediation batch test: exercises the batch-provision *success* wiring — each derived
 * spec is routed through [LuaProjectSettings.upsertAndActivate] (the seam
 * [net.internetisalie.lunar.rocks.env.HererocksProvisioner]'s success path now invokes on the EDT),
 * standing in for a fake provisioner whose process exited 0. Asserts BOTH provisioned specs land in
 * [LuaProjectSettings.resolveAllEnvs] — the exact live defect (batch envs never appearing in the
 * switcher because only the migration populated the set) this now catches.
 */
class BatchProvisionSetMembershipTest : EnvSettingsTestCase() {

    private lateinit var root: File

    override fun setUp() {
        super.setUp()
        root = Files.createTempDirectory("hererocks-batch-test").toFile()
    }

    override fun tearDown() {
        try {
            root.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testBatchSuccessAddsAllSpecsToSet() {
        if (SystemInfo.isWindows) return
        val rows = listOf(
            BatchRow(HererocksFlavor.PUC, "5.1"),
            BatchRow(HererocksFlavor.PUC, "5.3"),
        )
        val specs = BatchProvisionAction.deriveSpecs(root.absolutePath, rows)
        specs.forEach { spec -> fakeLayout(spec.directory) }

        val settings = LuaProjectSettings.getInstance(project)
        // Simulate each row's exit-0 success (provisioner's invokeLater success wiring).
        specs.forEach { settings.upsertAndActivate(project, it) }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        val dirs = settings.resolveAllEnvs().map { it.directory }.toSet()
        assertEquals("both batch-provisioned envs must be in the set", 2, dirs.size)
        assertTrue(dirs.containsAll(specs.map { it.directory }))
    }

    private fun fakeLayout(directory: String) {
        val bin = File(directory, "bin").also { it.mkdirs() }
        writeScript(File(bin, "lua"))
        writeScript(File(bin, "luarocks"))
    }

    private fun writeScript(file: File) {
        file.writeText("#!/bin/sh\necho fake\n")
        file.setExecutable(true)
    }
}
