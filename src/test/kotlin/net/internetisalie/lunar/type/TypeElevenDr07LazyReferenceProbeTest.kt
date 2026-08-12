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
 * TYPE-11 DR-07 — Risk 1.3, **measured reachable. This class reproduces BUG-434 and is not a gate.**
 *
 * `materializeClass` builds every `@field` member type and every supertype as a
 * `LuaTypeReference(name, decl)` whose `resolved` is `by lazy`, and the enclosing `LuaClassType` is
 * memoized in `typeCache` beside the frame recorded at **its** materialization.
 * `LuaGraphType.fromLuaType` flattens those references during a snapshot build
 * (`LuaGraphType.kt:251`) — but a `by lazy` that was already forced never calls the manager again,
 * so it never reaches `resolveType`, the cache hit that would have replayed the frame never happens,
 * and the frame open at that moment learns nothing about the file the reference resolved into.
 *
 * The arms differ **only** in whether the reference was forced once before the build, and that alone
 * flips the verdict (`main` @ `bf715eb2`):
 *
 * ```
 * arm1 cold       urls=[lib.lua, gadget.lua]  pinnable=false
 * arm2 pre-forced urls=[lib.lua]              pinnable=true
 * arm3 pre-forced before=[spin] after=[spin] sameSnapshot=true    <- stale
 * arm4 cold       before=[spin] after=[spun] sameSnapshot=false
 * ```
 *
 * Attribution proven by mutation: with `LuaTypeReference.resolved`'s `by lazy` replaced by a plain
 * `get()`, every arm reports `urls=[lib.lua, gadget.lua]`, `pinnable=false`, `after=[spun]`.
 *
 * ⚠ **These cases print; they do not assert**, because asserting today's behaviour would lock the
 * defect in. When BUG-434 is fixed, arm 2 must print `pinnable=false` and arm 3 `after=[spun]
 * sameSnapshot=false`, and these prints become assertions.
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

    /** Arm 1 — nothing forced the reference first, so the build itself resolves it and records it. */
    fun testTheReferenceIsFlattenedInsideTheFrameWhenNothingForcedItFirst() {
        myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        println(describe("arm1 cold", libraryFile))
    }

    /** Arm 2 — a read at depth 0 forced it first, so the build gets a memoized answer and no report. */
    fun testTheReferenceIsAlreadyForcedWhenSomethingReadItFirst() {
        myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        forceTheMemberReferenceOutsideAnyFrame(consumer)
        println(describe("arm2 pre-forced", libraryFile))
    }

    /** Arm 3 — the end-to-end consequence: is the wrongly-granted pin a stale type the user reads? */
    fun testWhetherTheEscapedPinSurvivesAnEditToTheProjectFileItDependsOn() {
        val projectClass = myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        val consumer = myFixture.configureByText("consumer.lua", "local pad = 1\n")
        forceTheMemberReferenceOutsideAnyFrame(consumer)
        println(describe("arm3 pre-forced", libraryFile))
        val before = snapshotOf(libraryFile)
        println("TYPE11-DR07 arm3 before=${partFieldsIn(libraryFile)}")
        rewriteAssertingRootsAreStill(projectClass, rewrittenProjectText)
        val after = snapshotOf(libraryFile)
        println("TYPE11-DR07 arm3 after=${partFieldsIn(libraryFile)} sameSnapshot=${before === after}")
    }

    /** Arm 4 — the control for arm 3, identical but for the pre-force. */
    fun testTheSameEditIsSeenWhenNothingForcedTheReferenceFirst() {
        val projectClass = myFixture.addFileToProject("gadget.lua", projectText)
        val libraryFile = installLibrary()
        println(describe("arm4 cold", libraryFile))
        val before = snapshotOf(libraryFile)
        println("TYPE11-DR07 arm4 before=${partFieldsIn(libraryFile)}")
        rewriteAssertingRootsAreStill(projectClass, rewrittenProjectText)
        val after = snapshotOf(libraryFile)
        println("TYPE11-DR07 arm4 after=${partFieldsIn(libraryFile)} sameSnapshot=${before === after}")
    }
}
