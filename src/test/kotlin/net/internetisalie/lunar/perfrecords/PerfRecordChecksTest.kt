package net.internetisalie.lunar.perfrecords

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MAINT-45-02: the self-checks `LuaPerfRecordWorkloadsTest` applies, against known inputs (TC 30, 32-34). */
class PerfRecordChecksTest {
    private val noChecks = WorkloadChecks(emptySet(), emptyMap(), emptySet())

    @Test
    fun `TC 30 a required counter at zero is a violation naming it`() {
        val checks = noChecks.copy(required = setOf("stub:lunar.global.decl"))
        val found = violations(checks, emptyMap(), mapOf("lookupItems" to 200L))
        assertEquals(1, found.size, found.toString())
        assertTrue("stub:lunar.global.decl" in found.single(), found.single())
    }

    @Test
    fun `TC 32 an exact output that differs is a violation naming it`() {
        val checks = noChecks.copy(exactOutputs = mapOf("resolved" to 320L))
        val found = violations(checks, mapOf("write" to 1L), mapOf("resolved" to 319L))
        assertEquals(1, found.size, found.toString())
        assertTrue("resolved" in found.single(), found.single())
    }

    @Test
    fun `TC 33 a positive output at zero is a violation naming it`() {
        val checks = noChecks.copy(positiveOutputs = setOf("lookupItems"))
        val found = violations(checks, emptyMap(), mapOf("lookupItems" to 0L))
        assertEquals(1, found.size, found.toString())
        assertTrue("lookupItems" in found.single(), found.single())
    }

    @Test
    fun `TC 34 warm counts come from the warm pass and zeros are dropped`() {
        val warm = Pass(mapOf("x" to 1L, "z" to 0L), emptyMap(), 5L)
        val recorded = Pass(mapOf("x" to 2L, "z" to 0L), emptyMap(), 7L)
        val record = toRecord(WorkloadSpec("w", "parse", "text"), warm, recorded)
        assertEquals(mapOf("x" to 1L), record.warm.counts)
        assertEquals(mapOf("x" to 2L), record.counts)
        assertEquals(7L, record.durationMs)
    }

    @Test
    fun `D1 a root count that differs across passes is named with both values`() {
        val warm = Pass(mapOf("write" to 2L), emptyMap(), 0L)
        val recorded = Pass(mapOf("write" to 3L), emptyMap(), 0L)
        assertEquals(listOf("counts differ across passes: write 2 -> 3"), passDisagreements(warm, recorded))
    }

    @Test
    fun `D2 a key absent from a pass reads as zero and keys report in root order`() {
        val warm = Pass(mapOf("write" to 2L, "read" to 1L), emptyMap(), 0L)
        val recorded = Pass(mapOf("write" to 3L), emptyMap(), 0L)
        assertEquals(
            listOf("counts differ across passes: read 1 -> 0", "counts differ across passes: write 2 -> 3"),
            passDisagreements(warm, recorded),
        )
    }

    @Test
    fun `D3 stub counts are not compared`() {
        val warm = Pass(mapOf("write" to 2L, "stub:lunar.global.decl" to 5L), emptyMap(), 0L)
        val recorded = Pass(mapOf("write" to 2L, "stub:lunar.global.decl" to 7L), emptyMap(), 0L)
        assertEquals(emptyList(), passDisagreements(warm, recorded))
    }
}
