package net.internetisalie.lunar.type

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeSourceRecorder
import net.internetisalie.lunar.lang.psi.types.LuaTypes
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot

/**
 * TYPE-11 DR-07 / **BUG-434 — the regression gate for the sixth under-recording channel.**
 *
 * `materializeClass` builds every `@field` member type and every supertype as a
 * `LuaTypeReference(name, decl)` whose answer is memoized, and `LuaGraphType.fromLuaType` flattens
 * those references during a snapshot build (`LuaGraphType.kt:251`). Before the fix the memoization
 * carried no frame: a reference already forced at `depth() == 0` — by a hover, a completion,
 * `LuaOverrideLineMarkerProvider`, a hierarchy walk, an assignability inspection — short-circuited
 * before `LuaTypeManager.resolveType`, so the cache hit that would have replayed the consumed file
 * never happened and the frame open at that moment learnt nothing.
 *
 * The four arms differ **only** in whether the reference was forced once before the build. On
 * `main` @ `6f238e7c`, that alone flipped the verdict:
 *
 * ```
 * arm1 cold       urls=[lib.lua, gadget.lua]  pinnable=false
 * arm2 pre-forced urls=[lib.lua]              pinnable=true
 * arm3 pre-forced before=[spin] after=[spin] sameSnapshot=true    <- a stale type, from a granted pin
 * arm4 cold       before=[spin] after=[spun] sameSnapshot=false
 * ```
 *
 * ⚠ **These cases used to print rather than assert**, deliberately, so that recording the defect
 * would not cement it. The fix — `LuaTypeReference` memoizing its `SourceFrame` beside its answer and
 * replaying it on every read — makes arms 1 and 2 agree and arms 3 and 4 agree, so the prints are now
 * assertions. Arms 1 and 4 are the controls: they were already green with the defect present, and
 * only arms 2 and 3 go red when the frame is dropped from the memoization.
 */
class TypeElevenDr07LazyReferenceProbeTest : TypeElevenDefinitionLibraryTestCase() {
    private val libraryText =
        """
        ---@meta

        ---@class Widget
        ---@field part Gadget
        local Widget = {}

        ---@type Widget
        libWidget = nil
        """.trimIndent() + "\n"

    private val projectText =
        """
        ---@class Gadget
        ---@field spin number
        local Gadget = {}
        """.trimIndent() + "\n"

    private val rewrittenProjectText =
        """
        ---@class Gadget
        ---@field spun number
        local Gadget = {}
        """.trimIndent() + "\n"

    private fun installLibrary(): VirtualFile {
        val libraryRoot = installDefinitionLibrary("luassert", mapOf("lib.lua" to libraryText))
        return checkNotNull(libraryRoot.findChild("lib.lua"))
    }

    private fun describe(
        label: String,
        libraryFile: VirtualFile,
    ): String {
        val snapshot = snapshotOf(libraryFile)
        val sourceFrame = frameOf(snapshot)
        return "TYPE11-DR07 $label urls=${sourceFrame.urls.map { it.substringAfterLast('/') }} " +
            "absences=${sourceFrame.absences} rescued=${sourceFrame.rescuedGlobals} " +
            "warm=${sourceFrame.unreplayedWarm} inProgress=${sourceFrame.inProgressHits} " +
            "pinnable=${verdictOn(libraryFile, snapshot)}"
    }

    private fun verdictOn(
        libraryFile: VirtualFile,
        snapshot: LuaTypes,
    ): Boolean = runReadAction { LuaTypesSnapshot.isPinnable(psiFileOf(libraryFile), frameOf(snapshot)) }

    /**
     * The library's build consumed the project file that declares `Gadget`, and is therefore refused
     * a pin — the verdict a cold build reaches, which is the verdict every build must reach.
     */
    private fun assertTheProjectFileReachedTheFrame(
        label: String,
        libraryFile: VirtualFile,
    ) {
        println(describe(label, libraryFile))
        val snapshot = snapshotOf(libraryFile)
        val consumedNames = frameOf(snapshot).urls.map { it.substringAfterLast('/') }
        assertTrue(
            "$label: gadget.lua declares the type `Widget.part` names, so building lib.lua's snapshot " +
                "consumed it; the recorded sources were $consumedNames",
            "gadget.lua" in consumedNames,
        )
        assertFalse(
            "$label: lib.lua's snapshot depends on a project file, so pinning it to the generation " +
                "tracker would survive an edit that changes its content",
            verdictOn(libraryFile, snapshot),
        )
    }

    /** Forces `Widget.part`'s reference at `depth() == 0`, exactly as a hover or a completion would. */
    private fun forceTheMemberReferenceOutsideAnyFrame(context: PsiFile) {
        runReadAction {
            val widget = LuaTypeManager.getInstance(project).resolveType("Widget", context)
            val part = (widget as? LuaClassType)?.getMembers()?.get("part")?.type
            println(
                "TYPE11-DR07 pre-force depth=${LuaTypeSourceRecorder.depth()} " +
                    "part=${part?.javaClass?.simpleName} members=${part?.getMembers()?.keys}",
            )
        }
    }

    /** The fields the snapshot believes `libWidget.part` has — `[spin]` before the edit, `[spun]` after. */
    private fun partFieldsIn(libraryFile: VirtualFile): Set<String> =
        runReadAction {
            val widget =
                (snapshotOf(libraryFile).getGlobalType("libWidget") as? LuaGraphType.Union)
                    ?.types
                    ?.filterIsInstance<LuaGraphType.Table>()
                    ?.firstOrNull()
            val part = widget?.getMembers()?.get("part")?.write
            (part as? LuaGraphType.Table)?.getMembers()?.keys.orEmpty()
        }

    /** Renames the project field and asserts the library snapshot followed it rather than being pinned. */
    private fun assertTheRenameReachesTheLibrarySnapshot(
        label: String,
        libraryFile: VirtualFile,
        projectClass: PsiFile,
    ) {
        val before = snapshotOf(libraryFile)
        println("TYPE11-DR07 $label before=${partFieldsIn(libraryFile)}")
        rewriteAssertingRootsAreStill(projectClass, rewrittenProjectText)
        val after = snapshotOf(libraryFile)
        val fieldsAfter = partFieldsIn(libraryFile)
        println("TYPE11-DR07 $label after=$fieldsAfter sameSnapshot=${before === after}")
        assertFalse("$label: the edited project file must not leave the library snapshot in place", before === after)
        assertEquals("$label: the library snapshot must see the renamed project field", setOf("spun"), fieldsAfter)
    }

    /** Arm 1 — nothing forced the reference first, so the build itself resolves it and records it. */
    fun testTheReferenceIsFlattenedInsideTheFrameWhenNothingForcedItFirst() {
        myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        assertTheProjectFileReachedTheFrame("arm1 cold", libraryFile)
    }

    /** Arm 2 — a read at depth 0 forced it first; the replayed frame must make that indistinguishable. */
    fun testTheReferenceIsAlreadyForcedWhenSomethingReadItFirst() {
        myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        forceTheMemberReferenceOutsideAnyFrame(consumer)
        assertTheProjectFileReachedTheFrame("arm2 pre-forced", libraryFile)
    }

    /** Arm 3 — the end-to-end consequence: a pre-forced reference must not buy a stale type. */
    fun testWhetherTheEscapedPinSurvivesAnEditToTheProjectFileItDependsOn() {
        val projectClass = myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        forceTheMemberReferenceOutsideAnyFrame(consumer)
        println(describe("arm3 pre-forced", libraryFile))
        assertTheRenameReachesTheLibrarySnapshot("arm3", libraryFile, projectClass)
    }

    /** Arm 4 — the control for arm 3, identical but for the pre-force. */
    fun testTheSameEditIsSeenWhenNothingForcedTheReferenceFirst() {
        val projectClass = myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        println(describe("arm4 cold", libraryFile))
        assertTheRenameReachesTheLibrarySnapshot("arm4", libraryFile, projectClass)
    }
}
