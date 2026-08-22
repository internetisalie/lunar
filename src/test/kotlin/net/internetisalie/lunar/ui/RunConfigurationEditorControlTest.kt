package net.internetisalie.lunar.ui

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.redis.run.LuaRedisSettingsEditor
import net.internetisalie.lunar.run.test.LuaTestFramework
import net.internetisalie.lunar.run.test.LuaTestSettingsEditor
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.comboLabelled
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.itemsOf
import net.internetisalie.lunar.ui.RunConfigurationEditorProbe.renderedText

/**
 * TC-BUG-448-15 / TC-BUG-448-18: a combo shows the user a word, not the identifier it stores, and
 * the one combo that has to display arbitrary text is wide enough to.
 *
 * The values asserted are **rendered** ones — what the combo's renderer produces for a model item —
 * paired with an assertion that the *stored* items are unchanged. That pairing is the fix:
 * `testTargetType` still persists as `FILE`, and renaming it would silently discard saved
 * configurations. `test the target type combo still stores the uppercase keys` exists so a future
 * "simplification" that renames the model instead of adding a renderer fails here.
 *
 * **What this cannot assert:** how anything looks once painted. `Connection` is checked through
 * `getPreferredSize`, which `ComboBox` answers straight from `setMinimumAndPreferredWidth` with no
 * window needed — and which is the number that decides the rendered width here, because
 * `FormBuilder.getFill` gives a `JComboBox` `GridBagConstraints.NONE` and so never stretches it to
 * fill the column, unlike the text fields beside it.
 */
class RunConfigurationEditorControlTest : BasePlatformTestCase() {
    fun `test the framework combo renders product names, not enum constants`() {
        val combo = comboLabelled("Test framework:", testEditor())

        assertEquals(
            listOf("Busted", "Lunity"),
            LuaTestFramework.entries.map { renderedText(combo, it) },
        )
    }

    fun `test the target type combo renders words, not stored keys`() {
        val combo = comboLabelled("Target type:", testEditor())

        assertEquals(
            listOf("File", "Directory", "Pattern"),
            itemsOf(combo).map { renderedText(combo, it) },
        )
    }

    fun `test the target type combo still stores the uppercase keys`() {
        val combo = comboLabelled("Target type:", testEditor())

        assertEquals(
            "renaming a persisted value would silently discard saved configurations",
            listOf("FILE", "DIRECTORY", "PATTERN"),
            itemsOf(combo),
        )
    }

    fun `test the connection combo is not the narrowest control on its page`() {
        val editor = redisEditor()
        val connection = comboLabelled("Connection:", editor)
        val execMode = comboLabelled("Execution mode:", editor)

        assertTrue(
            "Connection holds arbitrary text and was measured at 72px beside 360px siblings; " +
                "it is ${connection.preferredSize.width}px against Execution mode's " +
                "${execMode.preferredSize.width}px",
            connection.preferredSize.width > execMode.preferredSize.width,
        )
    }

    private fun testEditor(): SettingsEditor<*> = register(LuaTestSettingsEditor(project))

    private fun redisEditor(): SettingsEditor<*> = register(LuaRedisSettingsEditor(project))

    private fun <T : SettingsEditor<*>> register(editor: T): T {
        Disposer.register(testRootDisposable, editor)
        return editor
    }
}
