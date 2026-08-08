---
id: "COMP-09-DESIGN"
title: "Technical Design"
type: "design"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# Technical Design: COMP-09 — Member Enumeration

## 1. What the de-risking measured, and what it overturned

Run 2026-08-07 on gce-builder (`CompNineDrSpikeTest`). Two results contradict what BUG-429 and an
earlier revision of COMP-09-01 asserted. Both assertions were read off call shapes; neither survived
being run.

### 1.1 The critical path is NOT `materialize` (DR-02)

```
resolveGlobal   9 568 ms      <- 99.9 %
materialize        10 ms
getMembers          0 ms      (3 700 members)
```

BUG-429 stated that `LuaGraphType.materialize(global).getMembers()` "builds the complete type graph
for every member … before the loop yields its first element". **It does not.** Materialization and
enumeration together are 10 ms. The entire cost is `LuaTypeManager.resolveGlobal`, and inside it:

```
resolveGlobal → doResolveGlobal → typeOfGlobalIn(scope) → globalTypeIn(file)
                                                          → LuaTypesSnapshot.forFile(declaringFile)
```

`LuaTypesSnapshot.forFile` builds the **entire per-file type graph for the 242 KiB declaring
library file** in order to answer one question: what type does it give the global `wx`.

**This reframes the capability.** The missing thing is not only "enumerate members from an index" —
enumeration is already fast. It is *"answer a symbol's type without building its declaring file's
whole graph"*. The two `getAllKeys` scans are real (§1.3) but they are inside the 10 ms, and fixing
them would not have moved the headline number at all.

### 1.2 The cost is per-keystroke, not per-session (DR-02b/c)

| | ms |
| :-- | --: |
| cold | 800 |
| warm, no PSI change | **0** |
| after one keystroke **in the consumer file** | **608** |

(Constants-only fixture, hence 800 ms not 9 568 ms; the **ratio** is the finding — 76 % repaid.)

`typeCache` (`LuaTypeManagerImpl:34-44`) and the per-file snapshot (`LuaTypes:215-222`) both depend
on project-wide `PsiModificationTracker`, so an edit anywhere invalidates the *library's* snapshot.
Typing `wx.wxF` therefore pays the full graph build on each character. The NFR's per-keystroke clause
was right, but the mechanism is cache invalidation forcing recomputation — not, as it stated,
cancellation and restart.

### 1.3 The receiver key is dot-only (DR-06)

Raised in review against COMP-09-01's claim that swapping `collectMethodMembers`' scan for
`getElements(KEY, receiver)` is "a strict simplification". Measured:

```
keys under ColonHost : [ColonHost, ColonHost.staticDot, ColonHost:dotless, ColonHost:scale]
getElements(KEY, "ColonHost") -> [ColonHost.staticDot]        <- dot form only
getElements(KEY, "wx")        -> [wx.wxFileExists, wx.wxFrame]
```

`LuaFuncStubElementType:69-75` sinks a receiver key only when the name contains `'.'`, while
`memberNameOf:466` matches `receiver.` **and** `receiver:`. So the swap would silently drop every
colon-declared method — a correctness regression, not a simplification. `function C:m()` is the
dominant idiomatic form for class methods.

**And one step further than the review went:** the `ColonHost` receiver key exists *only because
`ColonHost.staticDot` happens to use the dot form*. A class whose members are all colon-declared has
**no receiver key at all**. The keying is incidental to member style, not a property of the class.

### 1.4 DR-01, done properly — and it makes DR-06's risk concrete

Redone across both entry points, with colon methods in the fixture:

| receiver | `resolveGlobal` | `resolveType` | golden members |
| :-- | :-- | :-- | :-- |
| `wx` (`---@class wx` + `wx = {}`) | `LuaTableLiteralType` | `LuaClassType` | `wxFileExists, wxFrame, wxID_ANY, wxID_OK` |
| `wxFrame` (`local` + `@class`) | **null** | `LuaClassType` | `GetTitle, Show, staticCount` |
| `AllColon` (all members colon-declared) | **null** | `LuaClassType` | `alpha, beta` |

Three things fall out.

**Today's scan DOES enumerate colon methods.** `wxFrame:Show`, `wxFrame:GetTitle`, `AllColon:alpha`
and `AllColon:beta` are all present, because `memberNameOf` matches `receiver:`. So DR-06's risk is
no longer hypothetical: **`AllColon` would go from 2 members to 0** under the proposed swap, and it
is now golden-file-verified rather than argued.

**A receiver can resolve through *both* entry points, with different types.** `wx` is
`LuaTableLiteralType` via `resolveGlobal` and `LuaClassType` via `resolveType`, because it carries
both `---@class wx` and `wx = {}`. So COMP-09-07's golden file must record **both** answers per
receiver; capturing one would let a change alter the other undetected. The first DR-01 attempt took
`viaGlobal ?: viaType` and would have done exactly that.

**`resolveGlobal` is not the only door.** COMP-09-01's site list is written against the
`resolveGlobal` path; the `resolveType` path reaches enumeration through `materializeClass` /
`materializeUnhostedClass` — which is where the two `getAllKeys` scans live. Two doors, one room.

## 1.5 §3.1 ANSWERED — the graph build is the cost, and names can bypass it

**(b) `LuaTypesSnapshot.forFile` carries essentially the whole path.**

```
LuaTypesSnapshot.forFile(library)          823 ms   (123 KiB root)
resolveGlobal, snapshot now warm            53 ms
```

So the answer to "can a global's type be answered without `forFile`?" is: **not today** — the type
*is* the graph. But that is the wrong question, which (c) shows.

**(c) Member NAMES can be had from the existing indexes without any graph — and the bottleneck is
not where I assumed.**

```
getElements(KEY,"wx")                 200 names   296 ms
LuaMemberFieldIndex full key scan   25 335 keys    44 ms      <-- the "expensive" scan
index-only name enumeration, total              340 ms
compare: resolveGlobal                        9 568 ms
```

⚠ **WITHDRAWN — see §2.** The figures once quoted here (`getAllKeys` 44 ms / `getElements` ~1.5 ms
each) compared two different index subsystems and printed a filtered match count rather than a key
total. Neither is evidence for anything. Candidate C — 43 ms for a 17 234-key scan *plus* 500
`getElements` — is the only figure from this run that holds, and it says the scan is not the
dominant term. COMP-09-09's bound is **entries traversed**, per `non-functional.md`.

**Which gives the design its answer.** 340 ms is 28× better than 9 568 ms and still over the 100 ms
budget, because it loads stubs. Names must come from the index *value*, not from the elements:

> A receiver-keyed index whose **value is the member name**, so enumeration is a key lookup returning
> strings with zero stub deserialisation and zero graph construction.

`LuaMemberFieldIndex` already has a value field, currently `""` (`:71`) — "The value is unused;
navigation re-resolves the field identifier on demand". That is the field to use, and it is why the
fix is an index *shape* change rather than a new subsystem.

Types then arrive separately, and only where needed: the checker keeps `forFile`, and completion
renders type text lazily per visible row or not at all.

## 1.6 §3.2 ANSWERED — the `@class` door has a different bottleneck, and one fix still covers both

```
resolveType("Big0")  cold                       949 ms   (500 members)
resolveType("Big1")  same file, warm            167 ms   (500 members)
A  forFile(big.lua), measured AFTER the above  1674 ms   <-- still COLD
B  catsClassTags-shaped walk, AST warm            22 ms
C  getAllKeys(17 234) + getElements per match     66 ms
D  first AST walk of an untouched 253 KiB file   352 ms   (~333 ms of it parse)
```

**The `@class` door never builds the type graph.** Candidate A was measured *after* two
`resolveType` calls and was still 1 674 ms — so `forFile` had not been invoked. This door is
therefore *not* the §1.1 defect, and §3.2's premise ("if the two have different bottlenecks, one fix
does not cover both") was right about the first half.

**Its cold cost is file-level one-time work, not enumeration.** 949 ms cold against 167 ms once the
file is warm. An equivalent untouched file costs 352 ms just to parse and walk (D), so the parse is
the largest identified component. ~430 ms of the cold cost remains unattributed — probably stub
construction and the first `getContainingFiles` touch. **Recorded as unattributed rather than
assigned**, since attribution by elimination is what produced the two errors in §1.1 and §1.5.

**Its marginal cost is ~167 ms per class** for 500 members — already over the 100 ms budget on its
own, before any file-level cost. About half is B + C (88 ms); the rest is `funcTypeFromStub` per
method plus `LuaImplicitFields`.

**But one fix still covers both doors**, because the index-value enumeration of §1.5 needs neither
PSI nor stubs:

| door | what it pays today | what an index value removes |
| :-- | :-- | :-- |
| `resolveGlobal` | `forFile` graph build, 823–1 674 ms | all of it (§1.5) |
| `resolveType` | AST parse 352 ms + walk 22 ms + scan/stub loads 66 ms + per-method stub reads | the parse, the walk and the stub loads |

So the answer to §3.2 is: **different bottleneck, same remedy.** That is a better outcome than the
question anticipated, and it is why the fix is one index change rather than two subsystems.

**Caveat that survives.** The `@class` door needs member *types* (`funcTypeFromStub`), not only names.
An index of names serves completion; the checker still needs types and therefore still pays. Which is
exactly §1.5's split, arrived at independently from the other door.

## 1.7 DECISION (REVISED after adversarial review) — no incremental yield, no lazy rendering

An earlier revision withdrew COMP-09-04 (incremental yield) and replaced it with COMP-09-04b (lazy
type rendering), on the claim that eager type text would cost 3 600 × 1.5 ms ≈ 5.4 s and that
`renderElement` is called per visible row. **Both halves were wrong.** Step 9 review caught them; both
are now measured.

**There is no per-element type cost.** Median of 5, 3 700 members — `memberNode.write` plus
`displayName()` plus the `isColon`/`is Function` filter that `LuaCompletionContributor:384` applies:

```
per-member type + displayName, 3700 members:  median 4 ms  (min 4, max 11)
```

`memberNode.write` comes from the already-materialized graph (`materialize` 6–10 ms, `getMembers`
0 ms) and `LuaGraphType.displayName()` (`LuaGraphType.kt:149-173`) is a pure structural `when` with no
PSI, index or stub access. The "1.5 ms per element" was `StubIndex.getElements` — stub
deserialisation, a different operation — and the same design's candidate C did 500 `getElements` in
43 ms (0.09 ms each), contradicting it in the same document.

**And `renderElement` is not per visible row.** `BaseCompletionLookupArranger.java:187` calls it for
every element added, `LookupImpl.java:410` again on `addItem`, and `LookupElement.java:122-131`'s
javadoc says it "is called before the item can be shown … should be relatively fast … If there are
heavy computations involved, consider … moving into `getExpensiveRenderer()`". The per-visible-row API
is `getExpensiveRenderer` / `LookupElementBuilder.withExpensiveRenderer`, which these artifacts never
named.

**So both requirements are withdrawn, and nothing replaces them.** Presentation was never on the
critical path; the whole cost is *reaching* the members (§1.1, §1.6). Deleting COMP-09-04b removes the
only requirement that touched the completion contributor or `LuaMemberLookup`, which makes this
feature purely an indexing change.

NFR-2c keeps "incremental" as a property not to break. If a future measurement shows enumeration
itself slow, COMP-09-04 returns — and its mechanism would then be `getExpensiveRenderer`, not
`renderElement`.

## 1.8 Measurement discipline — the figures in §1.2/§1.6 were single-shot

Step 9 re-ran all three harnesses. `resolveType` cold came back 383 ms against the 949 ms recorded
(−60 %), and **§1.2's harness printed the opposite verdict** — `afterEdit > cold/2` evaluated false,
so it reported "once per session" where the recorded run reported "per-keystroke". Single
`measureTimeMillis` calls with no warm-up: every ratio derived from a *pair* of them sat inside its own
noise floor.

Re-measured with medians of 5:

```
classDoor warm-file + cold-class, 500 members:  median 120 ms  (min 110, max 154)
classDoor cold-file + cold-class:               1174 ms  (single — unrepeatable by construction)
per-member type + displayName, 3700 members:    median   4 ms  (min 4, max 11)
```

120 ms is still over the 100 ms budget, so §1.6's conclusion survives on better numbers. **The
per-keystroke claim does not** — see §2's correction table. Any figure quoted from here on is a median
of ≥5 or is marked single-shot.

## 2. Consequences for the plan

Corrections forced by Step 9 review, beyond §1.7/§1.8:

| Claim | Status |
| :-- | :-- |
| "per-keystroke, 76 % repaid" (§1.2) | **UNRELIABLE.** The harness verdict flipped on re-run. The *mechanism* is sound by reading (`LuaTypes.kt:214-222` deps include project-wide `PsiModificationTracker.MODIFICATION_COUNT`) but it is no longer a measured claim. Needs a repeated-run harness before being cited |
| "`getAllKeys` is cheap — 44 ms / 25 335 keys" (§1.5) | **WITHDRAWN as stated.** That was `FileBasedIndex.getAllKeys(LuaMemberFieldIndex)`, a different subsystem from the `StubIndex.getAllKeys(LuaGlobalDeclarationIndex)` scans it was used to exonerate; and the printed number was the *filtered* match count, not the key total, polluted by cross-test index accumulation. Candidate C (43 ms for a 17 234-key scan + 500 `getElements`) is the figure that actually supports the conclusion |
| "bound stub loads, not key visits" (COMP-09-09) | **Reverted.** Candidate C shows 500 `getElements` inside 43 ms. The bound should be on *entries traversed*, as `non-functional.md` states |
| "one index change covers both doors" (§1.6) | **Not yet supported** — see §4's rewrite. `LuaGlobalDeclarationIndex` is a `StringStubIndexExtension` with **no value field**, so function-declaration names cannot come from "an index value" |

| Was | Now |
| :-- | :-- |
| "Replace the two `getAllKeys` scans" is the first increment | Those scans are inside a 10 ms region. Real, but not the headline. Demoted to a work-bound fix (COMP-09-09), not a latency one |
| Fix direction is index-backed member enumeration | Fix direction is **avoiding the declaring file's whole-graph build** to answer one symbol's type. Enumeration is already fast |
| Swapping the scan is a strict simplification | It is a correctness regression until `indexStub` also sinks `substringBefore(':')` — a **stub index format change**: version bump, full reindex |
| Per-keystroke concern justified by cancel/restart | Justified by measured cache invalidation: 76 % of the cost repaid on an unrelated edit |

## 3. Open questions this design cannot yet answer

Deliberately unresolved — each needs a measurement that has not been taken, and inventing answers
here is what produced §1.1 and §1.3.

1. ~~Can a global's type be answered without `forFile`?~~ **Answered, §1.5.** Not the type — but the
   member *names* can, from an index value, which is what completion needs.
2. ~~Is the `@class` path dominated by `forFile` or by the `getElements`-per-key loop?~~
   **Answered, §1.6.** Neither — it never calls `forFile`, and its cold cost is dominated by the
   declaring file's AST parse. Different bottleneck, same remedy.
3. **Does narrowing invalidation help more than indexing?** If the library snapshot survived edits to
   unrelated files the cost would be once per session per file rather than 76 % repaid per keystroke
   (§1.2). Possibly a smaller change than an index; not obviously safe.
4. ~~Where does incremental yield apply?~~ **Decided, §1.7 — nowhere.** COMP-09-04 withdrawn,
   COMP-09-04b (lazy type rendering) added. Reopens only if a value-carrying index lookup measures
   slow in Phase 1.

## 4. Architecture — rewritten from a measured prototype (DR-09)

Two earlier revisions of this section were written from reading the code and each failed a Step 9
review (§4.9, retained below as the record). This one describes a prototype that exists, was
registered, indexed a real library root, and was measured: `LuaReceiverMemberIndex` plus
`CompNineDr09Test` / `CompNineDr09bTest`. Every claim below cites a printed figure, and the two
places the prototype disagreed with today's engine are stated as findings rather than smoothed over.

### 4.0 What DR-09 measured

Fixture for timing: one 238 KiB `---@meta` library — 3 400 `---@type number` constants, 200 dot
functions, 300 classes × 8 colon methods — the BUG-429 shape.

| measurement | result |
| :-- | :-- |
| `membersOf("wx")`, 3 600 members, median of 5 | **2 ms** (samples 13/2/2/3/2 — the 13 is the cold first call) |
| `membersOf("wxG7")`, 8 members, median of 5 | **0 ms** |
| `resolveGlobal("wx")`, same fixture, same run, cold | **13 655 ms** |
| externalizer round-trip, 4 members incl. non-ASCII | **exact**, 55 bytes; empty list 4 bytes |
| membership vs today's golden, 4 receivers | 3 exact; 1 differs by an engine defect (§4.4a) |

The narrow/wide pair is the important one: 8 members costs 0 ms on the same index in the same
project as 3 600 members costing 2 ms. That is COMP-09-09's work bound demonstrated, not asserted —
cost tracks members of the receiver, not index size.

### 4.1 The constraint that shapes it: two consumers, different needs

`addMethodsOf` produces `LuaTypeMember(name, type, visibility, description, sourceElement)`
(`LuaType.kt:21-27`), and `sourceElement` is **load-bearing** — `materializeClass:256-262` records
that `LuaOverrideLineMarkerProvider` uses it as a gutter navigation target, with the warning that
"the parity harness compares names and types only, so it would not catch that". An index of names
cannot produce a `LuaTypeMember`.

So enumeration splits by consumer, and only one of them can be served without PSI:

| consumer | needs | can an index answer it? |
| :-- | :-- | :-- |
| **completion** (`crossFileGlobalMembers`) | member name; whether it is a function (the `isColon` filter, `LuaCompletionContributor.kt:384`); an icon (`LuaMemberLookup.kt:19-23`) | **yes** — name + kind, no PSI |
| **type materialization** (`addMethodsOf`) | full `LuaTypeMember` incl. `sourceElement` | **no** — still needs the `LuaFuncDecl` |

The win differs accordingly:

- completion skips the graph build entirely — measured 2 ms against 13 655 ms on one fixture;
- materialization keeps `getElements` per member but stops **scanning every key to find them**.

### 4.2 `net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex`

Exists, registered, measured. `FileBasedIndexExtension<String, List<LuaReceiverMember>>`, key =
receiver name, value = the members that receiver declares **in one file**.

```kotlin
data class LuaReceiverMember(val name: String, val kind: Kind, val separator: Separator) {
    enum class Kind { FUNCTION, FIELD }
    enum class Separator { DOT, COLON }
}
```

A `FileBasedIndex` value is per (key, file), so key `wx` carries one list per declaring file and
`membersOf` combines them. That is what `LuaMemberFieldIndex`'s `<String, String>` shape could not
do — the **value type** is the fix, not the key. Collection-valued precedent in this repo is
`LuaFileBindingsIndex` (`:34-77`), whose externalizer this one is modelled on.

**Wire format** (§4.9's "no wire format specified" defect, now closed and round-trip-proved):

```
writeInt(count)
per member: writeUTF(name), writeByte(kind.ordinal), writeByte(separator.ordinal)
```

Ordinals are safe **only** because `getVersion()` gates the format; reordering either enum is a
version bump. Measured: 55 bytes for 4 members including a non-ASCII name, 4 bytes for an empty
list, restored `EXACT`.

### 4.3 Indexer algorithm — three sources, as implemented

1. **`function R.m()` / `function R:m()`** — `node.findChildByType(FUNC_NAME)?.text`, the same source
   `LuaFuncStubElementType.createStub:24` uses, so the two agree by construction. `Kind.FUNCTION`,
   separator from the character found.
2. **`R.f = value`, at any depth** — a member assignment inside a function still declares a member.
   Deliberately unlike `LuaGlobalAssignmentIndex`, which is top-level-only because a nested *bare*
   assignment may target an enclosing local. Rejects `R[i]` (keyed suffix), `f().x` (call suffix)
   and `R.a.b` (more than one suffix), matching `LuaImplicitFields.singleFieldSuffixName`.
3. **`---@field` on a `---@class R` comment** — via `LuaCatsDeclarations.fieldMembers`. Sources 3
   and 4 of COMP-09-03 were previously undesigned; this closes the `@field` half.

`Kind` for source 2 (§4.9 D3): `R.f = function() end` is `FUNCTION` — the RHS is syntactically a
`LuaFuncDef`. `R.f = someOtherFn` cannot be classified without resolution and is recorded `FIELD`.
Measured on the DR-09b fixture: `assignedFn=FUNCTION/DOT`, `aliasedFn=FIELD/DOT`. **This is a real
residual gap, not a solved one** — it is bounded to indirectly-assigned functions, and COMP-09-08's
gate must include a case for it so the cost is visible rather than assumed away.

For source 3, `Kind` comes from the declared type text starting `fun(`. `---@field onClose fun(self:
wxFrame): nil` measured as `FUNCTION/DOT`.

### 4.4 Membership vs today — measured, and the two divergences

`CompNineDr09Test.testDr09bMembershipVersusGolden` compares `membersOf` against
`materialize(resolve(...)).getMembers().keys` per receiver:

| receiver | golden | indexed | verdict |
| :-- | :-- | :-- | :-- |
| `wx` (globals, constants, dot functions) | 4 | 4 | **exact** |
| `wxFrame` (colon methods + `@field`) | 5 | 5 | **exact** |
| `AllColon` (every member colon-declared) | 2 | 2 | **exact** — the shape DR-06 showed has *no* receiver key today |
| `Shapes` (nested + assigned + keyed) | 5 | 4 | differs by `deep` — **the engine is wrong here**, §4.4a |

`AllColon` matching is the load-bearing result: it is the case COMP-09-01's original "strict
simplification" would have silently emptied (§1.3), and the new index gets it right because the
receiver key is derived at index time rather than inherited from the dot-only stub sink.

### 4.4a The `deep` divergence is a defect in the engine, not the index

The single mismatch was traced (`CompNineDr09bTest`) and it is not the prototype's:

```
Shapes.nested = {} ; Shapes.nested.deep = 1 ; Shapes.nested.alsoDeep = "s"

resolveGlobal("Shapes")  members = [alsoDeep, deep, direct, nested]   <- grandchildren hoisted
                         members["nested"].localMembers = {}          <- and the real parent is empty
resolveType("Shapes")    members = [direct, nested]                   <- correct
```

So the two doors disagree on the same receiver in the same file, and the global door is wrong twice
over: every nested member is offered at the one path where it does not exist (`Shapes.deep`) and
withheld from the one where it does (`Shapes.nested.deep`). `isExact=true` on the empty `nested` node
compounds it — the table is asserted complete while being demonstrably not. The same shape with no
`---@class` at all behaves identically (`Plain.mid.leaf` → `[leaf, mid]`), so this is the global
door's rule, not an interaction with the annotation.

Neither `memberNameOf` nor `LuaImplicitFields.singleFieldSuffixName` produces this — both reject
`base.x.y` explicitly. The flattening is `LuaTypesVisitor`/`LuaTypesSnapshot`'s member-write walk
recording the last suffix against the root receiver instead of descending. That is why two rounds of
reading missed it: both readings were correct about the code they read.

**This changes what COMP-09-07 can mean.** "Behaviour-preserving" is not well-defined while two
goldens exist for one receiver and one of them is a bug. COMP-09 therefore preserves the **`@class`
door's** membership (`resolveType` → `materializeClass`), which the prototype matches exactly on all
four receivers, and **does not** reproduce the global door's flattening. That is a deliberate,
recorded behaviour change, not an accident, and it is the correct direction — but it must be stated
in the plan and gated, not discovered by a user.

### 4.5 Consumer 1 — completion, with the scope semantics measured (§4.9 D1/D2)

Both defects the second review raised were reproduced, and they are different problems:

**D2 — the superset is real.** `testDr09d2UnionVersusFirstDeclaringFile`, with a global `wx` in a
library and an unrelated **file-local** `wx` in another file:

```
DR-09d2 wx.lua        -> [real]
DR-09d2 unrelated.lua -> [privateToThisFile, alsoPrivate]
union  = [alsoPrivate, privateToThisFile, real]
golden = [real]
VERDICT: SUPERSET CONFIRMED, extra=[alsoPrivate, privateToThisFile]
```

Risk 1.1 predicted this in its own words and the previous §4.5 walked into it. A flat union over
`allScope` is **not** adoptable.

**D1 — scope precedence is distinguishable, and reproducing it fixes part of D2.**
`testDr09d1ScopePrecedence`, with `assert.fromLibrary` in a library and `assert.fromProject` in the
project:

```
membersOf(projectScope) = [fromProject]
membersOf(allScope)     = [fromLibrary, fromProject]
golden (today)          = [fromProject]
```

So today's BUG-427 precedence — `projectScope`, then `allScope` only if the first is empty — is
observable in *membership*, not just in which declaration a member resolves to.

**The rule, therefore:**

> `membersOf` mirrors `doResolveGlobal:150-151`: try `projectScope`; if it yields nothing, try
> `allScope`. **Within the chosen scope, take the members of the first declaring file only** —
> `typeOfGlobalIn:160-165`'s `.firstNotNullOfOrNull`, not a union.

First-file-only is what kills D2's superset: `unrelated.lua`'s local `wx` is a different file, so it
is never reached. This is a narrower `membersOf` than the prototype's current `processValues` union,
and the union variant remains in the prototype **only** as the D2 probe. Phase 1 must implement
first-file-only and re-run `testDr09b` — matching on all four receivers is the gate, not an
expectation.

The residual is honest and small: a *genuinely* additive second file (`function wx.extra()` in a
second library file, same global) contributes nothing today either, because
`typeOfGlobalIn` stops at the first. The index preserves that limitation rather than fixing it; if it
should be fixed, that is its own bug.

```kotlin
private fun crossFileGlobalMembers(receiver: PsiElement): List<LuaReceiverMember> {
    val nameRef = bareNameOf(receiver) ?: return emptyList()
    return LuaReceiverMemberIndex.membersOf(nameRef.text, nameRef.project)   // scope rule is internal
}
```

The emit loop keeps its shape; `memberType` is replaced by `kind`:

```kotlin
if (isColon && member.kind != Kind.FUNCTION) continue
result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(member), 100.0))
```

`LuaMemberLookup` gains an overload taking `LuaReceiverMember`: the icon comes from `kind`, and **no
type text is set** on this path — the index has no type. §1.7 measured type rendering at 4 ms for
3 700 members, so this is not a performance choice; it is that the type is genuinely absent. That is
a **visible behaviour change** and TC 3 must expect it rather than treat it as a regression.

### 4.6 Consumer 2 — materialization (`addMethodsOf`)

Signature unchanged; only how candidate keys are found changes. Note this door uses `allScope`
directly (BUG-399, `:441`) — it is not the global-resolution door and does not inherit §4.5's
precedence rule.

```kotlin
private fun addMethodsOf(scan: MethodScan, membersMap: MutableMap<String, LuaTypeMember>) {
    val scope = GlobalSearchScope.allScope(project)
    for (member in LuaReceiverMemberIndex.membersIn(scan.receiver, project, scope)) {
        if (member.kind != Kind.FUNCTION) continue
        if (membersMap.containsKey(member.name)) continue                       // first-wins, preserved
        val key = "${scan.receiver}${if (member.separator == COLON) ":" else "."}${member.name}"
        val decls = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, key, project, scope, LuaFuncDecl::class.java)
        val decl = decls.firstOrNull { scan.onlyIn == null || it.containingFile == scan.onlyIn } ?: continue
        membersMap[member.name] = LuaTypeMember(member.name, funcTypeFromStub(scan.className, decl), sourceElement = decl)
    }
}
```

`collectMethodMembers` and `materializeUnhostedClass:328` drop their `getAllKeys` argument. Four
behaviours preserved verbatim, each a test:

| rule | where it lives today | how it survives |
| :-- | :-- | :-- |
| `allScope`, not projectScope (BUG-399) | `:441` | same scope passed to `membersIn` |
| first-wins within a receiver | `:445` | unchanged `containsKey` guard |
| file confinement for a local-declared class (BUG-398) | `:456` | unchanged `scan.onlyIn` filter |
| nested qualifiers are not members | `memberNameOf:467` | derived at index time (§4.3) |

Two entry points, deliberately named apart: `membersOf` (completion — scope precedence, first file)
and `membersIn` (materialization — an explicit scope, all files). Collapsing them is how D1 and D2
happened.

### 4.7 COMP-09-05 — `@class`-declared metamethods

Change site: `LuaGraphType.fromLuaType`'s `is LuaClassType ->` branch, which today passes no
`metamethods`. When converting, any member whose name is in `LuaGraphType.Trait`'s metamethod sets
(`LuaGraphType.kt:118-126`) contributes to `metamethods` **as well as** remaining a member.

"As well as" is deliberate: `LuaGraphType.kt:50-52` records that metamethods are held separately
because adding them to `localMembers` "would make `t.__add` complete on the instance, which is not
what Lua exposes". A `@class`-declared `__add` is already in `localMembers` today — so keeping it
there is behaviour-preserving and only the operator check gains it. **Observe this before changing
it** (checklist item): if `t.__add` does *not* complete today, that comment describes an intent the
code does not implement, and this becomes a decision rather than a preservation.

### 4.8 Registration and reindex boundaries

```xml
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex"/>
```

Already added beside the five existing entries (`plugin.xml:668`) for DR-09; nothing consumes it yet.
No new service, no EP.

| index | today | after |
| :-- | :-- | :-- |
| `LuaReceiverMemberIndex` | 1 (DR-09) | 1 |
| `LuaMemberFieldIndex` | 1 | unchanged — not modified |
| `LuaGlobalAssignmentIndex` | 2 | unchanged |
| stub format (`LuaFileElementType.getStubVersion`) | 4 | **unchanged** |

The stub row is the payoff, and Step 9 confirmed the argument: the sink stores the whole `FUNC_NAME`,
so `C:m` is itself a key. Deriving receivers in a *new* `FileBasedIndex` leaves the dot-only stub sink
alone — DR-06's asymmetry is sidestepped rather than fixed in place, at no stub-format cost.

Every version bump forces a full reindex on first run, and **no benchmark may cross one**.

### 4.9 Platform obligations

Previously absent entirely, which `non-functional.md:26-30` and the engineering contract make
binding:

- **Read action.** `membersOf`/`membersIn` touch `FileBasedIndex` and must be called under one.
  Completion contributors already run under a read action; `addMethodsOf` does too. No new
  requirement, but stated so a future caller does not assume otherwise.
- **`ProgressManager.checkCanceled()`** inside the value-processing loop. The 3 600-member call is
  2 ms, so this is not about the loop being long — it is that a `processValues` callback that never
  yields cannot be cancelled if a pathological receiver appears.
- **`DumbService`.** `FileBasedIndex` queries are unavailable during indexing. `crossFileGlobalMembers`
  must degrade to empty rather than throw. Today's path resolves through the type engine, which has
  its own dumb-mode behaviour — Phase 1 must establish what that is and match it, and this is
  **untested by DR-09**.

### 4.10 The record: what the two Step 9 reviews found

Kept because the pattern is the point. §4 was written twice from reading and failed twice; every
figure in §4.0–§4.5 above comes from a run.

- **D1** — flat `allScope` reverted BUG-427's precedence. **Confirmed** and fixed (§4.5).
- **D2** — `membersOf` union was a membership superset. **Confirmed by measurement** and fixed by
  first-file-only (§4.5).
- **D3** — `Kind` syntactic where the filter is semantic. **Partly real**: direct
  `= function() end` is classifiable, indirect assignment is not (§4.3), and the residue is now a
  bounded, gated gap.
- No `membersOf` algorithm, no wire format, `@field` undesigned, no read-action/cancel/dumb-mode
  statement — all closed above.
- Confirmed correct by both reviews: the no-stub-bump argument, the separator round-trip, the four
  preserved materialization behaviours, and the collection-valued index's viability.

### 4.11 Still not designed

- **COMP-09-08 (latency gate)** and **COMP-09-09 (work bound)**. DR-09 supplies the *evidence* a
  bound is achievable (0 ms narrow vs 2 ms wide) but neither an assertion mechanism nor a
  first-element harness. DR-02a remains open and still blocks NFR-1.
- **Narrowing cache invalidation** (§3.3 / DR-07) — independent, possibly a smaller win.
- **Removing the four caches** — re-measure each; a follow-up.
- **`LuaImplicitFields` / `LuaTypesVisitor:1349` / `catsClassTags`' file walks**. Phase 4 measures
  whether §4.5/§4.6 already moved them below budget.

## 5. Requirement coverage

Rewritten after DR-09. Every "covered" row now cites a printed figure from a prototype run rather
than a call-shape argument; the rows that are still not designed say so.

| Requirement | Covered by | Evidence |
| :-- | :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | §4.2 — implemented and registered | §4.0 — `membersOf("wx")` 2 ms median for 3 600 members vs `resolveGlobal` 13 655 ms, same fixture, same run |
| COMP-09-02 no full-file walk | §4.3 | §1.6 — the walk and the parse are the `@class` door's cost; §4.0 — the index path pays neither |
| COMP-09-03 all sources, dot **and** colon | §4.3 covers **3 of 4** — funcs, assignments, `@field`; metamethods are COMP-09-05 | §4.4 — `AllColon` (every member colon-declared) enumerates 2 of 2, the case a naive swap emptied |
| ~~COMP-09-04~~ incremental yield | **withdrawn** | §1.7 — no tail to stream |
| ~~COMP-09-04b~~ lazy type rendering | **withdrawn** | §1.7 — measured 4 ms for 3 700 members; presentation was never the cost, and `renderElement` is not per-visible-row |
| COMP-09-05 `@class` metamethods | §4.7 — change site named, behaviour to observe first | COMP-04-DR-01 / BUG-426; unmeasured, and the checklist observes `t.__add` before the change |
| COMP-09-06 no new type source | §4.1 | the split — names for completion, `forFile` retained for the checker — keeps the checker's inputs unchanged |
| COMP-09-07 behaviour-preserving | §4.4 measured; §4.4a **redefines the bar** | 3 of 4 receivers exact; on the 4th the two doors disagree and the global one is wrong (§4.4a). COMP-09 preserves the `@class` door and deliberately does not reproduce the global door's flattening |
| COMP-09-08 latency enforced | **NOT DESIGNED** — no test class, no first-element harness (DR-02a is `todo`, "blocks NFR-1") | §1.6 — even the warm-file `@class` path is 167 ms, over budget with no library involved |
| COMP-09-09 work bound | **evidence yes, mechanism no** | §4.0 — 8 members 0 ms vs 3 600 members 2 ms on one index shows the bound holds; no assertion mechanism is specified |
