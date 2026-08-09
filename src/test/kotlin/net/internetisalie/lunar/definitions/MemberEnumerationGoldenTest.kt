package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.insight.LuaOverrideLineMarkerProvider
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import java.io.File

/**
 * COMP-09's golden: **what member enumeration returns today**, recorded before anything replaces it.
 *
 * This is the hard gate `implementation-plan.md` puts in front of Phase 1. The natural
 * implementation of an index-backed enumeration returns a **superset** — the eager path carries
 * scope and file-confinement rules (`MethodScan.onlyIn`, BUG-398) that an index key does not — and a
 * superset silently turns enumeration into a new type source, which is the experiment BUG-395
 * already ran and reverted after it regressed four suites at once (BUG-397).
 *
 * Three properties make it a golden rather than a dump:
 *
 * 1. **Every row names its DOOR.** `resolveGlobal` and `resolveType` are two doors to one room and
 *    they disagree — `wx` resolves through both with different types, `wxFrame` and `AllColon`
 *    through only one, and on `Shapes` the global door hoists a grandchild (`deep`) that does not
 *    exist at the path it is offered on (BUG-430). A golden collapsed with
 *    `resolveGlobal(r) ?: resolveType(r)` is therefore not merely imprecise: it is silently
 *    door-dependent, and it would certify a known defect as the contract. Design §1.4/§4.4a.
 * 2. **Every BINDING SHAPE is present** — see [Comp09GoldenFixture]. Member style is what three
 *    de-risking rounds varied while missing three real defects; binding shape is what found them.
 * 3. **The `sourceElement` case is recorded.** `LuaTypeMember.sourceElement` is load-bearing —
 *    `LuaOverrideLineMarkerProvider` navigates to it — and a golden of names and types cannot see a
 *    change to it (`LuaTypeManagerImpl:256-262` says so in as many words).
 *
 * The completion rows are the third door, and they are what Phase 2's exit diffs against: today's
 * cross-file path takes the *first* declaring file only, so a union is a superset that Phase 3's
 * materialization diff would not catch.
 */
class MemberEnumerationGoldenTest : LibraryRootTestCase() {
    override fun getTestDataPath(): String = System.getProperty("user.dir")

    fun testGoldenIsUnchanged() {
        registerLibraryRoot(Comp09GoldenFixture.files())
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val rendered = render()
        println(BANNER)
        println(rendered)
        println(BANNER)
        val recorded = File(testDataPath, GOLDEN_PATH)
        assertTrue("the golden is missing at ${recorded.path}; regenerate it from the run above", recorded.isFile)
        assertEquals(
            "member enumeration moved. Every difference is either a declared expectation " +
                "(requirements.md TC 7c, design §4.5a) or a regression — decide which, in writing, " +
                "before re-recording ${recorded.path}",
            recorded.readText().trim(),
            rendered.trim(),
        )
    }

    private fun render(): String =
        buildString {
            HEADER.forEach { appendLine(it) }
            Comp09GoldenFixture.receivers.forEach { receiver ->
                appendLine("${receiver.name}|shape|${receiver.shape}")
                memberRows(receiver.name, Door.GLOBAL).forEach { appendLine(it) }
                memberRows(receiver.name, Door.CLASS).forEach { appendLine(it) }
                completionRows(receiver.name).forEach { appendLine(it) }
            }
            overrideRows().forEach { appendLine(it) }
        }

    /** The two type-engine doors, kept apart on purpose. Design §4.4a. */
    private enum class Door { GLOBAL, CLASS }

    private fun memberRows(
        receiverName: String,
        door: Door,
    ): List<String> =
        runReadAction {
            val typeManager = LuaTypeManager.getInstance(project)
            val contextFile = myFixture.file
            val resolvedType =
                when (door) {
                    Door.GLOBAL -> typeManager.resolveGlobal(receiverName, contextFile)
                    Door.CLASS -> typeManager.resolveType(receiverName, contextFile)
                }
            val label = door.name.lowercase()
            if (resolvedType == null) {
                return@runReadAction listOf("$receiverName|$label|<unresolvable>")
            }
            val rows =
                LuaGraphType
                    .materialize(resolvedType, contextFile)
                    .getMembers()
                    .map { (memberName, memberNode) ->
                        "$receiverName|$label|$memberName:${memberNode.write.displayName()}"
                    }
            rows.sorted().ifEmpty { listOf("$receiverName|$label|<none>") }
        }

    /** The user-visible door: what `R.<caret>` offers. Phase 2's exit diffs against these rows. */
    private fun completionRows(receiverName: String): List<String> {
        val offered = completionsFor("$receiverName.<caret>\n").sorted()
        return offered.ifEmpty { listOf("<none>") }.map { "$receiverName|completion|$it" }
    }

    /**
     * `LuaOverrideLineMarkerProvider.findSuperMembers` is the one consumer that reads
     * [net.internetisalie.lunar.lang.psi.types.LuaTypeMember.sourceElement], so the golden records
     * the source PSI class and its file — a name-and-type golden cannot see this move.
     *
     * `onClose` is the `@field`-declared signature (no `LuaFuncDecl` → the provider draws an
     * *Implement* icon); `Show` is the concrete colon method (→ *Override*); `ownFn` is declared only
     * on `Derived` and must find nothing.
     */
    private fun overrideRows(): List<String> =
        runReadAction {
            OVERRIDE_CASES.flatMap { (className, methodName) ->
                val superMembers =
                    LuaOverrideLineMarkerProvider.findSuperMembers(className, methodName, myFixture.file)
                if (superMembers.isEmpty()) {
                    listOf("override|$className:$methodName|<none>")
                } else {
                    superMembers.map { member ->
                        val source = member.sourceElement
                        val sourceClass = source?.let { it::class.simpleName } ?: "<no source element>"
                        val sourceFile = source?.containingFile?.name ?: "-"
                        "override|$className:$methodName|${member.name}|$sourceClass|$sourceFile"
                    }
                }
            }
        }

    private companion object {
        const val GOLDEN_PATH = "src/test/resources/comp09/member-enumeration.golden"
        const val BANNER = "===== COMP-09 GOLDEN ====="

        val OVERRIDE_CASES =
            listOf(
                "Derived" to "Show",
                "Derived" to "onClose",
                "Derived" to "inheritedFn",
                "Derived" to "ownFn",
            )

        val HEADER =
            listOf(
                "# COMP-09 golden — member enumeration AS IT BEHAVES TODAY.",
                "# Regenerated by MemberEnumerationGoldenTest; one fact per line so a movement is a",
                "# one-line diff in review.",
                "#",
                "# <receiver>|global|<member>:<type>      resolveGlobal -> materialize -> getMembers",
                "# <receiver>|class|<member>:<type>       resolveType   -> materialize -> getMembers",
                "# <receiver>|completion|<lookupString>   what `R.<caret>` offers",
                "# override|<class>:<method>|<member>|<sourceElement class>|<file>",
                "#",
                "# The two doors are recorded SEPARATELY and never collapsed with `?:` (design §1.4,",
                "# §4.4a): two of these receivers resolve through only one door, and on `Shapes` the",
                "# global door hoists `deep` from `Shapes.nested.deep` — BUG-430, a defect this file",
                "# records rather than endorses. COMP-09 preserves the CLASS door.",
            )
    }
}
