package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * NAV-13-08 — <kbd>Shift+F6</kbd> at a colon call site changes **which rename handler the registry
 * selects**, and `LuaInplaceRenameHandler` names no resolve API at all: it is *handed* the resolved
 * element by `CommonDataKeys.PSI_ELEMENT`'s editor data rule, which follows the caret's reference.
 * That is why `design.md` §7's derivation has a **receive** half.
 *
 * Its gate `declaringNameRefOf` (`:130-135`) tests
 * `LuaDeclarationSite.kindOf(leaf)?.isFileLocal != true` on whatever the platform resolved. Before
 * this feature that was a `LOCAL_FUNCTION` leaf (`isFileLocal=true`) and an inline template started;
 * now it is a `METHOD_FUNCTION` leaf (`isFileLocal=false`) and the handler declines, so
 * `PsiElementRenameHandler` and the colon-method refusal take over.
 *
 * **The pre-feature template was worse than a mis-targeted rename.** Committing `RENAMED` into it
 * rewrote *every* `m` in the file — the local function's declaration, the method's own declaration,
 * the call site and the plain call — from a caret the user put on a **method call**.
 *
 * **No case here injects `CommonDataKeys.PSI_ELEMENT` into a data context.** The context comes from
 * `DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` and nothing is added
 * to it, so `PsiElementRenameHandler.getElement` returns what `TargetElementUtil` computes rather
 * than what this case assumed — a `SimpleDataContext` carrying an injected element would assert the
 * case away. `LuaInplaceRenameTest`'s class KDoc records that trap.
 *
 * **The assertions are on the handler type, never on the platform's class name**, so they survive a
 * platform that adds a second fallback handler.
 *
 * Covers `requirements.md` case 31.
 */
@RunWith(JUnit4::class)
class LuaColonCallRenameHandlerTest : BasePlatformTestCase() {
    /**
     * **Mutation** (`requirements.md` #1): delete the colon branch from
     * `LuaNameReference.multiResolve` — `isAvailableOnDataContext` becomes `true`, the registry
     * offers `[LuaInplaceRenameHandler]`, and an inline template starts on `range=(95,96) text='m'`.
     */
    @Test
    fun theInplaceHandlerDeclinesAColonCallSite() {
        configureAtColonCallSite()

        assertFalse(
            "the platform now hands the handler a METHOD_FUNCTION leaf, which its isFileLocal gate declines",
            LuaInplaceRenameHandler().isAvailableOnDataContext(contextAtCaret()),
        )
    }

    /**
     * The registry layer. A predicate answering `false` is not the same as the registry choosing a
     * different handler, and only this layer can see the latter — which is what the user gets.
     *
     * **Mutation**: as above — the offered list becomes `[LuaInplaceRenameHandler]`.
     */
    @Test
    fun theRegistryOffersNoInplaceHandlerAtAColonCallSite() {
        configureAtColonCallSite()

        val offered = RenameHandlerRegistry.getInstance().getRenameHandlers(contextAtCaret())

        assertTrue("Shift+F6 must still find some handler", offered.isNotEmpty())
        assertTrue(
            "no offered handler may be a LuaInplaceRenameHandler: ${offered.map { it.javaClass.simpleName }}",
            offered.none { it is LuaInplaceRenameHandler },
        )
    }

    /**
     * The document layer. Driving the handler the registry actually selects is what shows the user's
     * outcome: the refactoring refuses and no template exists, so the file cannot be rewritten.
     *
     * **Mutation**: as above — a template starts and committing it rewrites four occurrences.
     */
    @Test
    fun invokingTheOfferedHandlerRefusesAndStartsNoTemplate() {
        configureAtColonCallSite()
        val offered = RenameHandlerRegistry.getInstance().getRenameHandlers(contextAtCaret())
        val handler = requireNotNull(offered.firstOrNull()) { "the registry offered no handler" }

        val disposable = Disposer.newDisposable()
        try {
            TemplateManagerImpl.setTemplateTesting(disposable)
            assertRefused(handler)
            assertNull(
                "no in-place template may start at a colon call site",
                TemplateManagerImpl.getTemplateState(myFixture.editor),
            )
        } finally {
            Disposer.dispose(disposable)
        }
        assertEquals("and the file is byte-identical afterwards", HV8_FIXTURE, myFixture.file.text)
    }

    private fun assertRefused(handler: RenameHandler) {
        try {
            handler.invoke(project, myFixture.editor, myFixture.file, contextAtCaret())
        } catch (refused: CommonRefactoringUtil.RefactoringErrorHintException) {
            assertNotNull("the refusal must carry a message", refused.message)
            return
        }
        fail("expected the offered handler to refuse the colon call site")
    }

    /** HV-8 step 1's own fixture, caret on the `m` of `t:m()` — offset 95. */
    private fun configureAtColonCallSite() {
        myFixture.configureByText("test.lua", HV8_FIXTURE)
        myFixture.editor.caretModel.moveToOffset(95)
    }

    /** The one source of a data context here. Nothing is injected — see the class KDoc. */
    private fun contextAtCaret(): DataContext =
        DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)

    private companion object {
        const val HV8_FIXTURE =
            "---@deprecated Use the method instead\n" +
                "local function m() end\n" +
                "local t = {}\n" +
                "function t:m() end\n" +
                "t:m()\n" +
                "m()\n"
    }
}
