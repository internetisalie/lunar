---
id: "BUG-554"
title: "A method's optional `@param name?` is required in its declared signature, so every call that omits it is *Too few arguments*"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-554: A method's optional `---@param name?` is required in its declared signature

Found while triaging the corpus movement of the LuaCATS grammar batch ([[BUG-542-BATCH]]): lazy.nvim annotations that used to fail to parse are now type-checked, and this gap is what fires. A bug report: root cause not investigated.

## 1. Reproduction

Light fixture (`BasePlatformTestCase`), `myFixture.enableInspections(LuaTypeAssignabilityInspection(), LuaReturnTypeMismatchInspection())`, `configureByText`, then `doHighlighting()`. Executed 2026-10-28 on `8e7a48f2` (before the batch) with a throwaway probe test (not committed); the same hits reproduce after it.

```lua
---@class C
local C = {}
---@param a string
---@param b? string
function C:m(a, b) end
---@type C
local c = C
c:m("x")
```

## 2. Expected vs Actual
- **Expected**: no arity diagnostic: `b` is declared optional, so one argument is enough.
- **Actual**: `8: Too few arguments: expected at least 2, got 1` (reported twice).

## 3. Context
- **Corpus effect**: net +52 `LuaTypeAssignability` hits in `lazynvim.baseline`: 140 *Too few arguments* added, 60 removed, and 28 *Too many arguments* removed (the last is [[BUG-502]]'s intended effect). The added hits appeared once [[BUG-502]] made `---@param opts? {indent?: number}` a real `@param`. Example: `view/render.lua:175` `self:append("Tasks: ", "LazyH2")` against `Text:append(str, hl?, opts?)` ("expected at least 3, got 2").
- **Possibly relevant, not established**: `LuaCatsDeclarations.paramTypes` returns name → type only, and `LuaTypeManagerImpl.funcTypeFromStub` builds `LuaParameter`s from it with no optional flag.

## 4. Root Cause

Read from code at `38878817`; nothing in this section was executed except the reproduction in §1.

**Where the message comes from.** `LuaTypeGraph.checkFunctionCompatibility` (`LuaTypeGraph.kt:849`)
counts `minParams = value.params.count { !it.isOptional && !it.isVararg }` (`:856`) and emits
*Too few arguments: expected at least $minParams, got $provided* (`:871`). It is a WARNING because
`value.declaredSignature` is true (`:865`). The dry-run twin `isFunctionCompatible` uses the same
count (`:833`). Both checks already honour `Parameter.isOptional`. The arity check is correct, and
the flag reaching it is wrong.

**How a method's signature is built.** A receiver typed as a `@class` resolves through
`LuaTypeManagerImpl.materializeClass` (`LuaTypeManagerImpl.kt:344`). Its last step,
`collectMethodMembers` (`:380`, `:513`) → `addMethodsOf`, types every `function C:m` / `function C.f`
member with `funcTypeFromStub(scan.className, decl)` (`:560`). That is `funcTypeFromStub`'s only
caller. In `funcTypeFromStub` (`:601`):

- the declared parameters are a `Map<String, String>` of name → type, read from
  `stub.luacatsParamTypes` on the stub arm or `LuaCatsDeclarations.paramTypes(cats)` on the AST arm
  (`:617-620`);
- each one becomes `LuaParameter(pName, LuaTypeReference(pType, decl))` (`:623`), so `isOptional`
  takes its default of `false` (`LuaStructuredTypes.kt:92`);
- `declaredSignature = params.isNotEmpty()` (`:633`) makes the resulting arity a contract.

`LuaGraphType.fromLuaType` copies `p.isOptional` faithfully when it converts the
`LuaFunctionType` to a graph function (`LuaGraphType.kt:265-267`), so the `false` reaches `:856`
unchanged.

**Why the `?` is lost.** The grammar puts the marker in its own child:
`paramTag ::= '@param' ((<<ArgName NAME>> <<ArgSymbol ('?')>>?) | <<ArgSymbol ('...')>>) <<ArgType type>>`
(`luacats.bnf:143`). `LuaCatsDeclarations.paramTypes` (`LuaCatsDeclarations.kt:89-90`) reads only
`argName` and `argType` and never reads `argSymbol`. The only tag reader on this path therefore
discards the marker, on both arms:

- **AST arm** (`decl.stub == null`, the method is in the open file): `paramTypes(cats)` drops it.
  This is the executed reproduction in §1.
- **Stub arm** (the method is in another file, which is how `lazy/view/text.lua`'s `Text:append`
  reaches `view/render.lua`): `LuaFuncStubElementType.createStub` stores the same `paramTypes`
  output (`LuaFuncStubElementType.kt:29`), and `serialize` writes only name and type per parameter
  (`:42-46`). The stub cannot carry the marker, because it has nowhere to store it.

**Free functions are not affected** (read from code, and partly executed by BUG-552's tests). A free
function's signature never passes through `funcTypeFromStub`:

- **In-file:** `LuaTypesVisitor` builds the graph function from the AST and sets
  `isOptional = matchingCat?.argSymbol?.text == "?"` (`LuaTypesVisitor.kt:1541`).
- **Cross-file:** `resolveGlobal` → `typeOfGlobalIn` → `globalTypeIn` runs the declaring file's
  `LuaTypesSnapshot` (`LuaTypeManagerImpl.kt:235-260`), which is the same visitor, and converts the
  result back through `graphTypeToLuaType`, which keeps `p.isOptional` (`LuaTypes.kt:240`).
- **Other `LuaParameter` sources:** a `fun(a, b?: T)` type goes through
  `TypeParser.parseFunctionArgument`, which sets the flag (`TypeParser.kt:162-168`). The only
  `LuaParameter` construction that drops it is `LuaTypeManagerImpl.kt:623` (grep for
  `LuaParameter(` in `src/main`: three sites, at `LuaTypes.kt:240`, `TypeParser.kt:168` and
  `LuaTypeManagerImpl.kt:623`).

The defect therefore covers every `@class` member declared as `function C:m` or `function C.f`. It
covers module tables too, because a module whose stub `exportedTypeString` names a class resolves
through `materializeClass` as well (`getModuleType`, `LuaTypeManagerImpl.kt:272-284`). It does not
cover `---@field m fun(...)` members: those are built by `TypeParser`, and `addMethodsOf` skips any
name already in `membersMap`. The stub field `LuaLocalFuncStub.luacatsParamTypes` has no reader:
`stub?.luacatsParamTypes` occurs only at `LuaTypeManagerImpl.kt:618`, where `stub` is a
`LuaFuncStub`.

**Not investigated:** why §1 reports the hit twice.

## 5. Fix Strategy

### 5.1 The stub schema has to change

A correct fix must change the stub schema. On the stub arm, the serialized `LuaFuncStub` is the
only information available, and it does not record the `?` (`LuaFuncStubElementType.kt:42-46`).
Without a schema change, two options remain, and both are wrong:

- reading the marker from PSI on the stub arm loads the AST, which defeats the stub;
- encoding the marker into an existing string (writing the key as `b?`, or writing the type as
  `string?`) changes what the existing serialized fields mean. It still needs a version bump, as
  BUG-401's re-keying did (`LuaFileElementType.kt:18-19`), and it would hide the fact in a format
  that every reader has to know to decode.

The schema change replaces the parameter **map** with a **list of records** that carry the flag.
This keeps the stub constructors at their current arity: `LuaFuncStubImpl` has 5 parameters and
`LuaLocalFuncStubImpl` has 4. A separate `luacatsOptionalParams` field would add a sixth parameter
to a constructor that is already over the contract's 3-argument cap. The tag reader stays the one
shared extractor that MAINT-34-03 made it, used by both `*FuncStubElementType`s.

### 5.2 Changes

1. **`luacats/lang/psi/LuaCatsDeclarations.kt`**: replace `paramTypes` (`:88-90`) with:
   ```kotlin
   /** One `@param`: its name, its type string, and whether `name?` marked it optional (BUG-554). */
   data class ParamDecl(
       val name: String,
       val typeName: String,
       val isOptional: Boolean,
   )

   /**
    * Every `@param` on [comment], in declaration order. A repeated name keeps its first position
    * and its last tag, which is the order and the winner the former name → type map had.
    */
   fun params(comment: LuaCatsComment): List<ParamDecl> =
       comment.paramTagList
           .map { ParamDecl(it.argName?.text ?: "", it.argType.text, it.argSymbol?.text == "?") }
           .associateBy { it.name }
           .values
           .toList()
   ```
   `---@param ... T` keeps the name `""`, and `isOptional` is false because its `argSymbol` is
   `...`. That behaviour is unchanged.
2. **Stub interfaces and impls**: in `LuaFuncStub`, `LuaFuncStubImpl`, `LuaLocalFuncStub` and
   `LuaLocalFuncStubImpl`, replace `val luacatsParamTypes: Map<String, String>` with
   `val luacatsParams: List<LuaCatsDeclarations.ParamDecl>`. The constructor positions stay the
   same.
3. **New `lang/psi/stubs/impl/LuaCatsParamStubCodec.kt`**: one serializer shared by both twins, so
   that the twins cannot drift:
   ```kotlin
   internal object LuaCatsParamStubCodec {
       fun write(params: List<LuaCatsDeclarations.ParamDecl>, out: StubOutputStream) {
           out.writeInt(params.size)
           params.forEach {
               out.writeName(it.name)
               out.writeName(it.typeName)
               out.writeBoolean(it.isOptional)
           }
       }

       fun read(input: StubInputStream): List<LuaCatsDeclarations.ParamDecl> =
           List(input.readInt()) {
               LuaCatsDeclarations.ParamDecl(
                   input.readName()?.string ?: "",
                   input.readName()?.string ?: "",
                   input.readBoolean(),
               )
           }
   }
   ```
   `List(n) { }` evaluates its elements in index order, so the read order matches the write order.
4. **`LuaFuncStubElementType.kt`**:
   - `createStub` (`:29`): `catsComment?.let { LuaCatsDeclarations.params(it) } ?: emptyList()`;
   - `serialize` (`:42-46`): `LuaCatsParamStubCodec.write(stub.luacatsParams, dataStream)`;
   - `deserialize` (`:56-62`): `LuaCatsParamStubCodec.read(dataStream)`. This removes the
     `mutableMapOf`.

   The field order is unchanged (name, return type, params, `rootIsLocal`).
5. **`LuaLocalFuncStubElementType.kt`**: make the same three edits (`createStub` `:28`,
   `serialize` `:41-45`, `deserialize` `:54-60`).
6. **Stub version**: in `LuaFileElementType.getStubVersion()` (`LuaFileElementType.kt:21`), change
   `5` to `6`. Add one comment line beside the BUG-401 and BUG-509 lines: *BUG-554 added each
   `@param`'s optional marker to the serialized func stubs.* This is the only stub version constant
   (grep `getStubVersion` in `src/`: `LuaFileElementType.kt:21` only).
7. **`LuaTypeManagerImpl.funcTypeFromStub`** (`:617-623`):
   ```kotlin
   val declared: List<LuaCatsDeclarations.ParamDecl> =
       stub?.luacatsParams
           ?: cats?.let { LuaCatsDeclarations.params(it) }
           ?: emptyList()
   ...
   val params =
       declared.map { LuaParameter(it.name, LuaTypeReference(it.typeName, decl), isOptional = it.isOptional) }
   ```
   The logic-line count is unchanged at about 20, under the 30-line cap. `declaredSignature` stays
   `params.isNotEmpty()`.

The fix adds no arity check and changes neither `checkFunctionCompatibility` nor
`isFunctionCompatible`. It only gives them the flag they already read. The fix uses no `!!` and no
wildcard imports: the existing wildcard `import com.intellij.psi.stubs.*` in
`LuaFuncStubElementType.kt:3` is BUG-421 debt, and the fix adds no new import of that kind. Every
new collection is a read-only `List`.

The parameter's type string stays raw (`string`, not `(string) | nil`). This matches the free path,
where an optional parameter's nil-acceptance belongs to the call-site demand (`demandTypeOf`,
`LuaTypesVisitor.kt:1063-1068`, BUG-552) and not to the declared type.

## 6. Test Strategy

None of the tests below have been executed. The expected before and after outcomes are derived from
§4.

### 6.1 Regression test: `net.internetisalie.lunar.analysis.LuaOptionalMethodParamArityTest`

The test extends `IndexedBasePlatformTestCase` and runs under `@RunWith(JUnit4::class)`, following
the shape of `LuaOptionalParamExplicitNilTest`. It enables only `LuaTypeAssignabilityInspection` and
keeps descriptions starting with `Too few arguments` or `Too many arguments`. It compares
`hits.distinct()`, because §1 reports each hit twice.

Declaration (`decl`):
```lua
---@class C
local C = {}
---@param a string
---@param b? string
function C:m(a, b) end
```

The test runs each case on two arms. Each arm asserts which branch it took before it asserts any
hit:

- **AST arm**: `configureByText("t.lua", decl + "\n---@type C\nlocal c = C\n" + call)`. The arm
  first asserts that the file's only `LuaFuncDecl` has `stub == null`.
- **Stub arm**: `addFileToProject("defs.lua", decl)`, then
  `configureByText("t.lua", "---@type C\nlocal c\n" + call)`. The arm asserts that
  `StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, "C:m", project, allScope, LuaFuncDecl::class.java).single().stub`
  is non-null. It checks this both before and after `doHighlighting()`. If the check after
  highlighting fails, highlighting loaded `defs.lua`'s AST and the arm collapsed into the AST
  branch. In that case, §6.2 is the stub-arm proof, and this arm stays as the end-to-end check.

| Case | Call | Expected after fix | Before fix |
|------|------|-------------------|------------|
| omitted optional (the bug) | `c:m("x")` | `[]` | `[Too few arguments: expected at least 2, got 1]`: **fails** |
| control: too few required | `c:m()` | `[Too few arguments: expected at least 1, got 0]` | `… at least 2, got 0`: **fails**, which pins the new minimum |
| control: too many | `c:m("x", "y", "z")` | `[Too many arguments: expected at most 2, got 3]` | same, passes |
| control: full call | `c:m("x", "y")` | `[]` | `[]`, passes |
| control: free function, in-file | `decl2 + "f(\"x\")"` | `[]` | `[]`, passes |
| control: free function, cross-file | `defs.lua = decl2`, call `f("x")` | `[]` | `[]`, passes |

Here `decl2` is `---@param a string\n---@param b? string\nfunction f(a, b) end`.

The stub arm's `local c` relies on `---@type C` alone giving `c` the declared type. If it does not,
use `---@type C\nlocal c = {}`: the test filters on arity messages, so a table-vs-class hit does
not interfere.

### 6.2 Type-level parity case: `LuaCatsStubAstParityTest` TC-11

`net.internetisalie.lunar.lang.types.LuaCatsStubAstParityTest` already forces and asserts both
branches. Add `testOptionalMethodParamAgrees` with
`ParityCase(id = "TC-11", source = <decl with C__>, className = "C__", expectedMembers = setOf("m"), expectedSupertypes = emptyList(), expectedMemberTypeContains = mapOf("m" to "fun(a: string, b?: string)"))`
and `index = 11`. `LuaFunctionType.name` renders `b?` only when `isOptional` is set
(`LuaStructuredTypes.kt:117`). Before the fix, both arms render `b: string`, so the case fails on
both. In the same file, update TC-9 `testStubVersionIsCurrent` (`:364`) from `5` to `6`.

### 6.3 Extractor unit test: `LuaCatsDeclarationsTest`

`paramsOf` (`:30-36`) moves to `LuaCatsDeclarations.params`:

- `testOptionalKeyTableParamIsDeclared` asserts `params.map { it.name }` and
  `params.single { it.name == "opts" }.typeName`;
- `testMalformedTableParamStillFallsBackToLdoc` asserts `params.map { it.name }`;
- add `testOptionalMarkerIsCarried`: `---@param a string\n---@param b? string\nfunction f0(a, b) end`
  gives `isOptional` values `[false, true]`.

### 6.4 Gates and corpus

- Run the full unit suite through `gce-builder` with `test --rerun`.
- Run the corpus lane with `test -PwithCorpus`. The fix is expected to lower lazynvim's
  `inspection.LuaTypeAssignability` (`src/test/resources/corpus/lazynvim.baseline:11`, currently
  `526`) by about the 140 *Too few arguments* hits that the batch's triage attributes to this
  defect, so the change that fixes the bug re-records the corpus with `-PrecordCorpusBaseline`.
  Other members' baselines may also move: any `@class` method with a `name?` parameter is affected,
  and this is unmeasured. Re-record every baseline that moves and quote the deltas in the PR.
- Member-file type goldens (`types-golden/member-files/*.golden`, which run on the builder only)
  could change wherever a declared member signature with `name?` is rendered. `penlight-Date`'s
  *Too few* lines are `HYPOTHESIS`-tier, that is, inferred signatures, and should not move. This is
  unverified.
- Not covered by this bug: an explicit `nil` passed to an optional **member** parameter
  (`c:m("x", nil)`). That call goes through `checkFunctionCompatibility`'s edge
  (`LuaTypeGraph.kt:889-891`) and not through BUG-552's `demandTypeOf`, so it may still report
  *nil … not assignable to string*. Probe it once in §6.1's harness. If it fires, file it
  separately rather than widen this fix.

## 7. Verification (measured)

**Phase 0 premises.**

- **Red before the fix, on both arms.** The bug case and the too-few control both failed, for the reason §4 names:
  - AST arm: `[Too few arguments: expected at least 2, got 1]`;
  - stub arm: the same message;
  - the AST arm's `c:m()` was `[… at least 2, got 0, … at least 1, got 0]`. Two paths report arity on a method call, and only the `funcTypeFromStub` one ignored the `?`.
- **The other four controls passed** before the fix, as §6.1 predicted.
- **TC-11** failed on the stub arm with `fun(a: string, b: string): unknown`. The stub arm asserts first, so this run did not show the AST arm's result. The AST arm's red-before is shown by the regression test.
- **`---@type C` / `local c` gives `c` the class type**, so the report's fallback was not needed.
- **The stub arm is stub-backed before highlighting but not after.** The post-highlighting check failed on every stub-arm case, as §6.1 allowed. That assertion was dropped and TC-11 kept as the branch proof. Mutation (b) below shows the stub-arm cases still read the signature from the stub.

**Green after the fix.** `LuaOptionalMethodParamArityTest` passes 10/10. `LuaCatsStubAstParityTest` passes 13/13, with TC-11 and TC-9 = 6. `LuaCatsDeclarationsTest` passes 10/10.

**Mutations.** Each was snapshotted to scratch, restored with `cp`, and verified with `cmp`.

| Mutation | Red | Green |
| :-- | :-- | :-- |
| (a) `funcTypeFromStub` drops `isOptional` | the 4 method arity cases (2 AST, 2 stub) and TC-11 | the rest |
| (b) codec writes `false` for the flag | the 2 stub-arm arity cases and TC-11 (STUB) | the AST-arm cases |

**Adjacent probe** (§6.4). `c:m("x", nil)` reports nothing, in-file or cross-file, so no bug was filed. That silence is weak evidence. `c:m("x", 1)` and even `c:m(1, "y")` also report nothing, while the free-function `g("x", 1)` reports `number is not assignable to union nil | string`. So argument *types* do not appear to be checked against a method's declared parameters at all. This was observed only; it was not investigated and not filed.

## 8. Baseline movement (measured)

One re-record after merging `origin/main` (`30dcef47`). Only `lazynvim.baseline` moved:

| Key | Before | After |
| :-- | --: | --: |
| `inspection.LuaTypeAssignability` | 526 | 306 |
| `inspection.LuaReturnTypeMismatch` | 64 | 58 |
| `inspection.LuaSuspiciousConcatenation` | 12 | 14 |
| `symbol.LuaSuspiciousConcatenation.{ ... }` | 12 | 14 |
| `inspection.LuaUnusedLocal` | 1 | 3 |
| `symbol.LuaUnusedLocal.to` | — | 1 |
| `symbol.LuaUnusedLocal.url` | — | 1 |

The lines were triaged hit by hit. The lazy.nvim sweep was run twice with temporary per-hit printing: once with the fix, once with mutation (a). The two hit lists were then diffed.

- **Removed (226):** every one is a *Too few arguments* hit, 220 from `LuaTypeAssignability` and 6 from `LuaReturnTypeMismatch`. The fix lowers only the required-parameter count, so a call can lose a hit only if every parameter it omits is marked `?`. The drop is larger than §6.4's estimate of about 140, because that estimate was the batch's net delta, not this fix's own count.
- **Added (4):** each one sits inside the range of a removed *Too few arguments* hit. `LuaTypeAssignability` is registered at level `ERROR`, and the platform hides a lesser-severity highlight inside an ERROR's range (`UpdateHighlightersUtil.isWarningCoveredByError`). These hits are therefore unmasked pre-existing ones, not new behaviour:
  - `view/render.lua:367` and `:370`, *operand of type '{ ... }'*, inside `self:append(icon .. " ", hl)` and `self:append(key .. " ", hl)`. These are [[BUG-553]]'s pattern.
  - `view/init.lua:307`, *Unused local variable* `to` and `url`, inside `self:on_key(key, function() … end)` (`:300`). These reproduce in isolation with no method call at all. Filed as [[BUG-564]].
- **zerobrane:** did not move, so the BUG-541 flicker did not occur.

**Type goldens** were re-recorded with `-PrecordTypeGolden`. Four moved:
- `shapes/pipeUnion`: 3 `HYPOTHESIS|Pipe | nil is not assignable to { ... }` errors removed. Their cause was `fh:read()` failing `Pipe:read(fmt?)`'s arity.
- `shapes/fileUnionStdlib`: 4 `File | nil …` errors removed. The cause is the same, through the bundled stub's `file:read`, on the stub arm.
- `member-files/luacheck-cache`: the same 4 `File | nil …` errors removed.
- In all four files, including `member-files/luarocks-zip`, a type hash changed (`File | nil`, `Pipe | nil`, `{ ... } | Pipe`). The rendered member signature now includes `fmt?`. Only hashes changed, not display names.
- `LuaMemberDemandHubTest` case 5 asserted the three `pipeUnion` hypotheses. It now asserts none, and TYPE-12's requirements case 5 was updated to match. With these hypotheses gone, TYPE-12's A6TH mutation is no longer red in cases 5, 9 or 10 (measured). Filed as [[BUG-565]].
