package net.internetisalie.lunar.lang.psi.types

import com.intellij.ide.startup.ServiceNotReadyException
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import net.internetisalie.lunar.lang.indexing.LuaCatsTypeNameIndex
import net.internetisalie.lunar.lang.indexing.LuaGlobalAssignmentIndex
import net.internetisalie.lunar.lang.indexing.LuaMemberFieldIndex
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal const val INDEX_COUNT = 5

internal const val DUMB_OFFSET = 1L shl 62

/**
 * MAINT-63 design §2.1 — per-URL change counters and a resolution count, from which an unpinned
 * type snapshot's churn dependency is built ([trackerFor]).
 *
 * Holds URL strings and longs only. [fileChanged] and [structureChanged] are lock-free and safe from
 * any thread; [LuaSourceChangePreprocessor] calls them. The counting reads run in read actions.
 */
class LuaSourceChangeTracker(
    private val targetProject: Project,
) {
    private val fileCounters = ConcurrentHashMap<String, AtomicLong>()
    private val structureEpoch = SimpleModificationTracker()
    private val stampTotal = LuaStampTotal()

    fun fileChanged(url: String) {
        fileCounters.computeIfAbsent(url) { AtomicLong() }.incrementAndGet()
    }

    fun structureChanged() {
        structureEpoch.incModificationCount()
    }

    fun countOf(url: String): Long = fileCounters[url]?.get() ?: 0L

    /**
     * Design §3.3: moves whenever an index answer a build can read without recording a URL, or a
     * file-less PSI event, can change resolution. Dumb mode answers a constant [DUMB_OFFSET] above.
     */
    fun resolutionCount(): Long {
        val shared = structureEpoch.modificationCount
        if (DumbService.isDumb(targetProject)) return stampTotal.dumbValue(shared)
        val stamps =
            try {
                indexStamps()
            } catch (_: IndexNotReadyException) {
                return stampTotal.dumbValue(shared)
            } catch (_: ServiceNotReadyException) {
                return stampTotal.dumbValue(shared)
            }
        return stampTotal.advance(stamps, shared)
    }

    fun psiModificationCount(): Long = PsiModificationTracker.getInstance(targetProject).modificationCount

    /**
     * Design §3.1: the per-source tracker for a snapshot of [psiFile] built under [frame], or `null`
     * when its sources cannot be identified, which keeps `MODIFICATION_COUNT`.
     */
    fun trackerFor(
        psiFile: PsiFile,
        frame: LuaTypeSourceRecorder.SourceFrame,
    ): ModificationTracker? {
        val ownUrl = psiFile.originalFile.virtualFile?.url ?: return null
        if (frame.unreplayedWarm.isNotEmpty()) return null
        val sources = HashSet<String>()
        sources.add(ownUrl)
        sources.addAll(frame.urls)
        sources.addAll(frame.inProgressHits)
        if (sources.any { LuaTypeSourceRecorder.isUnidentified(it) }) return null
        return LuaSourceSetTracker(this, sources, frame.pendingFrames.toList())
    }

    private fun indexStamps(): LongArray =
        longArrayOf(
            stampOf(StubUpdatingIndex.INDEX_ID),
            stampOf(LuaGlobalAssignmentIndex.KEY),
            stampOf(LuaCatsTypeNameIndex.KEY),
            stampOf(LuaReceiverMemberIndex.KEY),
            stampOf(LuaMemberFieldIndex.KEY),
        )

    private fun <K, V> stampOf(id: ID<K, V>): Long =
        FileBasedIndex.getInstance().getIndexModificationStamp(id, targetProject)

    companion object {
        fun getInstance(project: Project): LuaSourceChangeTracker = project.service()
    }
}

/**
 * Design §2.2 — an unpinned snapshot's churn dependency: the counters of its recorded sources and of
 * every source its pending frames have recorded so far, plus the resolution count. A pending frame
 * whose sources cannot be identified adds the PSI modification count, as if it had no tracker.
 */
internal class LuaSourceSetTracker(
    private val owner: LuaSourceChangeTracker,
    private val sources: Set<String>,
    private val pending: List<LuaTypeSourceRecorder.SourceFrame>,
) : ModificationTracker {
    override fun getModificationCount(): Long {
        val live = HashSet(sources)
        val degraded = collectPending(live)
        val fallback = if (degraded) owner.psiModificationCount() else 0L
        return live.sumOf { owner.countOf(it) } + owner.resolutionCount() + fallback
    }

    /** Design §3.3 steps 1–3: adds every reachable pending frame's sources; `true` when degraded. */
    private fun collectPending(live: MutableSet<String>): Boolean {
        val visited = Collections.newSetFromMap(IdentityHashMap<LuaTypeSourceRecorder.SourceFrame, Boolean>())
        val work = ArrayDeque(pending)
        var degraded = false
        while (work.isNotEmpty()) {
            val frame = work.removeLast()
            if (!visited.add(frame)) continue
            if (frame.unreplayedWarm.isNotEmpty()) degraded = true
            live.addAll(frame.urls)
            live.addAll(frame.inProgressHits)
            work.addAll(frame.pendingFrames)
        }
        return degraded || live.any { LuaTypeSourceRecorder.isUnidentified(it) }
    }
}

/**
 * Design §2.2a — turns the index stamps into a total that never repeats an earlier value once any
 * stamp has changed, even when a stamp goes down (risks Risk 1.3).
 */
internal class LuaStampTotal {
    private val lastStamps = LongArray(INDEX_COUNT)
    private var offset = 0L
    private var highWater = Long.MIN_VALUE
    private var lastIndexTerm = 0L

    @Synchronized
    fun advance(
        stamps: LongArray,
        shared: Long,
    ): Long {
        val changed = !stamps.contentEquals(lastStamps)
        var total = shared + offset + stamps.sum()
        if (changed && total <= highWater) {
            offset += highWater + 1 - total
            total = highWater + 1
        }
        stamps.copyInto(lastStamps)
        lastIndexTerm = offset + stamps.sum()
        highWater = maxOf(highWater, total)
        return total
    }

    @Synchronized
    fun dumbValue(shared: Long): Long = shared + lastIndexTerm + DUMB_OFFSET
}
