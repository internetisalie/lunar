package net.internetisalie.lunar.rocks.browser

/**
 * The card the browser panel shows, owned by [LuaRocksBrowserModel] and mutated on the EDT only
 * (design §2.5). An honest error (unresolved binary / failed CLI) is a distinct state from an empty
 * result (a query that ran and matched nothing) — design §3.5.
 */
sealed interface BrowserState {
    /** Neutral zero-query Marketplace prompt. */
    data object Idle : BrowserState

    /** A CLI call is in flight. */
    data object Loading : BrowserState

    /** Marketplace search results (may be empty — a ran-but-matched-nothing query). */
    data class Results(val rows: List<LuaRockRow>) : BrowserState

    /** Installed-tab rows from the canonical tree. */
    data class Installed(val rows: List<InstalledRockRow>) : BrowserState

    /** An honest failure; [message] is rendered with a Configure link. */
    data class Error(val message: String) : BrowserState

    /** No project rock tree resolved; Install is disabled and the no-tree hint shows. */
    data object NoTree : BrowserState
}
