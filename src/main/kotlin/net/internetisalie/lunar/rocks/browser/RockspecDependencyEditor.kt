package net.internetisalie.lunar.rocks.browser

/**
 * Pure text editor that appends a dependency to a rockspec's `dependencies = { … }` table
 * (ROCKS-16-13, risks DR-05). Kept pure so it is fully unit-testable; the VFS write is done by the
 * caller under a `WriteCommandAction`.
 *
 * Handles the single-line (`{ "lua >= 5.1" }`) and multi-line forms, is idempotent (an existing
 * entry for the same package name is left untouched), and appends a fresh `dependencies` block when
 * none exists.
 */
object RockspecDependencyEditor {

    private val DEPENDENCIES_BLOCK = Regex("(?s)dependencies\\s*=\\s*\\{(.*?)}")

    /**
     * Returns [rockspecText] with [entry] (e.g. `"inspect >= 3.1"`) added to `dependencies`.
     * If the package (the leading token of [entry]) is already listed, the text is returned unchanged.
     */
    fun addDependency(rockspecText: String, entry: String): String {
        val match = DEPENDENCIES_BLOCK.find(rockspecText) ?: return appendNewBlock(rockspecText, entry)
        val body = match.groupValues[1]
        if (containsPackage(body, packageOf(entry))) return rockspecText
        val rebuilt = "dependencies = {${insertInto(body, entry)}}"
        return rockspecText.replaceRange(match.range, rebuilt)
    }

    private fun insertInto(body: String, entry: String): String {
        val existing = body.trim().trimEnd(',')
        if (existing.isEmpty()) return " \"$entry\" "
        return if (body.contains('\n')) {
            "${body.trimEnd().trimEnd(',')},\n   \"$entry\",\n"
        } else {
            " $existing, \"$entry\" "
        }
    }

    private fun appendNewBlock(rockspecText: String, entry: String): String {
        val separator = if (rockspecText.endsWith("\n") || rockspecText.isEmpty()) "" else "\n"
        return "$rockspecText${separator}dependencies = { \"$entry\" }\n"
    }

    private fun containsPackage(body: String, name: String): Boolean =
        Regex("[\"']${Regex.escape(name)}(?=[\"' ])").containsMatchIn(body)

    private fun packageOf(entry: String): String = entry.substringBefore(' ').trim()
}
