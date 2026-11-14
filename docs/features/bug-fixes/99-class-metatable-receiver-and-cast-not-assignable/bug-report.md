---
id: "BUG-99"
title: "A `@cast` is ignored, so values the cast excludes are still reported at the uses after it and at every caller"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-99: A `@cast` is ignored, so excluded values are still reported after it and at its callers

Found by MAINT-65's TC 7 diagnostic. The type engine never applies `---@cast`. Values the cast excludes still
reach every use after it, and the demand those uses raise travels back to each caller. MAINT-65's gate
promotes two of these findings to ERROR, and base already carries ten ERROR rows from the same mechanism,
so this bug blocks MAINT-65.

The chained-call family that was originally filed here (luals `compiler.lua:2508`) has a separate cause and
is now BUG-100.

## 1. Reproduction
Type corpus member luals. Read `script/vm/compiler.lua` alone in a fresh fixture.

| `compiler.lua` line | Code | Finding |
|---|---|---|
| 2798–2799 (family B) | `vm.compileByGlobal(source)` and `vm.compileByVariable(source)`, after `---@cast source parser.object` at 2796 | `vm.generic \| vm.generic is not assignable to parser.object` |
| 1685, 1703, 1974, 1976 (family C) | the result of `vm.getReturnOfFunction`, declared `(parser.object\|vm.generic)?`, passed to `vm.compileNode` after a nil guard | `parser.object \| vm.generic \| nil is not assignable to parser.object` |

Base (1a9933cd) and change (1e3e5b8b) counts are in MAINT-65 risks-and-gaps, DR-11.

### 1.1 Light fixture

Each case reproduces on base (4d14bf37) as a `BasePlatformTestCase`, using `myFixture.addFileToProject` and
`LuaTypesSnapshot.forFile(use).getErrors()`. Everything in it is fully resolved, so it needs no MAINT-65 gate,
and family B already shows as ERROR on base.

`mdefs.lua`:

```lua
---@class vm
local vm = {}

---@class Obj
---@field type string

---@class Gen
---@field proto Obj

---@param source Obj
---@return boolean
function vm.compileByGlobal(source)
    return true
end

---@param proto Obj
---@return Gen
function vm.newGen(proto)
    return { proto = proto }
end

return vm
```

`use.lua` (BC-FULL):

```lua
---@class vm
local vm = require('mdefs')

---@param obj Obj
---@return (Obj|Gen)?
function vm.getReturn(obj)
    if obj.type == 'function' then
        return obj
    end
    if obj.type == 'doc' then
        return vm.newGen(obj)
    end
    return nil
end

---@param source Obj|Gen
function vm.compileNode(source)
    if not source then
        return
    end
    ---@cast source Obj
    vm.compileByGlobal(source)
end

---@param mfunc Obj
local function bind(mfunc)
    local r = vm.getReturn(mfunc)
    if not r then
        return
    end
    vm.compileNode(r)
end
```

| Case | Base result |
|---|---|
| BC-FULL | 40 findings, 10 of them ERROR. `Gen \| Gen \| Gen \| { ... } is not assignable to Obj` appears at `source` on lines 17 and 22 (family B, corpus 2782/2798), and at `r` / `vm.getReturn(mfunc)` on lines 27 and 31 (family C, corpus 1685/1703/1974/1976). `Gen \| { ... } is not assignable to Obj` appears at `vm.newGen(obj)` on line 11 and on `vm.getReturn` (corpus 839 and 809–842). |
| BC-CAST-ONLY: the header plus `vm.compileNode` alone | 10 × HYPOTHESIS `Gen \| Obj is not assignable to Obj`. The declared `Obj\|Gen` value reaches `vm.compileByGlobal(source)` as though the cast were absent. |
| BC-NOCAST (control): BC-FULL with the `---@cast` line removed | same findings as BC-FULL. Without the cast these are expected: `Gen` really does reach an `Obj` parameter. |

Removing `---@cast source parser.object` from a reduced corpus `compiler.lua` leaves the finding list
byte-identical apart from line shifts. The reduced file is `getReturnOfFunction`, `bindReturnOfFunction` and
`compileNode`, read against the real luals tree. Keeping only `vm/generic.lua` and `vm/global.lua` besides the
consumer still reproduces it. `vm/global.lua` is needed because it declares
`vm.compileByGlobal(source: parser.object)`.

## 2. Expected
No finding at any of these sites. At lines 2798–2799, `---@cast` narrows `source` to `parser.object`. The
callers pass a `parser.object | vm.generic` into a parameter that accepts it.

## 3. Actual
Base reports family B as HYPOTHESIS at 2798–2799 and as ERROR at 2782. It reports family C as ERROR
(`vm.generic | vm.generic`) and as HYPOTHESIS (`parser.object | vm.generic | nil`) at 1685, 1703, 1974 and
1976. With MAINT-65's gate, the 2798–2799 rows become ERROR and family C gains seven HYPOTHESIS rows.

Related: BUG-98, where a class and its constructor table split into two union arms. BUG-100, the chained-call
family, was split out of this report.

## 4. Root cause: the type engine never reads `@cast`

`LuaCatsCastTag` is parsed (`luacats.bnf:86`) and `LuaCatsComment.getCastTagList()` exists
(`LuaCatsLazyCommentImpl.kt:46`). Its only readers are documentation code (`LuaComment.kt:137`,
`LuaDocGenerator.kt:16`); `grep -rn 'cast' src/main/kotlin/net/internetisalie/lunar/lang/psi/types/` finds
nothing. After `---@cast source parser.object`, the name `source` still resolves to the parameter's own
`VariableNode`. That node is fed by three sources:
- its declared value, through `LuaTypeGraphBridge.injectParamAnnotations` (`LuaTypeGraphBridge.kt:156`);
- every same-file caller's argument, through `LuaTypeGraph.checkFunctionCompatibility` (`LuaTypeGraph.kt:837`,
  `addEdge(useParam.node, valueParam.node)`);
- through the caller's local, the callee's own returns. These carry the inferred `return vm.createGeneric(...)`
  value beside the declared `(parser.object|vm.generic)?` (`LuaTypeGraph.kt:833`).

The use after the cast, `vm.compileByGlobal(source)`, raises a *declared* `parser.object` demand on that node.
`checkNodePairs` pairs every reaching value with that demand, so the `vm.generic` values are reported:
- **B (2782, 2798, 2799):** at the use site itself.
- **C (1685, 1703, 1974, 1976, 809–842, 839):** the same demand propagates back through `source` to each
  caller's argument and to `getReturnOfFunction`'s return. C has no cause of its own: it is B's demand seen
  from upstream.

**Executed proof.** A probe implemented the plain form `---@cast name T`. It bound a fresh `VariableNode`,
seeded by a `declaredOrigin` value of `T`, into the current scope, the way `injectNarrowedBinding` does at
`LuaTypesVisitor.kt:516`. Results:
- BC-FULL went from 40 findings to 0, and BC-CAST-ONLY from 10 to 0. BC-NOCAST did not change.
- On the full `compiler.lua` (base), it removed all 17 ERROR rows of this mechanism: 10 ×
  `vm.generic | vm.generic is not assignable to parser.object` at 809, 839, 1685, 1703, 1974, 1976, 2782 and
  2798, plus 7 × `Missing required field` at 839.
- In total it removed 173 rows (497 → 327). It added three HYPOTHESIS rows at 1138 and 1139:
  `parser.object[] is not assignable to { ... }` on `selectNode`'s `list`. The removed rows had been masking
  them.

**Placement catch (executed).** The probe hooked `visitElement` on `LuaCatsLazyCommentImpl`, so it ran in
visit order. That worked when the cast came after another statement in the block, as at corpus 2796. It had
**no effect** when `---@cast` was the first line of a function body (BC-FULL with the `if not source` guard
removed): the findings were unchanged. In that position the comment is not visited before the block's
statements. The fix must therefore find a cast by the statement it precedes, not by visit order.

### 4.1 Ruled out (executed)

| Hypothesis | Evidence |
|---|---|
| A shared cause in the duplicate `vm.generic \| vm.generic` union | The duplicate is two `Table(className="vm.generic")` with identical member keys and distinct `VariableNode` maps. Data-class equality makes them unequal, so `LuaTypeAlgebra.canonicalize` cannot deduplicate them (`LuaTypeAlgebra.kt:61`). It is cosmetic: the cast probe removes the findings while the duplicate stays elsewhere. |
| Nil-guard narrowing drops `nil` (family C's `\| nil`) | It does **not** narrow. After `if not returnObject then return end`, `returnObject` still dumps with a `Nil` arm (that is BUG-70's scope). It is not causal, though: `checkCompatibility` drops the nil arm before judging (`LuaTypeAlgebra.informativeArms`, `LuaTypeGraph.kt:517`), and the `vm.generic` arm alone fails. |
| The `vm.node.object` alias | Read alone, the parameter's declared type dumps as `Any \| vm.variable`, so the alias is unresolved. Replacing it with `parser.object \| vm.generic` reproduces the same findings, so the alias is not the cause. |

## 5. Fix strategy: apply `@cast` in `LuaTypesVisitor`

1. **Find the casts.** In `visitBlock` (`LuaTypesVisitor.kt:463`), iterate the block's statements yourself
   rather than relying on `super.visitBlock`. Before each statement, read
   `getAllCatsComments(statement).flatMap { it.getCastTagList() }` (`LuaTypesVisitor.kt:310`) and apply the
   tags in order. This also covers a cast on the first line of a body (the placement catch in §4). First
   confirm where that comment is parented with `DebugUtil.psiToString`.
2. **Apply one tag.** Set `name = tag.argName.text`. If `scope.lookup(name)` is null, skip the tag. Take each
   `LuaCatsCastModifier` left to right, finding it with
   `PsiTreeUtil.getChildOfType(argType, LuaCatsCastModifier::class.java)`:
   - no `addOrSubtract`: the result is `T`;
   - `+T`: `Union.create(current + T)`;
   - `-T`: `LuaTypeAlgebra.subtractMember` (`LuaTypeAlgebra.kt:80`);
   - `-?` / `+?`: subtract or add `Nil`.

   Resolve `T` with `TypeParser.parse(modifier.type.text, tag)` and `LuaGraphType.fromLuaType`. Then bind a
   fresh `graph.variable(tag)`, seeded by `graph.value(tag, result, declaredOrigin = true)`, with
   `scope.declare(name, …)`. Do **not** connect it to the old node: cutting that connection is the fix.
3. The corpus uses every form: `---@cast source parser.object`, `---@cast nd vm.global | vm.variable`,
   `---@cast nd -vm.global, -vm.variable`, `---@cast node -?`.

Family C needs no fix of its own. **Not yet executed on `maint-65-impl`:** the claim that the cast fix also
removes the seven HYPOTHESIS rows the gate adds to family C is an inference. They are the same mechanism, with
one arm fewer, as base rows the cast probe did remove. The corpus gate in §6 confirms it.

Follow-up, not needed for MAINT-65: a declared parameter does not shield callers from the body's demands.
BC-NOCAST reports caller-side ERRORs for a mistake that is inside `compileNode`.

## 6. Test strategy

New class `src/test/kotlin/net/internetisalie/lunar/lang/types/LuaCastFlowTest.kt` (light fixture). It
asserts on `LuaTypesSnapshot.forFile(use).getErrors()` messages that contain `is not assignable`:

| Test | Fixture | Before fix | After fix |
|---|---|---|---|
| `testCastNarrowsParameterForLaterUses` | §1.1 BC-FULL | 40 findings (10 ERROR) | none |
| `testCastWithoutCallers` | BC-CAST-ONLY, guard kept | 10 HYPOTHESIS | none |
| `testCastOnFirstBodyLine` | BC-FULL with the `if not source` guard removed | findings | none |
| `testCastSubtractAndOptionalForms` | `---@cast x -Gen` and `---@cast x -?` on an `Obj\|Gen\|nil` local, then passed to an `Obj` parameter | finding | none |
| `testNoCastControl` (control) | §1.1 BC-NOCAST | the `vm.compileByGlobal(source)` finding | still reported |

**Corpus gate.** Read luals `script/vm/compiler.lua` alone (`-PwithTypeCorpus`, in the shape of MAINT-65's
`ProbeMaint65CompilerTest`). There must be no `vm.generic` row at 809, 839, 1685, 1703, 1974, 1976, 2782,
2798 or 2799. Run it **on base and on `maint-65-impl` with the fix applied**; the second run closes the §5 gap
on the seven gate-added rows. Review any added rows; on base, the cast probe added exactly the three at
1138/1139.
