package net.internetisalie.lunar.lang.types

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import net.internetisalie.lunar.lang.psi.LuaFuncCall
import net.internetisalie.lunar.lang.psi.LuaNameRef
import net.internetisalie.lunar.lang.psi.types.LuaGraphType
import net.internetisalie.lunar.lang.psi.types.LuaTypeManagerImpl
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * TYPE-13-00-DR-01 probe. Asks one question per receiver shape: starting from the receiver
 * expression of a colon call, does the STRUCTURAL route reach the declaring `function X:m()`
 * without consulting `LuaGraphType.Table.className`?
 *
 * Reports rather than asserts — the outcome is the de-risking result, not a contract.
 */
@RunWith(JUnit4::class)
class Type13Dr01StructuralReachProbe : BasePlatformTestCase() {
    private fun probe(
        label: String,
        fileName: String,
        source: String,
    ): String {
        val file = myFixture.configureByText(fileName, source)
        return runReadAction {
            val call =
                PsiTreeUtil
                    .findChildrenOfType(file, LuaFuncCall::class.java)
                    .lastOrNull { it.nameAndArgsList.firstOrNull()?.methodExpr != null }
                    ?: return@runReadAction "$label: NO COLON CALL FOUND"
            val methodExpr = call.nameAndArgsList.first().methodExpr!!
            val memberName = methodExpr.text.removePrefix(":").trim()
            val receiver: PsiElement = call.varOrExp

            val types = LuaTypesSnapshot.forFile(file)

            // The graph is keyed on a specific element; try every plausible handle for the
            // receiver rather than assuming which one carries the node.
            val candidates = linkedMapOf<String, PsiElement>("varOrExp" to receiver)
            receiver.firstChild?.let { candidates["varOrExp.firstChild(${it::class.java.simpleName})"] = it }
            PsiTreeUtil.findChildrenOfType(receiver, LuaNameRef::class.java).forEachIndexed { i, ref ->
                candidates["nameRef[$i]='${ref.text}'"] = ref
            }

            buildString {
                append("$label | member='$memberName' | receiverText='${receiver.text}'\n")
                candidates.forEach { (how, el) ->
                    val graphType = types.getValueType(el)
                    val luaType = types.graphTypeToLuaType(graphType)
                    val member = luaType.resolveMember(memberName)
                    val src = member?.sourceElement
                    append("    via $how")
                    append(" -> graphType=${graphType::class.simpleName}")
                    append(" className=${classNameOf(graphType) ?: "null"}")
                    append(" structural=${structuralMemberNames(graphType)}")
                    append(" resolveMember=${if (member == null) "MISS" else "HIT"}")
                    append(" sourceElement=${src?.let { it::class.java.simpleName + "@" + it.textOffset } ?: "null"}\n")
                }
                // Decisive for the fix shape: does the member's graph node already carry the
                // declaration PSI that LuaTypeMember drops on the floor?
                val gt = types.getValueType(candidates.values.last())
                memberNodeOf(gt, memberName)?.let { node ->
                    val el = node.element
                    append("    MEMBER NODE element=${el::class.java.simpleName}@${el.textOffset}")
                    append(" text='${el.text.take(24).replace("\n", " ")}'")
                    append(" parent=${el.parent?.let { it::class.java.simpleName } ?: "null"}\n")
                } ?: append("    MEMBER NODE: none\n")

                // The documented nominal route (AGENTS.md): recurse into a Union for the first
                // non-null className, then resolve the class by name. Probing it separately so a
                // MISS above is not misread as the nominal route failing.
                val nominalName = classNameOf(types.getValueType(candidates.values.last()))
                if (nominalName != null) {
                    val cls = LuaTypeManagerImpl(project).resolveType(nominalName, file)
                    val m = cls?.resolveMember(memberName)
                    append("    via NOMINAL resolveType('$nominalName')")
                    append(" -> ${if (m == null) "MISS" else "HIT"}")
                    append(
                        " sourceElement=${m?.sourceElement?.let {
                            it::class.java.simpleName + "@" + it.textOffset
                        } ?: "null"}\n",
                    )
                }
            }.trimEnd()
        }
    }

    private fun memberNodeOf(
        t: LuaGraphType,
        name: String,
    ): net.internetisalie.lunar.lang.psi.types.VariableNode? =
        when (t) {
            is LuaGraphType.Table -> t.getMembers()[name]
            is LuaGraphType.Union -> t.types.firstNotNullOfOrNull { memberNodeOf(it, name) }
            else -> null
        }

    private fun classNameOf(t: LuaGraphType): String? =
        when (t) {
            is LuaGraphType.Table -> t.className
            is LuaGraphType.Union -> t.types.firstNotNullOfOrNull { classNameOf(it) }
            else -> null
        }

    private fun structuralMemberNames(t: LuaGraphType): List<String> =
        when (t) {
            is LuaGraphType.Table -> (t.localMembers.keys + t.superTypes.flatMap { structuralMemberNames(it) }).toList()
            is LuaGraphType.Union -> t.types.flatMap { structuralMemberNames(it) }.distinct()
            else -> emptyList()
        }

    @Test
    fun dr01ReportPerReceiverShape() {
        val lines = mutableListOf<String>()

        lines +=
            probe(
                "A annotated-control",
                "a.lua",
                """
                ---@class Builder
                local Builder = {}
                function Builder:setName(n) end
                local b = Builder
                b:setName("x")
                """.trimIndent(),
            )

        lines +=
            probe(
                "B plain-local-table",
                "b.lua",
                """
                local t = {}
                function t:m() end
                t:m()
                """.trimIndent(),
            )

        lines +=
            probe(
                "C global-table",
                "c.lua",
                """
                Obj = {}
                function Obj:m() end
                Obj:m()
                """.trimIndent(),
            )

        lines +=
            probe(
                "D setmetatable-OO",
                "d.lua",
                """
                local Class = {}
                Class.__index = Class
                function Class:m() end
                local o = setmetatable({}, Class)
                o:m()
                """.trimIndent(),
            )

        lines +=
            probe(
                "E chained-second-segment",
                "e.lua",
                """
                ---@class B
                local B = {}
                ---@return B
                function B:m1() end
                function B:m2() end
                local x = B
                x:m1():m2()
                """.trimIndent(),
            )

        println("=== TYPE-13 DR-01 RESULTS ===")
        lines.forEach { println(it) }
        println("=== END TYPE-13 DR-01 ===")

        // The three findings TYPE-13's design is built on. Asserted so the plan cannot go stale
        // silently: if the engine's behaviour moves, this fails and the design is re-examined.
        val report = lines.joinToString("\n")

        // 1. The un-annotated shapes resolve structurally, with no className.
        assertTrue(
            "B plain-local-table must resolve m structurally",
            report.contains(Regex("B plain-local-table[\\s\\S]*?nameRef\\[0]='t'.*resolveMember=HIT")),
        )
        assertTrue(
            "C global-table must resolve m structurally",
            report.contains(Regex("C global-table[\\s\\S]*?nameRef\\[0]='Obj'.*resolveMember=HIT")),
        )

        // 2. And they reach a DECLARATION node, which is what makes the declaration walk possible.
        assertTrue(
            "B's member node must be the declaration's :m (LuaFuncNameMethod)",
            report.contains(Regex("B plain-local-table[\\s\\S]*?MEMBER NODE element=LuaFuncNameMethodImpl")),
        )
        assertTrue(
            "C's member node must be the declaration's :m (LuaFuncNameMethod)",
            report.contains(Regex("C global-table[\\s\\S]*?MEMBER NODE element=LuaFuncNameMethodImpl")),
        )

        // 3. The trap: setmetatable OO resolves to the CALL SITE, not the declaration. TYPE-13-05
        // exists because of this line. When it starts failing, the merge has been fixed.
        assertTrue(
            "D setmetatable-OO's member node is still the call-site LuaMethodExpr (TYPE-13-05 not yet fixed)",
            report.contains(Regex("D setmetatable-OO[\\s\\S]*?MEMBER NODE element=LuaMethodExprImpl")),
        )
    }
}
