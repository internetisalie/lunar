package net.internetisalie.lunar.corpus.audit

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.psi.LuaFile
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.types.LuaPrimitiveType
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/** MAINT-54 TC-2 to TC-19: the audit harness on fixtures whose engine behaviour DR-01/01b/01c/01d measured. */
@RunWith(JUnit4::class)
class TypeAuditHarnessTest : IndexedBasePlatformTestCase() {
    private val noCheckout = File("/nonexistent-type-audit-checkout")

    @Test
    fun testTC02ClassifyDeclaredKinds() {
        val file = myFixture.addFileToProject("kinds.lua", KINDS)
        val comment = requireNotNull(PsiTreeUtil.findChildrenOfType(file, LuaFuncDecl::class.java).last().catsComment)
        val classified = KIND_TEXTS.map { AuditKinds.classify(it, comment)?.let(::describe) }
        val primitive = "PRIMITIVE"
        val expected =
            listOf(
                primitive,
                primitive,
                "TABLE",
                "TABLE",
                "RECORD",
                "CLASS",
                null,
                "ARRAY",
                "UNION",
                "PRIMITIVE optional",
                "PRIMITIVE optional",
                "FUNCTION",
                "FUNCTION",
                "GENERIC bound=PRIMITIVE:string",
                null,
            )
        assertEquals(expected, classified)
    }

    @Test
    fun testTC03PickSpacesSitesEvenly() {
        assertEquals(listOf(0, 2, 5, 7), AuditSelection.pick(10, 4))
        assertEquals(listOf(0, 1, 2), AuditSelection.pick(3, 4))
    }

    @Test
    fun testTC04GlobalCallSitesIncludeArity() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pTwo('a', 'b')\n")
        val expected =
            listOf(
                "arg.primitive PROJECT_FILE GLOBAL ['a']@5 -> true / \"ctl\"",
                "arg.primitive PROJECT_FILE GLOBAL ['b']@10 -> true / \"ctl\"",
                "arity.tooMany PROJECT_FILE GLOBAL []@13 -> , true / null",
                "arity.tooFew PROJECT_FILE GLOBAL [, 'b']@8 ->  / null",
            )
        assertEquals(expected.sorted(), scan(caller).sites.map { render(caller, it) }.sorted())
    }

    @Test
    fun testTC05EveryShapeIsEmitted() {
        myFixture.addFileToProject("defs.lua", "$DEFS\n---@param f fun(a: number, b: number)\nfunction pFun2(f) end\n")
        val caller = myFixture.addFileToProject("caller.lua", SHAPES_CALLER)
        val sites = scan(caller).sites.filterNot { it.cell.shape.startsWith("arity.") }
        assertEquals(SHAPES_EXPECTED.sorted(), sites.map { render(caller, it) }.sorted())
        val returned = sites.single { it.cell.shape == "return.mismatch" }
        assertEquals("return 'x'", caller.text.substring(returned.site.startOffset, returned.site.endOffset))
        assertEquals("local function r() return 'x' end", returned.declRange?.substring(caller.text))
        val field = sites.single { it.cell.shape == "field.assign" }
        assertEquals("b.n = 1", field.site.substring(caller.text))
    }

    @Test
    fun testTC06CrossFileMethodsResolveThroughTheGlobal() {
        myFixture.addFileToProject("defs.lua", DEFS)
        myFixture.addFileToProject("svc.lua", SVC)
        val caller = myFixture.addFileToProject("caller.lua", "Box:m('a')\nSvc:run(1, 'a')\n")
        val scanned = scan(caller)
        val sites = scanned.sites.filterNot { it.cell.shape.startsWith("arity.") }
        assertEquals(
            listOf("'a'", "1", "'a'").map { "arg.primitive PROJECT_FILE METHOD [$it]" },
            sites
                .sortedBy {
                    it.edit.startOffset
                }.map { "${it.cell.shape} ${it.cell.path} ${it.cell.form} [${it.edit.substring(caller.text)}]" },
        )
        assertEquals(0, scanned.coverage.unresolvedMethod)
    }

    @Test
    fun testTC07LocalHostedFieldTagIsFound() {
        val lbox = myFixture.addFileToProject("lbox.lua", LBOX)
        val host = PsiTreeUtil.findChildrenOfType(lbox, LuaLocalVarDecl::class.java).first()
        val member = LuaTypeMember("n", LuaPrimitiveType.NUMBER, sourceElement = host)
        val tag = AuditSiteFinder.fieldTag(member, "n")
        assertEquals("number", tag?.argType?.text)
        assertEquals("n", tag?.fieldDescriptor?.argName?.text)
        val caller = myFixture.addFileToProject("caller.lua", "---@param b LBox\nlocal function f(b) b.n = 1 end\n")
        val fields = scan(caller).sites.filter { it.cell.shape == "field.assign" }
        assertEquals(listOf(CalleePath.PROJECT_FILE), fields.map { it.cell.path })
    }

    @Test
    fun testTC08CallCountersSumToCalls() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller =
            myFixture.addFileToProject(
                "caller.lua",
                "pStr 'a'\npTable {}\nnoSuchHelper('a')\nnope(1)\npTwo('a')\npStr('a')\n-- @param x number\n",
            )
        val coverage = scan(caller).coverage
        assertEquals(6, coverage.calls)
        assertEquals(2, coverage.stringOrTableArgs)
        assertEquals(2, coverage.unresolvedCallee)
        assertEquals(1, coverage.arityGuard)
        assertEquals(1, coverage.eligible)
        assertEquals(coverage.calls, callCounterSum(coverage))
        assertEquals(1, coverage.ldocParam)
    }

    @Test
    fun testTC09WrongArgumentIsReportedAtTheSite() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pStr('okay')\npNum('x')\n")
        val before = caller.text
        val record = AuditInjector(myFixture).measure(siteAt(caller, "arg.primitive", "'okay'"))
        assertEquals(AuditOutcome.REPORTED, record.outcome)
        assertEquals(
            listOf(
                AuditEntry("ERROR", AuditInjector.ASSIGN_RULE, "boolean is not assignable to string", Anchor.AT_SITE),
            ),
            record.newErrors,
        )
        assertEquals(before, caller.text)
    }

    @Test
    fun testTC10CompatibleEditIsSilent() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pStr('okay')\npNum('x')\n")
        val site = siteAt(caller, "arg.primitive", "'okay'").copy(wrong = "'other'")
        val record = AuditInjector(myFixture).measure(site)
        assertEquals(AuditOutcome.SILENT, record.outcome)
        assertEquals(emptyList<AuditEntry>(), record.newErrors)
    }

    @Test
    fun testTC11PreexistingErrorSkipsTheSite() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pNum('x')\n")
        val record = AuditInjector(myFixture).measure(siteAt(caller, "arg.primitive", "'x'"))
        assertEquals(AuditOutcome.SKIPPED_PREEXISTING, record.outcome)
    }

    @Test
    fun testTC12SilentLenientSiteIsByDesign() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pTable({})\n")
        val record = AuditInjector(myFixture).measure(siteAt(caller, "lenient.primitiveForTable", "{}"))
        assertEquals(AuditOutcome.LENIENT_BY_DESIGN, record.outcome)
    }

    @Test
    fun testTC13SameFileErrorsAreAnchored() {
        val file = myFixture.addFileToProject("kdefs.lua", "$KDEFS\nkNum(1)\n")
        val site = siteAt(file, "arg.primitive", "1")
        assertEquals(CalleePath.SAME_FILE, site.cell.path)
        val record = AuditInjector(myFixture).measure(site)
        assertEquals(AuditOutcome.REPORTED, record.outcome)
        assertEquals(listOf("AT_DECL", "AT_PARAM", "BODY"), record.newErrors.map { it.anchor.name }.sorted())
    }

    @Test
    fun testTC14NoisyControlAndBodyNoise() {
        val file = myFixture.addFileToProject("kdefs.lua", "$KDEFS\nkNum(1)\n")
        val injector = AuditInjector(myFixture)
        val site = siteAt(file, "arg.primitive", "1")
        assertEquals(AuditOutcome.NOISY, injector.measure(site.copy(control = "'x'")).outcome)
        val atSite = AuditEntry("ERROR", AuditInjector.ASSIGN_RULE, "m", Anchor.AT_SITE)
        val body = AuditEntry("ERROR", AuditInjector.ASSIGN_RULE, "m", Anchor.BODY)
        assertEquals(AuditOutcome.REPORTED, injector.outcome(site, listOf(atSite), listOf(body)))
    }

    @Test
    fun testTC15DeclaringFileErrorsAreNotShifted() {
        myFixture.addFileToProject("defs.lua", "$DEFS\npNum('x')\n")
        val caller = myFixture.addFileToProject("caller.lua", "pStr('okay')\n")
        val record = AuditInjector(myFixture).measure(siteAt(caller, "arg.primitive", "'okay'"))
        assertEquals(AuditOutcome.REPORTED, record.outcome)
        assertEquals(emptyList<AuditEntry>(), record.newErrors.filter { it.anchor == Anchor.DECLARING_FILE })
    }

    /**
     * Risks-and-gaps DR-01e: every table argument to `number[]` already errors on this engine, `{}`
     * included, so the argument is an untyped name and the control is original — the only way to
     * reach an `arg.array` site whose wrong edit raises nothing but the `ret=true` error.
     */
    @Test
    fun testTC16OtherRuleIsDistinguished() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pArray(list)\n")
        val site = siteAt(caller, "arg.array", "list").copy(wrong = "function() end", control = null)
        assertEquals(AuditOutcome.REPORTED_OTHER_RULE, AuditInjector(myFixture).measure(site).outcome)
    }

    @Test
    fun testTC17BadEditRangeIsFailed() {
        myFixture.addFileToProject("defs.lua", DEFS)
        val caller = myFixture.addFileToProject("caller.lua", "pStr('ok')\n")
        val before = caller.text
        val site = siteAt(caller, "arg.primitive", "'ok'").copy(edit = TextRange(10_000, 10_001))
        assertEquals(AuditOutcome.FAILED, AuditInjector(myFixture).measure(site).outcome)
        assertEquals(before, caller.text)
    }

    @Test
    fun testTC18RevertMismatchIsThrown() {
        val document = EditorFactory.getInstance().createDocument("a")
        val thrown = runCatching { AuditInjector(myFixture).verifyReverted(document, "b") }.exceptionOrNull()
        assertEquals("RevertMismatch", thrown?.javaClass?.simpleName)
    }

    @Test
    fun testTC19RenderMatchesTheGoldenSample() {
        val golden = File(System.getProperty("user.dir"), "tooling/corpus/testdata/type_audit_sample.json")
        assertEquals(golden.readText(), AuditOutput.render(samplePayload()))
    }

    private fun scan(file: PsiFile): AuditScan = AuditSiteFinder.scan(file as LuaFile, noCheckout)

    private fun siteAt(
        file: PsiFile,
        shape: String,
        editText: String,
    ): AuditSite = scan(file).sites.single { it.cell.shape == shape && it.edit.substring(file.text) == editText }

    private fun render(
        file: PsiFile,
        site: AuditSite,
    ): String =
        "${site.cell.shape} ${site.cell.path} ${site.cell.form} [${site.edit.substring(
            file.text,
        )}]@${site.edit.startOffset} " +
            "-> ${site.wrong} / ${site.control}"

    private fun describe(kind: DeclaredKind): String {
        val optional = if (kind.optional) " optional" else ""
        val bound = kind.bound?.let { " bound=${it.kind}:${it.text}" }.orEmpty()
        return "${kind.kind}$optional$bound"
    }

    private fun callCounterSum(coverage: AuditCoverage): Int =
        with(coverage) {
            stringOrTableArgs + unresolvedCallee + unresolvedMethod + formMismatch + noTag + arityGuard +
                eligible
        }

    private fun samplePayload(): AuditPayload {
        val primitive = AuditCell("arg.primitive", CalleePath.PROJECT_FILE, CalleeForm.GLOBAL)
        val union = AuditCell("arg.union", CalleePath.SAME_FILE, CalleeForm.LOCAL)
        val lenient = AuditCell("lenient.primitiveForTable", CalleePath.PROJECT_FILE, CalleeForm.GLOBAL)
        val coverage = AuditCoverage(10, 1, 2, 1, 0, 3, 1, 2, 1, 4)
        val cells = listOf(AuditPopulation(primitive, 9), AuditPopulation(union, 1), AuditPopulation(lenient, 2))
        return AuditPayload(1, "sample", SAMPLE_COMMIT, 4, coverage, cells, sampleRecords(primitive, union, lenient))
    }

    private fun sampleRecords(
        primitive: AuditCell,
        union: AuditCell,
        lenient: AuditCell,
    ): List<AuditRecord> {
        val reported =
            AuditEntry("ERROR", AuditInjector.ASSIGN_RULE, "boolean is not assignable to string", Anchor.AT_PARAM)
        val otherRule =
            AuditEntry(
                "ERROR",
                AuditInjector.RETURN_RULE,
                "boolean is not assignable to union number | string",
                Anchor.AT_DECL,
            )
        val file = "lua/sample.lua"
        return listOf(
            AuditRecord(primitive, file, 3, "string", "true", AuditOutcome.REPORTED, listOf(reported)),
            AuditRecord(union, file, 5, "string|number", "true", AuditOutcome.REPORTED_OTHER_RULE, listOf(otherRule)),
            AuditRecord(primitive, file, 7, "number", "true", AuditOutcome.SILENT, emptyList()),
            AuditRecord(primitive, file, 9, "boolean", "\"audit\"", AuditOutcome.NOISY, emptyList()),
            AuditRecord(primitive, file, 11, "string", "true", AuditOutcome.SKIPPED_PREEXISTING, emptyList()),
            AuditRecord(primitive, file, 0, "integer", "true", AuditOutcome.FAILED, emptyList()),
            AuditRecord(lenient, file, 13, "table", "true", AuditOutcome.LENIENT_BY_DESIGN, emptyList()),
        )
    }

    private companion object {
        const val SAMPLE_COMMIT = "0123456789abcdef0123456789abcdef01234567"

        /** Risks-and-gaps DR-01's fixture. */
        val DEFS =
            """
            ---@class Box
            ---@field n number
            Box = {}
            ---@param s string
            function Box:m(s) end
            ---@param s string
            function pStr(s) end
            ---@param n number
            function pNum(n) end
            ---@param b boolean
            function pBool(b) end
            ---@param t table
            function pTable(t) end
            ---@param t {a: number}
            function pRecord(t) end
            ---@param b Box
            function pClass(b) end
            ---@param xs number[]
            function pArray(xs) end
            ---@param u string|number
            function pUnion(u) end
            ---@param f fun()
            function pFun(f) end
            ---@param s? string
            function pOpt(s) end
            ---@generic T : string
            ---@param v T
            function pGen(v) end
            ---@param a string
            ---@param b string
            function pTwo(a, b) end
            """.trimIndent()

        /** Risks-and-gaps DR-01b's fixture. */
        val KDEFS =
            """
            ---@class Box
            ---@field n number
            Box = {}
            ---@param s string
            function Box:m(s) return s:upper() end
            ---@param s string
            function kStr(s) return s:upper() end
            ---@param t table
            function kTab(t) return t.k end
            ---@param n number
            function kNum(n) return n + 1 end
            ---@param s string
            function kCat(s) local x = s .. "!" return #s + #x end
            ---@param b Box
            function kBox(b) return b.n + 1 end
            """.trimIndent()

        /** Risks-and-gaps DR-01d's callee files. */
        val SVC =
            """
            ---@class Svc
            Svc = {}
            ---@param n number
            ---@param s string
            function Svc:run(n, s) return n + #s end
            ---@param x number
            function Svc.stat(x) return x end
            """.trimIndent()

        val LBOX =
            """
            ---@class LBox
            ---@field n number
            local LBox = {}
            ---@param s string
            function LBox:lm(s) end
            return LBox
            """.trimIndent()

        val KIND_TEXTS =
            listOf(
                "string",
                "boolean",
                "table",
                "table<string, number>",
                "{a: number}",
                "Box",
                "Unknown",
                "number[]",
                "string|number[]",
                "string|nil",
                "string?",
                "fun(x: number)",
                "function",
                "T",
                "any",
            )

        val KINDS =
            (
                listOf("---@class Box", "---@field n number", "Box = {}", "---@generic T : string") +
                    KIND_TEXTS.mapIndexed { index, text -> "---@param p$index $text" } +
                    "function kinds(${KIND_TEXTS.indices.joinToString(", ") { "p$it" }}) end"
            ).joinToString("\n", postfix = "\n")

        val SHAPES_CALLER =
            """
            pUnion(1)
            pArray({1})
            pFun(function() end)
            pFun2(print)
            pGen('s')
            pOpt('s')
            pClass(Box)
            ---@return string
            local function r() return 'x' end
            ---@param b Box
            local function f(b) b.n = 1 end
            """.trimIndent() + "\n"

        val SHAPES_EXPECTED =
            listOf(
                "arg.union PROJECT_FILE GLOBAL [1]@7 -> true / 7",
                "arg.array PROJECT_FILE GLOBAL [{1}]@17 -> true / {}",
                "arg.function PROJECT_FILE GLOBAL [function() end]@27 -> 42 / function() end",
                "arg.function PROJECT_FILE GLOBAL [print]@49 -> 42 / function(p1, p2) end",
                "arg.generic PROJECT_FILE GLOBAL ['s']@61 -> true / \"ctl\"",
                "arg.optional PROJECT_FILE GLOBAL ['s']@71 -> true / \"ctl\"",
                "arg.class PROJECT_FILE GLOBAL [Box]@83 -> function() end / null",
                "lenient.primitiveForTable PROJECT_FILE GLOBAL [Box]@83 -> true / null",
                "return.mismatch SAME_FILE LOCAL ['x']@132 -> true / \"ctl\"",
                "field.assign PROJECT_FILE FIELD [1]@182 -> true / 7",
            )
    }
}
