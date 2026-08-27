package net.internetisalie.lunar.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level persisted state for the rename dialog's two non-code-search checkboxes
 * (REFACT-01-15, design §2.9).
 *
 * **Why this exists rather than a delegation to the platform.**
 * `RenamePsiElementProcessorBase.isToSearchInComments` returns
 * `element instanceof PsiFileSystemItem && …` — a hard `false` for every non-file element — and
 * `setToSearchInComments` is a no-op for them (`RenamePsiElementProcessorBase.java:195-212`).
 * `RefactoringSettings` has `RENAME_SEARCH_IN_COMMENTS_FOR_FILE` but no `…_FOR_VARIABLE`
 * (`RefactoringSettings.java:22-26`), so a Lua identifier's checkbox has nothing to persist into
 * and the user's choice would be discarded between invocations.
 *
 * Both defaults are `false`: Lua's dynamic-access idioms (`_G["name"]`, `require`d module tables)
 * make a string match far likelier to be coincidental than in a statically typed language, and
 * REFACT-01-20 records that non-code search is best-effort by construction.
 *
 * Mirrors [LuaEditorOptions] — the same `@Service`/`@State`/`PersistentStateComponent` shape and
 * the same accessor, with its own `Storage` file. Holds no `Project`, `Editor` or PSI reference,
 * which an application-level service must not (engineering contract §4).
 */
@Service(Service.Level.APP)
@State(
    name = "LuaRefactoringSettings",
    storages = [Storage("lunar.refactoring.xml")],
    category = SettingsCategory.CODE,
)
class LuaRefactoringSettings : PersistentStateComponent<LuaRefactoringSettings.State> {
    class State {
        var renameSearchInComments: Boolean = false
        var renameSearchForText: Boolean = false
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var renameSearchInComments: Boolean
        get() = myState.renameSearchInComments
        set(value) {
            myState.renameSearchInComments = value
        }

    var renameSearchForText: Boolean
        get() = myState.renameSearchForText
        set(value) {
            myState.renameSearchForText = value
        }

    companion object {
        val instance: LuaRefactoringSettings
            get() = ApplicationManager.getApplication().getService(LuaRefactoringSettings::class.java)
    }
}
