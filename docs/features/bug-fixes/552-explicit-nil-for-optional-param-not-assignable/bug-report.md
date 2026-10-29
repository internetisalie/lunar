---
id: "BUG-552"
title: "An explicit `nil` argument for an optional `@param mode? string` is reported as not assignable"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-552: `loadfile(file, nil, env)` reports *nil value is not assignable to string* on LuaJIT 2.1

Found by [[MAINT-39]]'s corpus sweep of `lazy.nvim` (MAINT-39 risks-and-gaps, DR-12). A bug
report: reproduction executed, root cause not investigated.

## 1. Reproduction (executed)

Light fixture (`BasePlatformTestCase`) with `LuaTypeAssignabilityInspection` enabled, the project
pinned to LuaJIT 2.1 with `CorpusTarget.pin`, `myFixture.doHighlighting()`. Executed 2026-10-28 on
`5dd675a4` with a throwaway probe test (not committed).

```lua
local env = {}
local f = loadfile("x.lua", nil, env)
print(f)
```
→ `2:'nil':nil value is not assignable to string`

Control from the same run, no hit: `loadfile("x.lua")`, the optional parameter omitted.

## 2. Expected vs Actual
- **Expected**: no hit. The stub declares `---@param mode? string`
  (`src/main/resources/runtime/luajit/luajit-2.1/builtin.lua`), so `mode` accepts `nil`, whether
  omitted or passed explicitly.
- **Actual**: an explicit `nil` is checked against `string`, as if the parameter were not optional.

## 3. Context
- **Not reproduced on Standard 5.1** in the same run, because that stub declares
  `loadfile(filename)` with one parameter. Whether every optional `---@param name? T` rejects an
  explicit `nil`, or only this stub's, is not established.
- **Corpus site**: `lua/lazy/pkg/rockspec.lua:228`, part of the
  `inspection.LuaTypeAssignability=448` floor in `src/test/resources/corpus/lazynvim.baseline`. A
  fix lowers it, and the fixing change re-records the baseline.

## 4. Root Cause

Read from code at `adf41f40`; nothing in this section was executed except the reproduction in §1.

The `?` marker survives every hop from the stub to the call site as `Parameter.isOptional`. The
call-site check never reads it, and the parameter's type is never widened to `T|nil`.

**How `loadfile` reaches the check.**
1. `LuaTypeAssignabilityInspection.buildVisitor` only re-reports `LuaTypesSnapshot.getErrors()`
   (`analysis/LuaTypeAssignabilityInspection.kt:21-40`). The error comes from the type graph.
2. `loadfile` is a free global. `freeGlobalSeed` resolves it through
   `LuaTypeManager.resolveGlobal`, converts it with `LuaGraphType.fromLuaType`, and adds the seed
   to `declarationTypedNodes` (`lang/psi/types/LuaTypesVisitor.kt:1419-1427`).
3. `resolveGlobal` → `doResolveGlobal` tries project scope first, then all scope, which is where
   the bundled `runtime/luajit/luajit-2.1/builtin.lua` is found (`LuaTypeManagerImpl.kt:217-232`).
   `globalTypeIn` runs a full `LuaTypesSnapshot` over that file and converts the graph type back
   with `graphTypeToLuaType` (`LuaTypeManagerImpl.kt:253-261`). This is the **AST path**.
   `LuaFuncStub.luacatsParamTypes` is not used here.
4. Inside the stub file's own analysis, the function visitor builds each parameter with
   `isOptional = matchingCat?.argSymbol?.text == "?"` (`LuaTypesVisitor.kt:1533-1535`).
   `paramTag ::= '@param' (<<ArgName NAME>> <<ArgSymbol ('?')>>?) … <<ArgType type>>`
   (`luacats/lang/parser/luacats.bnf:143`) keeps `?` out of `argType`, so
   `LuaTypeGraphBridge.injectParamAnnotations` resolves `paramTag.argType.text` = `"string"`. It
   gives the parameter node a bare `String` source and a bare `String` declared use
   (`LuaTypeGraphBridge.kt:128-144`). It does not consult `argSymbol`.
5. `functionToLuaType` carries `p.isOptional` and the bare `String` (`p.node.write`) into
   `LuaParameter` (`LuaTypes.kt:240`). `fromLuaType` carries `p.isOptional` back into
   `Function.Parameter`, over a member node whose `read` is the bare `String`
   (`LuaGraphType.kt:266`, `memberNodeFor` at `:385-396`).
6. At the call, `visitFuncCall` takes the declaration-typed branch and calls
   `demandDeclaredParams` (`LuaTypesVisitor.kt:950-955`). With exactly three arguments
   `positionsAreUnambiguous` passes (`:1112-1115`). The demand is
   `val declaredType = param.node.read` (`:1055`), i.e. bare `String`, and
   **`param.isOptional` is never read**. The edge from the literal `nil`'s value node to that use
   is a direct value→use edge, so it is checked with `certain = true` (`LuaTypeGraph.kt:178-188`).
   That reaches the `Nil`-vs-non-`Nil` branch and emits `nil value is not assignable to string`
   (`LuaTypeGraph.kt:678-694`).

**Why omitting the argument gives no hit.** `loadfile("x.lua")` passes one argument against three
parameters, and `positionsAreUnambiguous` returns early (`LuaTypesVisitor.kt:1052`, `:1112-1115`).
The optional parameter is never checked at all, so the "control" in §1 does not exercise
optionality.

**Scope: every optional param, or only this stub's.** Read from code, not executed:
- **Declared in another file** (bundled stub, definitions library, or a project file): every
  `---@param name? T` with a scalar `T` reaches the same `demandDeclaredParams` line, and an
  explicit literal `nil` in a full-arity call is predicted to be reported. A project file in a
  different file takes the same route as the stub: `doResolveGlobal` only changes which scope
  answers first (`LuaTypeManagerImpl.kt:225-231`), and both answer through `globalTypeIn`. `T` is
  scalar because `carriesGraphNodes` skips function and table types (`LuaTypesVisitor.kt:1077-1083`).
- **Declared in the same file**: the call goes through `checkFunctionCompatibility`, which wires
  the argument into the parameter node (`LuaTypeGraph.kt:889-891`). The bare `String` declared use
  from step 4 is in that node's down-set. With a single caller, the only non-declared reaching
  definition is the `nil`, so `checkNodePairs` computes `certain = true` (`LuaTypeGraph.kt:442-444`).
  The same message is predicted. This is a second site with the same defect.
- **Class members resolved through `funcTypeFromStub`** (`LuaTypeManagerImpl.kt:604-627`) also
  lose the marker: `LuaCatsDeclarations.paramTypes` stores `argType.text` only
  (`LuaCatsDeclarations.kt:89-90`, which the `LuaFuncStub` path also uses) and `LuaParameter(pName, …)` takes the default
  `isOptional = false` (`:623`). That is a third, related gap, and the fix below does not cover
  it. It is recorded here and not fixed by this bug, because the stub schema would have to change
  to fix it.

## 5. Fix Strategy

Widen the **demand**, not the inferred type, to `T|nil` wherever a `?` parameter is checked. The
declared source type is left alone, so hover, inlay and signature rendering (`fun(mode?: string)`)
stay as they are.

**Change A, the root cause of this report** (`lang/psi/types/LuaTypesVisitor.kt`):
- Add a private helper next to `demandDeclaredParams`:
  ```kotlin
  /** BUG-552: a `?` parameter accepts nil, so its demand is `T|nil`, the same as `---@param p T|nil`. */
  private fun demandTypeOf(param: LuaGraphType.Function.Parameter): LuaGraphType =
      if (param.isOptional) {
          LuaGraphType.Union.create(listOf(param.node.read, LuaGraphType.Nil))
      } else {
          param.node.read
      }
  ```
- In `demandDeclaredParams`, replace `val declaredType = param.node.read` (`:1055`) with
  `val declaredType = demandTypeOf(param)`. Nothing else in the function changes.
- How it behaves: `Union.create` canonicalizes the members (`LuaGraphType.kt:87`,
  `LuaTypeAlgebra.canonicalize`) and never drops `nil` (`LuaTypeAlgebra.kt:14-16`). A `Nil` value
  against a union use is accepted by the `useType is Union` branch, because
  `isCompatible(Nil, Nil)` holds (`LuaTypeGraph.kt:592-621`). A wrong non-nil scalar such as
  `Number` still matches no member and is still reported, with the union message
  (`LuaTypeGraph.kt:623-638`). For a table-typed optional param, `carriesGraphNodes` recurses into
  the union (`:1081`), so table and function params stay skipped as before.

**Change B, the same-file site** (`lang/psi/types/LuaTypeGraphBridge.kt`), applied only under the
rule in §6:
- Add a private helper:
  ```kotlin
  private fun demandTypeFor(paramTag: LuaCatsParamTag, graphType: LuaGraphType): LuaGraphType =
      if (paramTag.argSymbol?.text == "?" && graphType !is LuaGraphType.Generic) {
          LuaGraphType.Union.create(listOf(graphType, LuaGraphType.Nil))
      } else {
          graphType
      }
  ```
  (`LuaCatsParamTag.getArgSymbol()` exists: `src/main/gen/…/luacats/lang/psi/LuaCatsParamTag.java:14`.)
  Generics are excluded so that generic instantiation, which keys on the node's `write`
  (`LuaTypesVisitor.kt:992-1002`), behaves exactly as before.
- In `injectParamAnnotations`, change only the use node at `:142` to
  `graph.use(context, demandTypeFor(paramTag, graphType), declaredDemand = true)`. The value
  source at `:138` stays `graphType`.

**Contract conformance:** both helpers have one or two arguments, and each is a few logic lines.
`demandDeclaredParams` and `injectParamAnnotations` do not grow. There is no `!!`, and the new
imports are explicit: `LuaCatsParamTag` in the bridge if it is not already imported, and no new
import in the visitor.

**Corpus:** the fix lowers `inspection.LuaTypeAssignability=448` in
`src/test/resources/corpus/lazynvim.baseline:11` by at least the `lua/lazy/pkg/rockspec.lua:228`
site. Other members' baselines may move too. The fixing change re-records the corpus baselines
with `test -PwithCorpus`, and the routine `run test` does not run the corpus sweep. The commit
states the before→after counts.

## 6. Test Strategy

New class **`net.internetisalie.lunar.analysis.LuaOptionalParamExplicitNilTest`**
(`src/test/kotlin/net/internetisalie/lunar/analysis/LuaOptionalParamExplicitNilTest.kt`). It
extends `net.internetisalie.lunar.lang.types.IndexedBasePlatformTestCase`, the base
`LuaCrossFileGlobalFunctionTest` uses for bare cross-file `function name()` globals, and it is
annotated `@RunWith(JUnit4::class)`.

Shared helper: `assignabilityHits(text: String): List<String>` calls
`myFixture.enableInspections(LuaTypeAssignabilityInspection())`, then
`myFixture.configureByText("t.lua", text)`, then `myFixture.doHighlighting()`, and returns the
`description`s containing `"is not assignable to"`. The LuaJIT cases pin with
`CorpusTarget.pin(project, Target(LuaPlatform.LUAJIT, requireNotNull(PlatformVersionRegistry.findVersion(LuaPlatform.LUAJIT, "2.1"))))`
and call `CorpusTarget.restore(project, saved)` in `finally`. The construction is the one used in
`corpus/MemberTargetPinTest.testManifestTargetPinsTheLuaJitRuntime`.

The cross-file fixture `defs.lua`, added with `myFixture.addFileToProject`:
```lua
---@param name string
---@param mode? string
function open(name, mode) end
```

| Test | Input | Expected | Before fix |
|------|-------|----------|-----------|
| `testExplicitNilForOptionalStubParamIsSilent` | LuaJIT 2.1; `local env = {}` / `local f = loadfile("x.lua", nil, env)` / `print(f)` | no hit | red. Executed as §1's probe on `5dd675a4`, not on `adf41f40` |
| `testExplicitNilForOptionalCrossFileParamIsSilent` | `defs.lua` above; `open("x", nil)` | no hit | red, predicted (§4 scope), not executed |
| `testExplicitNilForOptionalInFileParamIsSilent` | the three `defs.lua` lines plus `open("x", nil)` in `t.lua` itself | no hit | red, predicted (§4 scope), not executed |
| `testWrongTypeForOptionalStubParamIsReported` (control) | LuaJIT 2.1; `loadfile("x.lua", 1, {})` | exactly one hit containing `number is not assignable to` | green; must stay green |
| `testExplicitNilForRequiredCrossFileParamIsReported` (control) | `defs.lua` above; `open(nil, "r")` | exactly one hit, `nil value is not assignable to string` | green; must stay green |
| `testExplicitNilForRequiredInFileParamIsReported` (control) | the `defs.lua` lines in `t.lua` plus `open(nil, "r")` | exactly one hit, `nil value is not assignable to string` | green; must stay green |

The control assertion is `contains`, not equality: after Change A the message becomes the union
form (`number is not assignable to union nil | string`, ordering per `LuaTypeAlgebra`'s sort,
not executed).

**Order and decision rule:**
1. Write the class and run it before any fix (`gce-builder.sh run "test --tests *LuaOptionalParamExplicitNilTest*"`).
   Record which rows are red. This settles the "every optional param or only this stub's" question
   empirically, and the result goes into the commit message.
2. Apply Change A. The stub and cross-file rows must go green.
3. Apply Change B **only if** `testExplicitNilForOptionalInFileParamIsSilent` was red in step 1.
   If it was green, drop Change B and keep the row as a control.
4. Mutation proof (`mutation-proof` skill): revert `demandTypeOf` to `param.node.read`, and the
   stub and cross-file rows must go red. If B was applied, revert `demandTypeFor` to `graphType`,
   and the in-file row must go red. All three controls must stay green under every mutant.
5. Gate on the full suite (`run test`), then `test -PwithCorpus` to re-record the baselines (§5).
   The existing pins must stay green: `LuaDeclaredContractErrorTest` (in-file required-param nil
   via two variables), `LuaCrossFileGlobalFunctionTest`, and `ExpectedCallbackResolverTest`.

**Not executed:** every §4 claim beyond §1's reproduction; the predicted before-fix state of the
cross-file and in-file rows; the exact union message wording; and the corpus delta.

## 7. Result (measured)

Before the fix, all three target rows were red with `nil value is not assignable to string`, and
all three controls were green. So every optional `---@param name? T` with a scalar `T` rejected an
explicit `nil`, not only the LuaJIT stub's, and the same-file site was a real second defect. Both
Change A and Change B are applied, as §5 specifies.

| Mutant | Red | Green |
| :-- | :-- | :-- |
| Change A reverted (`demandTypeOf` never widens) | stub row, cross-file row | in-file row, all three controls |
| Change B reverted (`demandTypeFor` never widens) | in-file row | stub row, cross-file row, all three controls |

Change B also moves one type golden. In `types-golden/shapes/pipeUnion.golden:41`, the hash for
`Pipe:read`, declared `---@param fmt? string`, changes because the hash covers the parameter node's
`read`, which is now `string|nil`. The rendered type `fun(fmt?)` and the node's `write` are
unchanged. With Change B reverted, the committed golden passes again.

## 8. Baseline movement (measured)

One re-record (`-PwithCorpus -PrecordCorpusBaseline`), on top of BUG-551. Only
`inspection.LuaTypeAssignability` moved, in one member:

| Member | Before | After |
| :-- | --: | --: |
| `lazynvim` | 448 | 447 |

The baseline records counts, not sites, so it does not show which hit went. The
`lua/lazy/pkg/rockspec.lua:228` site from §3 is the expected one, but that was not checked. A first record, made before
merging BUG-551, also read zerobrane's `LuaSuspiciousConcatenation` as 17 rather than 19 (BUG-541
noise, not committed). The record on top of BUG-551 read 19.
