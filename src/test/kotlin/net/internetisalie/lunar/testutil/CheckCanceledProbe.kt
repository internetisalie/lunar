package net.internetisalie.lunar.testutil

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase
import com.intellij.openapi.util.Computable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Support for tests that count `ProgressManager.checkCanceled()` calls through a
 * `CoreProgressManager.CheckCanceledHook` (BUG-18, BUG-20).
 *
 * A hook is reached directly only on `CoreProgressManager.doCheckCanceled`'s `ONLY_HOOKS` branch,
 * and which branch runs is a JVM-wide static: while **any** thread is under a cancelled indicator
 * every thread takes `INDICATOR_PLUS_HOOKS`, which consults just the current thread's indicator. A
 * thread with none then makes checks that no hook ever sees, and the fixture's own background work
 * (code vision, non-blocking read actions) puts the JVM in that state at random.
 *
 * [underLiveIndicator] makes the count independent of that branch, because
 * [StandardProgressIndicatorBase.checkCanceled] runs the hooks itself. `EmptyProgressIndicator`
 * would not do: its `checkCanceled` is final and runs no hooks.
 * [whileAnotherThreadIsUnderACancelledIndicator] forces the state the fixture otherwise reaches by
 * chance, so a counter can be shown to be immune to it.
 */
object CheckCanceledProbe {
    private const val HOLD_TIMEOUT_SECONDS = 30L
    private const val DELEGATING_INDICATOR = "com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase"
    private const val DELEGATE_FRAME_SEARCH_DEPTH = 24L

    /** Runs [work] on this thread under a live, uncancelled indicator. */
    fun <T> underLiveIndicator(work: () -> T): T =
        ProgressManager.getInstance().runProcess(Computable { work() }, StandardProgressIndicatorBase())

    /**
     * True when the hook call being made is the repeat that a delegating indicator makes of a check it
     * already reported. Call it from inside a hook.
     *
     * Where the thread's indicator delegates to others, as the `PotemkinProgress` a rename runs under
     * does through its `ProgressWindow`, `AbstractProgressIndicatorExBase.checkCanceled` runs the
     * hooks once for itself and once per delegate. That happens only on the `INDICATOR_PLUS_HOOKS`
     * branch, so a count that includes the repeats doubles whenever another thread is under a
     * cancelled indicator (BUG-20). The rename cannot be run under an indicator of the test's choosing,
     * because it installs its own.
     */
    fun isRepeatByIndicatorDelegate(): Boolean =
        StackWalker.getInstance().walk { frames ->
            frames
                .limit(DELEGATE_FRAME_SEARCH_DEPTH)
                .anyMatch { it.className == DELEGATING_INDICATOR && it.methodName == "delegate" }
        }

    /**
     * Runs [measurement] while a pooled thread sits under a cancelled indicator, which is the state
     * that flips `CoreProgressManager`'s check-canceled behaviour for every thread in the JVM.
     */
    fun <T> whileAnotherThreadIsUnderACancelledIndicator(measurement: () -> T): T {
        val cancelled = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val indicator = StandardProgressIndicatorBase()
        val worker =
            ApplicationManager.getApplication().executeOnPooledThread {
                val holdUntilFinished =
                    Runnable {
                        indicator.cancel()
                        cancelled.countDown()
                        finished.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    }
                ProgressManager.getInstance().runProcess(holdUntilFinished, indicator)
            }
        try {
            check(cancelled.await(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "the pooled thread never reached its cancelled indicator"
            }
            return measurement()
        } finally {
            finished.countDown()
            worker.get(HOLD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }
}
