---
id: "TYPE-14-RISKS"
title: "14: Risks and gaps"
type: "risk"
parent_id: "TYPE-14"
folders:
  - "[[features/type/requirements|requirements]]"
---

# TYPE-14 Risks and Gaps

## Risk 1.1 — A lazy node that mints during a resolution walk moves the BUG-473 meter [Medium]

Design §5.6's `Table` arm materializes an instantiated class (`Box<string>`) through
`fromLuaType`, which mints member nodes and bumps `LuaTypeGraph.revision`. If that happens inside
a `LazyValueElement.compute`, it runs mid-walk: every root memo computed under the earlier
revision is invalidated the moment it is stored, and the next read of the same variable walks
again. That is the exact shape BUG-473 measured at ×5.9 per doubling.

- **Mitigation**: the lazy compute memoizes materializations per distinct instantiated name in a
  map captured by the lambda (§5.6), so the mint happens once per lazy node, not once per read.
  `LuaTypeGraphRootResolutionBudgetTest` is the gate at Phase 2 and Phase 5.
- **What would make it High**: a class whose instantiated name changes between reads (a binding
  that is itself a union that grows as the walk proceeds). The memo is keyed on the name, so a
  growing union mints once per distinct name it passes through — bounded by the number of arms,
  which `MAX_UNION_BREADTH` already caps.

## Risk 1.2 — The declared-path copy must not share a node with the seed [Medium]

`declaredFunctionOf` returns the memoized seed's own `Function`, whose parameter and return nodes
every call in the file reads. BUG-425 established that writing a call site's argument into one of
those nodes rewrites the signature for everybody (`ExpectedCallbackResolverTest.testTableSortComparatorSlotResolves`
went red when it was tried). Design §5.8 copies every position into a fresh node.

- **Mitigation**: requirements case 11 asserts the seed's parameter write is unchanged after three
  calls with three different argument types; the plan's Phase 2 mutation wires the demand against
  the seed and must go red.

## Risk 1.3 — The safety net hides a real defect [Low]

TYPE-14-02 makes any `Generic` compatible with anything. A binding that silently fails (a path
that does not project, an arity mismatch) now produces `Undefined` and no report, where today it
produces a false error. That is the intended trade — BUG-419's posture, "a guess that conflicts
with a guess is not a diagnostic" — but it also means an implementation bug in §5.4 shows up as a
missing hint rather than a red test, unless a test asserts the type.

- **Mitigation**: every requirements case asserts the inferred type, not merely the absence of
  errors. The plan's Phase 1 regression file asserts absence; Phases 2–5 assert presence.

## Risk 1.4 — The grammar change reaches REFACT-08 [Medium → measured Low]

REFACT-08's contracts depend on the PSI shape of a bare type argument: the parent-chain
predicate `isDeclarationSlotHolder`, `shadowedTypeParameterNames` built on it, and the reference
contributor's `LuaCatsTypeParam` registration. DR-02 run a measured the obvious rule shape breaking
the predicate (and, through it, the rename processor) while the reference contributor's own test
stayed green — the node kind survived, the parent chain did not; run b measured the `private` form
breaking nothing. See "DR-02 result".

## Risk 1.5 — Stub version bump forces a full re-index [Low]

The version bumps §9 lists (`LuaFileElementType.getStubVersion`, `LuaCatsTypeNameIndex.getVersion`, `LuaReceiverMemberIndex.getVersion`) rebuild every Lua stub and those indexes on first open.
Unavoidable when a serialized field or a key changes; one-time.

## Design Gaps

### Gap 2.1 — `Arg(i)` projection reads a type back out of a nominal name — CLOSED

A `Table` carries its type arguments only in `className` (`"Box<string>"`); there is no field.
§5.4 step 5 re-parses the argument text. Closed by construction: the text is what the grammar
produced and what `resolveParameterized` accepts, and DR-01 H confirms `TypeParser` round-trips
the nested shapes. Adding an `arguments` field to `Table` was considered and rejected — it would
change `data class` equality for every consumer to serve one projection step.

### Gap 2.2 — A callback slot bound only by an un-annotated lambda — OPEN, out of scope

`---@param fn fun(a: T)` with **no** other source of `T` and a lambda `function(a) … end`: there
is nothing to bind `T` from. LuaLS's own #1883 is open on this half. TYPE-14-16 covers the half
with a sibling source. Recorded here so it is not mistaken for a §5.12 defect.

### Gap 2.3 — Instantiated names that do not resolve — KNOWN LIMITATION

`Box<T>` returned where `T` bound to an anonymous table displays as `Box<{ ... }>` and has no
members (§8). LuaLS prints an expanded form here (#1856, open). Not a regression: today the
same call yields `Box<T>` with no members.

## Adjacent defects and gaps found by DR-01, not owned here

- **Table constructor versus array type.** `---@type string[]` `local xs = {}` reports
  `{ ... } is not assignable to string[]` on the unmodified engine (DR-01 B and I, the `'local xs
  = {}'` lines). Every array-typed local initialised with a constructor is a false error today.
  Not a generics defect; filed as [[BUG-481]] with the probe line as its reproduction.
- **Structured `@field` types resolve to nothing.** `funcTypeFromStub` and `materializeClass` wrap
  every type string in `LuaTypeReference`, and `resolveType` resolves *names* only — so
  `---@field items string[]` on a class is `UNKNOWN` today (design §2, "`LuaTypeReference` can
  name any type string"). TYPE-14 routes only generic-bearing strings through the parser (§4.8);
  routing every structured string is a separate, measurable change with its own cost profile.
- **Go to Class / rename for a parameterized head.** `classDeclarationLeaf` is null for
  `---@class Box<T>` by REFACT-08-17's design; re-keying the engine on `Box` (TYPE-14-08) does not
  give navigation a leaf. Requirements "Out of Scope".

## Future work — the LuaLS open list this feature deliberately leaves

| Survey item | Why deferred |
| :-- | :-- |
| Q2 scoped `---@type T` in a body | needs a per-function type-parameter scope for `@type`; no upstream design |
| Q3 `fun<K>(...)` method-level parameters on class fields | annotation-syntax extension; LuaLS has none |
| Q4 vararg generics (`xpcall`, `unpack`, `assert`) | parser and engine extension for `...` positions; upstream open |
| Q5 `Object.\`T\`` prefixed capture | rare; the plain capture (TYPE-14-13) covers the documented use |
| Q7 `` `T`[] `` keyed tables, Q9 `T[key]` | no upstream semantics |
| #1856 compact display of a recursive instantiation | display-only; Gap 2.3 |

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
|----|--------|----------|--------|
| TYPE-14-00-DR-01 | Probe every generic shape on the unmodified engine and record inferred types, errors, index keys and parse shapes | the "What the engine does today" table; every behaviour claim in design §2 | **done** — see "DR-01 result" |
| TYPE-14-00-DR-02 | Apply design §6's grammar rule, regenerate headlessly, run the REFACT-08 suites and the nested-argument probe | Risk 1.4; design §6's claim that the bare-name shape is unchanged | **done** — see "DR-02 result" |
| TYPE-14-00-DR-03 | On the unmodified engine, measure what `LuaCatsTypeNavigation` finds for a parameterized head under its raw and head names, and what `resolveType` versus `TypeParser.parse` return for an undeclared simple name | design §4.9/§5.10 (the re-matching consumers move with the key); design §4.5 (the null-skip must be preserved) | **done** — see "DR-03 result" |

### DR-01 result

`Type14Dr01GenericsReachProbeTest` (throwaway, builder, `ddaac805`; one test method, fixtures
accumulate in one light project, so the `Box` of fixture D is visible to E and H — that is why
`resolveType(Box)` in E returns `[echo]` and the index shows two keys). Verbatim:

```
A control same-file identity: s graph=string lua=string | n graph=number lua=number | errors none
B nested T[] param: a graph=undefined lua=unknown
B nested T[] return: w graph=undefined[] | { ... } lua=unknown[] | {  }
B nested T|nil param: m graph=T | nil | string lua=T | nil | string
B errors 'local function first(xs) retur': { ... } | string[] is not assignable to undefined[] ; 'local function first(xs) retur': { ... } is not assignable to undefined[] ; 'local function wrap(v) return ': { ... } is not assignable to undefined[] ; 'local function must(v) return ': nil | string is not assignable to T ; 'local function must(v) return ': string is not assignable to T ; 'local function must(v) return ': string is not assignable to nil ; 'local xs = {}': { ... } is not assignable to string[] ; 'xs': { ... } is not assignable to undefined[] ; 'xs': { ... } is not assignable to undefined[] ; 'xs': { ... } | string[] is not assignable to undefined[] ; 'v': string is not assignable to nil
C cross-file free global generic: s graph=T lua=T | n graph=T lua=T | errors '"x"': string is not assignable to T ; '1': number is not assignable to T
C resolveGlobal(ident) = LuaFunctionType fun(v: T): T
D generic method on typed receiver: r graph=undefined lua=unknown | on class table: q graph=T | number lua=T | number | errors ':echo': fun(v) | fun(v) is not assignable to fun(p) ; 'Box:echo(1)': fun(v) | fun(v) is not assignable to fun(p) ; 'v': number is not assignable to T ; 'function Box:echo(v) return v ': number is not assignable to T ; 'function Box:echo(v) return v ': T | number is not assignable to T
E class generic: b graph=Box<string> lua=Box<string> | v graph=undefined lua=unknown | b.value <no b.value> | errors none
E resolveType(Box)=LuaClassType(Box) members=[echo] | resolveType(Box<T>)=LuaClassType(Box<T>) members=[value] | resolveType(Box<string>)=null
E LuaClassNameIndex keys containing Box = [Box<T>, Box]
E TypeParser(Box<string>) = LuaParameterizedType(Box<string>) members=[echo]
F constraint in body: m graph=undefined lua=unknown | p.k <no p.k>
F constraint at call: bad graph=number lua=number | good graph=Base lua=Base | errors '{}': Missing required field 'k' ; '.k': T is not assignable to { ... }
G backtick capture: w graph=T | { ... } lua=T | {  } | errors '{}': Missing required field 'w' ; 'local function new(cls) return': { ... } is not assignable to T
G TypeParser(`T`) = LuaPrimitiveType(unknown) members=[]
H grammar 'table<string, Widget|nil>': errorElements=0 [] | TypeParser=LuaPrimitiveType(table) members=[]
H grammar 'Box<string[]>': errorElements=0 [] | TypeParser=LuaTypeReference(Box) members=[echo]
H grammar 'table<string, table<string, number>>': errorElements=0 [] | TypeParser=LuaPrimitiveType(table) members=[]
H grammar 'fun(a: T): T': errorElements=0 [] | TypeParser=LuaFunctionType(fun(a: T): T) members=[]
H grammar 'table<string, number>': errorElements=0 [] | TypeParser=LuaParameterizedType(table<string, number>) members=[]
H grammar 'Box<T>': errorElements=0 [] | TypeParser=LuaParameterizedType(Box<T>) members=[echo]
I filter shape: r graph=undefined[] | { ... } | string[] lua=unknown[] | {  } | string[] | lambda a graph=undefined | errors 'local function filter(fn, t) r': undefined[] | { ... } | string[] is not assignable to undefined[] ; 'local function filter(fn, t) r': { ... } is not assignable to undefined[] ; 'local function filter(fn, t) r': { ... } | string[] is not assignable to undefined[] ; 'local xs = {}': { ... } is not assignable to string[] ; 'xs': { ... } is not assignable to undefined[] ; 't': { ... } is not assignable to undefined[] ; 't': { ... } | string[] is not assignable to undefined[]
J parameterized parent: Kid=LuaClassType(Kid) members=[own] | PBase=LuaClassType(PBase) members=[inherited]
```

Two findings changed the design rather than confirming it:

1. **Nested and cross-file generics are not silent — they report.** B, C, D, F, G and I all carry
   `… is not assignable to T` or `… to undefined[]`. That moved TYPE-14-02 from a footnote to the
   first phase, on its own.
2. **A nested type argument parses as prose, without an error element.** H shows
   `errorElements=0` and `TypeParser = table` for `table<string, Widget|nil>` — the `<…>` tail is
   a `description`. That is why TYPE-14-07 is a Must and not a Could: the engine cannot see the
   argument at all today, and the author gets no signal.

### DR-02 result

Two executions of the §6 grammar edit, both regenerated headlessly with `generate.sh` at
`ddaac805` and run on the builder. Run a's selection: `Type14Dr01GenericsReachProbeTest` (fixture
H, extended to report each argument's PSI kind), `LuaCatsTypeDeclarationsTest`,
`LuaCatsTypeReferenceTest`, `LuaCatsParserTest`, `LuaCatsTypeRenameTest` and
`LuaCatsStubAstParityTest`. Run b added `LuaCatsLazyCommentTest` and
`LuaCatsDocumentationRendererSectionsTest` to the same selection, which is why its test count is
higher than run a's.

**Run a — `typeArgument` as a public rule.** The generator produced `LuaCatsTypeArgument` with
`getType()`/`getTypeParam()` and changed `LuaCatsParameterizedName.getTypeParamList()` to
`getTypeArgumentList()`; the only compile break was `TypeParser.kt:86`. Parsing was correct:

```
H grammar 'table<string, Widget|nil>': errorElements=0 args=[PARAM(string), TYPE(Widget|nil)] description=null | TypeParser=LuaParameterizedType(table<string, Widget | nil>) members=[]
H grammar 'Box<string[]>': errorElements=0 args=[TYPE(string[])] description=null | TypeParser=LuaParameterizedType(Box<string[]>) members=[echo]
H grammar 'table<string, table<string, number>>': errorElements=0 args=[PARAM(string), TYPE(table<string, number>)] description=null | TypeParser=LuaParameterizedType(table<string, table<string, number>>) members=[]
H grammar 'table<string, number>': errorElements=0 args=[PARAM(string), PARAM(number)] description=null | TypeParser=LuaParameterizedType(table<string, number>) members=[]
H grammar 'Box<T>': errorElements=0 args=[PARAM(T)] description=null | TypeParser=LuaParameterizedType(Box<T>) members=[echo]
```

— but the builder reported `76 tests completed, 4 failed`, one being the probe itself (which
fails by design to print) and the others REFACT-08's:

```
LuaCatsTypeDeclarationsTest > testIsDeclarationSlotHolderShapes FAILED           (LuaCatsTypeDeclarationsTest.kt:200)
LuaCatsTypeDeclarationsTest > testShadowedTypeParameterNamesForAParameterizedClassTag FAILED  (:275)
LuaCatsTypeRenameTest > testParameterizedClassOwnTypeParameterIsNotAUse FAILED   (LuaCatsTypeRenameTest.kt:72)
```

Mechanism: the public rule inserts a `LuaCatsTypeArgument` node between `LuaCatsTypeParam` and
`LuaCatsParameterizedName`, and `isDeclarationSlotHolder` tests `holder.parent is
LuaCatsParameterizedName` ([LuaCatsTypeDeclarations.kt:107](../../../../src/main/kotlin/net/internetisalie/lunar/luacats/lang/psi/LuaCatsTypeDeclarations.kt)).
The predicate keeps the *node kind*; it does not keep the *parent chain*.

**Run b — `private typeArgument`.** Grammar-Kit inlines a private rule, so no node is generated
and the parent chain is unchanged. Selection: run a's suites plus `LuaCatsLazyCommentTest` and
`LuaCatsDocumentationRendererSectionsTest`.

Regenerated at `ddaac805` — the generator emits `getTypeParamList()` **unchanged** and adds
`getTypeList(): List<LuaCatsType>` on `LuaCatsParameterizedName`; no `LuaCatsTypeArgument` class
exists. The only Kotlin consumer that needed a change was `TypeParser.kt:86` (it now walks the
node's children in document order, since a name argument and a nested argument land in different
lists). Both test files ran **unmodified**. Builder result over that selection:

```
94 tests completed, 1 failed        ← the probe, which fails by design to print its report
```

Probe H, byte-identical to run a's parse shapes:

```
H grammar 'table<string, Widget|nil>': errorElements=0 args=[PARAM(string), TYPE(Widget|nil)] description=null | TypeParser=LuaParameterizedType(table<string, Widget | nil>) members=[]
H grammar 'Box<string[]>': errorElements=0 args=[TYPE(string[])] description=null | TypeParser=LuaParameterizedType(Box<string[]>) members=[echo]
H grammar 'table<string, table<string, number>>': errorElements=0 args=[PARAM(string), TYPE(table<string, number>)] description=null | TypeParser=LuaParameterizedType(table<string, table<string, number>>) members=[]
H grammar 'table<string, number>': errorElements=0 args=[PARAM(string), PARAM(number)] description=null | TypeParser=LuaParameterizedType(table<string, number>) members=[]
H grammar 'Box<T>': errorElements=0 args=[PARAM(T)] description=null | TypeParser=LuaParameterizedType(Box<T>) members=[echo]
```

Design §6 specifies the private form. Run a is kept above because its failure is the mutation
that requirements case 14 names: dropping `private` is a one-word edit that turns the tests listed
under run a red, which is a stronger and more reachable mutation than dropping the predicate.

### DR-03 result

`Type14Dr03IndexConsumersProbeTest` (throwaway, builder, unmodified engine at `ddaac805`; one file
declaring `---@class Box<T>` and `---@class Plain`). Verbatim:

```
DR-03 processNames keys containing Box/Plain = [LunarFixtureBox, Box, Box<T>, Plain]
DR-03 processElements("Box") = 0 item(s) []
DR-03 processElements("Box<T>") = 1 item(s) [Box<T>]
DR-03 processElements("Plain") = 1 item(s) [Plain]
DR-03 undeclared name: resolveType(Widget)=null | TypeParser.parse(Widget)=LuaTypeReference(Widget) -> fromLuaType=undefined
DR-03 declared name control: resolveType(Plain)=LuaClassType(Plain)
```

(The keys `LunarFixtureBox` and a bare `Box` come from other files in the light test project's library roots; `processElements("Box")` finds no class tag whose text is `Box`, which is the measurement.)

Read against the code: `LuaCatsTypeNameIndex` keys the parameterized class on its raw text, and
`LuaCatsTypeNavigation.processElements` re-matches that raw text — so the class is found under
`Box<T>` and not under `Box` today. Re-keying the index to `Box` without changing the matcher would
find it under neither. The undeclared-name line is design §4.5's reason to keep the token fast
path: `resolveType` answers null (the tag is skipped), the parser answers a reference that resolves
to `UNKNOWN` (the tag would be injected).

## Test Case Gaps

- A generic class whose method returns `self` (`---@return self`) on an instantiation: `funcTypeFromStub`
  substitutes the receiver class *name*; the instantiated class must substitute
  `Box<string>`. Add to `Type14GenericClassTest` when Phase 5 lands (design §5.9's `substitute`
  handles the `LuaTypeReference(className)` case through the token fast path, since the name
  contains no parameter token — so `self` on an instantiation resolves to the **raw** class today.
  Worth a case; not a Must).
- Two `@generic` tags on one comment (design §8) — a parse-shape case for Phase 1.

## See Also
- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
