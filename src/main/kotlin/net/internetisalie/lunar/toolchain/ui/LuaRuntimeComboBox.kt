package net.internetisalie.lunar.toolchain.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SimpleTextAttributes
import net.internetisalie.lunar.lang.LuaIcons
import net.internetisalie.lunar.toolchain.model.LuaRegisteredTool
import net.internetisalie.lunar.toolchain.model.LuaToolHealth
import net.internetisalie.lunar.toolchain.model.Origin
import net.internetisalie.lunar.toolchain.model.isUsable
import net.internetisalie.lunar.toolchain.registry.LuaToolKindRegistry
import net.internetisalie.lunar.toolchain.model.Capability
import net.internetisalie.lunar.toolchain.probe.LuaToolProbe
import net.internetisalie.lunar.toolchain.registry.LuaToolchainRegistry
import net.internetisalie.lunar.toolchain.resolve.LuaToolResolver
import java.nio.file.Path
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

private const val LUA_KIND_ID = "lua"

/**
 * TOOLING-05 §2.5/§3.1. Wires a RUNTIME-tool [ComboBox] for the run/test/wizard editors,
 * replacing the legacy interpreter combo customizer. The model lists the project-resolved
 * default plus every usable RUNTIME inventory entry; a typed path not in the registry becomes a
 * background-probed ad-hoc entry while the default stays listed (the load-bearing ROCKS-16
 * subtlety). Ad-hoc entries are never auto-registered.
 */
object LuaRuntimeComboBox {

    fun customize(project: Project, field: ComboBox<LuaRegisteredTool>) {
        val default = LuaToolResolver.getInstance().resolveRuntime(project)
        val binder = ComboBinder(field, default)

        field.model = binder.buildModel(typed = null)
        field.renderer = LuaRuntimeListCellRenderer()
        field.isEditable = true
        // The editor must stringify a tool to its PATH (the tool identity the editor round-trips on):
        // then configureEditor's setText matches the typed text and never re-enters during a
        // document notification (avoids "Attempt to mutate in notification").
        field.editor = PathComboBoxEditor()
        field.item = default

        val editorComponent = field.editor.editorComponent
        if (editorComponent is JTextField) {
            editorComponent.document.addDocumentListener(binder.documentListener(editorComponent))
        }
    }

    /** Holds the invariant state (the field + its default) so the helpers stay within the arg cap. */
    private class ComboBinder(
        private val field: ComboBox<LuaRegisteredTool>,
        private val default: LuaRegisteredTool?
    ) {
        fun buildModel(typed: LuaRegisteredTool?): DefaultComboBoxModel<LuaRegisteredTool> {
            val byPath = LinkedHashMap<String, LuaRegisteredTool>()
            listOfNotNull(typed, default).forEach { byPath.putIfAbsent(it.path, it) }
            usableRuntimeTools().forEach { byPath.putIfAbsent(it.path, it) }
            return DefaultComboBoxModel(byPath.values.toTypedArray())
        }

        fun documentListener(editorComponent: JTextField): DocumentAdapter =
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = onTextChanged(editorComponent.text)
            }

        private fun onTextChanged(text: String) {
            if (text.isEmpty()) {
                field.selectedItem = null
                return
            }
            existingByPath(text)?.let {
                field.selectedItem = it
                return
            }
            val adHoc = adHocTool(text)
            field.model = buildModel(typed = adHoc)
            field.selectedItem = adHoc
            probeInBackground(adHoc)
        }

        private fun existingByPath(text: String): LuaRegisteredTool? =
            (0 until field.model.size)
                .mapNotNull { field.model.getElementAt(it) }
                .firstOrNull { it.path == text }

        private fun probeInBackground(adHoc: LuaRegisteredTool) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val kind = LuaToolKindRegistry.findById(LUA_KIND_ID) ?: return@executeOnPooledThread
                val result = LuaToolProbe.getInstance().probe(kind, Path.of(adHoc.path))
                val upgraded = adHoc.copy(
                    version = result.version,
                    luaVersion = result.luaVersion,
                    runtime = result.runtime,
                    health = adHoc.health.copy(probeOk = result.ok, reason = result.failure)
                )
                ApplicationManager.getApplication().invokeLater { applyProbe(upgraded) }
            }
        }

        private fun applyProbe(upgraded: LuaRegisteredTool) {
            if (existingByPath(upgraded.path)?.path != upgraded.path) return
            field.model = buildModel(typed = upgraded)
            field.selectedItem = upgraded
        }
    }
}

/**
 * Editor that DISPLAYS a [LuaRegisteredTool] as its path (so `configureEditor`'s setText matches
 * the typed text and never re-enters during a document notification), while still returning the
 * tool object from [getItem] when the text is unchanged (mirrors `BasicComboBoxEditor`'s
 * object-round-trip, keyed on the tool's path as its stable string form).
 */
private class PathComboBoxEditor : BasicComboBoxEditor() {
    private var storedTool: LuaRegisteredTool? = null

    override fun setItem(anObject: Any?) {
        val tool = anObject as? LuaRegisteredTool
        storedTool = tool
        val display = tool?.path ?: (anObject as? String).orEmpty()
        // Never re-write identical text: setText locks the document, which throws
        // "Attempt to mutate in notification" when this runs inside a DocumentListener.
        if (editor.text != display) editor.text = display
    }

    override fun getItem(): Any? {
        val text = editor.text
        if (text.isEmpty()) return null
        val tool = storedTool
        return if (tool != null && tool.path == text) tool else adHocTool(text)
    }
}

private fun usableRuntimeTools(): List<LuaRegisteredTool> {
    val runtimeKindIds = LuaToolKindRegistry.all()
        .filter { Capability.RUNTIME in it.capabilities }
        .map { it.id }
        .toSet()
    return LuaToolchainRegistry.getInstance().tools()
        .filter { it.kindId in runtimeKindIds && it.isUsable }
}

private fun adHocTool(path: String): LuaRegisteredTool =
    LuaRegisteredTool(
        id = UUID.randomUUID().toString(),
        kindId = LUA_KIND_ID,
        path = path,
        version = null,
        luaVersion = null,
        runtime = null,
        origin = Origin.MANUAL,
        environmentId = null,
        health = LuaToolHealth(fileExists = true, executable = true, probeOk = null, probedAtMtime = null, reason = null)
    )

/** Renders a RUNTIME tool: path in bold + `product version` in gray (unusable → "Invalid"). */
private class LuaRuntimeListCellRenderer : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(
        list: javax.swing.JList<out Any?>,
        value: Any?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean
    ) {
        when (value) {
            null -> append("No interpreter selected", SimpleTextAttributes.ERROR_ATTRIBUTES)
            is LuaRegisteredTool -> renderTool(value)
            else -> {
                append("Unknown Interpreter ", SimpleTextAttributes.ERROR_ATTRIBUTES)
                append(value.toString(), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
            }
        }
    }

    private fun renderTool(tool: LuaRegisteredTool) {
        append(tool.path, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
        when {
            !tool.isUsable -> {
                append(" Invalid", SimpleTextAttributes.ERROR_ATTRIBUTES)
                icon = AllIcons.General.Error
            }
            tool.health.probeOk == null && tool.runtime == null -> {
                append(" Unknown", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                icon = AllIcons.Nodes.Unknown
            }
            else -> {
                append(" ${runtimeLabel(tool)}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                icon = LuaIcons.FILE
            }
        }
    }

    private fun runtimeLabel(tool: LuaRegisteredTool): String {
        val product = tool.runtime?.product ?: "Lua"
        val version = tool.runtime?.version ?: tool.version ?: ""
        return "$product $version".trim()
    }
}
