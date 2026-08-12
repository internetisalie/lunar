package net.internetisalie.lunar.definitions

import com.intellij.openapi.application.runReadAction
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberWork
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaFunctionType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManager
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember

/**
 * COMP-09 Phase 3 — **the four behaviours `addMethodsOf` had to keep** when its candidate members
 * stopped coming from a scan of every `LuaGlobalDeclarationIndex` key and started coming from
 * `LuaReceiverMemberIndex.membersIn` (design §4.6).
 *
 * One test per row of §4.6's preservation table, because each rule lived in a *different* line of
 * the scan and the rewrite relocates them unevenly — two stay in `addMethodsOf`, one moves into a
 * helper, and one moves out of this file entirely:
 *
 * | rule | where it lived | where it lives now |
 * | :-- | :-- | :-- |
 * | `allScope`, not projectScope (BUG-399) | the scope passed to `getElements` | the scope passed to **both** `membersIn` and `getElements` |
 * | first-wins within a receiver | the `containsKey` guard | unchanged |
 * | file confinement for a local-declared class (BUG-398) | the `onlyIn` filter | `declaredMethod` |
 * | nested qualifiers are not members | `memberNameOf` | derived at **index** time (§4.3's `split`) |
 *
 * The last row is why these are not merely re-run of the existing class-door tests: the rule is no
 * longer enforced by code in `LuaTypeManagerImpl` at all, so a test that only exercised the old
 * function's arithmetic would now be testing nothing. Each test below asserts the *observable*
 * class-door membership, which is where the rule has to survive whichever file it moved to.
 *
 * [LibraryRootTestCase], not `BasePlatformTestCase`: a light fixture's own files are entirely inside
 * `GlobalSearchScope.projectScope`, so the BUG-399 row is structurally invisible without a real
 * library root — the blind spot that let BUG-395/398/399 each ship green.
 */
class MemberEnumerationMaterializationTest : LibraryRootTestCase() {
    /**
     * Row 1 — **BUG-399.** `function Widget.fromLibrary` is declared in a library file, which is
     * outside `projectScope` and inside `allScope`. A rewrite that asked either the receiver index
     * or the stub index in project scope offers nothing here, and the running IDE completes nothing
     * against a bundled stub or a definition library while the suite stays green.
     */
    fun testLibraryDeclaredMethodIsAMember() {
        registerLibraryRoot(
            mapOf(
                "widget.lua" to
                    """
                    ---@class Widget
                    local Widget = {}

                    ---@return string
                    function Widget.fromLibrary() end
                    """.trimIndent(),
            ),
        )
        val members = classMembers("Widget")
        assertTrue(
            "a method declared in a LIBRARY file is a class member (BUG-399) — allScope, not " +
                "projectScope. Offered: ${members.keys.sorted()}",
            "fromLibrary" in members,
        )
    }

    /**
     * Row 2 — **first-wins within a receiver**, asserted from both sides of the `containsKey` guard.
     *
     * `declared` is already in the map when `addMethodsOf` runs (it is an `@field`), so a `function`
     * declaration of that name must not replace it. `shared` is contested between the two scans
     * `collectMethodMembers` makes — the class name first, then the declaring local's name — and the
     * class name must win. Last-wins would pass an existence check on both and is exactly the drift
     * a name-only assertion cannot see, so both assert on the resolved *type*.
     */
    fun testFirstWinsWithinAReceiver() {
        registerLibraryRoot(
            mapOf(
                "widget.lua" to
                    """
                    ---@class Widget
                    ---@field declared string
                    local w = {}

                    ---@return number
                    function w.declared() end

                    ---@return number
                    function w.shared() end
                    """.trimIndent(),
                "widget-extra.lua" to
                    """
                    ---@return string
                    function Widget.shared() end
                    """.trimIndent(),
            ),
        )
        val members = classMembers("Widget")
        assertEquals(
            "an `@field` member is in the map before addMethodsOf runs, so the same-named function " +
                "declaration must not overwrite it",
            "string",
            members["declared"]?.type?.name,
        )
        assertEquals(
            "the class-name scan runs before the declaring local's scan, so `Widget.shared` must " +
                "win over `w.shared` — first-wins, not last-wins",
            "string",
            (members["shared"]?.type as? LuaFunctionType)?.returnType?.name,
        )
    }

    /**
     * Row 3 — **BUG-398.** A match on the class name is honoured project-wide because a class name
     * is a global namespace; a match on the *declaring local's* name is confined to that local's own
     * file, where the variable exists. `other.lua`'s `w` is a different variable that happens to
     * share a name, and the receiver index cannot tell them apart — it is keyed by text — so the
     * confinement has to survive on the consumer side or every same-named local in the project
     * donates its methods to the class.
     */
    fun testDeclaringLocalsNameIsConfinedToItsOwnFile() {
        registerLibraryRoot(
            mapOf(
                "widget.lua" to
                    """
                    ---@class Widget
                    local w = {}

                    function w.inDeclaringFile() end
                    """.trimIndent(),
                "other.lua" to
                    """
                    local w = {}

                    function w.inAnotherFile() end
                    """.trimIndent(),
            ),
        )
        val members = classMembers("Widget")
        assertTrue(
            "the declaring local's own file still contributes. Offered: ${members.keys.sorted()}",
            "inDeclaringFile" in members,
        )
        assertFalse(
            "an unrelated local named `w` in another file must not donate its methods to Widget " +
                "(BUG-398). Offered: ${members.keys.sorted()}",
            "inAnotherFile" in members,
        )
    }

    /**
     * Row 4 — **nested qualifiers are not members.** `function Widget.nested.deep` declares a member
     * of `Widget.nested`, not of `Widget`, and the old `memberNameOf` rejected it by counting
     * separators. That rule now lives at index time (§4.3's `split`), so this is the row most able
     * to regress silently: nothing in `LuaTypeManagerImpl` enforces it any more.
     *
     * This is also the shape of BUG-430 — the *global* door hoists `deep` onto the root. The `@class`
     * door does not, and COMP-09 preserves the door each consumer serves (§4.4a).
     */
    fun testNestedQualifiersAreNotMembers() {
        registerLibraryRoot(
            mapOf(
                "widget.lua" to
                    """
                    ---@class Widget
                    local Widget = {}

                    function Widget.plain() end

                    function Widget.nested.deep() end
                    """.trimIndent(),
            ),
        )
        val members = classMembers("Widget")
        assertTrue(
            "a single-separator member is still found. Offered: ${members.keys.sorted()}",
            "plain" in members,
        )
        assertFalse(
            "a grandchild is not a member of the root — the BUG-430 shape, which the @class door " +
                "must not acquire. Offered: ${members.keys.sorted()}",
            "deep" in members,
        )
        assertFalse(
            "and it must not arrive under its qualified name either. Offered: ${members.keys.sorted()}",
            "nested.deep" in members,
        )
        assertFalse(
            "nor is the intermediate qualifier synthesized from a function declaration. " +
                "Offered: ${members.keys.sorted()}",
            "nested" in members,
        )
    }

    /**
     * **COMP-09-09 at the materialization door** — design §4.10b assertion 2, moved from the index
     * to the consumer that Phase 3 converted.
     *
     * The existing `MemberEnumerationWorkBoundGateTest` asserts the bound on `membersIn` itself,
     * which was already index-backed and already green; requirements.md records that "the redness
     * COMP-09-09 is really about lives at the *consumers*". This is that assertion, taken through
     * `resolveType` — so it observes what `addMethodsOf` actually asks for, not what the index is
     * capable of. Under the `getAllKeys` scan this receiver's enumeration visited every key in the
     * project, so adding forty unrelated receivers moved the count by four thousand.
     *
     * **Guarded against vacuity two ways.** `resolveType` is memoized in `typeCache`, so a cached
     * second answer would leave the thread-local counter holding the *first* arm's numbers and the
     * comparison would pass having measured nothing — hence the `> 0` assertion on the noisy arm,
     * which only holds if `membersIn` genuinely ran again. And the counter is read inside the read
     * action, on the thread that recorded into it (`LuaReceiverMemberWork` is a `ThreadLocal`).
     */
    fun testMaterializationWorkDoesNotMoveWhenUnrelatedContentIsAdded() {
        registerLibraryRoot(
            mapOf(
                "widget.lua" to
                    buildString {
                        append("---@class Widget\nlocal Widget = {}\n\n")
                        repeat(METHOD_COUNT) { i -> append("function Widget.m$i() end\n\n") }
                    },
            ),
        )
        val quiet = measureClassDoor()
        registerLibraryRoot(
            (0 until NOISE_RECEIVERS).associate { r ->
                "noise$r.lua" to
                    buildString {
                        append("---@meta\n\n$NOISE_PREFIX$r = {}\n\n")
                        repeat(NOISE_MEMBERS) { i -> append("function $NOISE_PREFIX$r.n$i() end\n\n") }
                    }
            },
        )
        val noisy = measureClassDoor()
        println("COMP-09-09 @class door: quiet=$quiet noisy=$noisy")
        assertTrue(
            "the noisy arm read nothing, so `resolveType` was served from `typeCache` and this " +
                "comparison measured the quiet arm twice — the assertion below would pass vacuously",
            noisy.entries > 0,
        )
        assertEquals(
            "adding ${NOISE_RECEIVERS * NOISE_MEMBERS} unrelated indexed members changed how much " +
                "work materializing `Widget` does — the @class door is scanning, not looking up",
            quiet.entries,
            noisy.entries,
        )
        assertEquals("and it must not have widened its file set either", quiet.files, noisy.files)
        assertEquals(
            "the bound is the receiver's OWN declared members, not the size of the key space",
            METHOD_COUNT,
            quiet.entries,
        )
    }

    private data class Traversal(
        val entries: Int,
        val files: Int,
    )

    /** What one `@class`-door materialization asked the receiver index for. */
    private fun measureClassDoor(): Traversal {
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val contextFile = myFixture.file
        return runReadAction {
            LuaReceiverMemberWork.reset()
            LuaTypeManager.getInstance(project).resolveType("Widget", contextFile)
            Traversal(LuaReceiverMemberWork.entries, LuaReceiverMemberWork.files)
        }
    }

    /** The `@class` door's own membership — `resolveType`, never collapsed with `resolveGlobal`. */
    private fun classMembers(className: String): Map<String, LuaTypeMember> {
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        val contextFile = myFixture.file
        return runReadAction {
            val resolved = LuaTypeManager.getInstance(project).resolveType(className, contextFile)
            assertTrue("$className must resolve through the @class door, but got $resolved", resolved is LuaClassType)
            (resolved as LuaClassType).localMembers
        }
    }

    private companion object {
        const val METHOD_COUNT = 50
        const val NOISE_PREFIX = "Noise"
        const val NOISE_RECEIVERS = 40
        const val NOISE_MEMBERS = 100
    }
}
