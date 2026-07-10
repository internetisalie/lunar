package net.internetisalie.lunar.redis.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Hyperlinks server-side `user_script:<N>` error references to the run's script file (design §2.7, §3.6).
 *
 * Matches both `user_script:12:` and `@user_script: 7` (optional `@`, optional colon, optional
 * whitespace), converting the 1-based server line to the 0-based editor line (TC-CON-3). The script
 * file is resolved by URL each call ([scriptFileUrl]) — the filter holds **no** hard `VirtualFile`
 * field (engineering-contract §4); an unresolvable URL yields `null` (no link, no `!!`). [applyFilter]
 * is called by the platform on a pooled thread and returns an immutable [Filter.Result].
 */
class LuaRedisErrorLinkFilter(
    private val project: Project,
    private val scriptFileUrl: String,
) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = LINE_REFERENCE.find(line) ?: return null
        val serverLine = match.groupValues[1].toIntOrNull() ?: return null
        val file = VirtualFileManager.getInstance().findFileByUrl(scriptFileUrl) ?: return null

        val editorLine = (serverLine - 1).coerceAtLeast(0)
        val hyperlink = OpenFileHyperlinkInfo(project, file, editorLine)
        val startOffset = entireLength - line.length + match.range.first
        val endOffset = entireLength - line.length + match.range.last + 1
        return Filter.Result(startOffset, endOffset, hyperlink)
    }

    private companion object {
        /** `user_script:12` / `@user_script: 7` — optional `@`, optional colon, optional whitespace (design §3.6). */
        val LINE_REFERENCE = Regex("""(?:@?user_script:?\s*)(\d+)""")
    }
}
