package net.internetisalie.lunar.lang.insight

import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler
import net.internetisalie.lunar.lang.psi.LuaDeclarationKind
import net.internetisalie.lunar.lang.psi.LuaDeclarationSite
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.refactoring.LuaIntroduceVariableHandler

/**
 * Consolidated [RefactoringSupportProvider] for Lua.
 *
 * Supersedes the label-only provider: it keeps in-place rename for labels (REFACT-04, which owns
 * label rename — not REFACT-01, whatever this KDoc said before) and adds the Introduce Variable
 * handler (REFACT-02). Safe delete (REFACT-03) is enabled via [isSafeDeleteAvailable], which asks
 * [LuaDeclarationSite] the same question Find Usages and reference search ask, so only declaration
 * sites (locals, parameters, function names, globals, labels) are eligible.
 */
class LuaRefactoringSupportProvider : RefactoringSupportProvider() {
    /**
     * Design §3.2 — **unconditionally `false`, and that is the design, not an oversight.**
     *
     * The shipped predicate lived here and answered `true` for a file-local [LuaNameRef]. It was
     * moved verbatim into [isMemberInplaceRenameAvailable] because of Ground 3: when more than one
     * handler is renaming at a caret, `RenameHandlerRegistry.doGetRenameHandlers` deletes the first
     * map entry that is `instanceof MemberInplaceRenameHandler` and breaks
     * (`RenameHandlerRegistry.java:114-119`, over the multi-entry branch at `:111-113`). With this
     * method answering `true`, both `VariableInplaceRenameHandler` and `MemberInplaceRenameHandler`
     * claim a Lua declaration caret, so the registry deletes the member handler — the one Route B
     * needs — and the survivor is the variable handler, which commits through `renameSynthetic`
     * rather than [net.internetisalie.lunar.refactoring.rename.LuaRenameProcessor] and so never
     * moves a `---@param` tag. Measured against a running GoLand registering 34 rename handlers
     * (DR-02); with this method `false`, Route B's handler survives.
     *
     * **Do not "restore" the expression here.** Doing so re-creates exactly that deletion.
     * `testExactlyOneHandlerClaimsAFileLocalDeclarationAndItIsTheMemberHandler` (TC-02) is the
     * gate: it asserts the single selected handler is a `MemberInplaceRenameHandler`, and it
     * reddens the moment two in-place handlers claim the same caret.
     *
     * [context] is nullable because the platform derives it from
     * `file.findElementAt(editor.getCaretModel().getOffset())`
     * (`VariableInplaceRenameHandler.java:47`), which is null with the caret at end of file. It is
     * unread here; declaring it non-null would put a Kotlin null-check intrinsic on an IDE
     * availability path that can legitimately pass null.
     */
    override fun isInplaceRenameAvailable(
        element: PsiElement,
        context: PsiElement?,
    ): Boolean = false

    /**
     * Design §3.2 — the inline editor template is offered for a label, or for a **file-local**
     * declaration supplied as its [LuaNameRef] composite. Each clause is load-bearing and each has
     * its own test.
     *
     * - **`is LuaLabelName`** is REFACT-04's clause, unchanged. Gated by
     *   `testExactlyOneHandlerClaimsALabelAndItIsTheMemberHandler` (TC-11).
     * - **`is LuaNameRef`** excludes the declaration IDENTIFIER **leaf**, which the platform
     *   supplies at a usage caret, at a parameter declaration and at a `local function` name.
     *   `MemberInplaceRenameHandler.doRename` casts to `PsiNameIdentifierOwner`
     *   (`MemberInplaceRenameHandler.java:65`) and `MemberInplaceRenamer`'s constructor takes a
     *   `PsiNamedElement` (`MemberInplaceRenamer.java:63`); a leaf is a platform `LeafPsiElement`
     *   and is neither. Those carets are served by
     *   [net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler] instead (design
     *   §3.5), which is what keeps exactly one handler available per caret. Gated by
     *   `testMemberInplaceRenameIsWithheldFromAUsageCaret`.
     * - **`kindOf(...) != null`** excludes a plain read: `kindOf` of a usage leaf is null
     *   ([LuaDeclarationSite.kindOf]). This clause has nothing to do with `REFACT-07-11` — a usage
     *   caret never reaches this predicate, because the leaf it supplies fails
     *   `MemberInplaceRenameHandler.isAvailable`'s `instanceof PsiNameIdentifierOwner` gate at
     *   `:46`, which is upstream of the provider call at `:47`.
     * - **[LuaDeclarationKind.isFileLocal]** excludes globals (`REFACT-07-10`): an inline template
     *   previews the occurrences it highlights, and a Lua global is `_ENV.x`, visible in every
     *   file. Gated by `testNoInplaceHandlerClaimsAGlobalDeclaration` (TC-10) and
     *   `testMemberInplaceRenameIsWithheldFromAGlobalDeclaration`.
     * - **`?.` on `kindOf`** — `elementToRename.identifier` is `@NotNull` on the generated
     *   interface (`LuaNameRef.java:10-11`), so the safe call is on `kindOf`'s nullable result
     *   only.
     *
     * [context] is nullable for the same reason as in [isInplaceRenameAvailable]:
     * `MemberInplaceRenameHandler.isAvailable` derives it from `file.findElementAt(offset)` and
     * retries at `offset - 1` (`MemberInplaceRenameHandler.java:32-38`), either of which can be
     * null. It is unread here.
     */
    override fun isMemberInplaceRenameAvailable(
        elementToRename: PsiElement,
        context: PsiElement?,
    ): Boolean =
        elementToRename is LuaLabelName ||
            (
                elementToRename is LuaNameRef &&
                    LuaDeclarationSite.kindOf(elementToRename.identifier)?.isFileLocal == true
            )

    override fun getIntroduceVariableHandler(): RefactoringActionHandler = LuaIntroduceVariableHandler()

    override fun isSafeDeleteAvailable(element: PsiElement): Boolean = LuaDeclarationSite.kindOf(element) != null
}
