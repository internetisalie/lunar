package net.internetisalie.lunar.perfrecords

import java.security.MessageDigest

internal data class WorkloadSpec(
    val name: String,
    val kind: String,
    val fixture: String,
)

internal data class WorkloadChecks(
    val required: Set<String>,
    val exactOutputs: Map<String, Long>,
    val positiveOutputs: Set<String>,
)

internal data class Measured(
    val counts: Map<String, Long>,
    val outputs: Map<String, Long>,
)

internal data class Workload(
    val spec: WorkloadSpec,
    val checks: WorkloadChecks,
    val run: () -> Measured,
)

internal data class Pass(
    val rawCounts: Map<String, Long>,
    val outputs: Map<String, Long>,
    val durationMs: Long,
)

private const val FIXTURE_HASH_CHARS = 16
private const val RECORD_SCHEMA = 1

internal fun violations(
    checks: WorkloadChecks,
    rawCounts: Map<String, Long>,
    outputs: Map<String, Long>,
): List<String> {
    val missingCounters =
        checks.required
            .filter { rawCounts.getOrDefault(it, 0L) <= 0L }
            .map { "required counter $it is zero" }
    val wrongOutputs =
        checks.exactOutputs
            .filter { (key, expected) -> outputs[key] != expected }
            .map { (key, expected) -> "output $key is ${outputs[key]}, expected $expected" }
    val emptyOutputs =
        checks.positiveOutputs
            .filter { outputs.getOrDefault(it, 0L) <= 0L }
            .map { "output $it is zero" }
    return missingCounters + wrongOutputs + emptyOutputs
}

internal fun toRecord(
    spec: WorkloadSpec,
    warm: Pass,
    recorded: Pass,
): PerfRecord =
    PerfRecord(
        schema = RECORD_SCHEMA,
        workload = spec.name,
        kind = spec.kind,
        durationMs = recorded.durationMs,
        fixture = fixtureHash(spec.fixture),
        counts = positiveSorted(recorded.rawCounts),
        outputs = recorded.outputs.toSortedMap(),
        warm = PerfPassValues(positiveSorted(warm.rawCounts), warm.outputs.toSortedMap()),
    )

private fun positiveSorted(rawCounts: Map<String, Long>): Map<String, Long> =
    rawCounts.filterValues { it > 0L }.toSortedMap()

private fun fixtureHash(fixture: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(fixture.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(FIXTURE_HASH_CHARS)
