package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaNameRef

/**
 * REFACT-07, design §2.3/§3.5 — the in-place rename handler for the carets at which the platform
 * hands a rename handler a Lua declaration IDENTIFIER **leaf** rather than the declaring
 * [LuaNameRef] composite: a **usage** caret (`REFACT-07-11`), a **parameter** declaration caret and
 * a **`local function` name** caret. Every platform in-place handler refuses a leaf —
 * `MemberInplaceRenameHandler.isAvailable` requires `element instanceof PsiNameIdentifierOwner`
 * (`MemberInplaceRenameHandler.java:46`), and a leaf is a platform `LeafPsiElement`, whose class
 * Lunar does not own. This handler accepts exactly those elements, normalises the leaf up to its
 * declaring [LuaNameRef] and delegates to a locally constructed `MemberInplaceRenameHandler`, so
 * the template, the renamer and the commit are the platform's own — only the class the registry
 * holds is different.
 *
 * **It implements [RenameHandler] directly and must NOT be changed into a
 * `MemberInplaceRenameHandler` subclass.** `RenameHandlerRegistry.doGetRenameHandlers` deletes the
 * first map entry that is `instanceof MemberInplaceRenameHandler` and breaks
 * (`RenameHandlerRegistry.java:111-119`) whenever the map holds more than one entry. At every caret
 * this handler serves, the platform's member handler is *not* available, so a subclass would be the
 * only member-inplace instance in the map — and therefore the one deleted. `REFACT-07-11` and
 * `REFACT-07-09` would then be silently unreachable whenever anything else is renaming at that
 * caret. Implementing the interface directly puts this class outside the loop's reach. Design
 * Alternative I records the rejected form and the itemised cost of paying for it here.
 *
 * **Availability invariant — do not widen the gate to accept a [LuaNameRef].** This handler is
 * available only for an element whose node type is `IDENTIFIER`;
 * `MemberInplaceRenameHandler` is available only for a `PsiNameIdentifierOwner`, which REFACT-07
 * grants to the [LuaNameRef] composite and not to its child leaf. **No element is both**, so the
 * two are never simultaneously available and each caret selects exactly one handler
 * (`REFACT-07-02`). Accepting a [LuaNameRef] here would put two entries in the registry's map for a
 * `local` or global declaration caret, which triggers the removal loop above and shows the
 * `Renamer` chooser popup instead of starting the template
 * (`RenameElementAction.java:111-122`). `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable`
 * is where the composite is served; this class is the leaf's half and must stay disjoint from it.
 *
 * **Threading**: [isAvailableOnDataContext] runs on the EDT for every rename-action update in every
 * language (`RenameHandlerRegistry.java:106-110`, via `RenameHandler.isRenaming`'s default at
 * `RenameHandler.java:23-25`), so what it costs there matters. Lunar's own part of it is a pure PSI
 * shape test — an element-type comparison plus [LuaDeclarationSite.kindOf], with no index and no
 * VFS. **It does resolve, and the resolution is the platform's, not this class's**:
 * `PsiElementRenameHandler.getElement(dataContext)` reads `CommonDataKeys.PSI_ELEMENT`, whose
 * editor data rule is `TargetElementUtil.findTargetElement` (design §1 Premises:
 * `TextEditorPsiDataRule.kt:63-64` → `TargetElementUtilBase.java:235-236`), which follows a
 * reference. That work is cached by the data context and is the same call
 * `VariableInplaceRenameHandler.java:34` makes in this frame for every language, so this class adds
 * no EDT load that the platform was not already paying at this caret.
 *
 * **Memory**: no field holds a `Project`, `Editor`, `PsiFile` or `VirtualFile`. The single field is
 * a companion [ThreadLocal] holding a `Boolean`, cleared on every path including the exception one.
 */
class LuaInplaceRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        if (reentrancyGuard.get()) return false
        val currentEditor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        if (!currentEditor.settings.isVariableInplaceRenameEnabled) return false
        return declaringNameRefOf(PsiElementRenameHandler.getElement(dataContext)) != null
    }

    override fun invoke(
        project: Project,
        editor: Editor?,
        file: PsiFile?,
        dataContext: DataContext?,
    ) {
        val context = dataContext ?: return
        val currentEditor = editor ?: return
        val nameRef = declaringNameRefOf(PsiElementRenameHandler.getElement(context)) ?: return
        startTemplateOn(nameRef, currentEditor, context)
    }

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext?,
    ) {
        // An inline template needs an editor; the non-editor entry point is not this handler's.
    }

    /**
     * The re-entrancy guard replaces `VariableInplaceRenameHandler`'s private `ThreadLocal`
     * (`VariableInplaceRenameHandler.java:29`), which this class inherits nothing of. It is
     * required, not defensive: `MemberInplaceRenameHandler.doRename` calls `performDialogRename`
     * at `:69` when the template does not start, and `performDialogRename` re-queries
     * `RenameHandlerRegistry.getRenameHandler(dataContext)` at `:131` — which would select this
     * handler again. The platform's flag cannot be read instead: its only public reader,
     * `getInitialName()` (`:144-147`), returns null for the empty string that `doRename`'s
     * fall-through sets by passing `initialName = null` at `:87`.
     */
    private fun startTemplateOn(
        nameRef: LuaNameRef,
        editor: Editor,
        dataContext: DataContext,
    ) {
        reentrancyGuard.set(true)
        try {
            MemberInplaceRenameHandler().doRename(nameRef, editor, dataContext)
        } finally {
            reentrancyGuard.remove()
        }
    }

    /**
     * Design §3.5 — every step excludes a specific requirement's input and none is redundant.
     *
     * `val leaf = element ?: return null` is not a formality: testing `element?.node?.elementType`
     * would not smart-cast [element] to non-null and the [LuaDeclarationSite.kindOf] call below,
     * whose parameter is a non-null `PsiElement`, would not compile. The explicit `IDENTIFIER` test
     * is not subsumed by `kindOf` either — `kindOf` answers `LABEL` for a `LuaLabelName` composite
     * *before* it tests the element type, and `LABEL` is file-local, so without this test a label
     * would reach the final cast and be stopped only by accident, putting this handler in the map
     * beside the platform's for `REFACT-07-13`'s input. `as?` rather than `as` asserts
     * `kindOf`'s own invariant — every kind this feature is in scope for is classified through a
     * [LuaNameRef] parent — and is what would exclude a numeric-`for` leaf handed in directly; it
     * is **not** what keeps `REFACT-07-14` out of the template, since the data context supplies no
     * element at all at that caret.
     */
    private fun declaringNameRefOf(element: PsiElement?): LuaNameRef? {
        val leaf = element ?: return null
        if (leaf.node?.elementType != LuaElementTypes.IDENTIFIER) return null
        if (LuaDeclarationSite.kindOf(leaf)?.isFileLocal != true) return null
        return leaf.parent as? LuaNameRef
    }

    private companion object {
        val reentrancyGuard: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
    }
}
