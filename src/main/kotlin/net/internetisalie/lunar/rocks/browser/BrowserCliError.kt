package net.internetisalie.lunar.rocks.browser

/**
 * Signals an honest browser CLI failure — an unresolved `luarocks` binary or a non-zero exit —
 * as distinct from an empty result (a query that ran and matched nothing) (ROCKS-16-05, design §3.5).
 *
 * The model catches this and transitions to `BrowserState.Error`, which renders the error card with
 * a Configure link — never the misleading "No packages found". Non-browser callers keep the graceful
 * empty path via the `…OrEmpty` wrappers on [LuaRocksSearchService].
 *
 * [message] carries the trimmed CLI `stderr`, or the not-configured hint when the binary is unresolved.
 */
class BrowserCliError(override val message: String) : Exception(message) {
    companion object {
        /** Surfaced by the error card when no `luarocks` binary resolves (design §3.5). */
        const val LUAROCKS_NOT_CONFIGURED: String =
            "LuaRocks is not configured. Register or bind it under " +
                "Settings | Languages & Frameworks | Lua | Toolchain."
    }
}
