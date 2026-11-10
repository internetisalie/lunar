package net.internetisalie.lunar.corpus.audit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** MAINT-56 TC-28: the per-member deadline stops measuring between sites and says so. */
@RunWith(JUnit4::class)
class MemberDeadlineTest {
    @Test
    fun anExpiredDeadlineStopsBeforeTheNextSite() {
        val readings = ArrayDeque(listOf(0L, 5L, 11L, 12L))
        val deadline = MemberDeadline(limitNanos = 10L, clock = { readings.removeFirst() })

        val bounded = measureWithin(listOf("a", "b", "c"), deadline) { it.uppercase() }

        assertEquals(Bounded(listOf("A"), timedOut = true), bounded)
    }

    @Test
    fun aNullLimitMeasuresEverySite() {
        val deadline = MemberDeadline(limitNanos = null, clock = { Long.MAX_VALUE })

        val bounded = measureWithin(listOf("a", "b"), deadline) { it.uppercase() }

        assertEquals(Bounded(listOf("A", "B"), timedOut = false), bounded)
    }
}
