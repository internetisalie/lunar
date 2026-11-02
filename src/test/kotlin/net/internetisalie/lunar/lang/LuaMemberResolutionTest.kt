package net.internetisalie.lunar.lang

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import net.internetisalie.lunar.lang.psi.LuaMemberResolution
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import net.internetisalie.lunar.platform.LuaPlatform
import net.internetisalie.lunar.platform.target.PlatformVersionRegistry
import net.internetisalie.lunar.platform.target.RuntimeLibraryProvider
import net.internetisalie.lunar.platform.target.Target
import net.internetisalie.lunar.project.PlatformLibraryIndex
import net.internetisalie.lunar.settings.LuaProjectSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-53 Phase 3 (BUG-508, BUG-516): a member name resolves through the inferred type of its
 * receiver, and text stays the fallback only where no type exists.
 *
 * Each test names the mutation that turns it red; the requirement table in
 * `docs/features/maint/53-member-resolution-by-receiver-type/requirements.md` is the register.
 */
@RunWith(JUnit4::class)
class LuaMemberResolutionTest : IndexedBasePlatformTestCase() {
    override fun tearDown() {
        try {
            useStandardTarget("5.4")
        } finally {
            super.tearDown()
        }
    }

    /**
     * TC-09 — the receiver's own module wins over another file's same-named member.
     *
     * **Mutation**: make `doMultiResolve` skip `typedTargets`; both `M.clear` declarations resolve.
     */
    @Test
    fun aMemberResolvesToTheDeclarationOfItsReceiversType() {
        myFixture.addFileToProject(
            "vim/lsp/codelens.lua",
            "local M = {}\n\n---@deprecated\nfunction M.clear(client_id, bufnr) end\nreturn M\n",
        )
        val file =
            myFixture.addFileToProject(
                "lazy/manage.lua",
                "local M = setmetatable({}, {})\nfunction M.clear(plugins) end\nM.clear()\nreturn M\n",
            )

        assertEquals(
            listOf("lazy/manage.lua:2:function M.clear(plugins) end"),
            describedTargetsOf(file, "clear", line = 3),
        )
    }

    /**
     * TC-11 — a nested member is typed through its own step, not through the head.
     *
     * **Mutation**: make `receiverType` ignore `steps`; `a` declares `c`, so the read resolves to it.
     */
    @Test
    fun aNestedMemberIsNotLookedUpOnTheHead() {
        val file = myFixture.addFileToProject("nested.lua", "local a = {}\na.b = {}\na.c = 1\nprint(a.b.c)\n")

        assertEquals(emptyList<String>(), describedTargetsOf(file, "c", line = 4))
    }

    /**
     * TC-12 — a member of a `---@type Foo` variable resolves to the class's function.
     *
     * **Mutation**: as TC-09; text finds nothing for the variable `x`.
     */
    @Test
    fun aMemberOfAnAnnotatedVariableResolvesThroughItsClass() {
        val file =
            myFixture.addFileToProject(
                "class.lua",
                "---@class Foo\nlocal Foo = {}\nfunction Foo.new() end\n---@type Foo\nlocal x\nx.new()\n",
            )

        assertEquals(listOf("class.lua:3:function Foo.new() end"), describedTargetsOf(file, "new", line = 6))
    }

    /**
     * TC-13 — BUG-516: a stdlib file method resolves through `io.stdout`'s type.
     *
     * **Mutation**: delete the suffixed branch of `colonCallResults`; every call resolves to nothing.
     */
    @Test
    fun aSuffixedColonCallResolvesThroughTheReceiversType() {
        useStandardTarget("5.1")
        val file =
            myFixture.configureByText(
                "stdout.lua",
                "io.stdout:close()\nio.stdout:setvbuf(\"no\")\nio.stdout:nosuch()\n",
            )

        assertEquals(listOf("function File:close() end"), declarationLinesOf(file, "close"))
        assertEquals(listOf("function File:setvbuf(mode, size) end"), declarationLinesOf(file, "setvbuf"))
        assertEquals(emptyList<String>(), declarationLinesOf(file, "nosuch"))
    }

    /**
     * TC-14 — a member of a table-constructor local does not match another file's same-named member.
     *
     * **Mutation**: delete the same-file filter in `doMultiResolve`; `vim/lib.lua`'s `M.gone` returns.
     */
    @Test
    fun aMemberOfAConstructedLocalHasNoCrossFileTextMatch() {
        myFixture.addFileToProject("vim/lib.lua", "local M = {}\nfunction M.gone() end\nreturn M\n")
        val file = myFixture.addFileToProject("app/a.lua", "local M = {}\nM.gone()\nreturn M\n")

        assertEquals(emptyList<String>(), describedTargetsOf(file, "gone", line = 2))
    }

    /**
     * TC-15 — a later reassignment in the same block does not disqualify the constructed head.
     *
     * **Mutation**: as TC-14.
     */
    @Test
    fun aReassignedConstructedLocalStillHasNoCrossFileTextMatch() {
        myFixture.addFileToProject("vim/lib.lua", "local M = {}\nfunction M.gone() end\nreturn M\n")
        val file = myFixture.addFileToProject("app/c.lua", "local M = {}\nM = other\nM.gone()\n")

        assertEquals(emptyList<String>(), describedTargetsOf(file, "gone", line = 3))
    }

    /**
     * TC-16 — a member of an untyped, non-constructor local keeps the cross-file text answer.
     *
     * **Mutation**: make `isConstructedLocalHead` return true for any local, or make `doMultiResolve`
     * return `typed` when it is empty; the text answer is dropped.
     */
    @Test
    fun aMemberOfAnUntypedLocalKeepsTheTextFallback() {
        myFixture.addFileToProject("vim/lib.lua", "local M = {}\nfunction M.gone() end\nreturn M\n")
        val file = myFixture.addFileToProject("app/e.lua", "local M = make()\nM.gone()\nreturn M\n")

        assertEquals(listOf("vim/lib.lua:2:function M.gone() end"), describedTargetsOf(file, "gone", line = 2))
    }

    /**
     * TC-18 — a dot member resolved while its own file's snapshot is being built uses text, so no typed
     * lookup nests inside the build, and the later answer is not poisoned by it.
     *
     * **Mutation**: delete the under-construction guard in `multiResolve`; the counter rises.
     */
    @Test
    fun aDotMemberResolvedDuringItsFilesBuildIsNotTyped() {
        val text =
            "local M = {}\n---@param cb fun(x:string)\nfunction M.each(cb) end\n" +
                "M.each(function(s) return s end)\nreturn M\n"
        val file = myFixture.addFileToProject("each.lua", text)
        val before = LuaMemberResolution.TYPED_DURING_BUILD.get()

        val types = LuaTypesSnapshot.forFile(file)

        assertEquals(listOf("each.lua:3:function M.each(cb) end"), describedTargetsOf(file, "each", line = 4))
        val param = requireNotNull(file.findElementAt(text.indexOf("(s)") + 1)).parent
        assertEquals("string", types.graphTypeToLuaType(types.getValueType(param)).toString())
        assertEquals("no typed lookup ran inside the build", before, LuaMemberResolution.TYPED_DURING_BUILD.get())
    }

    /**
     * TC-24 — a suffixed colon call resolved while its file's snapshot is being built answers nothing,
     * and that answer is not kept.
     *
     * **Mutation**: delete the under-construction return in `colonCallResults`; the counter rises by 1.
     */
    @Test
    fun aSuffixedColonCallDuringItsFilesBuildAnswersNothing() {
        val text =
            "local M = {}\nM.obj = {}\n---@param cb fun(x:string)\nfunction M.obj:each(cb) end\n" +
                "M.obj:each(function(s) return s end)\nreturn M\n"
        val file = myFixture.addFileToProject("obj.lua", text)
        val before = LuaMemberResolution.TYPED_DURING_BUILD.get()

        val beforeBuild = describedTargetsOf(file, "each", line = 5)
        LuaTypesSnapshot.forFile(file)
        val afterBuild = describedTargetsOf(file, "each", line = 5)

        assertEquals("no typed lookup ran inside the build", before, LuaMemberResolution.TYPED_DURING_BUILD.get())
        assertEquals(emptyList<String>(), beforeBuild)
        assertEquals(emptyList<String>(), afterBuild)
    }

    /**
     * TC-23 — a head with no write of its own (a parameter) resolves its read to the write in the same
     * function, and not to the unrelated `y` writes of another file.
     *
     * **Mutation**: in `headType`, return null for a head with no write of its own; `other.lua`'s
     * `p.y` joins the answer.
     */
    @Test
    fun aMemberOfAParameterResolvesToItsOwnFilesWrite() {
        myFixture.addFileToProject("other.lua", "local p = {}\np.y = 2\nlocal x = {}\nx.y = 3\nreturn p, x\n")
        val file = myFixture.addFileToProject("g1.lua", "local function f(p)\n  p.y = 1\n  return p.y\nend\nreturn f\n")

        assertEquals(listOf("g1.lua:2:y"), describedTargetsOf(file, "y", line = 3))
    }

    private fun describedTargetsOf(
        file: PsiFile,
        name: String,
        line: Int,
    ): List<String> {
        val ref =
            PsiTreeUtil
                .findChildrenOfType(file, LuaNameRef::class.java)
                .single { it.text == name && lineOf(it) == line }
        return targetsOf(ref).map { "${relativeName(it)}:${lineOf(it)}:${firstLine(it)}" }
    }

    private fun declarationLinesOf(
        file: PsiFile,
        name: String,
    ): List<String> {
        val ref = PsiTreeUtil.findChildrenOfType(file, LuaNameRef::class.java).single { it.text == name }
        return targetsOf(ref).map { firstLine(it) }
    }

    private fun targetsOf(nameRef: LuaNameRef): List<PsiElement> =
        (nameRef.reference as PsiPolyVariantReference).multiResolve(false).mapNotNull { it.element }

    private fun firstLine(element: PsiElement): String = element.text.lineSequence().first()

    private fun relativeName(element: PsiElement): String =
        element.containingFile.virtualFile.path
            .substringAfter("/src/")

    private fun lineOf(element: PsiElement): Int =
        element.containingFile.text
            .substring(0, element.textOffset)
            .count { it == '\n' } + 1

    private fun useStandardTarget(label: String) {
        val version = requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label))
        runInEdtAndWait {
            val settings = LuaProjectSettings.getInstance(project)
            settings.setTargetAndNotify(Target(LuaPlatform.STANDARD, version))
            RuntimeLibraryProvider(project).getLibraryRoot(settings.state.getTarget())?.let { root ->
                VfsUtil.collectChildrenRecursively(root)
                VfsUtil.markDirtyAndRefresh(false, true, true, root)
            }
            PlatformLibraryIndex.reload()
        }
    }
}
