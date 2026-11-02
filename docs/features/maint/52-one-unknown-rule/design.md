---
id: "MAINT-52-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "MAINT-52"
folders:
  - "[[features/maint/52-one-unknown-rule/requirements|requirements]]"
---

# Technical Design: MAINT-52 — One Unknown Rule

All paths are under `src/main/kotlin/net/internetisalie/lunar/lang/psi/types/`. Line numbers are at
`7ec1adb7`.

## 1. The rule

| Where | `Undefined` | `Any` |
| :-- | :-- | :-- |
| A union (`LuaTypeAlgebra.canonicalize`) | alone: stays `Undefined`. Beside other members: becomes an `Any` arm | absorbs scalar arms, keeps structural arms (unchanged, BUG-397) |
| A declared union type (`fromLuaType`, `LuaUnionType`) | every member unresolved: the union is `Any` (§2.2) | as a union |
| A value or use demand (`checkCompatibility`, `isCompatible`) | unchecked (unchanged) | unchecked (unchanged) |
| A field demand (`LuaTypeAlgebra.isOptional`) | optional (unchanged) | **optional** |
| The reaching-definition merge (`resolveWrite`) | absence, dropped (unchanged, out of scope) | kept as a member (unchanged) |

The union row makes the conversions that run before `Union.create` redundant wherever an `Undefined`
member sits beside a known one (DR-02, M1). `canonicalize` cannot make an all-`Undefined` union `Any`:
`Undefined` alone is "no information" to every other caller. A declared union whose members all fail
to resolve is still a declaration, and it is `Any` today because of [[BUG-16]]'s conversion. §2.2
keeps that case and only that case (DR-07). An `Undefined` that means absence can also reach a union;
Risk 1.2 covers it.

## 2. Changes

Every site that decides what an unknown does, and what this feature does to it:

| Site | At `7ec1adb7` | Change |
| :-- | :-- | :-- |
| `LuaTypeAlgebra.simplify` | `LuaTypeAlgebra.kt:45-53` drops `Undefined` | §2.1 |
| `LuaGraphType.fromLuaType`, `LuaUnionType` branch | `LuaGraphType.kt:318-331`, every `Undefined` member → `Any` (BUG-16) | §2.2: narrowed to the all-unresolved union |
| `LuaGraphType.declaredParamNodeFor` | `LuaGraphType.kt:434`, `&& graphType != Undefined` (BUG-11) | §2.3: delete |
| `LuaTypesVisitor.demandTypeOf` | `LuaTypesVisitor.kt:1097-1105`, `&& declared != Undefined` (BUG-22) | §2.3: delete |
| `LuaTypeGraphBridge.demandTypeFor` | `LuaTypeGraphBridge.kt:154-164`, builds `T\|nil` with no unknown guard | none in code; its result for `T = Undefined` changes from `nil` to `Any` through §2.1 (DR-06) |
| `LuaTypesVisitor.visitBinOpExpr`, `and`/`or` | `LuaTypesVisitor.kt:713-714`, `carriedPart` `Undefined → Any` (BUG-441) | §2.4: delete |
| `LuaTypeGraph.isOptional` | `LuaTypeGraph.kt:985-990`, called at `:825` (`isStructurallyCompatible`) and `:978` (`checkTableCompatibility`) | §2.5: replaced by the shared predicate |
| `LuaUnionDiagnostics.isOptional` | `LuaUnionDiagnostics.kt:77-83`, a second copy, called at `:65` | §2.5: replaced by the shared predicate |
| `LuaTypesVisitor.visitBinOpExpr`, `rightType == Undefined` | `LuaTypesVisitor.kt:715` | kept: whole result `Undefined`, not a union |
| `LuaTypesVisitor.collectRhsNodes`, no node | `LuaTypesVisitor.kt:281-282` gives `Any` | kept: must survive `resolveWrite`; comment edited (§2.6) |
| `LuaTypesVisitor.visitFile`, file return | `LuaTypesVisitor.kt:384` | kept: not a union or a demand |
| `LuaTypeGraph.isUnknown` | `LuaTypeGraph.kt:304-307` | kept: already counts both spellings |
| `checkCompatibility` / `isCompatible` early returns | `LuaTypeGraph.kt:531`, `:718` | kept |
| `VariableElement.resolveWrite` `flatten` | `LuaTypeNodes.kt:197-224` | kept (requirements, Out of Scope) |
| `LuaGraphType.fromLuaType`, `LuaPrimitiveType.UNKNOWN -> Undefined` | `LuaGraphType.kt:273` | kept (Risk 1.1) |
| Visitor `Undefined` producers: placeholders `graph.value(…, LuaGraphType.Undefined)` at `LuaTypesVisitor.kt:291`, `:674`, `:675`, `:760`, `:791`, `:893`, `:978`; `subtractType` `:549`; `truthyPart` `:742`; `falsyPart` `:752`; `declaredMemberType` and `seedSubscriptElement` `:1340-1386` | as listed | kept (Risk 1.1) |
| `LuaTypes.typeOf`, an `Undefined` write shows the demand | `LuaTypes.kt:79-110` | kept ([[BUG-26]]) |
| `TypeParser.literalValueType` | `TypeParser.kt:113-118` | none: maps a literal to its value's type and never yields `UNKNOWN` |

Code outside the engine also tests for `Undefined` (`LuaCompletionContributor.kt:496`,
`LuaTypeInlayHintProvider.kt:110` and `:139`, `LuaMethodChainInlayHintProvider.kt:240`,
`LuaTypeManagerImpl.kt:259` and `:282`, `LuaSuspiciousConcatenationInspection.kt:73-84`). These read a
finished type and decide nothing about unions or demands; none changes.

### 2.1 `LuaTypeAlgebra.simplify`

Replace the body (`LuaTypeAlgebra.kt:45-53`) with:

```kotlin
private fun simplify(members: List<LuaGraphType>): List<LuaGraphType>? {
    val known = members.filter { it != LuaGraphType.Undefined }
    if (known.isEmpty()) return members
    val gradual = if (known.size < members.size) known + LuaGraphType.Any else known
    if (gradual.none { it is LuaGraphType.Any }) return gradual
    val structural = gradual.filter { it.isStructural() }
    if (structural.isEmpty()) return null
    return listOf(LuaGraphType.Any) + structural
}
```

`null` keeps its meaning: the union reduces to `Any`. An empty `members` returns `[]`, which
`collapse` turns into `Undefined`, as it does today.

KDoc changes in the same file:
- `canonicalize` (line 14): replace "`Undefined` is dropped (unless alone)" with "an `Undefined`
  member beside others becomes an `Any` arm, because a union that cannot account for one member
  cannot rule anything out".
- `simplify`: append the sentence "An `Undefined` member beside others is the engine saying it cannot
  tell, so it joins as `Any`; alone it is still no information."

### 2.2 `fromLuaType`, `LuaUnionType` branch

Replace lines 318-331 with:

```kotlin
is LuaUnionType -> {
    val result = Union(emptySet())
    visited[type] = result
    val memberTypes = type.types.map { fromLuaType(it, graph, visited) }.toSet()
    val created = Union.create(memberTypes)
    // A declared union none of whose members resolves is still a declaration: `Any`, not
    // the "no information" `Undefined` that `Union.create` keeps for it (BUG-16).
    val finalUnion = if (created == Undefined) Any else created
    visited[type] = finalUnion
    finalUnion
}
```

A member that is `Undefined` beside a resolved one is handled by §2.1, so the per-member
`.map { if (it == Undefined) Any else it }` goes. Only the all-unresolved case is converted here. If
it were not, `---@type Unres1|Unres2` would type a local by what is demanded of it and drop it from
its reaching definitions (DR-07). An empty `LuaUnionType` now becomes `Any` rather than `Undefined` as
well; the only producer is the cycle placeholder `Union(emptySet())` round-tripping through
`graphTypeToLuaType`, and `Any` can only remove a report there, never add one.

### 2.3 The optional-parameter guards

`declaredParamNodeFor`, line 434: `val acceptsNil = param.isOptional && graphType != Undefined`
becomes `val acceptsNil = param.isOptional`. In its KDoc (lines 416-423), replace "An optional
parameter of a known type also accepts `nil` (BUG-552); an unresolved one keeps its `Undefined`
demand, because `Undefined | nil` canonicalizes to `nil` (BUG-11)." with "An optional parameter also
accepts `nil` (BUG-552)."

`demandTypeOf` (lines 1097-1105) becomes:

```kotlin
/** BUG-552: a `?` parameter accepts nil, so its demand is `T|nil`, the same as `---@param p T|nil`. */
private fun demandTypeOf(param: LuaGraphType.Function.Parameter): LuaGraphType =
    if (param.isOptional) {
        LuaGraphType.Union.create(listOf(param.node.read, LuaGraphType.Nil))
    } else {
        param.node.read
    }
```

For an unresolved type both now build `Union.create(Undefined, Nil)`, which §2.1 makes `Any`.
`LuaTypeGraphBridge.demandTypeFor` already builds the same union with no guard, so a
`---@param o? unknown` on an in-file function demands `Any` instead of `nil` (DR-06). It needs no code
change.

### 2.4 `and` / `or`

In `visitBinOpExpr` delete the `carried` local (lines 713-714) and the BUG-441 comment paragraph
(lines 706-711, from "BUG-441: `Any`, not `Undefined`" to "where it belongs."). Use `carriedPart` in
its place:

```kotlin
val carriedPart = if (op == "and") falsyPart(leftType) else truthyPart(leftType)
if (rightType == LuaGraphType.Undefined) {
    LuaGraphType.Undefined
} else {
    LuaGraphType.Union.create(setOf(carriedPart, rightType))
}
```

### 2.5 One field-demand predicate

Add to `LuaTypeAlgebra`, after `subtractMember`:

```kotlin
/**
 * Whether a field demanded as [type] may be absent: a missing field reads as `nil`, so a union with
 * `nil` admits it, and so does an unknown type, which cannot rule `nil` out.
 */
fun isOptional(type: LuaGraphType): Boolean =
    when (type) {
        is LuaGraphType.Union -> type.types.any { it == LuaGraphType.Nil }
        LuaGraphType.Undefined, LuaGraphType.Any -> true
        else -> false
    }
```

Delete the private `isOptional` in `LuaTypeGraph` (lines 985-990) and in `LuaUnionDiagnostics`
(lines 77-83, with its KDoc). Change every call site (`LuaTypeGraph.kt:825`, `:978`,
`LuaUnionDiagnostics.kt:65`) from `isOptional(…)` to `LuaTypeAlgebra.isOptional(…)`. In
`LuaUnionDiagnostics`, this changes which union member is the closest match and which fields it lists
(DR-04).

### 2.6 Remaining comment edits

- `LuaGraphType.Undefined` KDoc (`LuaGraphType.kt:18`): "⊥ (bottom type). UNDEFINED is assignable to
  every type. No information: nothing has been inferred. Beside other union members it means the
  engine cannot tell, and becomes `Any` ([LuaTypeAlgebra.canonicalize])."
- `collectRhsNodes` (`LuaTypesVisitor.kt:278-280`): replace "`LuaTypeAlgebra.simplify` and
  `resolveWrite`'s `flatten` both drop `Undefined` from a union, which would restore exactly today's
  behaviour and fix nothing. `Any` already survives both" with "`resolveWrite`'s `flatten` drops
  `Undefined`, which would restore exactly today's behaviour and fix nothing. `Any` survives it".
- `truthyPart` KDoc: delete the sentence "Keeping it "when nothing else remains" was tried … most
  wrong." (it starts mid-line 731 and ends at 734). It explains a rejected alternative by
  `Union.create` dropping `Undefined`, which is no longer what `Union.create` does.
- `falsyPart` KDoc (`LuaTypesVisitor.kt:745-748`): replace "so a type with neither contributes
  nothing to `a and b` — the result is then just `b`." with "A type with neither yields `Undefined`,
  which `Union.create` turns into an `Any` arm beside `b`."

## 3. Threading and contract

No new class, extension point or `plugin.xml` entry. Every change is inside a snapshot build, which
already runs in a read action. No function gains an argument or exceeds the 30-line cap; `simplify` is
8 lines.

## 4. Tests

New `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaUnknownRuleTest.kt`:
`@RunWith(JUnit4::class) class LuaUnknownRuleTest : BasePlatformTestCase()`, one `@Test` per TC that
`requirements.md` places in it, with these private helpers:

```kotlin
private fun errors(source: String, library: String? = null): List<String>
private fun typeOf(name: String): String
```

- `errors` calls `myFixture.enableInspections(LuaTypeAssignabilityInspection())`, adds `library` as
  `lib.lua` with `myFixture.addFileToProject` when given, adds `source` as `t.lua` the same way and
  opens it with `myFixture.configureFromExistingVirtualFile(tFile.virtualFile)` (the form DR-02 ran),
  calls `myFixture.doHighlighting()`, then returns
  `LuaTypesSnapshot.forFile(myFixture.file as LuaFile).getErrors().map { it.message }.distinct().sorted()`.
- `typeOf` finds the first `LuaLocalVarDecl` in `myFixture.file` whose `attNameList` has a `nameRef`
  with text `name`, and returns `LuaTypesSnapshot.forFile(myFixture.file).getValueType(nameRef).displayName()`.
  Call it after `errors`.

Assert expected lists with `assertEquals`. In `LuaTypeAlgebraTest`, replace
`` `undefined is dropped unless it is the only member` `` with
`` `undefined beside other members is unknown` ``, asserting TC-01.

## 5. Boundary with MAINT-51 and TYPE-14

The optional-parameter rule (`?` makes a demand `T|nil`) has a copy in each of `declaredParamNodeFor`,
`demandTypeOf` and `LuaTypeGraphBridge.demandTypeFor`. [[MAINT-51]] merges the first two into one
function and owns where that rule lives, and it merges `detachedDemand` with [[TYPE-14]]'s per-call
copy. This feature deletes only the `Undefined` clause inside the first two (§2.3) and changes no
code in `demandTypeFor` or `detachedDemand`. If MAINT-51 lands first, delete the clause in its merged
function. Whichever lands second rebases a one-line conflict.

The field-demand predicate is this feature's. `LuaTypeAlgebra.isOptional` answers one question, *may a
field demanded as this type be absent*, and it is called from `isStructurallyCompatible`
(`LuaTypeGraph.kt:825`, under `isCompatible`) and `checkTableCompatibility` (`:978`, under
`checkCompatibility`). MAINT-51's "one optional and `nil` rule, used by `checkCompatibility` and
`isCompatible` alike" is the parameter and `nil`-value rule: whether a `nil` value or a missing
argument satisfies a demand. MAINT-51 does not add or change a field predicate; where it needs one, it
calls `LuaTypeAlgebra.isOptional`.

TYPE-14's generic binding is unaffected: DR-02 case c22 is identical under V0 and D.

## 6. Mutations

Each is applied to the code after §2. Each was executed as the prototype variant of the same name
(DR-02, DR-04, DR-06, DR-07).

| ID | Mutation |
| :-- | :-- |
| M1 | In §2.1, `val gradual = known` (no `Any` for a dropped `Undefined`) |
| M2 | In §2.5, `LuaGraphType.Undefined -> true` alone |
| M3 | `demandTypeOf` and `declaredParamNodeFor` never add `Nil` |
| M4 | In §2.5, `else -> true` |
| M5 | In §2.2, `val finalUnion = created` |
| M6 | `LuaUnionDiagnostics` keeps its private `isOptional` (lines 77-83 at `7ec1adb7`) and calls it at `:65` |

## 7. Open Questions

None. [[BUG-25]], [[BUG-26]] and [[BUG-27]] are follow-up work, not decisions this feature needs.
