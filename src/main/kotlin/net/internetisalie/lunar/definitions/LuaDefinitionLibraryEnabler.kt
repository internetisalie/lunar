package net.internetisalie.lunar.definitions

import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.provision.BalloonProvisionNotifier
import net.internetisalie.lunar.toolchain.provision.LuaProvisionNotifier
import net.internetisalie.lunar.util.newProjectBackgroundTask

/**
 * The behaviour behind the definition-libraries settings page (TARGET-08-06/-07/-08), kept out of
 * the Swing layer so it can be tested headlessly — the UI is a thin shell over this.
 *
 * Owns the half of TARGET-08-07 the fetcher deliberately does not: the fetcher detects failure and
 * returns null, and *this* reports it. Splitting them keeps the fetcher a pure function and puts
 * the user-facing message where the user action was.
 */
class LuaDefinitionLibraryEnabler(
    private val project: Project,
    private val fetcher: LuaDefinitionLibraryFetcher = LuaDefinitionLibraryFetcher(),
    // Design §3.2's seam, not an inline NotificationGroupManager call: it exists so balloon text is
    // defined once and so this is assertable without a live notification bus.
    private val notifier: LuaProvisionNotifier = BalloonProvisionNotifier(),
) {

    /** One catalog entry as the settings table shows it. */
    data class Row(
        val entry: LuaDefinitionEntry,
        val enabled: Boolean,
        val fetched: Boolean,
    )

    /** Every catalog entry, with its current enabled/fetched state. Empty if the catalog is unreadable. */
    fun rows(): List<Row> {
        val enabled = LuaProjectSettings.getInstance(project).enabledDefinitionLibraries.toSet()
        val catalog = runCatching { LuaDefinitionCatalogLoader.load() }.getOrNull() ?: return emptyList()
        return catalog.libraries.map { Row(it, it.id in enabled, fetcher.cachedRoot(it) != null) }
    }

    /**
     * Persists [enabledIds] and fetches whatever is newly needed, off the EDT.
     *
     * The enable list is written **first and unconditionally**, so a library the user ticked while
     * offline stays ticked and retries on the next apply (TARGET-08-07). The provider only ever
     * registers ids whose cache exists, so a failed fetch simply contributes no root.
     */
    fun apply(enabledIds: List<String>) {
        val settings = LuaProjectSettings.getInstance(project)
        settings.setEnabledDefinitionLibrariesAndNotify(enabledIds)

        val missing = missingEntries(enabledIds)
        if (missing.isEmpty()) return
        ProgressManager.getInstance().run(
            newProjectBackgroundTask("Lua: fetching definition libraries", project) { indicator ->
                val failures = fetchAll(missing, indicator)
                // The enable list is unchanged by now, so a setter call would short-circuit; the
                // roots still have to be refreshed because the trees only just landed on disk.
                settings.notifyDefinitionRootsChanged()
                if (failures.isNotEmpty()) reportFailures(failures)
            },
        )
    }

    /** Enabled entries (plus dependencies) with no cache yet — what an apply actually has to fetch. */
    fun missingEntries(enabledIds: List<String>): List<LuaDefinitionEntry> {
        if (enabledIds.isEmpty()) return emptyList()
        val catalog = runCatching { LuaDefinitionCatalogLoader.load() }.getOrNull() ?: return emptyList()
        return catalog.withDependencies(enabledIds).filterNot { fetcher.cachedRoot(it) != null }
    }

    /** Fetches each entry, returning the ids that failed. Cancellation propagates (never a failure). */
    fun fetchAll(entries: List<LuaDefinitionEntry>, indicator: ProgressIndicator): List<String> =
        entries.mapNotNull { entry ->
            indicator.text = "Fetching ${entry.displayName}…"
            entry.id.takeIf { fetcher.ensureCached(entry, indicator) == null }
        }

    private fun reportFailures(failedIds: List<String>) {
        notifier.notify(
            project,
            "Could not fetch Lua definition libraries: ${failedIds.joinToString(", ")}. They stay " +
                "enabled and are retried the next time you apply these settings; see idea.log.",
            NotificationType.ERROR,
        )
    }

}
