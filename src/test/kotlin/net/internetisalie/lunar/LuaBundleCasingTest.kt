package net.internetisalie.lunar

import org.junit.Test
import java.util.ResourceBundle
import kotlin.test.assertTrue

private val CAPITALIZED_WORD = Regex("^[A-Z][a-z]+$")
private val WHITESPACE = Regex("\\s+")

/**
 * Product names that are allowed to keep their own capitalization inside an otherwise
 * sentence-case control label (engineering-contract.md §6 "CASE"). CamelCase names such as
 * `LuaRocks`/`StyLua`/`LuaJIT`/`LuaCov` never match [CAPITALIZED_WORD] to begin with — an internal
 * capital breaks the `[A-Z][a-z]+` shape — so only the plain, single-capital product names need to
 * be listed explicitly here.
 */
private val ALLOWED_PRODUCT_WORDS = setOf("Lua", "Busted", "Tarantool")

/**
 * BUG-448 finding #1 / engineering-contract.md §6 "CASE": control labels, checkboxes and group
 * titles must be sentence case (`Advanced tools`, not `Advanced Tools`); only product names keep
 * their own casing. This pins that rule against every value in `LuaBundle.properties` so finding
 * #1 (`Enable Type Inference`, `Add Additional Completions`) cannot silently come back.
 *
 * A handful of existing keys are deliberately excluded because they are not settings-panel control
 * labels — each exclusion is evidence-backed, not a guess:
 *  - `color.*` — Colors & Fonts attribute descriptors use the platform's own
 *    `Category//Description` schema (see `color.tailcall=Tail Call Returns`), a different, older
 *    convention this test does not police.
 *  - `problems.action.toggleUsagePreview` — action/toolbar text, where Title Case is the platform's
 *    own convention (distinct from row/checkbox labels); also currently unused
 *    (`grep -rn "toggleUsagePreview" src/main` has no call site).
 *  - `application.interpreters.*` — dead keys: the interpreters table they described was removed by
 *    TOOLING-05 (`grep -rn "interpreters\\." src/main --include=*.kt` finds no reader).
 *  - `lua.type.hints.name` — names the Inlay Hints settings GROUP shown in the Preferences tree
 *    (wired via `plugin.xml`'s `nameKey`), not a row inside a hand-built panel.
 *  - `toolwindow.matrix.displayName` — a tool-window STRIPE TITLE (BUG-448 #21, already fixed).
 *    Window titles follow the platform's own Title Case convention (`Version Control`, `Structure`),
 *    a different UI element from the panel rows/checkboxes this rule targets.
 */
class LuaBundleCasingTest {
    @Test
    fun noControlLabelIsTitleCase() {
        val bundle = ResourceBundle.getBundle("net.internetisalie.lunar.LuaBundle")
        val offenders =
            bundle
                .keySet()
                .filterNot(::isOutOfScope)
                .associateWith { key -> bundle.getString(key) }
                .filterValues(::isTitleCase)
        assertTrue(
            offenders.isEmpty(),
            "Control labels must be sentence case, not Title Case (engineering-contract.md §6): $offenders",
        )
    }

    private fun isTitleCase(value: String): Boolean {
        val genericWords = value.trim().split(WHITESPACE).filterNot { it in ALLOWED_PRODUCT_WORDS }
        return genericWords.size >= 2 && genericWords.all { CAPITALIZED_WORD.matches(it) }
    }

    private fun isOutOfScope(key: String): Boolean =
        key.startsWith("color.") || key.startsWith("application.interpreters.") || key in OUT_OF_SCOPE_KEYS

    private companion object {
        val OUT_OF_SCOPE_KEYS =
            setOf(
                "problems.action.toggleUsagePreview",
                "lua.type.hints.name",
                "toolwindow.matrix.displayName",
            )
    }
}
