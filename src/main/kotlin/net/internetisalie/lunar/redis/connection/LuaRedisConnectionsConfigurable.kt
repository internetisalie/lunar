package net.internetisalie.lunar.redis.connection

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.CollectionListModel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.internetisalie.lunar.redis.resp.RespEndpoint
import net.internetisalie.lunar.settings.LuaProjectSettings
import net.internetisalie.lunar.toolchain.model.LuaToolKind
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.util.LunarCoroutineScopeService
import java.util.UUID
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * Project settings page for Redis/Valkey server connections (design §2.5, §4.3, §7).
 *
 * A short-lived [Configurable] (one per settings open) presenting a [JBList] of connections plus a
 * detail form (host/port/TLS/auth/db/provisioning) and a **Test Connection** button. The provisioning
 * control is BUG-381 step 2; before it, this KDoc claimed a control that had never existed. Swing layout runs
 * on the EDT (fast, non-blocking — engineering-contract §1); the Test Connection socket I/O runs
 * **off** the EDT on the project coroutine scope with a background progress indicator, marshalling the
 * result back via `withContext(Dispatchers.EDT)` (engineering-contract §1, §2). Secrets are held only
 * in-panel until [apply], which writes the metadata to [LuaRedisConnectionSettings] and the password to
 * [LuaRedisCredentialStore] — never to the XML.
 */
class LuaRedisConnectionsConfigurable(
    private val project: Project,
) : Configurable {
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
        val listComponent =
            ToolbarDecorator
                .createDecorator(connectionList)
                .setAddAction { addConnection() }
                .setRemoveAction { removeSelectedConnection() }
                .createPanel()
        // BUG-448 #4: BorderLayout.WEST hands the master list its *preferred* width and never
        // stretches it, which left the page filling 35% of the content area against a native
        // comparator's 95%. A splitter is the platform's master-detail idiom and resizes with the
        // page (engineering contract §6).
        val built = OnePixelSplitter(false, LIST_PROPORTION)
        built.firstComponent = listComponent
        built.secondComponent = form.component
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
            val outcome =
                withBackgroundProgress(project, "Testing Redis connection") {
                    probe(endpoint)
                }
            withContext(Dispatchers.EDT) {
                warnOnFlavorMismatch(draft.id, outcome)
                reportTestOutcome(outcome)
            }
        }
    }

    /** REDIS-03 §7.3: after a successful connect, warn once if the server flavor mismatches the target. */
    private fun warnOnFlavorMismatch(
        connectionId: String,
        outcome: TestOutcome,
    ) {
        val flavor = (outcome as? TestOutcome.Success)?.flavor ?: return
        val target =
            LuaProjectSettings
                .getInstance(project)
                .state
                .getTarget()
                .platform
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

        /**
         * BUG-381 step 2 — the control that makes `LocalBinary` and `Docker` provisioning reachable.
         * The launcher, persistence and both consumers have handled all three kinds since REDIS-01;
         * this form was the only thing standing between them and a user.
         */
        val provisioningCombo = ComboBox(ProvisioningKind.entries.toTypedArray())

        /**
         * Filled from [LuaRedisProvisioning.SERVER_TOOL_KIND_IDS] — the Redis module's own list of
         * binaries that speak RESP — and rendered with each kind's registry display name.
         */
        val toolKindCombo =
            ComboBox(serverToolKinds().toTypedArray()).apply {
                renderer = SimpleListCellRenderer.create("") { it.displayName }
            }

        val dockerImageField = JBTextField(18)

        var onEdited: () -> Unit = {}

        private lateinit var toolKindRow: Row
        private lateinit var dockerImageRow: Row

        val component: JComponent =
            panel {
                row("Name:") { cell(nameField) }
                row("Server:") { cell(provisioningCombo) }
                toolKindRow = row("Server binary:") { cell(toolKindCombo) }
                dockerImageRow = row("Docker image:") { cell(dockerImageField) }
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
            bindProvisioning(draft?.provisioning ?: LuaRedisProvisioning.Remote)
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
                provisioning = selectedProvisioning(),
            )

        private fun bindProvisioning(provisioning: LuaRedisProvisioning) {
            // The image is seeded for EVERY kind, not only Docker: switching the combo does not
            // re-bind, so a field left blank here is the empty box a user meets on picking Docker —
            // and one left holding the *previous* connection's image is worse. Found in the VNC
            // pass; `selectedProvisioning` already treats blank as the default, so this is the
            // visible half of a rule the model side already had.
            dockerImageField.text = (provisioning as? LuaRedisProvisioning.Docker)?.image ?: DEFAULT_DOCKER_IMAGE
            when (provisioning) {
                is LuaRedisProvisioning.LocalBinary -> {
                    provisioningCombo.item = ProvisioningKind.LOCAL_BINARY
                    toolKindCombo.item = serverToolKinds().firstOrNull { it.id == provisioning.toolKindId }
                }
                is LuaRedisProvisioning.Docker -> provisioningCombo.item = ProvisioningKind.DOCKER
                is LuaRedisProvisioning.Remote -> provisioningCombo.item = ProvisioningKind.REMOTE
            }
            syncProvisioningRows()
        }

        private fun selectedProvisioning(): LuaRedisProvisioning =
            when (provisioningCombo.item) {
                ProvisioningKind.LOCAL_BINARY ->
                    LuaRedisProvisioning.LocalBinary(toolKindCombo.item?.id ?: DEFAULT_SERVER_KIND_ID)
                ProvisioningKind.DOCKER ->
                    LuaRedisProvisioning.Docker(dockerImageField.text.trim().ifEmpty { DEFAULT_DOCKER_IMAGE })
                else -> LuaRedisProvisioning.Remote
            }

        /**
         * Shows the row the selected kind needs, and **disables Host and Port for every kind but
         * Remote**.
         *
         * That last part is not cosmetic. `LuaRedisServerLauncher.launchBinary` and `launchDocker`
         * each call `allocatePort()` and return `127.0.0.1`, and `LuaRedisRunProfileState.openClient`
         * prefers those over the connection's — so for an ephemeral server these two fields are
         * input the plugin silently ignores. Leaving them editable invites a user to set a port and
         * then wonder why the server is somewhere else.
         */
        private fun syncProvisioningRows() {
            val kind = provisioningCombo.item ?: ProvisioningKind.REMOTE
            toolKindRow.visible(kind == ProvisioningKind.LOCAL_BINARY)
            dockerImageRow.visible(kind == ProvisioningKind.DOCKER)
            val remote = kind == ProvisioningKind.REMOTE
            hostField.isEnabled = remote
            portField.isEnabled = remote
        }

        private fun installEditListeners(target: JComponent) {
            listOf(nameField, hostField, portField, usernameField, databaseField, dockerImageField)
                .forEach { field -> field.document.addUndoableEditListener { onEdited() } }
            passwordField.document.addUndoableEditListener { onEdited() }
            tlsCheckBox.addActionListener { onEdited() }
            toolKindCombo.addActionListener { onEdited() }
            provisioningCombo.addActionListener {
                syncProvisioningRows()
                onEdited()
            }
            syncProvisioningRows()
            target.isVisible = false
        }
    }

    private companion object {
        const val DEFAULT_PORT: Int = 6379

        /** Master/detail split, matching the platform's own list-plus-form settings pages. */
        const val LIST_PROPORTION: Float = 0.3f

        /** Mirrors `LuaRedisConnectionSettings.provisioningOf`'s fallbacks, so a blank field agrees with a blank XML. */
        const val DEFAULT_SERVER_KIND_ID: String = "redis-server"
        const val DEFAULT_DOCKER_IMAGE: String = "redis:8"

        /** The RESP-speaking kinds, resolved to registry entries for their display names. */
        fun serverToolKinds(): List<LuaToolKind> {
            val registered = LuaToolKindRegistry.all()
            return LuaRedisProvisioning.SERVER_TOOL_KIND_IDS.mapNotNull { id ->
                registered.firstOrNull { it.id == id }
            }
        }
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
    val provisioning: LuaRedisProvisioning,
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
            provisioning = provisioning,
        )

    fun toEndpoint(): RespEndpoint =
        RespEndpoint(host = host, port = port, tls = tls, database = database, username = username, password = password)

    companion object {
        fun from(
            connection: LuaRedisServerConnection,
            password: String?,
        ): LuaRedisConnectionDraft =
            LuaRedisConnectionDraft(
                id = connection.id,
                name = connection.name,
                host = connection.host,
                port = connection.port,
                tls = connection.tls,
                username = connection.username,
                password = password,
                database = connection.database,
                provisioning = connection.provisioning,
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
                provisioning = LuaRedisProvisioning.Remote,
            )
    }
}

/**
 * BUG-381 — the three provisioning kinds as the Server combo offers them.
 *
 * Separate from [LuaRedisProvisioning] because that sealed interface carries each kind's *parameter*
 * (`toolKindId`, `image`) and a combo item must not: the user picks Docker before typing an image,
 * and a combo of half-built model values is a combo whose selection can be invalid.
 */
private enum class ProvisioningKind(
    private val label: String,
) {
    REMOTE("Remote server"),
    LOCAL_BINARY("Local binary"),
    DOCKER("Docker image"),
    ;

    override fun toString(): String = label
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
