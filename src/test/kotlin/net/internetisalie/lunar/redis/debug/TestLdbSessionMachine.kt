package net.internetisalie.lunar.redis.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Socket-free coverage of [LdbSessionMachine] (design §2.11, §3.5).
 *
 * Covers TC-LDB-SM-1 (HANDSHAKE → ARMED → PAUSED on `+OK` then first stop) and TC-LDB-SM-2
 * (PAUSED → RUNNING → TERMINATED on continue+session-end, then a guarded no-op — no throw).
 */
class TestLdbSessionMachine {

    private fun pausedAt(line: Int): LdbSessionMachine {
        val machine = LdbSessionMachine()
        machine.onEvent(LdbEvent.Ack)
        machine.onEvent(LdbEvent.Stop(line, StopReason.BREAKPOINT, null))
        return machine
    }

    /** TC-LDB-SM-1: `+OK` arms the session; the first stop pauses it at the reported line. */
    @Test
    fun testHandshakeToArmedToPaused() {
        val machine = LdbSessionMachine()
        assertEquals(LdbState.HANDSHAKE, machine.state)

        machine.onEvent(LdbEvent.Ack)
        assertEquals(LdbState.ARMED, machine.state)

        machine.onEvent(LdbEvent.Stop(1, StopReason.STEP, "local a = 1"))
        assertEquals(LdbState.PAUSED, machine.state)
        assertEquals(1, machine.currentLine)
    }

    /** TC-LDB-SM-2: continue from PAUSED runs, session-end terminates, later commands no-op. */
    @Test
    fun testContinueThenSessionEndThenGuardedNoOp() {
        val machine = pausedAt(3)
        assertEquals(LdbState.PAUSED, machine.state)

        assertTrue(machine.onCommandSent(LdbCommand.Continue))
        assertEquals(LdbState.RUNNING, machine.state)

        machine.onEvent(LdbEvent.SessionEnded(EndReason.ENDED))
        assertEquals(LdbState.TERMINATED, machine.state)

        assertFalse(machine.onCommandSent(LdbCommand.Step), "step on a dead session is a no-op")
        assertFalse(machine.onCommandSent(LdbCommand.Eval("1")), "eval on a dead session is a no-op")
        assertEquals(LdbState.TERMINATED, machine.state)
    }

    /** Resume/step/print/eval are illegal outside PAUSED (guarded, not thrown). */
    @Test
    fun testStepIllegalBeforePause() {
        val machine = LdbSessionMachine()
        assertFalse(machine.onCommandSent(LdbCommand.Step))
        machine.onEvent(LdbEvent.Ack)
        assertFalse(machine.onCommandSent(LdbCommand.Step), "step is illegal while merely ARMED")
    }

    /** Breakpoints may be installed while ARMED (before the script runs) and while PAUSED. */
    @Test
    fun testBreakpointLegalWhenArmedOrPaused() {
        val machine = LdbSessionMachine()
        machine.onEvent(LdbEvent.Ack)
        assertTrue(machine.onCommandSent(LdbCommand.Break(5)))
        assertEquals(LdbState.ARMED, machine.state)

        val paused = pausedAt(5)
        assertTrue(paused.onCommandSent(LdbCommand.Break(9)))
    }

    /** A compile error terminates the session. */
    @Test
    fun testCompileErrorTerminates() {
        val machine = LdbSessionMachine()
        machine.onEvent(LdbEvent.Error(LdbErrorKind.COMPILE, "user_script:2: boom", 2))
        assertEquals(LdbState.TERMINATED, machine.state)
    }
}
