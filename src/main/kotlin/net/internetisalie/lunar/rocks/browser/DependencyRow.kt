package net.internetisalie.lunar.rocks.browser

/**
 * One clickable dependency row in the detail pane (ROCKS-16-07, design §4.2). [raw] is the
 * `"<name> <constraint>"` string from `luarocks show`; [packageName] is the leading token used for
 * the click-to-search action (absorbs BUG-368's `\n`-joined deps).
 */
data class DependencyRow(val raw: String) {
    val packageName: String get() = raw.substringBefore(' ').trim()
    override fun toString(): String = raw
}
