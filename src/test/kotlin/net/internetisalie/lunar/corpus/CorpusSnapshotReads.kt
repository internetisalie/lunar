package net.internetisalie.lunar.corpus

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.util.concurrency.AppExecutorUtil
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.EXCEPTION_PREFIX
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.OK
import net.internetisalie.lunar.corpus.SnapshotCostVerdict.TIMEOUT
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.types.ElementError
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import java.util.concurrent.TimeUnit

/** The read-and-digest logic shared by [LuaSnapshotCostSweepTest] and [LuaAloneDigestTest] (MAINT-65 design §2.3). */
internal object CorpusSnapshotReads {
    fun readErrors(
        file: LuaFile,
        deadlineMs: Long,
    ): Pair<String, List<ElementError>?> {
        val indicator = EmptyProgressIndicator()
        val cancel =
            AppExecutorUtil
                .getAppScheduledExecutorService()
                .schedule({ indicator.cancel() }, deadlineMs, TimeUnit.MILLISECONDS)
        return try {
            val errors =
                ProgressManager.getInstance().runProcess(
                    Computable { runReadAction { LuaTypesSnapshot.forFile(file).getErrors() } },
                    indicator,
                )
            OK to errors
        } catch (canceled: ProcessCanceledException) {
            TIMEOUT to null
        } catch (failure: Exception) {
            "$EXCEPTION_PREFIX${failure.javaClass.simpleName}" to null
        } finally {
            cancel.cancel(false)
        }
    }

    fun errorDigest(errors: List<ElementError>): Int =
        runReadAction {
            errors.map { "${it.element.textOffset}:${it.severity}:${it.message}" }.sorted().hashCode()
        }
}
