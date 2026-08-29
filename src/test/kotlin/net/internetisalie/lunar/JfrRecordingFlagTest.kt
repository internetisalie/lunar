package net.internetisalie.lunar

import org.junit.jupiter.api.Test
import java.lang.management.ManagementFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * MAINT-38-01/-04 — the JFR gate's default-off guarantee, asserted from inside the forked test JVM.
 *
 * Diagnostic instrumentation that stays on by accident is a permanent tax on every test run, and
 * nothing else in the suite can see the JVM it was launched with. `build.gradle.kts` publishes the
 * gate decision (`lunar.jfr.requested`) unconditionally and adds `-XX:StartFlightRecording`
 * conditionally, so the two disagree the moment the recording escapes its `-PjfrProfile` guard.
 *
 * Reads [ManagementFactory] rather than `jdk.jfr.FlightRecorder`: asking the JFR API whether a
 * recording exists initializes the recorder, which would make the observation the thing observed.
 */
class JfrRecordingFlagTest {
    private val startRecordingArguments: List<String>
        get() =
            ManagementFactory
                .getRuntimeMXBean()
                .inputArguments
                .filter { it.startsWith(START_RECORDING_PREFIX) }

    @Test
    fun testFlightRecordingRunsExactlyWhenTheBuildWasAskedForOne() {
        val recordingRequested = System.getProperty(REQUEST_PROPERTY).toBoolean()

        assertEquals(
            recordingRequested,
            startRecordingArguments.isNotEmpty(),
            "`$REQUEST_PROPERTY` is $recordingRequested but the test JVM was launched with " +
                "$startRecordingArguments — the JFR argument must be gated on `-PjfrProfile`",
        )

        if (recordingRequested) {
            assertTrue(
                startRecordingArguments.all { it.contains(RECORDING_DIRECTORY_SEGMENT) },
                "A recording was requested but does not target `$RECORDING_DIRECTORY_SEGMENT`: $startRecordingArguments",
            )
        }
    }

    private companion object {
        const val REQUEST_PROPERTY = "lunar.jfr.requested"
        const val START_RECORDING_PREFIX = "-XX:StartFlightRecording"
        const val RECORDING_DIRECTORY_SEGMENT = "/build/jfr"
    }
}
