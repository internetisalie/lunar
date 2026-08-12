package net.internetisalie.lunar.lang.indexing

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import net.internetisalie.lunar.definitions.LibraryRootTestCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * COMP-09 Phase 1 — [LuaReceiverMemberIndex]'s four indexer sources, its wire format, and the two
 * entry points design §4.5 leaves standing. Nothing consumes the index yet; this is what says it is
 * correct before anything does.
 *
 * **A `LibraryRootTestCase`, not a light fixture, and that is load-bearing.** A light fixture's whole
 * project is inside `GlobalSearchScope.projectScope`, so §4.5's projectScope-then-allScope precedence
 * — the rule BUG-427 exists for — is structurally unobservable there. BUG-395 and BUG-398 both
 * shipped green against light fixtures while the running IDE completed nothing.
 *
 * These cases replace `CompNineDr09Test`/`CompNineDr09bTest`, the DR-09 harnesses, which printed
 * their findings rather than asserting them. Every case they covered is re-homed: the externalizer
 * round-trip and the four golden receivers here, DR-09's D2 union-versus-first-file question in
 * [testTheTwoDoorsDisagreeAboutASecondDeclaringFile], its file-local contamination half — Risk 1.1's
 * measured firing shape — in [testAFileLocalReceiverIsNotASelectableDeclaringFile], and DR-09b's
 * `deep` finding in [testANestedQualifierContributesNoEntry], whose conclusion design §4.4a already
 * records as BUG-430.
 *
 * The contamination half was for one commit attributed to `CompNineDr14Test`'s local-receiver arm.
 * That method has **no assertion** — it prints a verdict — so the claim was false and the case was
 * uncovered until the method above was written. Printing is not a gate, including when the thing
 * being credited to a printout is this feature's own primary risk.
 */
class LuaReceiverMemberIndexTest : LibraryRootTestCase() {
    // ------------------------------------------------------------------ the wire format

    /**
     * Ordinals are only safe because `getVersion()` gates the format, and the non-ASCII name is here
     * because `writeUTF` is modified UTF-8: a Lua identifier cannot contain one today, but an index
     * value read back wrong is silent, so the format is pinned rather than assumed.
     */
    fun testTheExternalizerRoundTripsEveryFieldIncludingNonAscii() {
        val original =
            listOf(
                LuaReceiverMember("Show", LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.COLON),
                LuaReceiverMember("wxID_ANY", LuaReceiverMember.Kind.FIELD, LuaReceiverMember.Separator.DOT),
                LuaReceiverMember("wxFileExists", LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.DOT),
                LuaReceiverMember("ünïcødé", LuaReceiverMember.Kind.FIELD, LuaReceiverMember.Separator.DOT),
            )
        assertEquals("kind and separator must survive, not only the name", original, roundTrip(original))
    }

    /** The empty list is a real index value — a receiver whose only entry was the opacity sentinel. */
    fun testTheExternalizerRoundTripsAnEmptyList() {
        assertEquals(emptyList<LuaReceiverMember>(), roundTrip(emptyList()))
    }

    private fun roundTrip(value: List<LuaReceiverMember>): List<LuaReceiverMember> {
        val externalizer = LuaReceiverMemberIndex().valueExternalizer
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { externalizer.save(it, value) }
        return DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use { externalizer.read(it) }
    }

    // ------------------------------------------------------------------ the four sources

    /** Sources 1 and 2 on one receiver: a declared function, an assigned field, an assigned table. */
    fun testDotMembersAreIndexedWithTheirKindAndSeparator() {
        seedGolden()
        assertEquals(
            listOf("aliasedFn", "assignedFn", "caption", "nested", "wxFileExists", "wxID_ANY"),
            namesOf("wx"),
        )
        assertMember("wx", dotFunction("wxFileExists"))
        assertMember("wx", dotField("wxID_ANY"))
    }

    /**
     * Source 1's colon form. DR-06: the existing stub sink is dot-only, so `C:m` has no receiver key
     * at all today — the separator has to be derived at index time, and it has to survive the wire.
     */
    fun testColonMembersKeepTheColonSeparator() {
        seedGolden()
        assertMember(
            "wxFrame",
            LuaReceiverMember("Show", LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.COLON),
        )
        assertMember("wxFrame", dotFunction("staticCount"))
    }

    /**
     * The sharpest form of DR-06, and the case design §1.3's "strict simplification" would have
     * silently emptied: a receiver with no dot member anywhere.
     */
    fun testAnAllColonReceiverStillEnumerates() {
        seedGolden()
        assertEquals(listOf("alpha", "beta"), namesOf("AllColon"))
        assertTrue(
            "every member of AllColon is colon-declared, so a dot-only derivation returns nothing",
            unionMembersOf("AllColon").all { it.separator == LuaReceiverMember.Separator.COLON },
        )
    }

    /**
     * The nested-qualifier rule, preserved from `LuaTypeManagerImpl.memberNameOf`: a member has
     * exactly one separator, so `Shapes.nested.deep = 1` contributes `nested` to `Shapes` and
     * nothing else — not `deep` to `Shapes`, and not a key for `nested`.
     *
     * Today's **global** door does hoist `deep` onto `Shapes`; that is BUG-430 (design §4.4a), a
     * defect COMP-09 deliberately does not reproduce, and this is the assertion that says so.
     */
    fun testANestedQualifierContributesNoEntry() {
        seedGolden()
        assertEquals(listOf("direct", "nested", "plain"), namesOf("Shapes"))
        assertEquals(
            "`Shapes.nested.deep = 1` must not key the intermediate name either",
            emptyList<String>(),
            namesOf("nested"),
        )
        assertTrue("a keyed suffix `Shapes[1]` is not a member name", "1" !in namesOf("Shapes"))
    }

    /**
     * **The input filter must accept exactly what `LuaFileType` is registered for.**
     *
     * `plugin.xml` registers it for `extensions="lua;rockspec"` *and*
     * `fileNames=".luacheckrc;.busted"`; this index's filter used to read `file.extension == "lua"`,
     * so three of those four registrations were silently unindexed. It survived every gate the
     * feature has — the golden fixture is all `.lua`, and so is the corpus tree — because a *subset*
     * is invisible to a guard built to catch a superset (design §4.9, Risk 1.1).
     *
     * Measured on the shape below before the fix: the `@class` door returned
     * `[fromLua, same]` where the `getAllKeys` scan it replaced returned
     * `[fromBusted, fromLua, fromLuacheckrc, fromRockspec, same]` — the stub builder runs for the
     * *file type*, so the scan saw all four registrations and the index saw one.
     *
     * **Both doors, and they legitimately disagree.** The union door reads the index across the whole
     * scope and must now see all four. The completion door must **not**, and that is not this defect:
     * it selects its declaring file through `LuaGlobalAssignmentIndex`, whose own filter is still
     * `extension == "lua"`, so only `multi.lua` is ever a candidate. That filter is pre-existing and
     * out of COMP-09's scope; this asserts today's completion behaviour so that closing it elsewhere
     * is a deliberate, visible move rather than a side effect.
     */
    fun testEveryFileTypeRegistrationIsIndexed() {
        registerLibraryRoot(
            mapOf(
                "multi.lua" to "---@meta\n\nMulti = {}\n\nfunction Multi.fromLua() end\n\nreturn Multi\n",
                "multi.rockspec" to "function Multi.fromRockspec() end\n",
                ".luacheckrc" to "function Multi.fromLuacheckrc() end\n",
                ".busted" to "function Multi.fromBusted() end\n",
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(
            "the union door must see every registration LuaFileType carries, not only `.lua`",
            listOf("fromBusted", "fromLua", "fromLuacheckrc", "fromRockspec"),
            namesOf("Multi"),
        )
        assertEquals(
            "the completion door picks ONE declaring file and gets its candidates from " +
                "LuaGlobalAssignmentIndex, which is still `.lua`-only — pre-existing, out of scope, " +
                "and pinned here so that changing it is deliberate",
            listOf("fromLua"),
            globalNamesOf("Multi"),
        )
    }

    /** Source 3 — `---@field`, with `Kind` taken from the declared type text. */
    fun testClassFieldsAreIndexedAndTypedFromTheirDeclaredType() {
        seedGolden()
        assertMember("wxFrame", dotField("title"))
        assertMember("wxFrame", dotFunction("onClose"))
        assertMember("wx", dotField("caption"))
    }

    /**
     * Design §4.3's D3 residue, bounded and gated rather than assumed away: `= function() end` is
     * syntactically classifiable, `= someOtherFn` is not, so the second is recorded `FIELD`. It is
     * wrong, it is known, and COMP-09-08's gate is where its cost shows up.
     */
    fun testADirectFunctionLiteralIsAFunctionAndAnAliasIsAField() {
        seedGolden()
        assertMember("wx", dotFunction("assignedFn"))
        assertMember("wx", dotField("aliasedFn"))
    }

    // ------------------------------------------------------------------ the two doors

    /**
     * DR-09's D2, as an assertion rather than a printout. The completion door takes the **first
     * declaring file** — the `LuaGlobalAssignmentIndex` candidate, which `two-b.lua` is not, because
     * it declares no bare `R` — and materialization takes the union.
     *
     * Collapsing the two is defect D2: at the completion call site the union invents members.
     */
    fun testTheTwoDoorsDisagreeAboutASecondDeclaringFile() {
        registerLibraryRoot(
            mapOf(
                "two-a.lua" to "---@meta\n\nR = {}\n\nfunction R.fromA() end\n\nreturn R\n",
                "two-b.lua" to "---@meta\n\nfunction R.fromB() end\n",
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals("the completion door reads the first declaring file only", listOf("fromA"), globalNamesOf("R"))
        assertEquals("materialization wants every declaring file — BUG-399", listOf("fromA", "fromB"), namesOf("R"))
    }

    /**
     * **Risk 1.1's measured firing shape, as an assertion.** `risks-and-gaps.md:41-46` records that a
     * flat `membersOf(receiver, allScope)` union returned `[alsoPrivate, privateToThisFile, real]`
     * against a golden of `[real]` — "the extras came from an unrelated file-local `wx`". That is the
     * defect the whole of design §4.5 was rewritten around, and until this method existed it was
     * asserted nowhere: [testTheTwoDoorsDisagreeAboutASecondDeclaringFile] uses two files that both
     * declare a **global**, and `CompNineDr14Test.testDr14LocalReceiverIsNotSelectable` — cited as
     * holding this half — contains no assertion at all.
     *
     * The rule that saves it: selection comes from `LuaGlobalAssignmentIndex`, and a file-local
     * `local wx = {}` never enters that index, so `unrelated.lua` is not a **selectable declaring
     * file** however many `wx` members it contributes to *this* index. The union is not merely a
     * superset here — take the wrong door and the answer is contaminated by a receiver that is a
     * different object.
     */
    fun testAFileLocalReceiverIsNotASelectableDeclaringFile() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to "---@meta\n\n---@class wx\nwx = {}\n\nfunction wx.real() end\n\nreturn wx\n",
                "unrelated.lua" to
                    "local wx = {}\n\nfunction wx.privateToThisFile() end\n\n" +
                    "function wx.alsoPrivate() end\n\nreturn wx\n",
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        assertEquals(
            "a file-local `wx` is absent from LuaGlobalAssignmentIndex and therefore not a " +
                "selectable declaring file — the completion door must return the global's members alone",
            listOf("real"),
            globalNamesOf("wx"),
        )
        assertEquals(
            "and the union door is exactly the contaminated set risks-and-gaps.md Risk 1.1 measured, " +
                "which is why the two doors may never be collapsed (defect D2)",
            listOf("alsoPrivate", "privateToThisFile", "real"),
            namesOf("wx"),
        )
    }

    /**
     * BUG-427's precedence, which is why this class needs a real library root: a project declaration
     * beats a library one, and `allScope` is only consulted when `projectScope` yields nothing.
     */
    fun testTheCompletionDoorPrefersAProjectDeclarationOverALibraryOne() {
        myFixture.addFileToProject("proj.lua", "R = {}\n\nfunction R.fromProject() end\n")
        myFixture.configureByText("consumer.lua", "local x = 1\n")
        registerLibraryRoot(mapOf("lib.lua" to "---@meta\n\nR = {}\n\nfunction R.fromLibrary() end\n\nreturn R\n"))
        assertEquals(listOf("fromProject"), globalNamesOf("R"))
        assertEquals("the union is scope-blind by design", listOf("fromLibrary", "fromProject"), namesOf("R"))
    }

    /**
     * Design §4.5's BL-8, which DR-14 never exercised because its `exclude` declared no receiver.
     *
     * During completion the PSI file is a **copy**, and a copy does not share the original's
     * `VirtualFile` — so `exclude?.virtualFile != vf` matches nothing and the exclusion silently
     * never fires. The consumer must therefore pass `containingFile?.originalFile`, exactly as
     * `doResolveGlobal:143` does. Both arms are asserted: the copy is shown *not* to exclude, which
     * is the failure the contributor has to avoid, and the original is shown to exclude.
     */
    fun testExcludeFiresForTheOriginalFileAndNotForACompletionCopy() {
        myFixture.configureByText("b.lua", "R = {}\n\nfunction R.fromB() end\n")
        registerLibraryRoot(mapOf("a.lua" to "---@meta\n\nR = {}\n\nfunction R.fromA() end\n\nreturn R\n"))
        val here = myFixture.file
        val copy = here.copy() as PsiFile
        assertSame("the platform sets originalFile on a copy — the contributor relies on it", here, copy.originalFile)
        assertEquals(
            "excluding the context file must fall through to the library declaration",
            listOf("fromA"),
            globalNamesOf("R", here),
        )
        assertEquals(
            "passing the copy instead of its originalFile makes the exclusion silently never fire — " +
                "design §4.5's BL-8, asserted so a consumer cannot reintroduce it unnoticed",
            listOf("fromB"),
            globalNamesOf("R", copy),
        )
    }

    // ------------------------------------------------------------------ fixture and helpers

    /**
     * The four receivers design §4.4 tabulates, in the binding shapes it names. Kept in one fixture
     * so that a receiver's absence is a real absence rather than a fixture that was never seeded.
     */
    private fun seedGolden() {
        registerLibraryRoot(
            mapOf(
                "wx.lua" to
                    """
                    ---@meta

                    ---@class wx
                    ---@field caption string
                    wx = {}

                    ---@type number
                    wx.wxID_ANY = nil

                    ---@param filename string
                    ---@return boolean
                    function wx.wxFileExists(filename) end

                    wx.assignedFn = function() end

                    local helper = function() end
                    wx.aliasedFn = helper

                    wx.nested = {}

                    return wx
                    """.trimIndent(),
                "wxframe.lua" to
                    """
                    ---@meta

                    ---@class wxFrame
                    ---@field title string
                    ---@field onClose fun(self: wxFrame): nil
                    local wxFrame = {}

                    ---@param show boolean
                    ---@return boolean
                    function wxFrame:Show(show) end

                    ---@return number
                    function wxFrame.staticCount() end
                    """.trimIndent(),
                "allcolon.lua" to
                    """
                    ---@meta

                    ---@class AllColon
                    local AllColon = {}

                    ---@return string
                    function AllColon:alpha() end

                    ---@return string
                    function AllColon:beta() end
                    """.trimIndent(),
                "shapes.lua" to
                    """
                    ---@meta

                    ---@class Shapes
                    Shapes = {}

                    Shapes.nested = {}
                    Shapes.nested.deep = 1

                    Shapes.direct = 2

                    Shapes[1] = "keyed"

                    function Shapes.plain() end
                    """.trimIndent(),
            ),
        )
        myFixture.configureByText("consumer.lua", "local x = 1\n")
    }

    private fun dotField(name: String) =
        LuaReceiverMember(name, LuaReceiverMember.Kind.FIELD, LuaReceiverMember.Separator.DOT)

    private fun dotFunction(name: String) =
        LuaReceiverMember(name, LuaReceiverMember.Kind.FUNCTION, LuaReceiverMember.Separator.DOT)

    /**
     * The **union** door. Named for the door it exercises because `membersOf` is a retired name
     * (design §4.5's two-door table: "no method named `membersOf` survives"): a helper that does not
     * say which of the two doors it opens is the ambiguity defects D1, D2 and B2 all came from.
     */
    private fun unionMembersOf(receiver: String): List<LuaReceiverMember> =
        runReadAction { LuaReceiverMemberIndex.membersIn(receiver, project, GlobalSearchScope.allScope(project)) }

    private fun namesOf(receiver: String): List<String> = unionMembersOf(receiver).map { it.name }.sorted()

    private fun globalNamesOf(
        receiver: String,
        exclude: PsiFile? = null,
    ): List<String> =
        runReadAction {
            LuaReceiverMemberIndex.membersOfGlobal(receiver, project, exclude).map { it.name }.sorted()
        }

    private fun assertMember(
        receiver: String,
        expected: LuaReceiverMember,
    ) {
        val declared = unionMembersOf(receiver)
        val found = declared.firstOrNull { it.name == expected.name }
        assertEquals("$receiver.${expected.name} among $declared", expected, found)
    }
}
