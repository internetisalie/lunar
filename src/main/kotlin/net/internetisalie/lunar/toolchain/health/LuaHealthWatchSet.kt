package net.internetisalie.lunar.toolchain.health

import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * Immutable set of watched canonical paths for the toolchain health monitor (design §3.2):
 * inventory binary paths, environment root dirs, and environment `bin/` dirs. All strings — the
 * monitor holds no [com.intellij.openapi.vfs.VirtualFile] references (arch memory rule).
 */
data class LuaHealthWatchSet(
    val exactPaths: Set<String>,
    val envRoots: Set<String>,
    val binDirs: Set<String>
) {
    companion object {
        val EMPTY = LuaHealthWatchSet(emptySet(), emptySet(), emptySet())
    }
}

/**
 * Pure match predicate for a delete/move event path (design §3.2): the event path is watched
 * exactly, or a watched path is a descendant of the (deleted/moved) event path.
 */
fun matchesDeleteOrMove(eventPath: String, watchSet: LuaHealthWatchSet): Boolean {
    val allWatched = watchSet.exactPaths + watchSet.envRoots + watchSet.binDirs
    if (eventPath in allWatched) return true
    val prefix = "$eventPath/"
    return allWatched.any { it.startsWith(prefix) }
}

/**
 * Pure match predicate for a content/property-change event path (design §3.2): an exact binary
 * path, or a direct child of a watched `bin/` dir.
 */
fun matchesContentChange(eventPath: String, watchSet: LuaHealthWatchSet): Boolean {
    if (eventPath in watchSet.exactPaths) return true
    val parent = eventPath.substringBeforeLast('/', missingDelimiterValue = "")
    return parent.isNotEmpty() && parent in watchSet.binDirs
}

/**
 * Dispatches a concrete [VFileEvent] to the relevant pure predicate (design §3.2). All other event
 * types are ignored. The event path is `event.file?.canonicalPath ?: event.path`.
 */
fun matchesWatchedEvent(event: VFileEvent, watchSet: LuaHealthWatchSet): Boolean {
    val eventPath = event.file?.canonicalPath ?: event.path
    return when (event) {
        is VFileDeleteEvent, is VFileMoveEvent -> matchesDeleteOrMove(eventPath, watchSet)
        is VFileContentChangeEvent, is VFilePropertyChangeEvent -> matchesContentChange(eventPath, watchSet)
        else -> false
    }
}
