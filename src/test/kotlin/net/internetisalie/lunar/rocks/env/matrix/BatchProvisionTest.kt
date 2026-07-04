package net.internetisalie.lunar.rocks.env.matrix

import junit.framework.TestCase
import net.internetisalie.lunar.rocks.env.HererocksFlavor

/** Phase 5: batch spec derivation (TC-9). */
class BatchProvisionTest : TestCase() {

    fun testDeriveSpecsForVersionMatrix() {
        val rows = listOf(
            BatchRow(HererocksFlavor.PUC, "5.3"),
            BatchRow(HererocksFlavor.PUC, "5.4"),
        )
        val specs = BatchProvisionAction.deriveSpecs("/p/envs", rows)

        assertEquals(2, specs.size)
        assertEquals("/p/envs/PUC-5.3", specs[0].directory)
        assertEquals("/p/envs/PUC-5.4", specs[1].directory)
        assertEquals("5.3", specs[0].luaVersion)
        assertEquals(HererocksFlavor.PUC, specs[0].flavor)
        assertTrue("each spec gets a fresh non-blank id", specs.all { it.id.isNotBlank() })
        assertTrue("ids are unique", specs.map { it.id }.toSet().size == 2)
    }
}
