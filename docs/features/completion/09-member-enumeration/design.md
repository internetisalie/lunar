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

**`getAllKeys` over 25 335 keys costs 44 ms.** The scan is *cheap*. What costs is
`getElements` — 296 ms for 200 elements, ~1.5 ms each, because each one deserialises a stub. And
`collectMethodMembers` calls `getElements` **per matching key**, so for a 3 600-member receiver that
is seconds. My earlier framing — "`getAllKeys` is the defect" — was wrong in the same way as §1.1:
inferred from the call shape, refuted by running it. COMP-09-09's work bound is still right, but the
work it must bound is **stub loads**, not key visits.

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

## 4. Architecture

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

The win differs accordingly, and both are measured:

- completion skips `forFile` entirely (§1.5: 823–1 674 ms) and the `@class` door's AST parse (§1.6:
  ~298–352 ms);
- materialization keeps `getElements` per member but stops **scanning every key to find them** —
  O(members of R) instead of O(all keys), which is COMP-09-09's bound.

### 4.2 `net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex` (new)

```kotlin
/** One member a receiver declares in one file. `kind` exists for the isColon filter and the icon. */
data class LuaReceiverMember(val name: String, val kind: Kind, val separator: Separator) {
    enum class Kind { FUNCTION, FIELD }
    enum class Separator { DOT, COLON }
}

class LuaReceiverMemberIndex : FileBasedIndexExtension<String, List<LuaReceiverMember>>() {
    override fun getName(): ID<String, List<LuaReceiverMember>> = KEY
    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<List<LuaReceiverMember>>
    override fun getIndexer(): DataIndexer<String, List<LuaReceiverMember>, FileContent>
    override fun getVersion(): Int = 1
    override fun dependsOnFileContent(): Boolean = true
    override fun indexDirectories(): Boolean = false
    override fun getInputFilter(): FileBasedIndex.InputFilter          // extension == "lua"

    companion object { val KEY: ID<String, List<LuaReceiverMember>> = ID.create("lunar.receiver.member") }

    /** Every member of [receiver] visible in [scope], unioned across declaring files. */
    fun membersOf(receiver: String, project: Project, scope: GlobalSearchScope): List<LuaReceiverMember>
}
```

**A `FileBasedIndex` value is per (key, file)**, so one key `wx` carries a list per declaring file and
`membersOf` unions them. That is what `LuaMemberFieldIndex`'s `<String, String>` shape could not do
(§2) — the value type is the fix, not the key.

**Modelled on `LuaGlobalAssignmentIndex`**, which already PSI-walks both declaration forms
(`:88-96`) in a `FileBasedIndex`. This is the prior art the earlier revision failed to name; the new
index is its sibling, not a replacement, and neither is retired.

### 4.3 Indexer algorithm

Input: a `LuaFile`'s `FileContent`. Output: `Map<String, List<LuaReceiverMember>>`.

1. Walk **all** `LuaAssignmentStatement` (not just top-level — a member assignment inside a function
   still declares a member; this differs deliberately from `LuaGlobalAssignmentIndex`, which is
   top-level-only because a nested *bare* assignment may target an enclosing local).
   For each `LuaVar` target with a non-empty `varSuffixList`, apply §4.4. Emit `Kind.FIELD`.
2. Walk all `LuaFuncDecl`. Take `node.findChildByType(LuaElementTypes.FUNC_NAME)?.text` — the same
   source `LuaFuncStubElementType.createStub:24` uses, so the two agree by construction. Apply §4.4.
   Emit `Kind.FUNCTION`.
3. Group by receiver.

### 4.4 Receiver derivation — the nested-qualifier rule

The behaviour to preserve is `memberNameOf` (`LuaTypeManagerImpl:462-468`):

```kotlin
if (!key.startsWith("$receiver.") && !key.startsWith("$receiver:")) return null
return key.substring(receiver.length + 1).takeIf { !it.contains('.') && !it.contains(':') }
```

— i.e. **exactly one separator**. `Foo.bar.baz` is *not* a member of `Foo`. So:

> Split the qualified name on the **first** `.` or `:`, whichever occurs earlier. If the remainder
> contains a further `.` or `:`, **emit nothing**. Otherwise emit
> `receiver = prefix`, `name = remainder`, `separator = the one found`.

`a.b.c` therefore contributes no entry, matching `memberNameOf`. This is **not** what
`substringBefore('.')` does — that would yield receiver `a`, member `b.c`, and admit a member the
current engine rejects. `LuaMemberFieldIndexTest.testDeepQualifiedKeyPresent` locks the *existing*
index's deep-key behaviour and is unaffected: that index keys on the whole dotted name and stays as
it is.

### 4.5 Consumer 1 — completion (`LuaCompletionContributor.crossFileGlobalMembers`)

```kotlin
private fun crossFileGlobalMembers(receiver: PsiElement): List<LuaReceiverMember> {
    val nameRef = bareNameOf(receiver) ?: return emptyList()
    return LuaReceiverMemberIndex.membersOf(
        nameRef.text, nameRef.project, GlobalSearchScope.allScope(nameRef.project),
    )
}
```

`allScope`, matching `addMethodsOf`'s comment (BUG-399: library declarations are members too).
The emit loop keeps its shape; `memberType` is replaced by `kind`:

```kotlin
if (isColon && member.kind != Kind.FUNCTION) continue
result.addElement(PrioritizedLookupElement.withPriority(LuaMemberLookup.create(member), 100.0))
```

`LuaMemberLookup` gains an overload taking `LuaReceiverMember`: the icon comes from `kind`, and
**no type text is set** on this path. Rationale: the index has no type, and §1.7 measured that adding
type text from a materialized graph costs 4 ms — so where the graph is already built (the in-file
path, unchanged) type text stays; on the cross-file path it is absent rather than expensive. That is
a **visible behaviour change** and TC 3 must be amended to expect it rather than treat it as a
regression.

### 4.6 Consumer 2 — materialization (`addMethodsOf`)

Signature unchanged; only how candidate keys are found changes.

```kotlin
private fun addMethodsOf(scan: MethodScan, membersMap: MutableMap<String, LuaTypeMember>) {
    val scope = GlobalSearchScope.allScope(project)
    for (member in LuaReceiverMemberIndex.membersOf(scan.receiver, project, scope)) {
        if (member.kind != Kind.FUNCTION) continue
        if (membersMap.containsKey(member.name)) continue                       // first-wins, preserved
        val key = "${scan.receiver}${if (member.separator == COLON) ":" else "."}${member.name}"
        val decls = StubIndex.getElements(LuaGlobalDeclarationIndex.KEY, key, project, scope, LuaFuncDecl::class.java)
        val decl = decls.firstOrNull { scan.onlyIn == null || it.containingFile == scan.onlyIn } ?: continue
        membersMap[member.name] = LuaTypeMember(member.name, funcTypeFromStub(scan.className, decl), sourceElement = decl)
    }
}
```

`collectMethodMembers` and `materializeUnhostedClass:328` drop their `getAllKeys` argument. **Four
behaviours preserved verbatim**, each becoming a test (§4.9):

| rule | where it lives today | how it survives |
| :-- | :-- | :-- |
| `allScope`, not projectScope (BUG-399) | `:441` | same scope passed to `membersOf` |
| first-wins within a receiver | `:445` | unchanged `containsKey` guard |
| file confinement for a local-declared class (BUG-398) | `:456` | unchanged `scan.onlyIn` filter |
| nested qualifiers are not members | `memberNameOf:467` | §4.4, at index time |

### 4.7 COMP-09-05 — `@class`-declared metamethods

`LuaGraphType.Table.metamethods` (`LuaGraphType.kt:54`) is populated only from `setmetatable`
(BUG-426's Known limitation; COMP-04-DR-01). `materializeClass:250-263` already collects `@field`
members into `membersMap`. The change:

> When converting a `LuaClassType` to `LuaGraphType.Table`, any member whose name is in
> `LuaGraphType.Trait`'s metamethod sets (`__add`, `__sub`, `__mul`, `__div`, `__mod`, `__pow`,
> `__unm`, `__idiv`, `__concat`, `__len`, …, enumerated at `LuaGraphType.kt:118-126`) contributes to
> `metamethods` **as well as** remaining a member.

"As well as" is deliberate: `LuaGraphType.kt:50-52` records that metamethods are held separately
because adding them to `localMembers` "would make `t.__add` complete on the instance, which is not
what Lua exposes". A `@class`-declared `__add` is already in `localMembers` today, so completion
behaviour is unchanged; only the operator check gains it.

### 4.8 Registration

```xml
<!-- src/main/resources/META-INF/plugin.xml, beside the five existing entries at :663-667 -->
<fileBasedIndex implementation="net.internetisalie.lunar.lang.indexing.LuaReceiverMemberIndex"/>
```

No other registration. No new service, no EP, no `plugin.xml` change beyond this line.

**Version bumps and their reindex boundaries** — every one forces a full reindex on first run, and
**no benchmark may cross one**:

| index | today | after |
| :-- | :-- | :-- |
| `LuaReceiverMemberIndex` | — | 1 (new) |
| `LuaMemberFieldIndex` | 1 | unchanged — not modified |
| `LuaGlobalAssignmentIndex` | 2 | unchanged |
| stub format (`LuaFileElementType.getStubVersion`) | 4 | **unchanged** — §4.4 derives receivers in the *new* index, so `LuaFuncStubElementType`'s dot-only sink is left alone |

That last row is the payoff of putting receiver derivation in a new `FileBasedIndex` rather than
amending the stub sink: no stub-format change, no `getStubVersion` bump, and DR-06's dot/colon
asymmetry is sidestepped rather than fixed in place.

### 4.9 What is deliberately not designed

- **Narrowing cache invalidation** (§3.3 / DR-07). Independent of this and possibly a smaller win.
- **Removing the four caches.** Acceptance criterion says re-measure each; that is a follow-up.
- **`LuaImplicitFields` / `LuaTypesVisitor:1349` / `catsClassTags`' file walks** (COMP-09-02's other
  sites). They are on the same critical path but need their own analysis; Phase 4 measures whether
  §4.5/§4.6 already moved them below budget.

## 5. Requirement coverage

Now writable: §3.1 and §3.2 are answered, and both doors resolve to the same remedy.

| Requirement | Covered by | Evidence |
| :-- | :-- | :-- |
| COMP-09-01 receiver-keyed enumeration | §4.1, §4.2 | §1.5 — names from an index value at key-lookup speed; §1.3 — the colon form needs its own key |
| COMP-09-02 no full-file walk | §4.3, §4.4 | §1.6 — the walk and the parse are the `@class` door's cost |
| COMP-09-03 all sources, dot **and** colon | §4.1, §4.2 | §1.3, §1.4 — `AllColon` enumerates 2 today and 0 under a naive swap |
| ~~COMP-09-04~~ incremental yield | **withdrawn** | §1.7 — no tail to stream |
| ~~COMP-09-04b~~ lazy type rendering | **withdrawn** | §1.7 — measured 4 ms for 3 700 members; presentation was never the cost, and `renderElement` is not per-visible-row |
| COMP-09-05 `@class` metamethods | not yet designed | COMP-04-DR-01 / BUG-426; Gap 2.3 says decide after the index shape is fixed, which §1.5 now fixes |
| COMP-09-06 no new type source | §4.3 | the split — names for completion, `forFile` retained for the checker — is what keeps the checker's inputs unchanged |
| COMP-09-07 behaviour-preserving | DR-01 golden | §1.4 — both doors per receiver, colon methods included |
| COMP-09-08 latency enforced | requirements NFR-3 | §1.6 shows even the warm-file `@class` path is 167 ms, i.e. over budget without a library involved |
| COMP-09-09 work bound | §4.4 | §1.5 — bound **stub loads**, not key visits: `getAllKeys` is 44 ms for 25 335 keys |
