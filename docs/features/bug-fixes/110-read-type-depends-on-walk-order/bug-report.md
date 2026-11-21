---
id: "BUG-110"
title: "A variable's demanded type depends on the order its down-set was built in, so the members of one cycle can read differently"
type: "bug"
parent_id: "BUG"
status: "done"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-110: A variable's demanded type depends on the order its down-set was built in

Found by the cluster-collapse prototype for BUG-97 part 2. Merging a strongly connected set of
variables into one node changed the order the `read` walk met their demands in, and penlight
`Date.lua` changed type although every merged variable reaches the same demands. The collapse is exact
only if `read` is a function of the demands reached, not of the order they are met in.

## 1. Reproduction
```lua
local function finish(p, sec)
  if p then sec = sec .. p end
  return sec
end
local function parse(p, h)
  local sec
  sec = h:sub(5, 6)
  if #sec == 0 then sec = nil end
  sec = finish(p, sec)
  return sec
end
```
Hover `h:sub(5, 6)`. Add `sec = finish(p, sec)` as the first statement after `local sec` and hover again.

## 2. Expected vs Actual
- **Expected**: the call reaches the same two demands either way (`..` in `finish`, `#` in `parse`), so
  it has the same type either way.
- **Actual**: `string` with the extra call, `any[] | string | { ... }` without it. In graph terms, two
  variables of one cycle (`theMembersOfACycleReadTheSameType`) read `..`'s `string` and `#`'s
  `any[] | string | { ... }` respectively, and one variable reads either, depending on which edge was
  added first.

## 3. Root Cause
`VariableElement.resolveRead` (`LuaTypeNodes.kt`) folds the demands in `downSet` iteration order, and
recurses into every downstream variable with a shared guard:
- a downstream variable contributes its *own* fold, computed over *its* down-set order;
- a variable the guard has already met contributes nothing;
- the fold then takes the first demand (BUG-395 merges tables, BUG-553 lets a table-excluding trait
  outrank them, everything else is first-wins).

`downSet` is in insertion order, and insertion order is the order edges were added and propagated
(`propagateUpward`), not the order the demands were written. The members of a cycle share one
transitive down-set, but each holds it in a different order, so each takes a different "first". In
`Date.lua`, `sec` in `parse_date_unsafe` and `parse_iso_end`'s `sec` parameter form that cycle; their
down-sets hold the parameter's `..` (`Stringable`) and the caller's `#sec` (`Lengthable`). Neither
demand admits no-table-and-is-a-table, so first-wins decides. Today the walk meets `..` first. The
prototype's merged node kept the caller's order, met `#` first, and `hourmin:sub(5,6)` showed
`#`'s `any[] | string | { ... }`.

The recursion adds nothing the root's own down-set lacks: down-sets are transitively closed
(`LuaTypeGraph.addEdge`), which BUG-97 already relies on for `declaredDemand`.

## 4. Fix Strategy
Fold the leaf demands of the root's own down-set (the non-variable uses), ranked by a key that is a
property of each demand, not of the walk:

| Tier | Rule | Source of intent |
|------|------|------------------|
| 1 | A table demand is present and a trait admitting no table is too: the earliest such trait | BUG-553: a member demand is a hypothesis, the trait is evidence against it |
| 2 | Two or more table demands: their merge, members taken earliest first | BUG-395: member demands accumulate |
| 3 | Otherwise the earliest demand | first-wins |

"Earliest" is the first demand the code makes, ranked by `LuaDemandRanks` (ties by creation order):
- **A demand the visitor records** ranks at its creation order in the graph: the order
  `LuaTypesVisitor` walked the source and recorded the demands. That is what first-wins meant when it
  was written, without the reordering edge propagation adds.
- **A converted declaration's demand** (`LuaGraphType.fromLuaType`) is created whenever the type is
  first converted, which says nothing about the code: `local append = table.insert` converts
  `table.insert`'s `pos: integer` long before any call passes it an argument. It ranks at the site
  that brings it into the code, and last until something does:
  - a signature's parameter or return ranks at the walk position of the first edge joining it to a
    code variable: a call, or a declaration meeting its converted signature;
  - a class or table member ranks at the creation order of the first code variable it is joined to,
    the member's declaration or first access. The edge itself comes later, when a call site joins
    the member, and ranking there puts the declaration behind the calls checked against it.

Each rank is fixed once the graph is built, so the fold is a function of the set of demands reached.
The recursion goes, so a read root iterates one down-set instead of every down-set of its cluster.

Rejected, measured on the goldens (§6.3): plain creation order (Date `ch` reads `number`); converted
demands after every code demand (new WARNING on `function Ar:two`); source offsets (luarocks `file`
displays `{ ... }`); wiring position for members too (new WARNING on `function Ar:two` again); a
member's rank for signature slots (`generic`'s `x` reads `undefined` instead of its `---@param T`).

## 5. Test Strategy
`LuaReadFoldOrderTest` (light fixture):
- two variables of a cycle read the same type (red on base: `Stringable` vs `Lengthable`);
- the same two demands wired in either order read the first-created one (red on base);
- BUG-553's trait outranks a table demand created before it (red on base);
- the reproduction above: both orders of the caller's statements read `string`;
- the Date.lua `ch` shape (`local append = table.insert`, `"…" .. ch`, `append(patt, ch)`) reads
  `string`;
- a generic method's own parameter still reads its `---@param T` though the class member's converted
  signature reaches it first.

`LuaTypeGraphCycleGuardTest.aReadRootFoldsOnlyTheUsesOfItsOwnDownSet` replaces the guarded-entry case
whose entry point is gone (red on base).

## 6. Findings classification

Measured on the libvirt builder from `ee04d88c`. Goldens were recorded with the golden hash
temporarily replaced by its input, so each changed line could be read.

### 6.1 Decision: a hub's method-name copy of a call diagnostic may go
A TYPE-12 member-demand hub (the first call site's member node, e.g. `:setName` at (89,97) in
`annotated3`) is bound both ways to the class's declaring member node (`:setName` at (53,61)). They
form one strongly connected set and reach exactly the same uses (probe: both hold uses 15, 39, 52 and
65), but on base they read differently, because each met a different one first. Every diagnostic
anchored on the hub's method name exists only because of that disagreement, and any fold that is a
function of the set gives both one read.

**The user accepted losing the method-name copy of an arity WARNING at a hub site, as long as the
enclosing call keeps it.** `arity` (105,109) and `selfCalls` (172,176) are therefore corrections:
duplicates removed, each enclosing call still reports the WARNING.

### 6.2 What moved
| Artifact | Result | Class |
|----------|--------|-------|
| WARNING lines | `arity` loses (105,109) *Too few arguments: expected at least 2, got 1*; `selfCalls` loses (172,176) *Too many arguments: expected at most 1, got 2*. No WARNING or ERROR line is added anywhere | correction (§6.1) |
| HYPOTHESIS lines (no inspection reports them) | hub method-name sites only: `annotated1/3/20` (89,97), `annotated-shapes` (894,897), `arity` (105,109), `chain` (119,123), (130,134), (139,143), (148,152), (167,171), `declaredFieldNil` (84,86), `fieldFunction` (94,98), `param` (138,146), `twoReceivers` (98,101), `unionReceiver` (137,139). Each hub now carries its declaring member's hypotheses, or none where the call already reports the same message | correction |
| Element types, shapes goldens | none | — |
| `penlight-Date.golden`, `luarocks-zip.golden` | 16 and 7 lines, member order only inside the internal dump of a merged table demand; every displayed type unchanged, including Date's `ch` and luarocks' `file` | neutral |
| `LuaMemberDemandHubTest` cases 02–04, `LuaAnnotatedClassDiagnosticsTest` | pinned lists updated to the rows above; the annotated-class shape is now 4n + 4 (was 4n + 3) | correction |
| Conformance `results.json` | byte-identical to base; no case leaves `agree` | — |
| Type audit (`testLuacheckMember`) | identical to base across all 562 keys, including the coverage keys | — |
| Corpus baselines | see §6.4 | — |

### 6.3 Rank variants measured
| Variant | Rejected because |
|---------|------------------|
| creation order for every demand | Date `local ch = fmt:sub(i, i)` and 24 uses read `number`: `table.insert`'s `pos` is converted at line 407, before the first `.. ch` |
| converted demands after every code demand | new WARNING (76,80) on `function Ar:two` |
| source offsets, converted demands at their first wiring | luarocks-zip `local file = cdr.file_name` and 14 uses display `{ ... }` instead of `any[] \| string \| { ... }` |
| every converted demand at its wiring position | new WARNING (76,80) on `function Ar:two`; hypotheses added at declaration names; 33 luarocks member reads change |
| every converted demand at its code variable's creation | `generic`'s `function G:id(x)` parameter reads `undefined`: the member's converted return reaches `x` through `return x` and ranked ahead of the method's own annotations |
| **slots at wiring position, members at code variable (shipped)** | — |

### 6.4 Full suite

`test --rerun --no-build-cache -PwithCorpus` on the libvirt builder: 598 files, 3892 tests,
0 failures, 0 errors, 4 skipped. No corpus baseline changes.
