---
id: "BUG-427"
title: "A global `function f() end` in another file resolves to no type, and the assignment form loses its annotations"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "high"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-427: cross-file global functions resolve to nothing, or to an un-annotated type

Split out of BUG-425, which fixed the *demand* half of "out-of-file signatures never reach the type
graph" and found on measurement that two shapes fail earlier than that, in resolution.

## Measured (2026-08-07, indexed fixture, declaration in a second file)

```
---@param n number
function count(n) end      ->  resolveGlobal("count") = null              <- no type at all
---@param n number
count = function(n) end    ->  resolveGlobal("count") = fun(n: unknown)   <- resolves, @param lost
Lib = {}
---@param n number
function Lib.count(n) end  ->  checked; Lib.count("s") errors correctly   <- works (BUG-425)
```

Forcing a stub-index rebuild (`IndexedBasePlatformTestCase`) changes nothing, so this is not a test
fixture artefact.

Two independent defects:

1. **`resolveGlobal` does not see a global function *declaration*.** It resolves the assignment form
   `f = function() end` but not `function f() end`, which is how Lua code overwhelmingly declares
   globals.
2. **The assignment form resolves without its LuaCATS annotations.** `fun(n: unknown)` rather than
   `fun(n: number)`, so even where resolution works there is no contract to check.

## Why it matters

Every consumer of `resolveGlobal` is affected, not just assignability: hover, inlay hints, parameter
info and completion all read the same type. A project that declares its API as bare global functions
is invisible to all of them across file boundaries.

For BUG-425's purposes it means the contract population is smaller than it looks: only the
library-member form (`function Lib.f() end`) carries a checkable signature today.

## Fix direction — not traced

Unknown whether `function f() end` is missing from the global index, or indexed under a key
`resolveGlobal` does not consult. `LuaGlobalDeclarationIndex` stores `funcName.text` for
`<Class>:<method>` / `<Class>.<fn>` forms (see `AGENTS.md`), which is a hint that the *bare* form may
simply have no entry — but that is a hypothesis, and this report deliberately asserts nothing it did
not measure.

Defect 2 is likely the narrower fix: whatever builds the function type for the assignment form does
not consult the attached cats comment, while `funcTypeFromStub` does for the member form.

## Verification

- All three shapes above resolve to a `fun(n: number)` and the first two error on `count("s")`,
  matching the member form.
- `LuaDeclaredContractErrorTest` gains the two shapes alongside the member-form test it has now.
- Corpus re-baseline: BUG-425 moved nothing because so few contracts were reachable. Fixing this
  makes bare-global APIs checkable and **is** expected to move the counts — sample before accepting,
  the way BUG-425's own 244-false-positive measurement did.

## Fixed (2026-08-07) — two defects as filed, plus three the fix uncovered

### As filed

1. **`LuaGlobalAssignmentIndex` mapped only assignment targets.** A top-level `LuaFuncDecl` with no
   dotted or method name declares a global too; it is now indexed (version 1 → 2). Dotted forms are
   deliberately still excluded — those write a member, which is `LuaMemberFieldIndex`'s business.
2. **A function EXPRESSION owns no comment.** `---@param n number` above `count = function(n) end`
   is a sibling of the *assignment*, so `visitFunctionBody`'s prev-sibling walk found nothing.
   `LuaLocalVarDecl` is a `LuaCommentOwner` and answers directly; `LuaAssignmentStatement` is not,
   and its comment is frequently a sibling of an *ancestor* — so the lookup now climbs, with
   `LuaPsiImplUtil.getCatsComment`'s ownership rule reproduced (stop as soon as something else sits
   before us). Restricted to a lone right-hand side: with several expressions there is no way to
   tell which one a `@param` was written for.

### Uncovered by the fix, and fixed with it

3. **Precedence between declarations was undefined, and it broke `assert`.** Once bare
   `function assert(...)` in the bundled stubs became indexable, both the stub and a project's
   `assert = require("luassert")` declared the name, with nothing but index order deciding —
   `LuaGlobalMemberCompletionTest` went red. `doResolveGlobal` now searches **project scope first**,
   falling back to all scope: your own code wins over a library, which is the rule Lua itself
   implies. Caught only by the full suite; the targeted run was green.
4. **`graphTypeToLuaType` was not cycle-safe for functions** — MAINT-25-02 made tables safe by
   registering a placeholder before recursing and left functions registering only on the way out.
   A function type reachable from its own parameter or return recursed until the stack died.
   Latent until this fix made `setfenv`/`rawlen` resolvable, at which point luacheck's one-line
   `(setfenv and rawlen)(setfenv and rawlen)` sample **killed the highlight pass**. Fixed and
   mutation-proved (`LuaTypeGraphCycleGuardTest.selfReferentialFunctionTypeConvertsInsteadOfOverflowing`).
5. **`and`/`or` were modelled as the union of both operands**, injecting a spurious `boolean` arm
   into every `cond and x or y` — Lua's ternary. Invisible while such values could not cross a file
   boundary; the moment they could, it produced **312 of 335** new corpus errors. Now `a and b`
   carries a's *falsy* arms and `a or b` its *truthy* ones, with two rules measured into existence:
   the truthy filter drops `boolean` unconditionally (keeping it "only when nothing else remains"
   left all 312 in place, because the informative arms are often `Undefined` and `Union.create`
   drops those first), and an `Undefined` right operand makes the whole expression `Undefined`
   (without that, `value and f(...)` with an un-inferable `f` collapsed to a bare `nil` — 40 more
   false positives).

### Corpus — a large net improvement, fully attributed

| member | `LuaTypeAssignability` | `LuaReturnTypeMismatch` |
| :-- | --: | --: |
| zerobrane | 323 → **278** | 59 → **16** |
| luarocks | 196 → **144** | 22 → 15 |
| luacheck | 213 → **201** | 10 → 6 |
| penlight | 91 → 91 | 17 → 19 |
| **total** | **823 → 714** (−109) | **108 → 56** (−52) |

Every rise was sampled rather than accepted:

- `LuaSuspiciousConcatenation` +1 on luacheck, +1 on luarocks — **genuine new detections**. The
  operand types got precise enough for the inspection to see a `{ ... }` and a `nil` operand, both
  of which really are errors in Lua.
- `LuaUndeclaredVariable` +6 on zerobrane (1 945 → 1 951) — the BUG-417 severity-precedence effect
  running the *right* way: fewer type ERRORs means fewer buried warnings. The inspection-parity
  criterion improved with it, from 70/72 files to **71/72**, and the totals from 1 948/1 954 to
  1 953/1 954.
- `LuaReturnTypeMismatch` +2 on penlight — the residual class below.

**Still 37 new emissions on zerobrane** (against 74 removed), dominated by `nil value is not
assignable to …` where an `and`/`or` result carries a `nil` arm into a demand. Filed as **BUG-428**;
not chased here because it is a fourth defect and the net movement is already strongly negative.
