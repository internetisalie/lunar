---
id: "TYPE-SURVEY-CHANGES"
title: "Type System Survey: Changes"
type: "guide"
folders:
  - "[[features/type/requirements|requirements]]"
---

# Type System Survey: Changes

Whether type-system changes since 2026-08-01 corrected a rule or added a case. See the
[cause survey](type-system-survey.md).

# Did type-system changes generalize the design, or add special cases beside it?

Repository: `/home/mini/Documents/src/lua/lunar`, branch main, HEAD 8a10c1e9. Read-only audit, read 2026-11-01.
Judged from production diffs only. Commit titles, types, line counts and bug citations were not used. Bug reports were used only to learn a failing input.

## Population

`git log --since=2026-08-01 --format='%h' -- src/main/kotlin/net/internetisalie/lunar/lang/psi/types/ src/main/kotlin/net/internetisalie/lunar/luacats/`

- **55 commits**, 0 merges, earliest 458ddbf8 (2026-08-03). `luacats.flex`/`luacats.bnf` live under `luacats/`, so that path covers the LuaCATS lexer and grammar. `lua.flex`/`lua.bnf` are not LuaCATS and were out of scope.
- 2 excluded as pure ktlint: 21685809, 62d269c8. 21685809 was token-compared; its residue is `{ return x }` to `= x`, comment-hint moves and brace additions.
- 3 have no production logic in these paths (NO-LOGIC: KDoc or a duplicate comment): 6a9534a3, a4b7dff9, 7773984a.
- **50 classified.**

## Verdict rows

Class is by the main production change. "Later" lists population commits that changed the same logic again. A sibling marked (read) was confirmed in today's code by reading it; none was executed.

| # | sha | date | area | class | rule changed, or guard added | sibling not covered | later | conf |
|---|-----|------|------|-------|------------------------------|---------------------|-------|------|
| 1 | 458ddbf8 | 08-03 | engine | GENERAL | Every ValueNode threads the cycle guard through `writeWith`; the `is VariableElement` split is merged | none found | df332c34, db9ec063 | high |
| 2 | 307d6e54 | 08-04 | engine | NEW | Global resolution door (`resolveGlobal`, `getGlobalType`, `declareFileGlobals`). Side part is special: `resolveRead` merges only Table demands, every other kind stays first-wins | nested-block `X = 1` is not declared; top-level only | f08d7ca3, 426ac162, a40a3e19, afe6b0bb, 72cd1a99 | high |
| 3 | fa6c9b58 | 08-04 | engine | GENERAL | Class receiver is the class name plus declaring local names; `getModuleType` branches merged | `local a, b = ...` uses the first name only | 3869bc9c, b346f6c1, cb05af0f | medium-high |
| 4 | a80ae6ce | 08-04 | engine | GENERAL | `projectScope` to `allScope` at both lookups | none found | a40a3e19 | high |
| 5 | 6a9534a3 | 08-04 | engine | NO-LOGIC | KDoc only in population paths | n/a | n/a | high |
| 6 | 3869bc9c | 08-04 | engine | SPECIAL | Third materialize path `materializeUnhostedClass` with copied `@field` parsing; the host-decl rule stays beside it | bare `---@alias Name T`: `doResolveType` checks only the stub `LuaAliasIndex` (read) | 0a30ab22 merged the copy | medium-high |
| 7 | d23e0743 | 08-04 | LuaCATS | MIXED | General: CODE added to `description`. Special: separate `ldocParamTag` for `@param[opt=...]` | `returnDescription` (bnf:222) still lacks CODE (read) | 30dcef47 | high |
| 8 | f08d7ca3 | 08-06 | engine | MIXED | General: `any\|structural` keeps arms, gradual union. Special: `declarationTypedNodes` exemption from call demand plus `seedDeclaredMember` route | calls to free globals were left unchecked | 426ac162, 0d69c425, 8a10c1e9 | high |
| 9 | 7b3585c8 | 08-06 | LuaCATS | SPECIAL | `LuaCatsDeclaredType.isType`, a two-signal heuristic for `@param` at display sites only | `@return the value` takes the first word as type and does not consult it | none | medium |
| 10 | ca45fffa | 08-06 | engine | MIXED | General: "no information" is Undefined not nil at 6 sites; reaching-definition `certain`; convergence by pairs. Special: `informative` arm filter and "must not ENABLE" early return | nil forgiveness exists only in `checkCompatibility`; `isCompatible` stays strict, so `(string\|nil)[]` into `string[]\|number` fails on the use-union path (read) | 31d9c761, d6ce62e0, 8e7a48f2 | high |
| 11 | d17c28d3 | 08-06 | engine | GENERAL | Single sink: no error anchors file-wide; `reportIncompatible` re-anchors for all four compatibility sites | a range one character short of the file (leading comment) still buries infos | 31d9c761 | medium |
| 12 | 0a30ab22 | 08-07 | LuaCATS | GENERAL | One `@field` extractor replaces stub, AST and unhosted copies | none found | 0b7ac85e, 149a5144 | high |
| 13 | 0b7ac85e | 08-07 | LuaCATS | GENERAL | Parents are a list; `split(',')` removed | none found | 149a5144 | high |
| 14 | 70262c7b | 08-07 | LuaCATS | GENERAL | `@param`/`@return`/`@alias` go through a single reader | none found | dc5551d4 | high |
| 15 | 149a5144 | 08-07 | LuaCATS | REUSE | `fieldDisplayName`, `parentTypeNames` route callers to the shared reader | none found | none | high |
| 16 | 21685809 | 08-07 | n/a | EXCLUDED | Pure ktlint | n/a | n/a | high |
| 17 | 31d9c761 | 08-07 | engine | GENERAL | Incompatibility tier decided by `declaredDemand` at `reportIncompatible` | arity bypassed it (c4c958ce); `Missing required field` still calls `addError` at ERROR unconditionally, line 979 (read) | c4c958ce, d6ce62e0 | medium |
| 18 | a4b7dff9 | 08-07 | engine | NO-LOGIC | Duplicate comment removal | n/a | n/a | high |
| 19 | aa51396d | 08-07 | engine | GENERAL | Operand positions demand a capability (Trait); absorbs the inline `#` union | none found; bitwise is unconstrained, not wrong | ef48c172, 72cd1a99 | high |
| 20 | ef48c172 | 08-07 | engine | MIXED | General: `mergedTableOf` for both polarities, bail removed. New: metamethods arm | statement-form `setmetatable(t, mt)` augments only the call result, not `t` (read) | 4a614d25 | medium |
| 21 | 426ac162 | 08-07 | engine | SPECIAL | `demandDeclaredParams` beside the call demand, guarded by `positionsAreUnambiguous` and `carriesGraphNodes` | named in its own comment: `---@param w wxWindow`, `---@param cb fun(...)`, optional-omitted calls | 8e7a48f2, 8a10c1e9 | high |
| 22 | a40a3e19 | 08-07 | engine | MIXED | General: truthy/falsy `and`/`or`, project-first scope, function placeholder. Special: `enclosingStatementCats` lone RHS, boolean dropped unconditionally | `a, b = function...`; comment on `local t = { f = function(a) end }` attaches to the nested lambda | d6ce62e0 | medium-high |
| 23 | b6cc37d3 | 08-08 | engine | SPECIAL | Dumb guard and IndexNotReady catch in `resolveType`, a copy of `resolveGlobal`'s guard | `resolveModule` has no `isDumb` guard (read) | none | medium |
| 24 | 1be7cc0d | 08-11 | engine | NEW | Library provenance and type-source recorder, unwired | n/a | afe6b0bb | high |
| 25 | 7c7d234f | 08-11 | engine | GENERAL | Losing a mark must not grant a pin; `?: return` becomes a sentinel | `reportFile` (fixed by afe6b0bb) | afe6b0bb | medium |
| 26 | 7773984a | 08-11 | engine | NO-LOGIC | KDoc only | n/a | n/a | high |
| 27 | afe6b0bb | 08-11 | engine | NEW | Recorder wiring; co-located frame hoists 4 cache writes | n/a | bf715eb2 | high |
| 28 | 7c1f1862 | 08-11 | engine | NEW | Pinning | n/a | bf715eb2 | high |
| 29 | bf715eb2 | 08-11 | engine | GENERAL | `providerRan` flag removed; replay unconditional | none found | none | high |
| 30 | e7f0ba4d | 08-12 | engine | REUSE | Frame-with-answer idiom applied in `LuaTypeReference` | none found | none | medium |
| 31 | b346f6c1 | 08-12 | engine | REUSE | Key scan replaced by `LuaReceiverMemberIndex` | none found | cb05af0f | high |
| 32 | 4a614d25 | 08-12 | engine | REUSE | Fills `Table.metamethods` from `@class` members; a second filter, `ALL_METAMETHODS`, beside `startsWith("__")` | `---@operator add(V): V` has no type-engine consumer; only docs read `operatorTagList` (read) | 657aa19f | medium |
| 33 | 657aa19f | 08-12 | engine | GENERAL | `by lazy` removes the init cycle for every first-touch order | none found | none | medium |
| 34 | c4c958ce | 08-14 | engine | GENERAL | Arity is a contract only when declared (`declaredSignature`) | `---@param a` on `f(a, b)` makes `b` required, since `catsParams.isNotEmpty()` covers the whole signature (read); `---@return`-only signature is undeclared | 18618d8f, d6bee9eb, 2382558c | medium |
| 35 | d6ce62e0 | 08-20 | engine | GENERAL | ERROR needs unknown-free provenance; `Any` for empty RHS | computed only in `checkNodePairs`; the Array recursion drops `unknownProvenance` (line 671, read); Undefined and Any remain two spellings | c220541f | medium |
| 36 | a715abf1 | 08-20 | engine | SPECIAL | `if (original::class == to::class) original else to` | union original (`@class` local, `T\|nil`) is still replaced by the bare Table; its KDoc says so | none | high |
| 37 | 62d269c8 | 08-21 | n/a | EXCLUDED | Pure ktlint | n/a | n/a | high |
| 38 | dbd94bb4 | 08-21 | engine | REUSE | Stub names; `mayAssignMembers` through the index | `catsClassTags` still walks PSI | none | medium |
| 39 | df332c34 | 08-29 | engine | GENERAL | Revision-keyed root memo; bump lives inside `OrderedSet` | none found | db9ec063 | high |
| 40 | db9ec063 | 08-30 | engine | GENERAL | `write` computed once per visit | `useNode.read`/`declaredDemand` per pair; `unknownProvenance` reads `write` eagerly | 5038edc7 | medium-high |
| 41 | 0bccadae | 09-02 | engine | NEW | Member-declaration model; `putGraphMember` merges two branches | n/a | none | high |
| 42 | 52650fae | 09-03 | LuaCATS | NEW | `getReferences` override and type-declaration PSI | n/a | none | high |
| 43 | 1afdfdca | 09-03 | engine | NEW | O(1) probe `isSnapshotUnderConstruction` | n/a | none | high |
| 44 | 5038edc7 | 10-12 | engine | SPECIAL | Demand hub for colon calls only, plus `replicateHubAnchoredErrors` | dot member demand in `visitIndexExpr` is still `graph.variable(o)` per site (read) | none | medium-high |
| 45 | cb05af0f | 10-15 | engine | SPECIAL | `scan.onlyIn?.let { GlobalSearchScope.fileScope(it) } ?: scope` | unconfined scan: `Foo.m = 1` in a.lua with `function Foo:m` in b.lua, surviving entry by hash order | none | medium-high |
| 46 | 8e7a48f2 | 10-29 | engine | SPECIAL | Two copies, `demandTypeFor` and `demandTypeOf`, of "`?` means T\|nil", with `graphType !is Generic` in one only | method-call path (0d69c425 added a third copy) | 0d69c425, 8a10c1e9 | high |
| 47 | 30dcef47 | 10-29 | LuaCATS | MIXED | General: `optionalType` at nested positions, optional `argName`, parser marker fix. Special: per-state flex rules, Vim `\|tag\|` rule | `tupleType` (bnf:191) takes `type`, `typeParam ::= NAME` (bnf:210); `fun():(A,B)` models the first return only (read) | none | high |
| 48 | dc5551d4 | 10-29 | LuaCATS | GENERAL | `ParamDecl` list carries `isOptional` through stub and AST; map removed | `---@param x T\|nil` is not optional for arity; visitor line 1572 still reads `argSymbol` directly (read) | none | medium |
| 49 | 72cd1a99 | 10-29 | engine | SPECIAL | `if (tables.isNotEmpty()) tableExcludingTrait(demands)?.let { return it }` | declared `string` demand beside a member demand stays first-wins; a table with `__concat` plus members | none | high |
| 50 | 18618d8f | 10-29 | engine | GENERAL | `receiverDelta`/`alignedParams`; merges duplicated arity logic in `isCompatible` and check | none found | d6bee9eb, 2382558c, 0d69c425 | high |
| 51 | d6bee9eb | 10-29 | engine | GENERAL | Receiver counts only when a required parameter follows it: `if (required == 0) return 0` (changed from SPECIAL, see spot-check) | none found | 2382558c | medium |
| 52 | 0d69c425 | 10-29 | engine | SPECIAL | `declaredParamNodeFor` branch in `fromLuaType`; guard `param.isOptional && graphType != Undefined` | omitted-middle optional on a method is checked positionally while the global path has `positionsAreUnambiguous`; `demandTypeOf` lacks the Undefined guard, see "Unexecuted leads" | none | medium-high |
| 53 | 2382558c | 10-31 | engine | GENERAL | Bare `function` separated from zero-arg via `anySignature`/`Function.bare()`, at every construction site | none found; adds a flag and guards in `minArity`/`maxArity` | none | medium |
| 54 | c220541f | 11-01 | engine | SPECIAL | `.map { if (it == Undefined) Any else it }` at the Layer-1 union arm; `simplify` still drops Undefined | `demandTypeOf` builds the same union with no translation; visitor lines 383 and 713 hold their own copies (read) | none | medium |
| 55 | 8a10c1e9 | 11-01 | engine | SPECIAL | `detachedDemand` copies functions; `is LuaGraphType.Table -> null` | `---@param w wxWindow` still unchecked; returns dropped (read) | none | high |

## Inventory: accumulated today (read of current code, not history)

### `checkCompatibility` (LuaTypeGraph.kt:520)

| Item | Purpose |
|------|---------|
| Params `certain`, `declaredDemand`, `unknownProvenance` | Three flags threaded through every recursion, each deciding reporting only |
| Any and Undefined early returns (530-531) | Gradual types; same two checks open `isCompatible` |
| Trait short-circuit via `isCompatible` (541) | Satisfied capability; unsatisfied falls through for wording |
| `gradual` (548) | Any arm suppresses errors |
| `informative` (553) | Nil arm forgiven |
| "must not ENABLE" return (572) | Keeps the nil forgiveness from wiring new member edges |
| `if (gradual && !isCompatible...) continue` (581) | Gradual arms propagate only when they match |
| Table/Function/Union member filter, twice (580, 609) | Which arms propagate structure |
| Primitive into Table return (647) | Metatable methods; repeated at `isCompatible` 768 |
| Function and Table dispatch (658, 663) | Both drop all three flags; arity and missing-field take their own tier path |
| Array recursion (671) | Passes `certain` and `declaredDemand`, drops `unknownProvenance` |
| `Nil` with `certain` (683) | Only certain nil is a defect |

### `isCompatible` (LuaTypeGraph.kt:712)

| Item | Purpose |
|------|---------|
| Any and Undefined returns (717-718) | Same as above |
| Depth limit, breadth limit `shallowHeadMatch` (723, 730) | Safety; both approximate, neither memoized |
| Memo and cycle-assumes-true (741, 743) | Performance and termination |
| Union value: `Any in types \|\| all` (752) | Gradual rule repeated; **no nil forgiveness**, unlike `checkCompatibility` |
| Trait: `admits` or `implementsOperator` (757) | Capability check |
| Primitive into Table `true` (768) | Repeats line 647 |
| `value == Nil -> use == Nil` (771) | Strict nil; `certain` does not exist here |

### `demandDeclaredParams` (LuaTypesVisitor.kt:1049)

| Item | Purpose |
|------|---------|
| `positionsAreUnambiguous` | Exact count and no vararg; the only alignment rule, since `@overload` never reaches the engine |
| `takeUnless { it.isVararg }` | Unreachable given `positionsAreUnambiguous` excludes vararg |
| `demandTypeOf` | Third copy of "`?` means T\|nil" |
| `detachedDemand` null for Table | Exempts class-typed parameters |
| `detachedFunction` drops returns | `fun()` nil return must not error |
| `detachedParamNode` with `carriesGraphNodes` | Nested function or table param left untyped |
| `firstNode ... ?: return@forEachIndexed` | Node-less argument unchecked |
| Caller: `declarationTypedNodes` branch | Exists because shared seeds cannot take call-site edges |

### `fromLuaType` (LuaGraphType.kt:259)

| Item | Purpose |
|------|---------|
| VOID to Nil, UNKNOWN to Undefined, INTEGER to Number | Primitive mapping |
| FUNCTION to `Function.bare()`; `anySignature` | Bare function |
| `declaredSignature` selects `declaredParamNodeFor` or `memberNodeFor` | Declared demand and nil acceptance on parameters |
| `acceptsNil` guard `!= Undefined` | Works around `simplify` dropping Undefined |
| `implicitSelf` carried | Receiver shift |
| Union arm: Undefined to Any | Same workaround, opposite spelling |
| `LuaParameterizedType` to a named Table | Phase note; arguments discarded |
| `else -> Any` | Unknown kinds are gradual |
| `classTable` `ALL_METAMETHODS` | Second metamethod filter beside `startsWith("__")` |

### Do these look like one rule repeated?

Yes, in four cases.

1. **Unknown must not become stricter.** Spelled as Any/Undefined returns, `gradual`, `acceptsNil`, the union arm's translation, and visitor lines 383 and 713. Root: `simplify` drops Undefined but keeps Any, so each caller compensates.
2. **Nil arm means optional.** Forgiven in `checkCompatibility` only; built separately in `demandTypeFor`, `demandTypeOf`, `declaredParamNodeFor`; tested separately in two `isOptional` copies (LuaTypeGraph:985, LuaUnionDiagnostics:78). `isOptional` the parameter flag and `isOptional` the type test are unrelated.
3. **A declared contract outranks a guess.** `declaredDemand` for assignability, `declaredSignature` for arity, unconditional ERROR for missing fields, and `declarationTypedNodes` for callee routing. Four mechanisms.
4. **Shared nodes must not take call-site edges.** `declarationTypedNodes`, `carriesGraphNodes`, `detachedDemand`, member demand hubs. Same cause each time: structural checks wire edges into the type's own nodes.

`checkCompatibility` and `isCompatible` are also two implementations of one relation with deliberately different answers on nil, so a nested position gets whichever one the path reaches.

## Unexecuted leads

Found by reading; nothing was run. Not established as defects.

- `demandTypeOf` with `---@param x? Unresolved` on a free global: `Union.create(listOf(Undefined, Nil))` canonicalizes to `nil` per `LuaTypeAlgebra` line 51. The exact-count call `f("s")` would demand `nil`. `declaredParamNodeFor` guards this; `demandTypeOf` does not, unless `node.read` differs from Undefined there.
- `takeUnless { it.isVararg }` in `demandDeclaredParams` looks dead.

## Totals

55 rows, 50 classified. Every row in the verdict table is counted once.

| | GENERAL | REUSE | SPECIAL | NEW | MIXED | total |
|---|---|---|---|---|---|---|
| All | 20 | 5 | 12 | 7 | 6 | 50 |
| Aug | 16 | 5 | 5 | 4 | 5 | 35 |
| Sep | 0 | 0 | 0 | 3 | 0 | 3 |
| Oct | 4 | 0 | 5 | 0 | 1 | 10 |
| Nov | 0 | 0 | 2 | 0 | 0 | 2 |
| engine | 16 | 4 | 11 | 6 | 4 | 41 |
| LuaCATS | 4 | 1 | 1 | 1 | 2 | 9 |

## Areas where changes kept returning to the same logic

| Hot spot | Commits | Today |
|----------|---------|-------|
| Optional and nil | ca45fffa, 8e7a48f2, 0d69c425, dc5551d4, 30dcef47 | Three builders, two testers, one forgiving comparator |
| Declared-parameter checking | f08d7ca3, 426ac162, 8e7a48f2, 0d69c425, 8a10c1e9 | Two paths: `demandDeclaredParams` and `declaredParamNodeFor` |
| Arity | c4c958ce, 18618d8f, d6bee9eb, 2382558c | Converging: this run mostly improved the rule |
| Unknown vs Any | ca45fffa, f08d7ca3, d6ce62e0, c220541f | Two spellings, translation at each caller |
| `resolveRead` demand ranking | 307d6e54, aa51396d, ef48c172, 72cd1a99 | Table-first, Trait-excluding, otherwise first-wins |
| Class member materialization | fa6c9b58, 3869bc9c, 0a30ab22, b346f6c1, cb05af0f, dbd94bb4 | Readers converged; scoping grew a special case |
| Reporting flags on `checkCompatibility` | ca45fffa, 31d9c761, d6ce62e0 | Three threaded flags, dropped at function, table and partly array recursion |

## Spot-check

Diffs re-read: d6bee9eb, c220541f, b6cc37d3, a715abf1, 7b3585c8 (SPECIAL); d17c28d3, 2382558c, dc5551d4 (GENERAL). c4c958ce and 31d9c761 (GENERAL) were rechecked against their current code rather than the diff.

| sha | was | now | why |
|-----|-----|-----|-----|
| d6bee9eb | SPECIAL | **GENERAL** | Changed. The guard is the corrected rule (receiver counts only if a required parameter follows), it covers every such function, and no sibling turned up |
| c220541f | SPECIAL | SPECIAL | Kept, closest call. It fixes all declared unions, but `simplify` is unchanged and `demandTypeOf` lacks it. Confidence stays medium; MIXED is defensible |
| b6cc37d3 | SPECIAL | SPECIAL | Kept. `resolveModule` remains unguarded |
| a715abf1 | SPECIAL | SPECIAL | Kept. Its KDoc states unions are unchanged |
| 7b3585c8 | SPECIAL | SPECIAL | Kept. Sits at display sites and covers only `@param` |
| d17c28d3 | GENERAL | GENERAL | Kept. The sink is general; `isFileWide` is an exact-length test and silently drops errors, so confidence stays medium |
| 2382558c | GENERAL | GENERAL | Kept. It separates two meanings at every construction site, though it adds a fourth Function flag |
| dc5551d4 | GENERAL | GENERAL | Kept. The visitor's own `argSymbol` read remains a second reader |
| c4c958ce | GENERAL | GENERAL | Kept, with a confirmed partial-annotation sibling |
| 31d9c761 | GENERAL | GENERAL | Kept, confidence lowered to medium: `Missing required field` escapes the rule |

One change of mind in ten, on a guard-shaped commit. That suggests the boundary is soft for small guards, in both directions.

## Limits

- This is one reader's judgement of 50 diffs, not a measurement. The spot-check is the same reader checking itself, with no second rater and no inter-rater figure.
- Counts are unweighted: a 7-line probe and a 235-line feature each count once.
- "Sibling not covered" comes from reading. Nothing was executed, and "none found" means not found, not absent. Rows without (read) rest on the earlier reading of the diff.
- REUSE and NEW rows carry a thinner sibling search, because the class does not require one.
- Late commits have had less time to be revisited, so "later: none" on Oct-Nov rows is weak evidence. Likewise the Oct-Nov period is small (12 classified), and those commits were bug-driven against an engine already built, which may raise SPECIAL regardless of author habits.
- Area is by where the main logic sits; 3869bc9c could be counted either way. Commits touching only other paths (for example, the `analysis` layer that consumes these types) are outside the population.
- Scope is the two named directories. Generated parser output was not read.
