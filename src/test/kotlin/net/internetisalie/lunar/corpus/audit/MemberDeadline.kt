package net.internetisalie.lunar.corpus.audit

/**
 * The representative run's per-member limit (MAINT-56 design §5.2, BUG-96, BUG-97): measured from construction,
 * checked between sites, so a member overruns it by at most one site's measurement. A null limit never
 * expires, which keeps MAINT-54's audit unbounded.
 */
internal class MemberDeadline(
    private val limitNanos: Long?,
    private val clock: () -> Long = System::nanoTime,
) {
    private val startedAt = clock()

    fun expired(): Boolean = limitNanos != null && clock() - startedAt > limitNanos
}

/** What [measureWithin] measured, and whether the deadline stopped it first. */
internal data class Bounded<T>(
    val results: List<T>,
    val timedOut: Boolean,
)

/** [measure] over [sites] in order until [deadline] expires; the sites after that are not measured. */
internal fun <S, T> measureWithin(
    sites: List<S>,
    deadline: MemberDeadline,
    measure: (S) -> T,
): Bounded<T> {
    val results = mutableListOf<T>()
    for (site in sites) {
        if (deadline.expired()) return Bounded(results, timedOut = true)
        results += measure(site)
    }
    return Bounded(results, timedOut = false)
}
