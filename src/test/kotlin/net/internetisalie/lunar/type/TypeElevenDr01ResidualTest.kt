package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager

/**
 * TYPE-11 DR-01 — the two residual paths `requirements.md` names, as assertions rather than as a
 * paragraph.
 *
 * Every test here asserts **today's correct answer**. On `main` they are green, and that is the
 * point: DR-01's measurement is to pin `LuaTypesSnapshot.forFile`'s dependency to a generation
 * tracker for provenance-matched library files and re-run this class. A test that goes red under
 * the pinned build is a residual that fires; one that stays green is a residual that does not.
 * The verdict is therefore produced by running, not by reading `LuaTypesVisitor`.
 *
 * Both fixtures put the library file on the **provisioned** side and the changing declaration on the
 * **project** side, which is the only direction the generation dependency is unsound in
 * (library→library is safe under a shared tracker; project→library invalidates correctly because the
 * consumer's own file changed).
 */
class TypeElevenDr01ResidualTest : TypeElevenDefinitionLibraryTestCase() {
    private fun membersOfGlobal(
        name: String,
        context: PsiFile,
    ): Set<String> =
        runReadAction {
            LuaTypeManager
                .getInstance(project)
                .resolveGlobal(name, context)
                ?.getMembers()
                ?.keys
                ?.toSet()
                .orEmpty()
        }

    /**
     * Edits [file] and **refuses to let the edit be healed by anything but itself**.
     *
     * Measured 2026-08-09: run alone, residual path 2 reports `[afterEdit, beforeEdit]`; run after
     * the other TYPE-11 DR classes in the same JVM it reports `[afterEdit]`. The difference is a
     * `ProjectRootModificationTracker` tick arriving from a previous class's library install, which
     * discards the pinned snapshot and hands the test a green it did not earn. A harness that can be
     * silently healed by an unrelated tick is not a gate, so the tick count is asserted to be still.
     */
    private fun rewrite(
        file: PsiFile,
        text: String,
    ) = rewriteAssertingRootsAreStill(file, text)

    /**
     * Residual path 1 — a library file's own global takes its type from a free global that a
     * **project** file declares. `doResolveGlobal` searches project scope first (BUG-427,
     * `LuaTypeManagerImpl:162`), so the library snapshot embeds the project's type via
     * `freeGlobalSeed` (`LuaTypesVisitor:1322`) and is stale the moment the project file changes.
     */
    fun testALibraryGlobalTypedFromAProjectGlobalTracksThatProjectFile() {
        installDefinitionLibrary(
            "luassert",
            mapOf("alpha.lua" to "---@meta\n\nlibAlias = sharedByProject\n"),
        )
        val projectDeclaration =
            myFixture.addFileToProject("shared.lua", "sharedByProject = { beforeEdit = 1 }\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val before = membersOfGlobal("libAlias", consumer)
        println("DR-01 path1 before edit: libAlias members = $before")
        assertEquals("the library global must take the project declaration's members", setOf("beforeEdit"), before)

        rewrite(projectDeclaration, "sharedByProject = { afterEdit = 1 }\n")

        println("DR-01 path1 after edit: libAlias members = ${membersOfGlobal("libAlias", consumer)}")
        assertEquals(
            "editing the project file must be reflected in the library global's type",
            setOf("afterEdit"),
            membersOfGlobal("libAlias", consumer),
        )
    }

    /**
     * Residual path 2 — a project file adds a method to a class the library declares.
     * `materializeClass` → `collectMethodMembers` enumerates `LuaGlobalDeclarationIndex` project-wide
     * (`LuaTypeManagerImpl:427`), and `tableToLuaType` merges those nominal members into any graph
     * table carrying the class name (`LuaTypes:156-172`), so the project's method is frozen into the
     * **library file's own snapshot graph**. Extending a stub class from project code is an ordinary
     * Lua idiom.
     *
     * The fixture uses the **hosted** `---@class` form (`---@class C` over `local C = {}`), which is
     * the shape that actually carries a `className` into the graph. Probed on `main`: with the
     * unhosted form (`---@class C` over a bare `C = {}` — what the bundled stdlib stubs use,
     * BUG-400) the graph type is `Table(className=null, localMembers={})` and no project member
     * reaches the snapshot at all. Written the other way round this test would have been vacuous.
     */
    fun testAProjectDeclaredMethodOnAStubClassTracksThatProjectFile() {
        installDefinitionLibrary(
            "luassert",
            mapOf(
                "host.lua" to "---@meta\n\n---@class HostClass\nlocal HostClass = {}\nHostGlobal = HostClass\n",
                "host2.lua" to "---@meta\n\nlibHandle = HostGlobal\n",
            ),
        )
        val projectExtension =
            myFixture.addFileToProject("ext.lua", "function HostClass:beforeEdit() end\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val before = membersOfGlobal("libHandle", consumer)
        println("DR-01 path2 before edit: libHandle members = $before")
        assertTrue(
            "a project-declared method must be a member of the stub-defined class; got $before",
            "beforeEdit" in before,
        )

        rewrite(projectExtension, "function HostClass:afterEdit() end\n")

        val after = membersOfGlobal("libHandle", consumer)
        println("DR-01 path2 after edit: libHandle members = $after")
        assertTrue("renaming the project method must be reflected; got $after", "afterEdit" in after)
        assertFalse("the removed project method must disappear; got $after", "beforeEdit" in after)
    }

    /**
     * Residual path 3 — a library file `require`s a **project** module.
     *
     * `design.md` §6 states that `getModuleType` reports the project file and the library is
     * therefore not pinnable; until now that specific path was reasoned rather than run, and it was
     * the first item under `risks-and-gaps.md` "Test Case Gaps". It is a distinct door from the
     * other two paths here — `resolveModule`, not `resolveGlobal` — and it reaches the project file
     * through `resolveModuleCandidates`, which searches by *file name* rather than through any
     * global-scope ordering, so nothing in paths 1 or 2 covers it.
     *
     * Its complement is `TypeElevenDr18ModuleAbsenceTest`: there the module does not exist yet, so
     * there is no file to report and only the recorded **absence** denies the pin.
     *
     * ⚠ **Like that one, this asserts the answer without attributing it.** Measured: with
     * `getModuleType`'s `reportFile` deleted it stays green, because resolving the global `require`
     * goes through the all-scope fallback into `package.lua` and §3.3 step 7 denies the pin for that
     * reason instead (probed frame: `urls=[package.lua] rescued=[global:require]`). The attributable
     * gate for the §3.5 module row is
     * `LuaTypeManagerRecordingTest.testResolvingAModuleRecordsTheFileItWasReadFrom`.
     */
    fun testALibraryThatRequiresAProjectModuleTracksThatModule() {
        installDefinitionLibrary(
            "luassert",
            mapOf("requiring.lua" to "---@meta\n\nlibRequired = require(\"projmod\")\n"),
        )
        val projectModule = myFixture.addFileToProject("projmod.lua", "return { beforeEdit = 1 }\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        val before = membersOfGlobal("libRequired", consumer)
        println("DR-01 path3 before edit: libRequired members = $before")
        assertEquals("the library global must take the required project module's members", setOf("beforeEdit"), before)

        rewrite(projectModule, "return { afterEdit = 1 }\n")

        val after = membersOfGlobal("libRequired", consumer)
        println("DR-01 path3 after edit: libRequired members = $after")
        assertEquals(
            "editing the required project module must be reflected in the library global's type",
            setOf("afterEdit"),
            after,
        )
    }

    /**
     * Control — **project→project**. `b.lua`'s snapshot takes its type from a global `a.lua`
     * declares; editing `a.lua` must be visible through `b.lua`. This is what pins "project files
     * stay on the project-wide tracker".
     *
     * Two earlier drafts of this control could not fail, and both looked reasonable:
     * "the library still resolves after a consumer edit" (nothing in this design touches that), and
     * "a project file's snapshot tracks its **own** edits" — which is guaranteed by `forFile`'s
     * `psiFile` dependency (`CachedValueBase` reads `containingFile.modificationStamp` for a
     * `PsiElement` dependency) independently of the churn tracker, so pinning every file in the
     * project left it green. Only a **cross-file** project dependency is sensitive to the churn
     * tracker. Measured with `isProvisionedFile`/`isProvisionedUrl` forced true (every file pinned):
     * red, `expected:<[after]> but was:<[before]>`.
     */
    fun testAProjectToProjectDependencyIsNeverPinned() {
        installDefinitionLibrary("luassert", mapOf("gamma.lua" to "---@meta\n\nlibOnly = { fromLibrary = 1 }\n"))
        val source = myFixture.addFileToProject("a.lua", "projectShared = { before = 1 }\n")
        myFixture.addFileToProject("b.lua", "projectAlias = projectShared\n")
        announceRootsChange()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")

        assertEquals(setOf("fromLibrary"), membersOfGlobal("libOnly", consumer))
        assertEquals(setOf("before"), membersOfGlobal("projectAlias", consumer))

        rewrite(source, "projectShared = { after = 1 }\n")

        val aliasAfter = membersOfGlobal("projectAlias", consumer)
        println(
            "DR-01 control after project edit: projectAlias = $aliasAfter libOnly = ${membersOfGlobal(
                "libOnly",
                consumer,
            )}",
        )
        assertEquals("a project→project dependency must never be pinned", setOf("after"), aliasAfter)
        assertEquals(
            "a library global must still resolve alongside",
            setOf("fromLibrary"),
            membersOfGlobal("libOnly", consumer),
        )
    }
}
