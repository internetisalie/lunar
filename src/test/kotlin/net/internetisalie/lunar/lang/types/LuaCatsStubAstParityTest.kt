package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import net.internetisalie.lunar.lang.LuaParserDefinition
import net.internetisalie.lunar.lang.indexing.LuaClassNameIndex
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaAliasType
import net.internetisalie.lunar.lang.psi.types.LuaClassType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import net.internetisalie.lunar.luacats.lang.psi.LuaCatsFieldTag
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * MAINT-34-05: the stub and AST paths must agree about what a LuaCATS tag *means*.
 *
 * Every LuaCATS tag that feeds the type engine is read twice — once by `*StubElementType.createStub`
 * at index time, once by `LuaTypeManagerImpl` from live PSI — and which one runs depends only on
 * whether the file's AST happens to be loaded. They have drifted three times already (BUG-400,
 * BUG-401, BUG-402), and the drift is invisible to the repo's ordinary fixture idiom: measured,
 * `configureByText` takes the **AST** branch and `addFileToProject` takes the **STUB** branch, so a
 * test written the usual way exercises one arm and silently claims to cover both. Two of BUG-401's
 * own three regression tests passed with the bug present for exactly this reason.
 *
 * Each case is therefore materialized twice and compared. Two structural rules make that real:
 *
 * - **Arm-distinct class names.** `doResolveType` searches `allScope` and hands *every* matching
 *   declaration to `materializeClass`, which merges them into one result — so two same-named
 *   fixtures produce a single blend with no stub arm and no AST arm. `typeCache` is keyed on the
 *   name alone, which would additionally make the second resolution a cache hit and the comparison
 *   a tautology. Every identifier that must differ carries the `__` sentinel, replaced with
 *   `<arm><caseIndex>`.
 * - **Per-arm branch assertions.** Each arm asserts `decls.size == 1` and the branch it actually
 *   took (`stub != null` / `stub == null`) *before* asserting anything about the type. The
 *   fixture→branch mapping is measured, not contracted; without these the harness would degrade
 *   silently into running the AST branch twice, which is the failure mode it exists to prevent.
 */
@RunWith(JUnit4::class)
class LuaCatsStubAstParityTest : IndexedBasePlatformTestCase() {

    private data class ParityCase(
        val id: String,
        val source: String,
        val className: String,
        val expectedMembers: Set<String>,
        val expectedSupertypes: List<String>,
        val expectedMemberTypeContains: Map<String, String> = emptyMap(),
    )

    /**
     * `__` is the arm sentinel. A literal replace, never a [Regex] one — `$` in a replacement is a
     * group reference there. `__` is chosen over `$` because the fixture idiom is the raw string,
     * where `$` would need `${'$'}`.
     */
    private fun substitute(text: String, arm: String, index: Int) = text.replace("__", arm + index)

    /**
     * Renders a list with per-element delimiters, and every assertion here compares rendered forms
     * rather than the lists themselves.
     *
     * This is not decoration. BUG-402 splits `Base<string, number>` into the two fragments
     * `Base<string` and `number>`, and a list's default `toString` joins on `", "` — so the broken
     * two-element list and the correct one-element list both print as `[Base<string, number>]`.
     * Measured: the first run of this harness failed with
     * `expected:<[Base<string, number>]> but was:<[Base<string, number>]>`, which is a correct
     * verdict reported unreadably. Quoting each element makes the fragments visible.
     */
    private fun render(names: List<String>) = names.joinToString(", ", "[", "]") { "\"$it\"" }

    /** Resolves [case] on both arms and asserts they agree with expectations and each other. */
    private fun assertParity(case: ParityCase, index: Int) {
        val stubSource = substitute(case.source, "S", index)
        val astSource = substitute(case.source, "A", index)
        val stubName = substitute(case.className, "S", index)
        val astName = substitute(case.className, "A", index)

        myFixture.addFileToProject("stub$index.lua", stubSource)
        // Opened second, so the stub-arm file is never opened and stays stub-backed. `usage` is
        // used only for project and scope, so it is a safe context for both arms — taking one from
        // inside the added file would load its AST and destroy the stub arm.
        val usage = myFixture.configureByText("ast$index.lua", astSource)

        val stubType = runReadAction { resolveArm(case, stubName, "STUB", usage) }
        val astType = runReadAction { resolveArm(case, astName, "AST", usage) }

        assertArm(case, index, stubType, "STUB")
        assertArm(case, index, astType, "AST")

        // The parity assertion proper. Names are arm-qualified, so the AST arm's are mapped back
        // onto the stub arm's before comparing.
        assertEquals(
            "${case.id}: the two arms must expose the same members",
            render(stubType.getMembers().keys.sorted()),
            render(astType.getMembers().keys.map { it.replace("A$index", "S$index") }.sorted()),
        )
        assertEquals(
            "${case.id}: the two arms must expose the same supertypes",
            render(stubType.superTypes.map { it.name }),
            render(astType.superTypes.map { it.name.replace("A$index", "S$index") }),
        )
    }

    /** Locates the declaration, asserts the arm took the branch it claims, then resolves. */
    private fun resolveArm(case: ParityCase, name: String, arm: String, usage: com.intellij.psi.PsiFile): LuaClassType {
        val decls = StubIndex.getElements(
            LuaClassNameIndex.KEY,
            name,
            project,
            GlobalSearchScope.allScope(project),
            LuaLocalVarDecl::class.java,
        )
        assertEquals("${case.id}/$arm: expected exactly one declaration of $name", 1, decls.size)
        val stub = decls.first().stub
        if (arm == "STUB") {
            assertNotNull("${case.id}/$arm: addFileToProject must leave the decl stub-backed", stub)
        } else {
            assertNull("${case.id}/$arm: configureByText must leave the decl AST-backed", stub)
        }
        // A fresh manager per arm: typeCache is keyed on the name alone.
        val resolved = LuaTypeManagerImpl(project).resolveType(name, usage)
        assertTrue("${case.id}/$arm: $name must resolve to a class, was $resolved", resolved is LuaClassType)
        return resolved as LuaClassType
    }

    private fun assertArm(case: ParityCase, index: Int, type: LuaClassType, arm: String) {
        val armOf = { s: String -> substitute(s, if (arm == "STUB") "S" else "A", index) }
        assertEquals(
            "${case.id}/$arm: members",
            render(case.expectedMembers.map(armOf).sorted()),
            render(type.getMembers().keys.sorted()),
        )
        assertEquals(
            "${case.id}/$arm: supertypes",
            render(case.expectedSupertypes.map(armOf)),
            render(type.superTypes.map { it.name }),
        )
        case.expectedMemberTypeContains.forEach { (member, needle) ->
            val resolvedMember = type.resolveMember(armOf(member))
            assertNotNull("${case.id}/$arm: member $member must resolve", resolvedMember)
            assertTrue(
                "${case.id}/$arm: member $member's type must contain '$needle', was '${resolvedMember!!.type.name}'",
                resolvedMember.type.name.contains(needle),
            )
        }
    }

    /** TC-1: an optional `@field` is named without its marker and typed to admit nil (BUG-401). */
    @Test
    fun testFieldsAndOptionalMarkerAgree() {
        assertParity(
            ParityCase(
                id = "TC-1",
                source = """
                    ---@class C__
                    ---@field a string
                    ---@field b? number
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = setOf("a", "b"),
                expectedSupertypes = emptyList(),
                expectedMemberTypeContains = mapOf("b" to "nil"),
            ),
            index = 1,
        )
    }

    /**
     * TC-2: a parameterized parent is ONE supertype, not two fragments (BUG-402).
     *
     * The stub flattens `parentTypes.text` and `materializeClass` re-splits it on ',', cutting
     * `Base<string, number>` into `Base<string` and `number>`. The AST branch walks
     * `parentTypes.argTypeList`, which the grammar already split correctly.
     */
    @Test
    @Ignore(
        "BUG-402, fixed by MAINT-34-02. Witnessed on the STUB arm at this commit: " +
            "expected [\"Base<string, number>\"] but was [\"Base<string\", \"number>\"].",
    )
    fun testParameterizedParentIsOneSupertype() {
        assertParity(
            ParityCase(
                id = "TC-2",
                source = """
                    ---@class C__ : Base<string, number>
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = emptySet(),
                expectedSupertypes = listOf("Base<string, number>"),
            ),
            index = 2,
        )
    }

    /** TC-3: two plain parents stay two, in order. Only the names are asserted. */
    @Test
    fun testMultipleParentsAgree() {
        assertParity(
            ParityCase(
                id = "TC-3",
                source = """
                    ---@class C__ : A__, B__
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = emptySet(),
                expectedSupertypes = listOf("A__", "B__"),
            ),
            index = 3,
        )
    }

    /** TC-4: a key-descriptor field lands in `argType`, not `argName`. */
    @Test
    fun testKeyedFieldAgrees() {
        assertParity(
            ParityCase(
                id = "TC-4",
                source = """
                    ---@class C__
                    ---@field [string] number
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = setOf("[string]"),
                expectedSupertypes = emptyList(),
            ),
            index = 4,
        )
    }

    /** TC-5: `fieldScope` is a separate `argKeyword` child and must not leak into either name. */
    @Test
    fun testScopedFieldAgrees() {
        assertParity(
            ParityCase(
                id = "TC-5",
                source = """
                    ---@class C__
                    ---@field private p string
                    ---@field public q number
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = setOf("p", "q"),
                expectedSupertypes = emptyList(),
            ),
            index = 5,
        )
    }

    /**
     * TC-7: `@param`/`@return` through `funcTypeFromStub` — the nominal-layer consumer this feature
     * unifies. `LuaFunctionType.name` renders the whole signature, so one substring covers both.
     */
    @Test
    fun testMethodParamAndReturnAgree() {
        assertParity(
            ParityCase(
                id = "TC-7",
                source = """
                    ---@class C__
                    local C__ = {}

                    ---@param x string
                    ---@return boolean
                    function C__.f(x) end
                """.trimIndent(),
                className = "C__",
                expectedMembers = setOf("f"),
                expectedSupertypes = emptyList(),
                expectedMemberTypeContains = mapOf("f" to "fun(x: string): boolean"),
            ),
            index = 7,
        )
    }

    /** TC-8: inheritance resolves within one file, so members arrive from the parent too. */
    @Test
    fun testInheritedMembersAgree() {
        assertParity(
            ParityCase(
                id = "TC-8",
                source = """
                    ---@class Base__
                    ---@field inherited string
                    local Base__ = {}

                    ---@class C__ : Base__
                    ---@field own number
                    local C__ = {}
                """.trimIndent(),
                className = "C__",
                expectedMembers = setOf("own", "inherited"),
                expectedSupertypes = listOf("Base__"),
            ),
            index = 8,
        )
    }

    /** TC-6: `@alias` — not a class, so it gets its own method rather than a [ParityCase]. */
    @Test
    fun testAliasTargetAgrees() {
        val source = """
            ---@alias Handler__ fun(x: string): number
            local Handler__ = nil
        """.trimIndent()
        myFixture.addFileToProject("stub6.lua", substitute(source, "S", 6))
        val usage = myFixture.configureByText("ast6.lua", substitute(source, "A", 6))

        val targets = listOf("S", "A").map { arm ->
            runReadAction {
                val name = substitute("Handler__", arm, 6)
                val resolved = LuaTypeManagerImpl(project).resolveType(name, usage)
                assertTrue("TC-6/$arm: $name must resolve to an alias, was $resolved", resolved is LuaAliasType)
                (resolved as LuaAliasType).targetType.name
            }
        }
        assertEquals("TC-6/STUB: alias target", "fun(x: string): number", targets[0])
        assertEquals("TC-6: the two arms must agree on the alias target", targets[0], targets[1])
    }

    /**
     * TC-9: MAINT-34-06's acceptance check.
     *
     * Asserted through the singleton — `LuaFileElementType` is an `IStubFileElementType` and the
     * platform's `IElementType` registry has a hard size limit, so instantiating one per test
     * exhausts it and throws `ArrayIndexOutOfBoundsException` during bulk runs.
     */
    @Test
    fun testStubVersionIsCurrent() {
        assertEquals("stub version", 3, LuaParserDefinition.FILE.stubVersion)
    }

    /**
     * TC-10: the un-hosted path accumulates duplicate supertypes, and must keep doing so.
     *
     * Not a parity case — with no host declaration there is no `LuaLocalVarDecl` and so no stub arm.
     * This is the only coverage of `materializeUnhostedClass`'s behaviour, and asserting the
     * *count* is the whole point: a name-based de-duplication would silently make it 1.
     */
    @Test
    fun testUnhostedClassAccumulatesDuplicateSupertypes() {
        myFixture.addFileToProject("unhosted_a.lua", "---@meta\n---@class Shared : P\n")
        myFixture.addFileToProject("unhosted_b.lua", "---@meta\n---@class Shared : P\n")
        val usage = myFixture.configureByText("consumer.lua", "local x = 1\n")

        runReadAction {
            val shared = LuaTypeManagerImpl(project).resolveType("Shared", usage) as? LuaClassType
            assertNotNull("Shared must resolve through the un-hosted path", shared)
            val supers = shared!!.superTypes.map { it.name }
            assertEquals("both tags' parents accumulate — no de-duplication. Was: $supers", 2, supers.size)
            assertTrue("both must be named P. Was: $supers", supers.all { it == "P" })
        }
    }

    /**
     * The un-hosted path merges by two different rules, and neither was covered by any test.
     *
     * WITHIN one comment a repeated `@field` is **last-wins** (the extraction used to accumulate
     * into a map before returning); ACROSS tags it is **first-wins** (`putIfAbsent`, because several
     * `---@class Same` tags merge into one class). Writing the natural-looking single
     * `putIfAbsent`-per-member loop silently turns the first rule into first-wins — which is what
     * MAINT-34's own refactor did on its first draft, with the full suite staying green.
     *
     * Only the within-comment half is asserted, and deliberately so: the across-tags half depends on
     * which declaring file `FileBasedIndex` yields first, which is not a guaranteed order, so
     * asserting it would buy a flaky test rather than coverage. One file keeps this deterministic,
     * and it is the half the refactor actually flipped.
     */
    @Test
    fun testUnhostedDuplicateFieldInOneCommentIsLastWins() {
        myFixture.addFileToProject("dup_a.lua", "---@meta\n---@class DupU\n---@field a string\n---@field a number\n")
        val usage = myFixture.configureByText("dupconsumer.lua", "local x = 1\n")

        runReadAction {
            val dup = LuaTypeManagerImpl(project).resolveType("DupU", usage) as? LuaClassType
            assertNotNull("DupU must resolve through the un-hosted path", dup)
            assertEquals(
                "within one comment a repeated @field is last-wins",
                "number",
                dup!!.resolveMember("a")?.type?.name,
            )
        }
    }

    /**
     * §5.5: `sourceElement` is asymmetric BY DESIGN and load-bearing.
     *
     * `LuaOverrideLineMarkerProvider` uses it as gutter navigation targets, so collapsing the AST
     * path's `@field` tag to the host declaration would silently regress override navigation. The
     * parity assertions above compare names and types only and would not catch it.
     */
    @Test
    fun testSourceElementIsTagOnAstPathAndDeclOnStubPath() {
        val source = """
            ---@class Src__
            ---@field a string
            local Src__ = {}
        """.trimIndent()
        myFixture.addFileToProject("stub99.lua", substitute(source, "S", 99))
        val usage = myFixture.configureByText("ast99.lua", substitute(source, "A", 99))

        runReadAction {
            val stubType = LuaTypeManagerImpl(project).resolveType("SrcS99", usage) as LuaClassType
            val astType = LuaTypeManagerImpl(project).resolveType("SrcA99", usage) as LuaClassType

            assertTrue(
                "stub path: sourceElement is the host declaration, was " +
                    "${stubType.resolveMember("a")?.sourceElement?.javaClass?.simpleName}",
                stubType.resolveMember("a")?.sourceElement is LuaLocalVarDecl,
            )
            assertTrue(
                "AST path: sourceElement is the @field tag, was " +
                    "${astType.resolveMember("a")?.sourceElement?.javaClass?.simpleName}",
                astType.resolveMember("a")?.sourceElement is LuaCatsFieldTag,
            )
        }
    }
}
