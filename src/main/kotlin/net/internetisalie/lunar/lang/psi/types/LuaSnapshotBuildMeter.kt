package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiFile

/**
 * MAINT-60 design §2.2: attributes a read's time to each snapshot build it triggers, so the snapshot
 * cost sweep can tell a file's own build from the builds it reaches. Off unless a test installs a
 * [sink]: [measure] then runs the build and nothing else. Holds a path string, never the [PsiFile].
 */
internal object LuaSnapshotBuildMeter {
    data class BuildRecord(
        val path: String,
        val depth: Int,
        val inclusiveMs: Long,
        val selfMs: Long,
    )

    @Volatile
    var sink: ((BuildRecord) -> Unit)? = null

    private class Frame(
        val path: String,
        val startNs: Long,
    ) {
        var childNs = 0L
    }

    private val frames: ThreadLocal<ArrayDeque<Frame>> = ThreadLocal.withInitial { ArrayDeque() }

    fun <T> measure(
        file: PsiFile,
        build: () -> T,
    ): T {
        val activeSink = sink ?: return build()
        val stack = frames.get()
        val frame = Frame(file.virtualFile?.path ?: file.name, System.nanoTime())
        stack.addLast(frame)
        try {
            return build()
        } finally {
            stack.removeLast()
            val inclusiveNs = System.nanoTime() - frame.startNs
            stack.lastOrNull()?.let { parent -> parent.childNs += inclusiveNs }
            val selfNs = inclusiveNs - frame.childNs
            activeSink(BuildRecord(frame.path, stack.size, inclusiveNs / 1_000_000L, selfNs / 1_000_000L))
        }
    }
}
