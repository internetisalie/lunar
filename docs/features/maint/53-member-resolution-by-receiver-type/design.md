---
id: "MAINT-53-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-53"
folders:
  - "[[features/maint/53-member-resolution-by-receiver-type/requirements|requirements]]"
---

# Technical Design: MAINT-53 — Member Resolution by Receiver Type

## 1. Architecture Overview

### Current State

`LuaNameReference.multiResolve` sends a colon call to `LuaColonCallResolution` and everything else
through `ResolveCache` to `doMultiResolve`, which tries the lexical walk, then file bindings, then
the text key from `getQualifiedName`. A member can therefore bind to a local, and any file's
`M.clear` matches any other file's `M.clear`.

### Proposed Solution

Independent corrections, in delivery order:

1. **Inspection** (§3.1): `LuaUnusedLocalInspection` follows a read through reassignments. It ships
   before the kind fix (DR-05).
2. **Kind** (§3.2, §3.3): a member or function-name segment takes no lexical, bare-name or file-binding
   answer, and a bare name takes no method leaf. No type engine involved.
3. **Type** (§2, §3.4 to §3.6): a member resolves through the receiver's type first.

### Premises

| Constraint | Status |
|------------|--------|
| Text resolution stays as a fallback | Chosen. Most member sites have no typed receiver (DR-01). |
| Bare-receiver colon calls stay on NAV-13 | Chosen. Its refusals are pinned by tests, and it is the only path for them. It limits the colon share (DR-01). |
| A required module is typed from its returned expression, not `LuaTypeManager.resolveModule` | Forced for now: `resolveModule` types a module through `getFileReturnType`, which lacks `function M.x` members, and measured worse (DR-06). Removable through Gap 2.1. |
| `fromLuaType` keeps dropping `sourceElement` | Chosen. Carrying it moved goldens and NAV-13 tests and did not help `require` (DR-06). |
| Under construction, a member uses text | Chosen, same reason as NAV-13's guard (DR-03). |
| The drop rule applies only to a table-constructor local | Forced. Wider rules lost real usages (DR-06). |

## 2. Components

### 2.1 `net.internetisalie.lunar.lang.psi.LuaMemberResolution` (new)

A stateless `object` beside `LuaColonCallResolution`, in
`src/main/kotlin/net/internetisalie/lunar/lang/psi/LuaMemberResolution.kt`. Threading: read action,
inherited from `PsiReference`. It holds no PSI, `Project` or `PsiFile`. `LuaColonCallResolution` is
unchanged and remains the owner of bare-receiver colon calls.

Reuses `LuaTypesSnapshot.forFile`, `LuaTypesVisitor.isSnapshotUnderConstruction`,
`LuaTypeManager.getInstance(project).resolveGlobal`, `LuaMemberDeclarations.declarationOf`,
`memberFieldIdentifier` (`lang/indexing/LuaMemberFieldNames.kt:24`), `resolveModuleCandidates`
(`lang/path/LuaModuleFileResolver.kt:28`), `LuaScopeProcessor` and `LuaResolveUtil.scopeCrawlUp`.

```kotlin
object LuaMemberResolution {
    private data class MemberChain(val head: LuaNameRef, val steps: List<String>)

    @VisibleForTesting
    internal val TYPED_DURING_BUILD = AtomicInteger()

    fun isMemberName(element: PsiElement): Boolean
    fun isSuffixedColonCall(nameRef: LuaNameRef): Boolean
    fun typedTargets(nameRef: LuaNameRef): List<PsiElement>
    fun isConstructedLocalHead(nameRef: LuaNameRef): Boolean
}
```

### 2.2 Plugin registration

None. `LuaFileBindingsIndex.VERSION` goes from 5 to 6 (§3.3).

## 3. Algorithms

### 3.1 `LuaUnusedLocalInspection.collectUsedDeclarations`

Replace the loop over `reference.multiResolve(false)` with a walk, extracted to a private
`markUsed(usage: LuaNameRef, declLeaves: Set<PsiElement>, used: MutableSet<PsiElement>)`:

```kotlin
val seen = mutableSetOf<PsiElement>()
var frontier = targetsOf(usage)
while (frontier.isNotEmpty()) {
    val next = mutableListOf<PsiElement>()
    for (target in frontier) {
        ProgressManager.checkCanceled()
        if (!seen.add(target)) continue
        if (target in declLeaves) { used.add(target); continue }
        val write = target.parent as? LuaNameRef ?: continue
        if (isSimpleWriteTarget(write)) next += targetsOf(write)
    }
    frontier = next
}
```

`targetsOf(nameRef)` is `(nameRef.reference as? PsiPolyVariantReference)?.multiResolve(false)
?.mapNotNull { it.element }.orEmpty()`. `seen` terminates cycles such as `x = x`.

The defect needs a nested block. `LuaBlock.processDeclarations` (`LuaBlockExt.kt:39`) offers a block's
declarations before its assignment targets, so in one block a read binds to the local, which is why
`testWriteThenReadLocalNotFlagged` passes today. The walk reaches the inner block first, though, so a
read beside an assignment in a block nested under the local binds to that assignment's leaf (DR-05).

### 3.2 `LuaNameReference`: text path stops for a non-variable name

Add a private helper:

```kotlin
private fun namesATableKey(element: PsiElement): Boolean {
    val segment = element.parent
    return segment is LuaFuncNameProperty || segment is LuaFuncNameMethod || segment is LuaIndexExpr
}
```

It gates the text path at these points:

1. **File bindings.** `collectFileResults` adds every same-named file-scope local and global of the
   platform and required files (`LuaFileBindingsIndex`), and runs before the qualified-name return. A
   table key never names such a binding (TC-21). Move `val referenceName = name` above `val
   platformQuery`, then wrap everything from `val platformQuery =` through the end of the
   `importedResults.forEach { … }` loop, unchanged, in `if (!namesATableKey(element)) { … }`. The
   `platformQuery`, `requiresQuery` and `importedResults` declarations become locals of that block.
2. **Bare-name lookups.** Directly after the `if (qualifiedName != null) { … }` block:

   ```kotlin
   if (namesATableKey(element)) return results.distinctBy { it.element }.toTypedArray()
   ```

In `lexicalTargetOf`, extend the first test to `segment is LuaFuncNameProperty || segment is
LuaFuncNameMethod || segment is LuaIndexExpr`. `LuaMethodExpr` needs neither: the colon branch returns
first.

### 3.3 `LuaFileBindingsIndexer.funcDeclNameLeaves`

Returns `listOfNotNull(plain)` only, dropping `method`. Its KDoc is the line now orphaned above
`identifierOf` (`` /** `function foo` → `foo`; `function recv:m` / `recv.m` → the method-qualified leaf as
well. */``): move it directly above `funcDeclNameLeaves` and replace its text with
`` /** `function foo` → `foo`. A method's name, as in `function recv:m`, is not a file-scope binding. */``.
Bump `VERSION` to 6 with the comment `// 5 -> 6 (MAINT-53): a method's name is not a file-scope binding.`

### 3.4 `LuaMemberResolution` bodies

`chainOf` is private and returns null for any shape not listed.

```kotlin
private fun chainOf(nameRef: LuaNameRef): MemberChain? =
    when (val parent = nameRef.parent) {
        is LuaIndexExpr -> dotChainOf(parent)
        is LuaMethodExpr -> colonChainOf(parent)
        else -> null
    }

private fun dotChainOf(indexExpr: LuaIndexExpr): MemberChain? {
    val suffix = indexExpr.parent as? LuaVarSuffix ?: return null
    if (suffix.nameAndArgsList.isNotEmpty()) return null
    val luaVar = suffix.parent as? LuaVar ?: return null
    val head = luaVar.nameRef ?: return null
    val before = luaVar.varSuffixList.takeWhile { it !== suffix }
    return stepsOf(before)?.let { MemberChain(head, it) }
}

private fun colonChainOf(methodExpr: LuaMethodExpr): MemberChain? {
    val nameAndArgs = methodExpr.parent as? LuaNameAndArgs ?: return null
    val call = nameAndArgs.parent as? LuaFuncCall ?: return null
    if (call.nameAndArgsList.firstOrNull() !== nameAndArgs) return null
    val luaVar = call.varOrExp.`var` ?: return null
    val head = luaVar.nameRef ?: return null
    return stepsOf(luaVar.varSuffixList)?.let { MemberChain(head, it) }
}

private fun stepsOf(suffixes: List<LuaVarSuffix>): List<String>? =
    suffixes.map { suffix ->
        if (suffix.nameAndArgsList.isNotEmpty()) return null
        suffix.indexExpr.nameRef?.text ?: return null
    }
```

`a[1].b` has no `nameRef` in its first suffix, so `stepsOf` refuses it.

```kotlin
fun isMemberName(element: PsiElement): Boolean =
    element is LuaNameRef && (element.parent is LuaIndexExpr || element.parent is LuaMethodExpr)

fun isSuffixedColonCall(nameRef: LuaNameRef): Boolean =
    nameRef.parent is LuaMethodExpr && chainOf(nameRef)?.steps?.isNotEmpty() == true

fun typedTargets(nameRef: LuaNameRef): List<PsiElement> {
    if (LuaTypesVisitor.isSnapshotUnderConstruction(nameRef.containingFile)) {
        TYPED_DURING_BUILD.incrementAndGet()
    }
    val chain = chainOf(nameRef) ?: return emptyList()
    val receiver = receiverType(chain) ?: return emptyList()
    return membersNamed(receiver, nameRef.text).mapNotNull(::declarationTarget).distinct()
}

private fun receiverType(chain: MemberChain): LuaType? {
    var current = headType(chain.head) ?: return null
    for (step in chain.steps) {
        ProgressManager.checkCanceled()
        val member = membersNamed(current, step).firstOrNull { it.sourceElement != null } ?: return null
        current = member.type.takeIf(::isKnown) ?: return null
    }
    return current
}

private fun membersNamed(type: LuaType, name: String): List<LuaTypeMember> {
    val members = mutableListOf<LuaTypeMember>()
    type.resolveMember(name)?.let { members += it }
    if (type is LuaUnionType) type.types.forEach { arm -> arm.resolveMember(name)?.let { members += it } }
    return members
}

private fun isKnown(type: LuaType?): Boolean =
    type != null && type != LuaPrimitiveType.UNKNOWN && type != LuaPrimitiveType.ANY

private fun declarationTarget(member: LuaTypeMember): PsiElement? {
    val anchor = member.sourceElement ?: return null
    return when (val declaration = LuaMemberDeclarations.declarationOf(member)) {
        is LuaFuncDecl -> declaration
        is LuaAssignmentStatement ->
            PsiTreeUtil.getParentOfType(anchor as? LuaIndexExpr, LuaVar::class.java)?.let(::memberFieldIdentifier)
        is LuaVar -> memberFieldIdentifier(declaration)
        else -> null
    }
}
```

A field assigned in several places resolves to the one write `LuaMemberDeclarations.declaringNodeOf`
picks, and its other writes resolve to that write too (DR-08).

The walk stays in `LuaType` because the graph anchors every suffix of a `var` on its bare head, so the
value of `a.b` cannot be read from the head's node (TC-11). The union-arm loop is needed for the
reason `LuaColonCallResolution.declarationLeaves` documents.

### 3.5 Head type

Order matters: a global head and a `require` head both bypass the head's snapshot node.

```kotlin
private fun bindingOf(head: LuaNameRef): PsiElement? {
    val processor = LuaScopeProcessor(head.text)
    LuaResolveUtil.scopeCrawlUp(processor, head)
    return processor.result
}

private fun headType(head: LuaNameRef): LuaType? {
    val binding = bindingOf(head)
    if (binding == null) {
        val global = LuaTypeManager.getInstance(head.project).resolveGlobal(head.text, head)
        if (isKnown(global)) return global
    } else {
        requiredModuleType(binding)?.let { return it }
    }
    val types = LuaTypesSnapshot.forFile(head.containingFile)
    return types.graphTypeToLuaType(types.getValueType(head)).takeIf(::isKnown)
}

private fun initializerOf(binding: PsiElement): LuaExpr? {
    val declaration = PsiTreeUtil.getParentOfType(binding, LuaLocalVarDecl::class.java) ?: return null
    val index = declaration.attNameList.indexOfFirst { it.nameRef.identifier === binding }
    if (index < 0) return null
    return declaration.exprList?.exprList?.getOrNull(index)
}

private fun requiredModuleName(binding: PsiElement): String? {
    val call = initializerOf(binding) as? LuaFuncCall ?: return null
    if (call.varOrExp.text != "require") return null
    val args = call.nameAndArgsList.firstOrNull()?.args ?: return null
    val text = args.string?.text ?: args.exprList?.exprList?.singleOrNull()?.text ?: return null
    return text.trim('"', '\'')
}

private fun requiredModuleType(binding: PsiElement): LuaType? {
    val moduleName = requiredModuleName(binding) ?: return null
    val module = resolveModuleCandidates(binding.project, moduleName).firstOrNull() ?: return null
    if (LuaTypesVisitor.isSnapshotUnderConstruction(module)) return null
    val returned = topLevelReturnOf(module) ?: return null
    val types = LuaTypesSnapshot.forFile(module)
    val anchor = PsiTreeUtil.findChildOfType(returned, LuaNameRef::class.java)
        ?.takeIf { returned.text.all { c -> c.isLetterOrDigit() || c == '_' } } ?: returned
    return types.graphTypeToLuaType(types.getValueType(anchor)).takeIf(::isKnown)
}

private fun topLevelReturnOf(module: LuaFile): LuaExpr? =
    PsiTreeUtil.findChildrenOfType(module, LuaFinalStatement::class.java)
        .lastOrNull { statement ->
            PsiTreeUtil.getParentOfType(
                statement, LuaFuncDecl::class.java, LuaFuncDef::class.java, LuaLocalFuncDecl::class.java,
            ) == null
        }?.exprList?.exprList?.firstOrNull()
```

`anchor` selects the `LuaNameRef` when the returned expression is a plain identifier, the node that
carries the graph entry; for a table constructor it stays the expression. `getValueType` merges write
and read, which is why this path sees `function M.x`.

A head with no write of its own, such as a parameter, takes its type from the snapshot like any other
head. A member read from it is demand-only, has no `sourceElement`, and is rejected by
`declarationTarget` and by the steps walk, so no read invents a target; a member it writes is a real
target (TC-23).

### 3.6 Constructed local

```kotlin
fun isConstructedLocalHead(nameRef: LuaNameRef): Boolean {
    val chain = chainOf(nameRef) ?: return false
    val binding = bindingOf(chain.head) ?: return false
    return initializerOf(binding)?.text?.startsWith("{") == true
}
```

A later `M = other` in the same block does not disqualify the head, because the block offers the local
first (§3.1, TC-15). The answer is right: the text key `M.gone` names another file's `M` however this
one was reassigned. In a nested block the binding is the assignment, `initializerOf` returns null, and
text stands. Aliases such as `local string = string` are excluded by the initializer test.

### 3.7 `LuaNameReference` wiring

Rename the existing `doMultiResolve` body to `private fun resolveByText(incompleteCode: Boolean):
Array<ResolveResult>`, keeping `RESOLVE_INVOCATIONS` in it, and change that counter's KDoc to
`/** TC-03 observation seam: counts entries into [resolveByText] (the un-cached text path). */`.
`RESOLVER` keeps calling `doMultiResolve`.

```kotlin
override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val hostElement = myElement ?: return ResolveResult.EMPTY_ARRAY
    if (LuaColonCallResolution.isColonCallMemberName(hostElement)) return colonCallResults(hostElement)
    if (LuaMemberResolution.isMemberName(hostElement) &&
        LuaTypesVisitor.isSnapshotUnderConstruction(hostElement.containingFile)
    ) {
        return resolveByText(incompleteCode)
    }
    return ResolveCache
        .getInstance(hostElement.project)
        .resolveWithCaching(this, RESOLVER, /* needToPreventRecursion = */ false, incompleteCode)
}

private fun colonCallResults(hostElement: PsiElement): Array<ResolveResult> {
    val nameRef = hostElement as? LuaNameRef ?: return ResolveResult.EMPTY_ARRAY
    if (LuaMemberResolution.isSuffixedColonCall(nameRef)) {
        if (LuaTypesVisitor.isSnapshotUnderConstruction(nameRef.containingFile)) return ResolveResult.EMPTY_ARRAY
        return resolveResultsOf(LuaMemberResolution.typedTargets(nameRef))
    }
    val target = LuaColonCallResolution.declarationLeafOf(hostElement) ?: return ResolveResult.EMPTY_ARRAY
    return arrayOf(PsiElementResolveResult(target))
}

private fun doMultiResolve(incompleteCode: Boolean): Array<ResolveResult> {
    val nameRef = myElement as? LuaNameRef
    if (nameRef == null || !LuaMemberResolution.isMemberName(nameRef)) return resolveByText(incompleteCode)
    val typed = LuaMemberResolution.typedTargets(nameRef)
    if (typed.isNotEmpty()) return resolveResultsOf(typed)
    val textResults = resolveByText(incompleteCode)
    if (!LuaMemberResolution.isConstructedLocalHead(nameRef)) return textResults
    val hostFile = nameRef.containingFile
    return textResults.filter { it.element?.containingFile == hostFile }.toTypedArray()
}

private fun resolveResultsOf(targets: List<PsiElement>): Array<ResolveResult> =
    targets.map { PsiElementResolveResult(it) as ResolveResult }.toTypedArray()
```

A suffixed colon call's answer departs from the contract NAV-13 keeps for bare-receiver colon calls: it
may hold several targets, and a function target is the `LuaFuncDecl`, not the method's name leaf
(`LuaColonCallResolution.declarationLeafOf` and `declarationLeaves`). REFACT-09's rename rule (b)
consults only `declarationLeafOf`, which refuses a suffixed receiver (`receiverOf`), so in a colon-method
rename such a site is undecided unless the usage search collected it, as it is today.

The guard sits before `ResolveCache` for the reason the `multiResolve` KDoc already gives: a mid-build
answer must not outlive the build (TC-18). `colonCallResults` has its own under-construction return for
a suffixed colon call, which answers nothing rather than text (MAINT-53-06, MAINT-53-09; TC-24). Extend that KDoc to say so for dot members. A colon call
stays uncached, so a suffixed one recomputes per resolve; DR-02's figures include this. Add imports for
`LuaMemberResolution` and `LuaTypesVisitor`.

Update the KDoc of `declarationIdentifier`. Its argument that a nameless `funcDecl` cannot arrive rests
on every `decl` coming from `StubIndex.getElements(…, referenceName, …)`. A second source now exists:
`typedTargets` returns a `LuaFuncDecl` from `LuaMemberDeclarations.declarationOf` for a member found by
`resolveMember(nameRef.text)`, so its name is the reference's non-empty text as well. Say so.

## 4. Error Handling

An unmodelled shape returns null or empty and falls to text. A typed lookup on a suffixed colon call
returns nothing rather than a text guess. In dumb mode the text path's `StubIndex` calls already apply,
so this feature adds no new exposure.

## 5. Contract Conformance

No hard references: `LuaMemberResolution` is stateless. `CachedValuesManager`-backed `forFile` is the
only cache. All functions are at or under 30 lines and 3 arguments. Listings here use no `!!`.

## 6. Open Questions

None. Unsettled items are tracked in `risks-and-gaps.md`.

## 7. Requirement Coverage

| Requirement | Design |
|-------------|--------|
| MAINT-53-01 | §3.1 |
| MAINT-53-02 | §3.2 |
| MAINT-53-03 | §3.2 |
| MAINT-53-04 | §3.3 |
| MAINT-53-05 | §3.4, §3.5, §3.7 |
| MAINT-53-06 | §3.4, §3.7 |
| MAINT-53-07 | §3.6, §3.7 |
| MAINT-53-08 | §3.7 |
| MAINT-53-09 | §3.4, §3.7 |
| MAINT-53-10 | `implementation-plan.md` |
