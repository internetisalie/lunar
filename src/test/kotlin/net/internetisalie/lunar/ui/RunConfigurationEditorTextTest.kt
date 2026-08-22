package net.internetisalie.lunar.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.RawCommandLineEditor
import net.internetisalie.lunar.redis.run.LuaRedisSettingsEditor
import net.internetisalie.lunar.rocks.run.LuaRocksRunSettingsEditor
import net.internetisalie.lunar.run.LuaRunSettingsEditor
import net.internetisalie.lunar.run.test.LuaTestSettingsEditor
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.checkBoxesOf
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.controlLabelled
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.formLabelsOf
import javax.swing.JLabel

/**
 * TC-BUG-448-11 / TC-BUG-448-16 / TC-BUG-448-17 / TC-BUG-448-19 / TC-BUG-448-20: the four
 * run-configuration editors word their rows the way the platform words its own `Name:` row in the
 * same dialog — colon, mnemonic, sentence case, and no implementation detail on display.
 *
 * The audit measured **27 of 27** labelled rows with neither colon nor mnemonic, against a native
 * Go Build editor that underlines 10 of 10.
 *
 * **What this cannot assert:** that a mnemonic underline actually *paints*, or that Alt+the letter
 * moves focus. Both are look-and-feel outcomes of a rendered, focused window; the unit gate covers
 * the model (`displayedMnemonic`, `labelFor`) that the painting reads from.
 *
 * **Why `addMnemonicLabeledComponent` exists** is pinned by `test no label leaks the mnemonic
 * escape character`. `FormBuilder`'s `String` overload rewrites `&R` to U+001B followed by `R` and
 * hands that to a bare `JLabel`, which sets no mnemonic and leaves the control character in the
 * visible text — measured on the GoLand 2026.1 test platform. Reverting a row to that overload does
 * not merely lose the underline, it corrupts the label; and because that overload still sets
 * `labelFor`, such a row is still collected here rather than vanishing from the assertion.
 */
class RunConfigurationEditorTextTest : BasePlatformTestCase() {
    fun `test every labelled row ends its label with a colon`() {
        val offenders = allFormLabels().filterNot { it.text.endsWith(":") }

        assertEquals(
            "every leading label ends in a colon (engineering contract §6)",
            emptyList<String>(),
            offenders.map { it.text },
        )
    }

    fun `test every labelled row carries a mnemonic`() {
        val offenders = allFormLabels().filter { it.displayedMnemonic == 0 }

        assertEquals(
            "every leading label carries a mnemonic (engineering contract §6)",
            emptyList<String>(),
            offenders.map { it.text },
        )
    }

    fun `test no label leaks the mnemonic escape character`() {
        val offenders = allFormLabels().filter { MNEMONIC_ESCAPE in it.text }

        assertEquals(
            "FormBuilder's String overload leaves U+001B in the text and sets no mnemonic",
            emptyList<String>(),
            offenders.map { label -> label.text.map { it.code } },
        )
    }

    fun `test no label carries a parenthetical hint`() {
        val offenders = allFormLabels().filter { "(" in it.text }

        assertEquals(
            "format hints belong in emptyText, not in the label (engineering contract §6)",
            emptyList<String>(),
            offenders.map { it.text },
        )
    }

    fun `test mnemonics are unique inside each editor`() {
        val collisions = editors().mapNotNull { (name, editor) -> collisionIn(name, editor) }

        assertEquals("two rows sharing a mnemonic leave one unreachable", emptyList<String>(), collisions)
    }

    fun `test the environment field is labelled identically in every editor`() {
        val labels =
            editors()
                .flatMap { (_, editor) -> formLabelsOf(editor).map { it.text } }
                .filter { it.contains("Environment", ignoreCase = true) }
                .distinct()

        assertEquals(
            "LuaRocks said 'Environment' where the other editors said 'Environment variables'",
            listOf("Environment variables:"),
            labels,
        )
    }

    fun `test the editors between them still expose every labelled row the audit counted`() {
        assertEquals(
            "a row silently dropped from a form would make every 'no offender' assertion above vacuous",
            AUDITED_ROW_COUNT,
            allFormLabels().size,
        )
    }

    fun `test no checkbox label carries parenthetical detail`() {
        val offenders = checkBoxesOf(redisEditor()).map { it.text }.filter { "(" in it }

        assertEquals(
            "explanation belongs in comment()/emptyText (engineering contract §6)",
            emptyList<String>(),
            offenders,
        )
    }

    fun `test no checkbox label is a protocol keyword`() {
        val offenders =
            checkBoxesOf(redisEditor())
                .map { it.text }
                .filter { text -> PROTOCOL_KEYWORDS.any { it in text } }

        assertEquals("protocol keywords must not reach the user", emptyList<String>(), offenders)
    }

    fun `test every checkbox carries a mnemonic`() {
        val offenders = checkBoxesOf(redisEditor()).filter { it.mnemonic == 0 }

        assertEquals(emptyList<String>(), offenders.map { it.text })
    }

    fun `test the KEYS and ARGV fields carry their format hint as placeholder text`() {
        val editor = redisEditor()

        assertEquals(
            listOf("Space-separated", "Space-separated"),
            listOf("KEYS:", "ARGV:").map { placeholderOf(it, editor) },
        )
    }

    private fun allFormLabels(): List<JLabel> = editors().flatMap { (_, editor) -> formLabelsOf(editor) }

    private fun editors(): List<Pair<String, SettingsEditor<*>>> =
        listOf(
            "Lua" to register(LuaRunSettingsEditor(project)),
            "Lua Tests" to register(LuaTestSettingsEditor(project)),
            "LuaRocks" to register(LuaRocksRunSettingsEditor(project)),
            "Redis Script" to register(LuaRedisSettingsEditor(project)),
        )

    private fun redisEditor(): SettingsEditor<*> = register(LuaRedisSettingsEditor(project))

    private fun <T : SettingsEditor<*>> register(editor: T): T {
        Disposer.register(testRootDisposable, editor)
        return editor
    }

    private companion object {
        const val MNEMONIC_ESCAPE = '\u001B'

        /** 8 Lua + 7 Lua Tests + 5 LuaRocks + 7 Redis Script, as counted in the BUG-448 audit. */
        const val AUDITED_ROW_COUNT = 27

        val PROTOCOL_KEYWORDS = listOf("REPLACE", "EVAL", "EVALSHA", "FCALL", "FUNCTION LOAD")

        fun collisionIn(
            editorName: String,
            editor: SettingsEditor<*>,
        ): String? {
            val counted = formLabelsOf(editor).map { it.displayedMnemonic }.groupingBy { it }.eachCount()
            val duplicated = counted.filterValues { it > 1 }.keys
            return if (duplicated.isEmpty()) null else "$editorName: ${duplicated.map { it.toChar() }}"
        }

        fun placeholderOf(
            labelText: String,
            editor: SettingsEditor<*>,
        ): String = (controlLabelled(labelText, editor) as RawCommandLineEditor).editorField.emptyText.text
    }
}
