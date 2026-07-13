package net.internetisalie.lunar.redis.connection

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.util.LunarCoroutineScopeService
import java.awt.BorderLayout
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Project settings page for Redis/Valkey server connections (design §2.5, §4.3, §7).
 *
 * A short-lived [Configurable] (one per settings open) presenting a [JBList] of connections plus a
 * detail form (host/port/TLS/auth/db/provisioning) and a **Test Connection** button. Swing layout runs
 * on the EDT (fast, non-blocking — engineering-contract §1); the Test Connection socket I/O runs
 * **off** the EDT on the project coroutine scope with a background progress indicator, marshalling the
 * result back via `withContext(Dispatchers.EDT)` (engineering-contract §1, §2). Secrets are held only
 * in-panel until [apply], which writes the metadata to [LuaRedisConnectionSettings] and the password to
 * [LuaRedisCredentialStore] — never to the XML.
 */
class LuaRedisConnectionsConfigurable(private val project: Project) : Configurable {

    private val model = CollectionListModel<LuaRedisConnectionDraft>()
    private val connectionList = JBList(model)
    private val form = ConnectionForm()

    private var suppressFormEvents = false
    private var rootPanel: JComponent? = null

    override fun getDisplayName(): String = "Redis Connections"

    override fun createComponent(): JComponent {
        connectionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        connectionList.cellRenderer = ConnectionCellRenderer
        connectionList.addListSelectionListener { if (!it.valueIsAdjusting) onSelectionChanged() }
        form.onEdited = ::onFormEdited
        val listComponent = ToolbarDecorator.createDecorator(connectionList)
            .setAddAction { addConnection() }
            .setRemoveAction { removeSelectedConnection() }
            .createPanel()
        val built = JPanel(BorderLayout())
        built.add(listComponent, BorderLayout.WEST)
        built.add(form.component, BorderLayout.CENTER)
        rootPanel = built
        reset()
        return built
    }

    override fun isModified(): Boolean = model.items != savedDrafts()

    override fun apply() {
        val settings = LuaRedisConnectionSettings.getInstance(project)
        val existingIds = settings.connections().map { it.id }
        val currentIds = model.items.map { it.id }
        existingIds.filterNot { it in currentIds }.forEach { removedId ->
            settings.remove(removedId)
            LuaRedisCredentialStore.setPassword(removedId, null)
        }
        model.items.forEach { draft ->
            settings.upsert(draft.toConnection())
            LuaRedisCredentialStore.setPassword(draft.id, draft.password)
        }
    }

    override fun reset() {
        model.replaceAll(savedDrafts())
        if (model.size > 0) connectionList.selectedIndex = 0 else onSelectionChanged()
    }

    private fun savedDrafts(): List<LuaRedisConnectionDraft> =
        LuaRedisConnectionSettings.getInstance(project).connections().map { connection ->
            LuaRedisConnectionDraft.from(connection, LuaRedisCredentialStore.getPassword(connection.id))
        }

    private fun addConnection() {
        val draft = LuaRedisConnectionDraft.newDefault()
        model.add(draft)
        connectionList.selectedIndex = model.size - 1
    }

    private fun removeSelectedConnection() {
        val index = connectionList.selectedIndex
        if (index < 0) return
        model.remove(index)
        connectionList.selectedIndex = (index - 1).coerceAtLeast(if (model.size > 0) 0 else -1)
    }

    private fun onSelectionChanged() {
        val draft = connectionList.selectedValue
        suppressFormEvents = true
        form.bind(draft)
        suppressFormEvents = false
    }

    private fun onFormEdited() {
        if (suppressFormEvents) return
        val index = connectionList.selectedIndex
        if (index < 0) return
        model.setElementAt(form.snapshot(model.getElementAt(index).id), index)
    }

    private fun testConnection() {
        val draft = form.snapshot(connectionList.selectedValue?.id ?: return)
        val endpoint = draft.toEndpoint()
        LunarCoroutineScopeService.getInstance(project).scope.launch {
            val outcome = withBackgroundProgress(project, "Testing Redis connection") {
                probe(endpoint)
            }
            withContext(Dispatchers.EDT) {
                warnOnFlavorMismatch(draft.id, outcome)
                reportTestOutcome(outcome)
            }
        }
    }

    /** REDIS-03 §7.3: after a successful connect, warn once if the server flavor mismatches the target. */
    private fun warnOnFlavorMismatch(connectionId: String, outcome: TestOutcome) {
        val flavor = (outcome as? TestOutcome.Success)?.flavor ?: return
        val target = LuaProjectSettings.getInstance(project).state.getTarget().platform
        LuaRedisFlavorWarning.getInstance(project).warnOnceIfMismatch(connectionId, flavor, target)
    }

    private fun reportTestOutcome(outcome: TestOutcome) {
        val host = rootPanel
        when (outcome) {
            is TestOutcome.Success ->
                Messages.showInfoMessage(host, outcome.summary, "Test Connection")
            is TestOutcome.Failure ->
                Messages.showErrorDialog(host, outcome.message, "Test Connection")
        }
    }

    /** The form's Swing controls; layout only, so it stays on the EDT. */
    private inner class ConnectionForm {
        val nameField = JBTextField(24)
        val hostField = JBTextField(18)
        val portField = JBTextField(6)
        val tlsCheckBox = JBCheckBox("Use TLS")
        val usernameField = JBTextField(18)
        val passwordField = JBPasswordField()
        val databaseField = JBTextField(4)

        var onEdited: () -> Unit = {}

        val component: JComponent = panel {
            row("Name:") { cell(nameField) }
            row("Host:") { cell(hostField) }
            row("Port:") { cell(portField) }
            row { cell(tlsCheckBox) }
            row("Username:") { cell(usernameField) }
            row("Password:") { cell(passwordField.also { it.columns = 18 }) }
            row("Database:") { cell(databaseField) }
            row { button("Test Connection") { testConnection() } }
        }.apply { installEditListeners(this) }

        fun bind(draft: LuaRedisConnectionDraft?) {
            nameField.text = draft?.name ?: ""
            hostField.text = draft?.host ?: ""
            portField.text = draft?.port?.toString() ?: ""
            tlsCheckBox.isSelected = draft?.tls ?: false
            usernameField.text = draft?.username ?: ""
            passwordField.text = draft?.password ?: ""
            databaseField.text = draft?.database?.toString() ?: ""
            component.isVisible = draft != null
        }

        fun snapshot(id: String): LuaRedisConnectionDraft =
            LuaRedisConnectionDraft(
                id = id,
                name = nameField.text.trim(),
                host = hostField.text.trim(),
                port = portField.text.trim().toIntOrNull() ?: DEFAULT_PORT,
                tls = tlsCheckBox.isSelected,
                username = usernameField.text.trim().ifEmpty { null },
                password = String(passwordField.password).ifEmpty { null },
                database = databaseField.text.trim().toIntOrNull() ?: 0,
            )

        private fun installEditListeners(target: JComponent) {
            listOf(nameField, hostField, portField, usernameField, databaseField)
                .forEach { field -> field.document.addUndoableEditListener { onEdited() } }
            passwordField.document.addUndoableEditListener { onEdited() }
            tlsCheckBox.addActionListener { onEdited() }
            target.isVisible = false
        }
    }

    private companion object {
        const val DEFAULT_PORT: Int = 6379
    }
}

/**
 * Mutable in-panel snapshot of a connection **plus its plaintext password** (design §2.5). The password
 * is held only for the settings-page lifetime; [LuaRedisConnectionsConfigurable.apply] moves it to
 * [LuaRedisCredentialStore] and never to the persisted XML.
 */
data class LuaRedisConnectionDraft(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val tls: Boolean,
    val username: String?,
    val password: String?,
    val database: Int,
) {

    fun toConnection(): LuaRedisServerConnection =
        LuaRedisServerConnection(
            id = id,
            name = name,
            host = host,
            port = port,
            tls = tls,
            database = database,
            username = username,
            provisioning = LuaRedisProvisioning.Remote,
        )

    fun toEndpoint(): RespEndpoint =
        RespEndpoint(host = host, port = port, tls = tls, database = database, username = username, password = password)

    companion object {
        fun from(connection: LuaRedisServerConnection, password: String?): LuaRedisConnectionDraft =
            LuaRedisConnectionDraft(
                id = connection.id,
                name = connection.name,
                host = connection.host,
                port = connection.port,
                tls = connection.tls,
                username = connection.username,
                password = password,
                database = connection.database,
            )

        fun newDefault(): LuaRedisConnectionDraft =
            LuaRedisConnectionDraft(
                id = UUID.randomUUID().toString(),
                name = "New Connection",
                host = "127.0.0.1",
                port = 6379,
                tls = false,
                username = null,
                password = null,
                database = 0,
            )
    }
}

/** Renders a connection list row as `name — host:port` (design §2.5 UI). */
private object ConnectionCellRenderer : SimpleListCellRenderer<LuaRedisConnectionDraft>() {
    override fun customize(
        list: JList<out LuaRedisConnectionDraft>,
        value: LuaRedisConnectionDraft?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        val draft = value ?: return
        val label = draft.name.ifBlank { "(unnamed)" }
        text = "$label — ${draft.host}:${draft.port}"
    }
}
