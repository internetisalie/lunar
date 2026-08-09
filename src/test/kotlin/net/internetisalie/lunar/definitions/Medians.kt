package net.internetisalie.lunar.definitions

/**
 * Median-of-N reporting for COMP-09's measurement harnesses.
 *
 * The first standing rule of `implementation-plan.md`: **any figure quoted in a doc or a commit is a
 * median of ≥5**. It exists because Step 9 re-ran three single-shot harnesses and got a −60 % spread
 * and one *flipped verdict* — §1.2's harness reported "once per session" where the recorded run had
 * reported "per-keystroke", from the same code. Every ratio derived from a pair of unrepeated
 * `measureTimeMillis` calls sat inside its own noise floor.
 *
 * The spread is printed alongside the median so a reader can judge rather than trust. A quantity
 * that genuinely cannot be repeated — a cold snapshot build, which is warm the second time by
 * construction — is labelled `(single — unrepeatable by construction)` at its call site instead of
 * being averaged with warm samples, which is how a cold sample under four warm ones once reported
 * "1 ms vs 0 ms".
 */
object Medians {
    fun of(runs: List<Long>): Long = runs.sorted()[runs.size / 2]

    fun report(
        label: String,
        runs: List<Long>,
    ) {
        val sorted = runs.sorted()
        println("MEDIAN $label: median=${of(runs)}ms min=${sorted.first()} max=${sorted.last()} runs=$runs")
    }
}
