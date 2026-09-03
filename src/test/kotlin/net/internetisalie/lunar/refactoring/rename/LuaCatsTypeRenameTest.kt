package net.internetisalie.lunar.refactoring.rename

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsAliasTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsBuiltinType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsClassTag
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsElementTypes
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsNamedType
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsTypeDeclarations
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * `REFACT-08` Phase 4: [LuaCatsTypeRenameProcessor] (design.md §2.6, §3.6, §3.7, §3.9, §3.11) and
 * the [net.internetisalie.lunar.lang.insight.LuaTargetElementEvaluator] clause (§2.8).
 *
 * Row numbers below are `requirements.md`'s TC numbers. Rows 26-28 are written first, per the
 * implementation plan, because mutations T, U and V are the only falsifiers `REFACT-08-17` has.
 */
@RunWith(JUnit4::class)
class LuaCatsTypeRenameTest : BasePlatformTestCase() {
    private val elevenSlotUses =
        "--- @type Widget\n" +
            "--- @param p Widget\n" +
            "--- @return Widget\n" +
            "--- @class Panel : Widget\n" +
            "--- @field w Widget\n" +
            "--- @type Widget[]\n" +
            "--- @type table<string, Widget>\n" +
            "--- @alias Handle Widget|nil\n" +
            "--- @type fun(a: Widget): Widget\n" +
            "--- @cast v Widget\n" +
            "local function f(p) end\n"

    // ---- TC-26/27/28 — REFACT-08-17, the type-parameter exclusion. Written first. ----

    /**
     * TC-26 — a project `---@class T` and a parameterized `---@class Box<T>`'s OWN parameter share
     * the spelling "T"; the parameter is a differently-scoped declaration and must not move.
     *
     * Mutation T (mutation-proved by hand, `temporary-edits` skill): restoring
     * `holder is LuaCatsGenericType` as a conjunct of `isDeclarationSlotHolder` clause 2 rewrites
     * `boxes.lua` into `--- @class Box<Elem>`, renaming a second, differently-keyed type unasked.
     */
    @Test
    fun testParameterizedClassOwnTypeParameterIsNotAUse() {
        myFixture.configureByText("types.lua", "--- @class <caret>T\nlocal T = {}\n")
        val boxes = myFixture.addFileToProject("boxes.lua", "--- @class Box<T>\nlocal Box = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @param p T\nlocal function f(p) return p end\n")

        myFixture.renameElementAtCaret("Elem")

        myFixture.checkResult("--- @class Elem\nlocal T = {}\n")
        assertEquals("--- @param p Elem\nlocal function f(p) return p end\n", uses.text)
        assertEquals("boxes.lua must stay byte-identical", "--- @class Box<T>\nlocal Box = {}\n", boxes.text)
    }

    /**
     * TC-27 — a `---@generic T` declares a function-local type parameter; the two tags it governs
     * bind to that parameter, not to the project class of the same spelling.
     *
     * Mutation U (mutation-proved by hand): deleting clause 1 of
     * `isDeclarationSlotHolder` renames the `@generic` line's own parameter unasked. Mutation V
     * (mutation-proved by hand): deleting the shadowing clause from
     * `useHolderOf`/`useLeafOf` rewrites the `@param`/`@return` tags it shadows instead.
     */
    @Test
    fun testGenericDeclarationAndItsShadowedTagsAreNotUses() {
        myFixture.configureByText("types.lua", "--- @class <caret>T\nlocal T = {}\n")
        val gen =
            myFixture.addFileToProject(
                "gen.lua",
                "--- @generic T\n--- @param v T\n--- @return T\nlocal function id(v) return v end\n",
            )

        myFixture.renameElementAtCaret("Elem")

        myFixture.checkResult("--- @class Elem\nlocal T = {}\n")
        assertEquals(
            "gen.lua must stay byte-identical",
            "--- @generic T\n--- @param v T\n--- @return T\nlocal function id(v) return v end\n",
            gen.text,
        )
    }

    /**
     * TC-28 — the control for TC-27's shadowing clause: a `T` in a comment that declares no type
     * parameter is an ordinary use and is still rewritten, so the shadowing clause cannot be widened
     * into a blanket exclusion of the name.
     */
    @Test
    fun testATypeParameterSpellingWithNoGenericTagInSameCommentIsAnOrdinaryUse() {
        myFixture.configureByText("types.lua", "--- @class <caret>T\nlocal T = {}\n")
        val plain = myFixture.addFileToProject("plain.lua", "--- @param w T\nlocal function h(w) return w end\n")

        myFixture.renameElementAtCaret("Elem")

        myFixture.checkResult("--- @class Elem\nlocal T = {}\n")
        assertEquals("--- @param w Elem\nlocal function h(w) return w end\n", plain.text)
    }

    // ---- TC-1..TC-25 ----

    /** TC-1 — the declaration caret, eleven use slots, zero stale spellings. */
    @Test
    fun testRenameFromTheDeclarationCaretRewritesAllElevenUseSlots() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal Widget = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", elevenSlotUses)

        myFixture.renameElementAtCaret("Gadget")

        myFixture.checkResult("--- @class Gadget\nlocal Widget = {}\n")
        val staleWidgetSpellings = Regex("\\bWidget\\b").findAll(uses.text).count()
        val newGadgetSpellings = Regex("\\bGadget\\b").findAll(uses.text).count()
        assertEquals(0, staleWidgetSpellings)
        assertEquals(11, newGadgetSpellings)
    }

    /** TC-4 — a use caret substitutes to the declaration and both files move. */
    @Test
    fun testRenameFromAUseCaretMovesTheDeclarationToo() {
        val types = myFixture.addFileToProject("types.lua", "--- @class Widget\nlocal Widget = {}\n")
        myFixture.configureByText("uses.lua", "--- @param p Wid<caret>get\nlocal function f(p) return p end\n")

        myFixture.renameElementAtCaret("Gadget")

        myFixture.checkResult("--- @param p Gadget\nlocal function f(p) return p end\n")
        assertEquals("--- @class Gadget\nlocal Widget = {}\n", types.text)
    }

    /** TC-5 — `TargetElementUtil.findTargetElement` on a declaration caret finds the NAME leaf. */
    @Test
    fun testFindTargetElementOnADeclarationCaret() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal Widget = {}\n")
        val target = TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS)
        assertNotNull("the declaration caret must have a target (design §2.8)", target)
        assertEquals(LuaCatsElementTypes.NAME, target?.node?.elementType)
        assertEquals("Widget", target?.text)
    }

    /**
     * TC-6 — a re-opened class has both declaration slots rewritten, in every file.
     *
     * Mutation A (mutation-proved by hand, `temporary-edits` skill) — replace
     * `declarationLeaves(...)` with `listOf(element)` in `renameElement`. RED: `more.lua` keeps
     * `--- @class Widget`, so the project holds two types where it held one. See the report for
     * both pasted runs.
     */
    @Test
    fun testEveryDeclarationSlotMovesForAReopenedClass() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal Widget = {}\n")
        val more =
            myFixture.addFileToProject("more.lua", "--- @class Widget\n--- @field extra string\nlocal Widget = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @type Widget\nlocal w\n")

        myFixture.renameElementAtCaret("Gadget")

        myFixture.checkResult("--- @class Gadget\nlocal Widget = {}\n")
        assertEquals("--- @class Gadget\n--- @field extra string\nlocal Widget = {}\n", more.text)
        assertEquals("--- @type Gadget\nlocal w\n", uses.text)
    }

    /** TC-7 — the host Lua local is never in the rewrite set; the two symbols stay independent. */
    @Test
    fun testTheHostLuaDeclarationIsUntouchedAndTheTypeReResolves() {
        val types =
            myFixture.configureByText(
                "types.lua",
                "--- @class Wid<caret>get\n--- @field w string\nlocal Widget = {}\n",
            )
        myFixture.addFileToProject("uses.lua", "--- @type Widget\nlocal w\n")
        val before = runReadAction { LuaTypeManager.getInstance(project).resolveType("Widget", types) }
        assertNotNull("Widget must resolve before the rename", before)

        myFixture.renameElementAtCaret("Gadget")

        assertTrue("the host local must keep its own name", types.text.contains("local Widget = {}"))
        runReadAction {
            val afterOld = LuaTypeManager.getInstance(project).resolveType("Widget", types)
            val afterNew = LuaTypeManager.getInstance(project).resolveType("Gadget", types)
            assertNull("the old spelling must resolve to nothing", afterOld)
            assertNotNull("the new spelling must resolve", afterNew)
        }
    }

    /** TC-10 — a `@class table` use parses as `LuaCatsBuiltinType`, so no holder exists at all. */
    @Test
    fun testABuiltinTypeUseHasNoNamedTypeHolderAndNoReferences() {
        val types = myFixture.configureByText("types.lua", "--- @class table\nlocal T = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @param p table\nlocal function f(p) end\n")
        runReadAction {
            assertEquals(1, PsiTreeUtil.findChildrenOfType(uses, LuaCatsBuiltinType::class.java).size)
            assertTrue(PsiTreeUtil.findChildrenOfType(uses, LuaCatsNamedType::class.java).isEmpty())
            val declTag = requireNotNull(PsiTreeUtil.findChildOfType(types, LuaCatsClassTag::class.java))
            val declLeaf = requireNotNull(LuaCatsTypeDeclarations.classDeclarationLeaf(declTag))
            val results = ReferencesSearch.search(declLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertTrue(results.isEmpty())
        }
    }

    /**
     * TC-11 — renaming a `@class table` is refused, and both files stay byte-identical.
     *
     * **`table` does not isolate mutation F on its own.** The bundled `builtin.lua` and `table.lua`
     * stubs also declare `---@class table`
     * (`grep -rl '@class table\b' src/main/resources`), so deleting `substituteElementToRename`
     * step 2 (the builtin refusal, `REFACT-08-07`) still leaves this row refused — by step 3-4's
     * out-of-project refusal (`REFACT-08-16`) instead, which fires on `table` regardless. Measured
     * by hand: with step 2 deleted, this test stays GREEN. [testRenamingABuiltinKeywordWithNoLibraryDeclarationIsRefused]
     * below uses `integer` — a `BUILTIN_KEYWORDS` member no bundled stub declares — and is the row
     * that actually reddens under mutation F.
     */
    @Test
    fun testRenamingABuiltinKeywordTypeIsRefused() {
        val typesBefore = "--- @class table\nlocal T = {}\n"
        val usesBefore = "--- @param p table\nlocal function f(p) end\n"
        val types = myFixture.configureByText("types.lua", "--- @class ta<caret>ble\nlocal T = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", usesBefore)

        val refusal = attemptRename(myFixture.file, myFixture.editor.caretModel.offset)

        assertNotNull("the rename must be refused", refusal)
        assertEquals(typesBefore, types.text)
        assertEquals(usesBefore, uses.text)
    }

    /**
     * The mutation-F gate for `REFACT-08-07` (see [testRenamingABuiltinKeywordTypeIsRefused]'s
     * KDoc). `integer` has no bundled `---@class` declaration anywhere in `src/main/resources`, so
     * only step 2's builtin check can refuse it — REFACT-08-16's out-of-project check cannot.
     *
     * Mutation F (mutation-proved by hand, `temporary-edits` skill) — delete
     * `substituteElementToRename` step 2: RED, the rename proceeds and fails with
     * `IncorrectOperationException: Cannot modify a read-only file` against the bundled
     * `builtin.lua` stub instead of a clean refusal. Restored, green again.
     */
    @Test
    fun testRenamingABuiltinKeywordWithNoLibraryDeclarationIsRefused() {
        val typesBefore = "--- @class integer\nlocal T = {}\n"
        val usesBefore = "--- @param p integer\nlocal function f(p) end\n"
        val types = myFixture.configureByText("types.lua", "--- @class inte<caret>ger\nlocal T = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", usesBefore)

        val refusal = attemptRename(myFixture.file, myFixture.editor.caretModel.offset)

        assertNotNull("the rename must be refused", refusal)
        assertEquals(typesBefore, types.text)
        assertEquals(usesBefore, uses.text)
    }

    /** TC-13 — a caret on a parameterized class head's own name offers no rename target at all. */
    @Test
    fun testParameterizedClassHeadOffersNoRenameTarget() {
        myFixture.configureByText("types.lua", "--- @class Bo<caret>x<T>\nlocal Box = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @type Box\nlocal b\n")

        val target = TargetElementUtil.findTargetElement(myFixture.editor, CARET_TARGET_FLAGS)

        assertNull("a parameterized head's own name has no rename target", target)
        assertEquals("--- @class Box<T>\nlocal Box = {}\n", myFixture.file.text)
        assertEquals("--- @type Box\nlocal b\n", uses.text)
    }

    /**
     * TC-15 — a rename spanning two files is one undoable command. `TestDialogManager` must answer
     * OK for the platform's "Undo Renaming type Widget to Gadget?" confirmation, and the assertion
     * reads `Document` text, never `PsiFile.text` — the same probe read PSI first and saw
     * `typesRestored=false` on a document that had in fact been restored.
     */
    @Test
    fun testUndoAfterATwoFileRenameRestoresBothDocuments() {
        val types = myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal Widget = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @type Widget\nlocal w\n")
        val usesDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(uses.virtualFile))

        myFixture.renameElementAtCaret("Gadget")
        assertTrue(
            myFixture.editor.document.text
                .contains("Gadget"),
        )
        assertTrue(usesDocument.text.contains("Gadget"))

        val editor = FileEditorManager.getInstance(project).getSelectedEditor(types.virtualFile)
        val undo = UndoManager.getInstance(project)
        val previousDialog = TestDialogManager.setTestDialog(TestDialog.OK)
        try {
            undo.undo(editor as? TextEditor)
        } finally {
            TestDialogManager.setTestDialog(previousDialog)
        }

        assertEquals("--- @class Widget\nlocal Widget = {}\n", myFixture.editor.document.text)
        assertEquals("--- @type Widget\nlocal w\n", usesDocument.text)
    }

    /** TC-20 — the `@alias` declaration slot, independent of `@class`. */
    @Test
    fun testAliasDeclarationSlotResolvesAndRenames() {
        val types = myFixture.configureByText("types.lua", "--- @alias Han<caret>dle string\n")
        val uses =
            myFixture.addFileToProject(
                "uses.lua",
                "--- @param p Handle\n--- @return Handle\nlocal function f(p) return p end\n",
            )
        runReadAction {
            val tag = requireNotNull(PsiTreeUtil.findChildOfType(types, LuaCatsAliasTag::class.java))
            val leaf = requireNotNull(LuaCatsTypeDeclarations.aliasDeclarationLeaf(tag))
            assertTrue(LuaCatsTypeDeclarations.isDeclarationLeaf(leaf))
            val results = ReferencesSearch.search(leaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(2, results.size)
        }

        myFixture.renameElementAtCaret("Token")

        myFixture.checkResult("--- @alias Token string\n")
        assertEquals("--- @param p Token\n--- @return Token\nlocal function f(p) return p end\n", uses.text)
    }

    /** TC-21 — the third use spelling, `LuaCatsGenericType`, moves alongside `LuaCatsNamedType`. */
    @Test
    fun testGenericTypeHeadUseMovesAlongsideNamedTypeUse() {
        myFixture.configureByText("types.lua", "--- @class Bo<caret>x\nlocal B = {}\n")
        val uses =
            myFixture.addFileToProject(
                "uses.lua",
                "--- @type Box<string>\n--- @param p Box\nlocal function f(p) end\n",
            )
        runReadAction {
            val declLeaf =
                requireNotNull(
                    LuaCatsTypeDeclarations.classDeclarationLeaf(
                        requireNotNull(PsiTreeUtil.findChildOfType(myFixture.file, LuaCatsClassTag::class.java)),
                    ),
                )
            val results = ReferencesSearch.search(declLeaf, GlobalSearchScope.allScope(project)).findAll()
            assertEquals(mapOf("GENERIC_TYPE" to 1, "NAMED_TYPE" to 1), byHolder(results))
        }

        myFixture.renameElementAtCaret("Crate")

        myFixture.checkResult("--- @class Crate\nlocal B = {}\n")
        assertEquals("--- @type Crate<string>\n--- @param p Crate\nlocal function f(p) end\n", uses.text)
    }

    /**
     * TC-22 — a parameterized declaration head (`params.lua`'s `Box<T>`) is not a use of the
     * unrelated bare `Box` declaration and stays byte-identical.
     */
    @Test
    fun testParameterizedDeclarationHeadIsNotAUse() {
        myFixture.configureByText("types.lua", "--- @class Bo<caret>x\nlocal B = {}\n")
        val params = myFixture.addFileToProject("params.lua", "--- @class Box<T>\n--- @field item T\nlocal Box2 = {}\n")
        val uses =
            myFixture.addFileToProject(
                "uses.lua",
                "--- @type Box<string>\n--- @param p Box\nlocal function f(p) end\n",
            )

        myFixture.renameElementAtCaret("Crate")

        myFixture.checkResult("--- @class Crate\nlocal B = {}\n")
        assertEquals("--- @class Box<T>\n--- @field item T\nlocal Box2 = {}\n", params.text)
        assertEquals("--- @type Crate<string>\n--- @param p Crate\nlocal function f(p) end\n", uses.text)
    }

    /**
     * TC-23 — the negative control for TC-22: a generic head in a PARENT-TYPE position is still a
     * use and is still rewritten.
     */
    @Test
    fun testAParentTypeGenericHeadIsStillAUse() {
        myFixture.configureByText("types.lua", "--- @class Bo<caret>x\nlocal B = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", "--- @class Panel : Box<string>\nlocal Panel = {}\n")

        myFixture.renameElementAtCaret("Crate")

        myFixture.checkResult("--- @class Crate\nlocal B = {}\n")
        assertEquals("--- @class Panel : Crate<string>\nlocal Panel = {}\n", uses.text)
    }

    /**
     * TC-24 — a name the plugin's own bundled stub also declares (`File`, `runtime/standard/lua-5.4/io.lua`)
     * is refused, with both project files left byte-identical. Asserts on `outOfProjectDeclarationFiles`
     * being non-empty rather than on the jar path, which carries the plugin version.
     *
     * Mutation R (mutation-proved by hand) — delete the out-of-project refusal:
     * the rename succeeds with NO exception at all, the library's `File` survives, and one type is
     * silently split in two. Mutation R2 — R plus the write scope widened back to `allScope`:
     * `IncorrectOperationException` is thrown, but only AFTER `uses.lua` was already rewritten.
     * Both are mutation-proved by hand against the source (`temporary-edits` skill) rather than as
     * a permanent test method, because R2's failure mode is a mid-write exception — see the report
     * for both pasted runs.
     */
    @Test
    fun testATypeALibraryAlsoDeclaresIsRefused() {
        val typesBefore = "--- @class File\nlocal F = {}\n"
        val usesBefore = "--- @param p File\n--- @return File\nlocal function f(p) return p end\n"
        val types = myFixture.configureByText("types.lua", "--- @class Fi<caret>le\nlocal F = {}\n")
        val uses = myFixture.addFileToProject("uses.lua", usesBefore)
        runReadAction {
            val outside = LuaCatsTypeDeclarations.outOfProjectDeclarationFiles("File", project)
            assertFalse("the bundled stub must declare 'File' outside the project", outside.isEmpty())
        }

        val refusal = attemptRename(myFixture.file, myFixture.editor.caretModel.offset)

        assertNotNull("a type any library also declares must be refused", refusal)
        assertEquals(typesBefore, types.text)
        assertEquals(usesBefore, uses.text)
    }

    /** TC-25 — the control for TC-24: a project-only name is not refused. */
    @Test
    fun testAProjectOnlyTypeIsNotRefused() {
        myFixture.configureByText("types.lua", "--- @class Wid<caret>get\nlocal W = {}\n")
        val uses =
            myFixture.addFileToProject(
                "uses.lua",
                "--- @param p Widget\n--- @return Widget\nlocal function f(p) return p end\n",
            )

        myFixture.renameElementAtCaret("Gadget")

        myFixture.checkResult("--- @class Gadget\nlocal W = {}\n")
        val staleWidget = Regex("\\bWidget\\b").findAll(uses.text).count()
        val newGadget = Regex("\\bGadget\\b").findAll(uses.text).count()
        assertEquals(0, staleWidget)
        assertEquals(2, newGadget)
    }

    private fun byHolder(results: Collection<PsiReference>): Map<String, Int> =
        results
            .map { holderKindOf(it.element) }
            .groupingBy { it }
            .eachCount()

    private fun holderKindOf(holder: PsiElement): String =
        when (holder.node.elementType) {
            LuaCatsElementTypes.NAMED_TYPE -> "NAMED_TYPE"
            LuaCatsElementTypes.TYPE_PARAM -> "TYPE_PARAM"
            LuaCatsElementTypes.GENERIC_TYPE -> "GENERIC_TYPE"
            else -> "OTHER"
        }

    private fun attemptRename(
        file: PsiFile,
        offset: Int,
    ): CommonRefactoringUtil.RefactoringErrorHintException? {
        val editor = myFixture.editor
        editor.caretModel.moveToOffset(offset)
        val target =
            requireNotNull(TargetElementUtil.findTargetElement(editor, CARET_TARGET_FLAGS)) {
                "no target at caret in ${file.text}"
            }
        return try {
            LuaCatsTypeRenameProcessor().substituteElementToRename(target, editor)
            null
        } catch (thrown: CommonRefactoringUtil.RefactoringErrorHintException) {
            thrown
        }
    }

    private companion object {
        val CARET_TARGET_FLAGS =
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
    }
}
