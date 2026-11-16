---
id: "BUG-100"
title: "In a chained call `a():b()`, the result takes `a()`'s type, so `x = a():b()` and its uses are reported against the wrong class"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-100: In a chained call `a():b()`, the result takes `a()`'s type

Split out of BUG-99, where it was family A. MAINT-65's gate promotes luals `compiler.lua:2508` to ERROR,
so this bug blocks MAINT-65.

This is the limitation recorded in AGENTS.md (Lessons Learned, type engine): **"`visitFuncCall` only models
`nameAndArgsList.firstOrNull()`"**. The engine does not type the second and later segments of a chain. The
lesson treated that as a gap to work around when resolving a later step. This bug is what happens when the
gap is left alone: the whole chain is given the first segment's type, which is wrong rather than merely
missing.

## 1. Reproduction
Type corpus member luals. Read `script/vm/compiler.lua` alone in a fresh fixture.

| `compiler.lua` line | Code | Finding |
|---|---|---|
| 2508 | `local resolved = vm.getGeneric(ext):resolve(uri, source.signs)`, inside `if vm.getGeneric(ext) then` | `vm.generic \| vm.generic is not assignable to { ... }`, twice |
| 2509 | `for obj in resolved:eachObject() do` | `vm.generic \| vm.generic is not assignable to { ... }` |

`vm.getGeneric` is declared `---@return vm.generic?`, and `vm.generic`'s `resolve` is declared
`---@return vm.node`. On base the findings are HYPOTHESIS; with MAINT-65's gate (1e3e5b8b) the two at 2508
are ERROR (MAINT-65 risks-and-gaps, DR-11). Keeping only `vm/vm.lua`, `vm/generic.lua` and a 10-line
consumer reproduces the 2508/2509 findings exactly.

### 1.1 Light fixture

Each case reproduces on base (4d14bf37) as a `BasePlatformTestCase`, using `myFixture.addFileToProject` and
`LuaTypesSnapshot.forFile(use).getErrors()`. Everything in it is fully resolved, so the ERROR form needs no
MAINT-65 gate.

`defs.lua`:

```lua
---@class Node
local Node = {}
Node.__index = Node
function Node:each() end

---@class Gen
---@field proto string
local mt = {}
mt.__index = mt

---@return Node
function mt:resolve()
    return setmetatable({}, Node)
end

---@class Holder
---@field gen Gen

---@param h Holder
---@return Gen
function getGeneric(h)
    return h.gen
end
```

| Consumer `use.lua` | Base result |
|---|---|
| A-ERR: `---@type Holder` / `local h` / `---@param n Node` / `local function useNode(n) end` / (blank) / `useNode(getGeneric(h):resolve())` | 3 × ERROR `Gen \| Gen is not assignable to Node`, anchored on the call, on `n` and on `useNode` |
| A-HYP: `---@param h Holder` / `local function f(h)` / `    local resolved = getGeneric(h):resolve()` / `    resolved:each()` / `end` | 3 × HYPOTHESIS `Gen \| Gen is not assignable to { ... }`. This is the corpus shape of 2508/2509. |
| A-CTL (control): the A-ERR header, then `local g = getGeneric(h)` / `useNode(g:resolve())` / `local r = g:resolve()` / `r:each()` | no findings |

The dump shows that `getValueType(getGeneric(h):resolve())` is `Union(Table(Gen), Table(Gen))`, which is the
type of the *first* segment. A variant with a plain `---@class Gen` / `local Gen = {}` /
`function Gen:resolve()`, with no metatable and no `__index`, gives the same ERROR, so the `@class` metatable
plays no part.

## 2. Expected
`a():b()` has `b`'s return type. `useNode(getGeneric(h):resolve())` is clean, and at 2508 `resolved` is a
`vm.node`, which has `eachObject`.

## 3. Actual
The whole chain has `a()`'s return type (`Gen`, `vm.generic`). Every demand on the result is checked against
that class instead.

## 4. Root cause

`funcCall ::= varOrExp nameAndArgs+` (`lua.bnf:310`), so `vm.getGeneric(ext):resolve(uri, source.signs)` is
**one** `LuaFuncCall` with two `nameAndArgs`. `LuaTypesVisitor.visitFuncCall`
(`src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypesVisitor.kt:905`) makes two mistakes:
- It binds `elementNodes[o] = callResultNodes` for the whole call (lines 911–912).
- It wires those nodes only to `o.nameAndArgsList.firstOrNull()` (line 934), which here is the `(ext)`
  segment, through `graph.addEdge(calleeNode, graph.use(o, callDemand))` (line 1006). Segments after the
  first are never modelled.

So the expression carries `vm.getGeneric`'s return type instead of `resolve`'s, and every later demand on the
result is checked against `vm.generic`. At 2509 that demand is `resolved:eachObject()`, which is where the
`{ ... }` comes from.

**Executed proof.** A probe left the call untyped whenever `nameAndArgsList.size > 1`. It removed every
finding in the light fixture (A-ERR and A-HYP both went to 0) and left A-CTL clean. On the full
`compiler.lua` it removed exactly the five 2508/2509 rows.

**The 18-row lesson.** On its own, that probe also *added* 18 HYPOTHESIS rows on the full file
(`fun(source) | fun(source) is not assignable to fun(p)` at 998, 1114, 1703 and 1976, plus `source` rows at
2782 and 2798). An untyped result reaches `vm.compileNode` and widens what its parameter receives. With
BUG-99's cast probe also applied, the untyped variant added none. Erasing the chain's type is therefore not
a fix; the chain has to be modelled.

### 4.1 Ruled out (executed)

| Hypothesis | Evidence |
|---|---|
| The duplicate `vm.generic \| vm.generic` union | It is cosmetic. It is two `Table(className=…)` with identical member keys and distinct `VariableNode` maps, which `LuaTypeAlgebra.canonicalize` cannot deduplicate (`LuaTypeAlgebra.kt:61`). A-ERR reports `Gen \| Gen` for the same reason, and the finding comes from the wrong class, not from the duplicate. |
| A `@class` metatable whose `self` is typed as the anonymous constructor | The plain-class variant without a metatable gives the same ERROR. The `{ ... }` is the member demand from `resolved:eachObject()`, not `self`. |

## 5. Fix strategy: model every segment in `LuaTypesVisitor.visitFuncCall`

Model every `nameAndArgs`, not only `firstOrNull()` (line 934):
- Segment 0 keeps today's path.
- For segment *i* > 0, the receiver is segment *i*−1's first result node. A method segment (`:name(args)`)
  resolves through `methodSiteMember(receiver, methodExpr, name)` (line 969), with `implicitSelf = true` on
  the call demand. A plain `(args)` segment calls the previous result directly.
- `elementNodes[o]` holds the **last** segment's result nodes.

Leaving the call untyped is not an acceptable fix (§4, the 18-row lesson). Keep `LuaExpectedCallbackResolver.kt:28` and
`LuaTypesVisitor.kt:143/172/1188` on segment 0; they handle lambda seeding, `require` and `setmetatable`,
which concern the first call only. Once this lands, update the AGENTS.md lesson.

## 6. Test strategy

New class `src/test/kotlin/net/internetisalie/lunar/lang/types/ChainedCallResultTypeTest.kt` (light
fixture, §1.1 `defs.lua`). It asserts on `LuaTypesSnapshot.forFile(use).getErrors()` messages that contain
`is not assignable`:

| Test | Fixture | Before fix | After fix |
|---|---|---|---|
| `testChainedMethodResultMeetsDeclaredParam` | A-ERR | 3 ERROR | none |
| `testChainedMethodResultMemberDemand` | A-HYP | 3 HYPOTHESIS | none |
| `testChainedMismatchStillReported` | A-ERR with `---@param n Gen` | none (the chain is typed as `Gen`) | an error naming `Node` |
| `testSplitChainControl` (control) | A-CTL | none | none |

**Corpus gate.** Read luals `script/vm/compiler.lua` alone (`-PwithTypeCorpus`, in the shape of MAINT-65's
`ProbeMaint65CompilerTest`). There must be no `vm.generic` row at 2508 or 2509, on base and on
`maint-65-impl`. Diff the rest of the file's findings against the pre-fix read and classify every added row;
the 18-row lesson shows that a change here moves findings elsewhere.
