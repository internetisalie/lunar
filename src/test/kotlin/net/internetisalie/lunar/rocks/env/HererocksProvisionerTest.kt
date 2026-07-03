package net.internetisalie.lunar.rocks.env

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Phase 3: arg builder (TC-4/5) + concurrency guard (TC-10). */
class HererocksProvisionerTest : BasePlatformTestCase() {

    fun testArgsForPuc() {
        val spec = HererocksEnvState(directory = "/p/.lua", flavor = HererocksFlavor.PUC, luaVersion = "5.4", luarocksVersion = "latest")
        assertEquals(
            listOf("hererocks", "/p/.lua", "--lua", "5.4", "--luarocks", "latest"),
            HererocksProvisioner.argsFor(listOf("hererocks"), spec),
        )
    }

    fun testArgsForLuajit() {
        val spec = HererocksEnvState(directory = "/p/.lua", flavor = HererocksFlavor.LUAJIT, luaVersion = "2.1", luarocksVersion = "latest")
        val args = HererocksProvisioner.argsFor(listOf("python3", "-m", "hererocks"), spec)
        assertEquals(listOf("--luajit", "2.1"), args.subList(4, 6))
    }

    fun testConcurrencyGuardRefusesSecond() {
        val provisioner = HererocksProvisioner.getInstance(project)
        val spec = HererocksEnvState(directory = "/p/.lua")
        assertTrue(provisioner.tryReserve(spec))
        assertFalse("second reservation for same directory must be refused", provisioner.tryReserve(spec))
        provisioner.release(spec)
        assertTrue("directory is provisionable again after release", provisioner.tryReserve(spec))
        provisioner.release(spec)
    }
}
