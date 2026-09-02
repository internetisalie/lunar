package net.internetisalie.lunar.refactoring.rename

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.LuaLanguageLevel
import net.internetisalie.lunar.lang.insight.LuaRefactoringSupportProvider
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaLabelName
import net.internetisalie.lunar.lang.psi.LuaLocalFuncDecl
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LuaLabelRenameTest : BasePlatformTestCase() {
    @Test
    fun testNameIdentifierOwner() {
        myFixture.configureByText("test.lua", "::lbl::")

        val labelName = PsiTreeUtil.findChildOfType(myFixture.file, LuaLabelName::class.java)
        assertNotNull("LuaLabelName should be found in PSI", labelName)

        val owner = labelName as? PsiNameIdentifierOwner
        assertNotNull("LuaLabelName should be assignable to PsiNameIdentifierOwner", owner)

        val identifier = owner?.nameIdentifier
        assertNotNull("Name identifier should not be null", identifier)
        assertEquals("lbl", identifier?.text)
        assertEquals("lbl", owner?.name)

        WriteCommandAction.runWriteCommandAction(project) {
            owner?.setName("renamed")
        }

        assertEquals("renamed", owner?.name)
        assertEquals("renamed", owner?.nameIdentifier?.text)

        val parent = owner?.parent
        assertNotNull("Parent of labelName should not be null", parent)
        assertEquals("::renamed::", parent?.text)
    }

    @Test
    fun testRenameFromDeclaration() {
        myFixture.configureByText(
            "test.lua",
            """
            ::<caret>myLabel::
            goto myLabel
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("newLabel")

        myFixture.checkResult(
            """
            ::newLabel::
            goto newLabel
            """.trimIndent(),
        )
    }

    @Test
    fun testRenameFromReference() {
        myFixture.configureByText(
            "test.lua",
            """
            ::myLabel::
            goto my<caret>Label
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("newLabel")

        myFixture.checkResult(
            """
            ::newLabel::
            goto newLabel
            """.trimIndent(),
        )
    }

    @Test
    fun testScopeIsolatedRename() {
        myFixture.configureByText(
            "test.lua",
            """
            function a()
                ::<caret>L::
                goto L
            end
            function b()
                ::L::
                goto L
            end
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("L2")

        myFixture.checkResult(
            """
            function a()
                ::L2::
                goto L2
            end
            function b()
                ::L::
                goto L
            end
            """.trimIndent(),
        )
    }

    /**
     * TC-04-A (`REFACT-04-02`) — a rename rewrites every bound `goto`, not just the first one.
     *
     * **Mutation:** change `LuaLabelReference.isReferenceTo` (`LuaLabelReference.kt:50-56`) to
     * `return false` — the three `goto`s are left on the old name.
     */
    @Test
    fun testRenameRewritesEveryBoundGoto() {
        myFixture.configureByText(
            "test.lua",
            """
            ::<caret>myLabel::
            goto myLabel
            do goto myLabel end
            if true then goto myLabel end
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("newLabel")

        myFixture.checkResult(
            """
            ::newLabel::
            goto newLabel
            do goto newLabel end
            if true then goto newLabel end
            """.trimIndent(),
        )
    }

    /**
     * TC-04-B (`REFACT-04-17`) — a label with no `goto` renames alone; no usage survives to be
     * rewritten (or found) afterwards.
     *
     * **Mutation:** change `LuaNameDeclElementImpl.setName` (`LuaBaseElements.kt:61-70`) to
     * `return this` without the `node.replaceChild` call — the text is unchanged.
     */
    @Test
    fun testRenameWithNoGotoRenamesDeclarationAlone() {
        val file = myFixture.configureByText("test.lua", "::<caret>done::")

        myFixture.renameElementAtCaret("finished")

        myFixture.checkResult("::finished::")

        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        val usages = myFixture.findUsages(labelName)
        assertTrue("a label with no goto must report no usages", usages.isEmpty())
    }

    /**
     * TC-04-C (`REFACT-04-05`) — under shadowing, only the `goto`s bound to the renamed
     * declaration move; the inner shadowing label and the `goto` bound to it are untouched.
     *
     * `languageLevel` is set to LUA53 because this exact fixture is `label 'a' already defined on
     * line 1` on 5.4.7 (executed, `requirements.md` TC-04-C setup) — LUA53 is the level at which
     * the shadow is legal Lua, which is what makes this the shadowing case `REFACT-04-05` is about.
     *
     * **Mutation:** drop the `resolved === owner` identity test from `LuaLabelReference.isReferenceTo`
     * (`LuaLabelReference.kt:55`), leaving only `resolved.identifier.text == name` — the inner
     * `goto a` is rewritten too, because it now matches by spelling rather than by binding.
     */
    @Test
    fun testRenameUnderShadowingRewritesOnlyTheBoundGotos() {
        LuaProjectSettings.getInstance(project).state.languageLevel = LuaLanguageLevel.LUA53

        myFixture.configureByText(
            "test.lua",
            """
            ::<caret>a::
            do
              goto a
              ::a::
            end
            goto a
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("outer")

        myFixture.checkResult(
            """
            ::outer::
            do
              goto a
              ::a::
            end
            goto outer
            """.trimIndent(),
        )
    }

    /**
     * TC-04-M (`REFACT-04-09`, `-01`) — the two headlessly observable conjuncts of
     * `MemberInplaceRenameHandler.isAvailable`: the element is a `PsiNameIdentifierOwner`, and
     * `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` answers `true` for it. The
     * third conjunct, `editor.getSettings().isVariableInplaceRenameEnabled()`, is not observable
     * headlessly (DR-02, design §1) and is not asserted here.
     *
     * **Deviation from `implementation-plan.md`'s stated fixture**: the plan's negative case is
     * `local x = 1`'s `LuaNameRef`. As of REFACT-07 Phase 7-8 (`c52d333b`, which landed after this
     * design was written), `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable` also
     * returns `true` for a **file-local** declaration's `LuaNameRef` — confirmed by the passing
     * `testMemberInplaceRenameIsOfferedForAFileLocalDeclaration` in `LuaInplaceRenameTest.kt`, whose
     * fixture is exactly `local counter = 0`. `local x = 1` is therefore no longer a `false` case
     * and using it verbatim would assert something no longer true. A **global** declaration's
     * `LuaNameRef` is used instead — `LuaDeclarationKind.GLOBAL_VARIABLE.isFileLocal` is `false`,
     * confirmed by the same test file's `testMemberInplaceRenameIsWithheldFromAGlobalDeclaration`
     * — which preserves the row's intent (label offered; a non-label declaration is not) with a
     * fixture that is actually `false` under the code as it stands today.
     *
     * **Mutation:** change `LuaRefactoringSupportProvider.isMemberInplaceRenameAvailable`
     * (`LuaRefactoringSupportProvider.kt:87-95`) to `= false` unconditionally.
     */
    @Test
    fun testInPlaceRenameAvailabilityConjuncts() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ::top::
                x = 1
                """.trimIndent(),
            )

        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        val globalNameRef = PsiTreeUtil.findChildOfType(file, LuaNameRef::class.java)!!
        val provider = LuaRefactoringSupportProvider()

        assertTrue("a label must be a PsiNameIdentifierOwner", labelName is PsiNameIdentifierOwner)
        assertTrue(
            "in-place rename must be offered for a label",
            provider.isMemberInplaceRenameAvailable(labelName, null),
        )
        assertFalse(
            "in-place rename must not be offered for a global declaration's LuaNameRef",
            provider.isMemberInplaceRenameAvailable(globalNameRef, null),
        )
    }

    /**
     * TC-04-I (`REFACT-04-11`) — a label's use scope is its enclosing function.
     *
     * **Mutation:** remove the `getUseScope` override (design §2.4) from `LuaNameDeclElementImpl`
     * — the scope becomes a `GlobalSearchScope` instead of a `LocalSearchScope`.
     */
    @Test
    fun testLabelUseScopeIsEnclosingFunction() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                local function f()
                    ::<caret>done::
                    goto done
                end
                print("unrelated")
                """.trimIndent(),
            )

        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!
        val enclosingFunction = PsiTreeUtil.getParentOfType(labelName, LuaLocalFuncDecl::class.java)!!

        val useScope = labelName.useScope
        assertTrue("a label's use scope must be a LocalSearchScope", useScope is LocalSearchScope)
        val scopeElements = (useScope as LocalSearchScope).scope
        assertEquals(1, scopeElements.size)
        assertSame(enclosingFunction, scopeElements[0])
    }

    /**
     * TC-04-J (`REFACT-04-11`) — a top-level label's use scope is its file.
     *
     * **Mutation:** make `LuaLabelScopes.functionScopeOf` return null instead of the containing
     * file when no function ancestor exists — the override falls through to `super.getUseScope()`
     * and the scope is global.
     */
    @Test
    fun testTopLevelLabelUseScopeIsFile() {
        val file =
            myFixture.configureByText(
                "test.lua",
                """
                ::<caret>top::
                goto top
                """.trimIndent(),
            )

        val labelName = PsiTreeUtil.findChildOfType(file, LuaLabelName::class.java)!!

        val useScope = labelName.useScope
        assertTrue("a top-level label's use scope must be a LocalSearchScope", useScope is LocalSearchScope)
        val scopeElements = (useScope as LocalSearchScope).scope
        assertEquals(1, scopeElements.size)
        assertTrue("the single scope element must be the containing LuaFile", scopeElements[0] is LuaFile)
    }
}
