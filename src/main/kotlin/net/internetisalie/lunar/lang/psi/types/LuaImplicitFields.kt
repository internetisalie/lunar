package net.internetisalie.lunar.lang.psi.types

import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiTreeUtil
import net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex
import net.internetisalie.lunar.lang.psi.LuaAssignmentStatement
import net.internetisalie.lunar.lang.psi.LuaElementTypes
import net.internetisalie.lunar.lang.psi.LuaExpr
import net.internetisalie.lunar.lang.psi.LuaFuncDecl
import net.internetisalie.lunar.lang.psi.LuaFuncDef
import net.internetisalie.lunar.lang.psi.LuaLocalVarDecl
import net.internetisalie.lunar.lang.psi.LuaTableConstructor
import net.internetisalie.lunar.lang.psi.LuaTerminalExpr
import net.internetisalie.lunar.lang.psi.LuaVar

/**
 * BUG-398: the names a `---@class` is actually written through — the class name plus the local
 * variable each declaration binds it to.
 *
 * The two coincide in the idiomatic `---@class Player` / `local Player = {}`, and every member
 * lookup keyed on the class name alone quietly assumed they always would. LuaCATS libraries
 * routinely separate them — luassert declares `---@class luassert.internal` on `local internal = {}`
 * — and then `function internal.True()` and `internal.are = internal` are written against
 * `internal`, so a class-name-only match found nothing and the class materialized with **no members
 * at all**. Anything inheriting from it inherited nothing.
 *
 * Only the first bound name is taken: the class annotation sits on the declaration as a whole, so a
 * multi-name `local a, b = …` gives no way to say which name carries it, and guessing wider risks
 * pulling an unrelated variable's members in.
 */
internal fun classReceiverNames(
    className: String,
    decls: Collection<LuaLocalVarDecl>,
): Set<String> =
    buildSet {
        add(className)
        decls.forEach { decl -> declaredName(decl)?.let { add(it) } }
    }

/**
 * The first name a `local` declaration binds — **from the stub where there is one**.
 *
 * BUG-438: the AST read below is what de-stubbed the declaring file at the `@class` door, not the
 * walk in [LuaImplicitFields.collect] that runs immediately after it. `attNameList` and `.node` both
 * force the file to parse, and `classReceiverNames` calls this for every declaration before
 * `collect` is even entered — which is why the report's staged probe, whose next reading was taken
 * after `collect` returned, attributed the parse to `collect`. Staging one more reading between the
 * two puts it here: `03afterAllHostedParts=[false]`, `04afterClassReceiverNames=[true]`.
 *
 * `LuaLocalVarStub.names` is built from exactly this expression at stub time
 * (`LuaLocalVarStubElementType.createStub`), so the answer is the same one for the same declaration,
 * reached without loading anything. The AST path stays for a declaration with no stub — one in the
 * file being edited — where it is the only source and the parse is already paid for.
 *
 * SYNTAX-18: a partially-parsed declaration can lack its `nameRef` while the generated getter is
 * declared `@NotNull`, which logs an error rather than returning null. That hazard belongs to the
 * AST path and to stub construction; a stub already built carries whatever name it recorded.
 */
internal fun declaredName(decl: LuaLocalVarDecl): String? =
    decl.stub?.names?.firstOrNull()
        ?: decl.attNameList
            .firstOrNull()
            ?.node
            ?.findChildByType(LuaElementTypes.NAME_REF)
            ?.psi
            ?.text

/**
 * Discovers implicit class fields from assignments `ClassName.field = …` and
 * `self.field = …` (inside a `ClassName` method), adding them as members without
 * overwriting explicit `@field`s (TYPE-02-05).
 *
 * RHS types use light syntactic inference only (no type-graph / `resolveType` call),
 * to avoid materialization-time reentrancy (see TYPE-02-DR-03).
 */
object LuaImplicitFields {
    private const val SELF = "self"

    /**
     * [receivers] are the names the class is written through (see [classReceiverNames]); [files] are
     * the files to scan, which the caller derives from wherever the class is declared. Taking both
     * rather than the declarations themselves is what lets BUG-400's un-hosted `---@class` — declared
     * above a bare global assignment, with no `LuaLocalVarDecl` anywhere — use this too.
     */
    fun collect(
        receivers: Set<String>,
        files: Collection<PsiFile>,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        for (file in files) {
            if (!mayAssignMembers(receivers, file)) continue
            val assignments = PsiTreeUtil.findChildrenOfType(file, LuaAssignmentStatement::class.java)
            for (assignment in assignments) {
                collectFromAssignment(receivers, assignment, into)
            }
        }
    }

    /**
     * BUG-438 — **the walk below loads the AST, so a file with nothing to find must not reach it.**
     *
     * `findChildrenOfType` over a `PsiFile` de-stubs it and then visits every node. Staged
     * `isContentsLoaded` probes through `materializeClass` located the `@class` door's de-stub
     * exactly here — stub-only through `hostedParts`, loaded immediately after this call — making it
     * ~63 % of a door measured at 269 ms against NFR-1's 100 ms.
     *
     * Two conditions have to hold before the walk is skipped, and the first is what makes the second
     * safe:
     *
     * 1. **The AST is not loaded already.** If it is, there is no parse left to avoid and the walk
     *    is nearly free — and, decisively, a file the user is editing always has its AST loaded, so
     *    the index can never be consulted about content newer than itself. Restricting the guard to
     *    unloaded files is therefore not an optimisation, it is the freshness argument.
     * 2. **The index has no assignment mark for these receivers in this file.** That is the same
     *    question this walk answers, asked of content the index built from the same bytes.
     *
     * `SELF` joins the receiver set because rule two of [fieldNameFor] collects `self.field = …`
     * inside a method of the class, and the index keys that under `self` like any other receiver.
     * The index's `self` mark is *wider* than the walk's rule — it does not check the enclosing
     * function — which is the direction that costs a walk rather than a member.
     */
    private fun mayAssignMembers(
        receivers: Set<String>,
        file: PsiFile,
    ): Boolean {
        if (file !is PsiFileImpl || file.isContentsLoaded) return true
        val virtualFile = file.virtualFile ?: return true
        return LuaReceiverMemberIndex.assignsMembersTo(receivers + SELF, virtualFile, file.project)
    }

    private fun collectFromAssignment(
        receivers: Set<String>,
        assignment: LuaAssignmentStatement,
        into: MutableMap<String, LuaTypeMember>,
    ) {
        val vars = assignment.varList.varList
        val exprs = assignment.exprList.exprList
        vars.forEachIndexed { index, luaVar ->
            val field = fieldNameFor(receivers, luaVar) ?: return@forEachIndexed
            if (into.containsKey(field)) return@forEachIndexed
            val rhs = exprs.getOrNull(index)
            into[field] = LuaTypeMember(field, lightInferType(rhs), sourceElement = luaVar)
        }
    }

    /** Field name iff [luaVar] is a single `base.field` access matching the class context. */
    private fun fieldNameFor(
        receivers: Set<String>,
        luaVar: LuaVar,
    ): String? {
        val field = singleFieldSuffixName(luaVar) ?: return null
        val base = luaVar.nameRef?.text ?: return null
        return when {
            base in receivers -> field
            base == SELF && isInClassMethod(receivers, luaVar) -> field
            else -> null
        }
    }

    /** The field name for `base.field`, or null for `base[i]`, `base.x.y`, or a bare name. */
    private fun singleFieldSuffixName(luaVar: LuaVar): String? {
        val suffix = luaVar.varSuffixList.singleOrNull() ?: return null
        if (suffix.nameAndArgsList.isNotEmpty()) return null
        return suffix.indexExpr.nameRef?.text
    }

    /** True if [luaVar] sits inside a method `function <receiver>:m()` / `.m()`. */
    private fun isInClassMethod(
        receivers: Set<String>,
        luaVar: LuaVar,
    ): Boolean {
        val funcDecl = PsiTreeUtil.getParentOfType(luaVar, LuaFuncDecl::class.java) ?: return false
        val funcName = funcDecl.funcName
        if (funcName.nameRef.text !in receivers) return false
        return funcName.funcNameMethod != null || funcName.funcNamePropertyList.isNotEmpty()
    }

    /** Maps an RHS expression's syntactic KIND to a type without any graph/resolve call. */
    private fun lightInferType(rhs: LuaExpr?): LuaType =
        when (rhs) {
            null -> LuaPrimitiveType.ANY
            is LuaTableConstructor -> LuaPrimitiveType.TABLE
            is LuaTerminalExpr -> terminalType(rhs)
            is LuaFuncDef -> LuaPrimitiveType.FUNCTION
            else -> LuaPrimitiveType.ANY
        }

    private fun terminalType(terminal: LuaTerminalExpr): LuaType =
        when {
            terminal.number != null -> LuaPrimitiveType.NUMBER
            terminal.string != null -> LuaPrimitiveType.STRING
            terminal.text == "true" || terminal.text == "false" -> LuaPrimitiveType.BOOLEAN
            terminal.text == "nil" -> LuaPrimitiveType.NIL
            else -> LuaPrimitiveType.ANY
        }
}
