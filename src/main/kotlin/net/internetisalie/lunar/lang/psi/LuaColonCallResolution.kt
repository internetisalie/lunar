package net.internetisalie.lunar.lang.psi

import com.intellij.psi.PsiElement
import net.internetisalie.lunar.lang.psi.types.LuaMemberDeclarations
import net.internetisalie.lunar.lang.psi.types.LuaType
import net.internetisalie.lunar.lang.psi.types.LuaTypeMember
import net.internetisalie.lunar.lang.psi.types.LuaTypesSnapshot
import net.internetisalie.lunar.lang.psi.types.LuaTypesVisitor
import net.internetisalie.lunar.lang.psi.types.LuaUnionType

/**
 * NAV-13: maps the member name of a colon call `t:m()` to the IDENTIFIER leaf of the
 * `function t:m()` that declares it.
 *
 * `t:m(...)` is sugar for `t.m(t, ...)` (Lua 5.4 manual §3.4.10), so the member name is a **table
 * key, never a variable** — the lexical resolution [net.internetisalie.lunar.lang.LuaNameReference]
 * applies to every other `LuaNameRef` is unsound for it, and NAV-13-05 withdraws it. Lookup goes
 * through the *receiver's* inferred type instead.
 *
 * Every clause is written *accept only if*: an un-enumerated shape resolves to **null**, so a
 * mistake costs a missing navigation target rather than a wrong one (design §3.3, §3.5).
 *
 * Threading: requires a read action, inherited from `PsiReference.resolve()`. Pure PSI plus a
 * `CachedValuesManager`-backed [LuaTypesSnapshot.forFile]; no I/O, no index read, no lock. Stateless,
 * so no `Project` / `PsiFile` / `Editor` is retained.
 *
 * See `docs/features/navigation/13-colon-call-resolution/design.md` §2.1, §3.1–§3.6.
 */
object LuaColonCallResolution {
    /**
     * True for the `m` of `t:m()`. Design §3.1: `methodExpr ::= ':' nameRef` is the only rule with a
     * [LuaMethodExpr], so the test is exact — and the declaration side `function t:m()` spells its
     * name under [LuaFuncNameMethod], a different type, so a declaration's own name is never taken by
     * this route and keeps today's resolution. Two `instanceof`s, because this runs on every Lua name
     * resolution.
     */
    fun isColonCallMemberName(element: PsiElement): Boolean = element is LuaNameRef && element.parent is LuaMethodExpr

    /**
     * The method-name IDENTIFIER leaf this call site names, or null.
     *
     * Total over any [PsiElement] — no caller needs to pre-check (design §3.2).
     *
     * The [LuaTypesVisitor.isSnapshotUnderConstruction] guard is load-bearing, not defensive:
     * `LuaTypesVisitor` resolves the member name of *every* colon call while building the snapshot
     * (`propagateExpectedLambdaParams` → `LuaExpectedCallbackResolver.resolveCalleeType`), and
     * answering that from the snapshot under construction is a cycle the engine's fixpoint does not
     * model. Measured un-guarded, it takes `RootAccessor.WRITE` on the existing
     * `annotatedCallSiteFixture()` from 572 to 812 and reddens the budget methods that predate this
     * feature (design §3.6 decision 3).
     */
    fun declarationLeafOf(element: PsiElement): PsiElement? {
        val nameRef = element as? LuaNameRef ?: return null
        val receiver = receiverOf(nameRef) ?: return null
        val file = nameRef.containingFile
        if (LuaTypesVisitor.isSnapshotUnderConstruction(file)) return null
        val types = LuaTypesSnapshot.forFile(file)
        val receiverType = types.graphTypeToLuaType(types.getValueType(receiver))
        return declarationLeaves(receiverType, nameRef.text).singleOrNull()
    }

    /**
     * The receiver `t` of `t:m()`, or null. Design §3.3; each refusal was measured to prevent a
     * plausible-but-wrong target rather than merely to narrow the feature:
     *
     * - **A (chain)** — `x:m1():m2()` is *one* [LuaFuncCall] with a two-element `nameAndArgsList` and
     *   no PSI node for the value of `x:m1()`, so resolving a later segment against the receiver
     *   offers the receiver's own same-named member. `f():m()` is this case too: `funcCall ::=
     *   varOrExp nameAndArgs+` is greedy.
     * - **B (parenthesised head)** — `("s"):m()` has no `var` at all; the `nameRef` return covers
     *   `var`'s own `'(' expr ')' varSuffix+` alternative.
     * - **C (suffixed)** — the graph anchors every suffix of a `var` on that `var`'s bare head, so
     *   `a.b:m()`'s head `a` may carry an `m` belonging to `a.b` (TYPE-13 Gap 2.8).
     *
     * No `@NotNull`-getter hazard: `funcCall`, `nameAndArgs`, `methodExpr`, `var` and `varOrExp`
     * declare no pin, so a failed sub-rule rolls the section back and the node is never built
     * (design §3.3).
     */
    private fun receiverOf(nameRef: LuaNameRef): LuaNameRef? {
        val methodExpr = nameRef.parent as? LuaMethodExpr ?: return null
        val nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null
        val call = nameAndArgs.parent as? LuaFuncCall ?: return null
        if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null
        val receiverVar = call.varOrExp.`var` ?: return null
        if (receiverVar.varSuffixList.isNotEmpty()) return null
        return receiverVar.nameRef
    }

    /**
     * The distinct accepted declaration leaves for [memberName] on [receiverType]. Design §3.4.
     *
     * The union-arm loop is not redundant with the plain lookup: [LuaUnionType.resolveMember] returns
     * null unless **every** arm carries the name, and a `---@class`-annotated receiver aliased as
     * `local b = Builder` types as `{ … } | Builder`, whose anonymous table arm has no `setName`.
     * Measured, the loop raises the corpus declarations that gain a resolving call site from 68 to 84.
     *
     * `.distinct()` pairs with `singleOrNull` in [declarationLeafOf]: a `---@type A|B` receiver whose
     * arms each declare `m` has two equally good targets and no ground for choosing, so it refuses.
     */
    private fun declarationLeaves(
        receiverType: LuaType,
        memberName: String,
    ): List<PsiElement> {
        val members = mutableListOf<LuaTypeMember>()
        receiverType.resolveMember(memberName)?.let { members += it }
        if (receiverType is LuaUnionType) {
            receiverType.types.forEach { arm -> arm.resolveMember(memberName)?.let { members += it } }
        }
        return members.mapNotNull { methodNameLeafOf(it, memberName) }.distinct()
    }

    /**
     * The declaration's IDENTIFIER leaf, or null. Design §3.5.
     *
     * The `as? LuaFuncDecl` cast refuses everything else [LuaMemberDeclarations.declarationOf]
     * returns — a [LuaField] (a table-constructor entry) and a [LuaAssignmentStatement] (`t.m = f`).
     * Neither can be a search target: `LuaDeclarationSite.kindOf` of a field's name leaf is null, and
     * `identifierLeafOf` of an assignment is the *receiver*'s leaf, so admitting either would give a
     * Go-to target with no usage set — the asymmetry NAV-13-02/NAV-13-03 exist to prevent.
     *
     * `FUNC_NAME` is reached through the node rather than through `LuaFuncDecl.getFuncName()` because
     * `funcDecl` carries `pin = 1`: a [LuaFuncDecl] node exists with no `FUNC_NAME` child whenever a
     * keyword sits in the name slot, and that `findNotNullChildByClass` getter would raise
     * `TestLoggerAssertionError` (the SYNTAX-18 hazard). The declaration here comes from an arbitrary
     * file the type engine reached, so it is not under the caller's control.
     *
     * The `takeIf` ties the returned leaf's text to the reference's name — `isReferenceTo` compares
     * text before resolving, so a divergence would produce a Go-to target Find Usages cannot see. It
     * is measurably never taken (0 divergences over 14 116 corpus call sites) and `requirements.md`
     * records it as having no reachable falsifier rather than pairing it with a test that cannot fail.
     */
    private fun methodNameLeafOf(
        member: LuaTypeMember,
        memberName: String,
    ): PsiElement? {
        val declaration = LuaMemberDeclarations.declarationOf(member) as? LuaFuncDecl ?: return null
        val funcName =
            declaration.node.findChildByType(LuaElementTypes.FUNC_NAME)?.psi as? LuaFuncName ?: return null
        return funcName
            .funcNameMethod
            ?.nameRef
            ?.identifier
            ?.takeIf { it.text == memberName }
    }
}
