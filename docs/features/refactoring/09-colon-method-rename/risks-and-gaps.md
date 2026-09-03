---
id: "REFACT-09-RISKS"
title: "09: Risks & Gaps"
type: "risk"
parent_id: "REFACT-09"
folders:
  - "[[features/refactoring/09-colon-method-rename/requirements|requirements]]"
---

# REFACT-09: Risks and Gaps

## Critical Risks

### Risk 1.1 — The predicate accepts a shape whose call sites it cannot see [Medium]

- **Impact**: `REFACT-01-00-DR-03`'s measured failure, verbatim — the declaration renamed, call
  sites left bound to the old name, success reported. This is the only failure mode that matters;
  everything else in this feature is a refusal.
- **Why it is not Low**: `upSet` reach is not a characterised property ([[TYPE-13]] requirements,
  DR-05a), so the type engine cannot be reasoned about — only enumerated. The predicate therefore
  does **not** rest on the engine for containment (`design.md` §3.1 layer (a)); it rests on a
  syntactic whitelist over the receiver binding's occurrences, which is closed over the `var`
  grammar's two step kinds **and over an index step's two spellings** (mitigation 3).
- **Mitigation**:
  1. Every clause is written *accept only if*, so an unenumerated occurrence position falls to
     `Verdict.Escape` (design §3.3 property (E)).
  2. Every clause has an executed mutation, and each mutant was measured to flip exactly its own
     fixture — see `requirements.md` "Test Cases". The mutants that produce a half-rename rather than
     a refusal — dropping the `findReferences` arm, and dropping the `self` occurrence half — were
     each observed doing so on their own fixture.
  3. **The closure is over the grammar's ALTERNATIVES, not only its productions**, and that is the
     part reviewing the property does not catch. `indexExpr ::= ('[' expr ']') | ('.' nameRef)`
     has two spellings and only the second names its member in the PSI, so a clause that compares
     `nameRef?.text` decides one spelling and silently drops the other; a `.name` step that names
     some *other* member of the receiver looks unrelated and is in fact how `self` gets rebound.
     Each escape §3.3 derives this way has a fixture on which the predicate, without it, was
     **measured accepting and rewriting** rather than refusing — `requirements.md` cases 25 and 27,
     output in `design.md` §3.3. Where a clause's absence produces a rewrite rather than a refusal,
     executing the mutation is not optional.
  4. The whitelist is stated as a property over the grammar rather than as a shape list, so a Lua
     form nobody has written down is already refused rather than newly unhandled.
- **Residual — and it is the one path whose failure mode is a half-rename rather than a refusal.**
  `design.md` §3.5's `decideRemainingSites` treats *undecided* and *decided elsewhere* as different
  answers: a null from `structuralDeclarationOf` refuses, a non-null one that is not this
  declaration is **skipped**. So a site for which the engine returns a *wrong* non-null declaration
  is silently dropped from the rename set instead of refusing it. [[TYPE-13]] Gap 2.12 is the one
  known instance and `design.md` §3.6 property 1 converts that one to a null.
- **What is not measured**: how often the engine answers *wrongly* rather than *not at all*.
  Gap 2.3's corpus run counted how often the R5 route declines — 226 of 941 declarations reach an
  undecided site — but "undecided" is the refusing answer; a wrong non-null answer is
  indistinguishable from a correct one without an independent oracle for "which declaration does
  this colon call really bind to", which is the question the whole feature exists because nothing
  in the repo can answer. **This is stated as unmeasured rather than argued away.** It is moot
  while Gap 2.3's accepted set is empty — `decideRemainingSites` is only reached on an accepting
  path — and it becomes the first thing to measure if any widening is ever taken.

### Risk 1.2 — Replacing `refactoring.rename.colonMethod` silently drops a refusal [Low]

- **Impact**: a rename that used to refuse now proceeds because a branch was rewired rather than
  re-decided.
- **Mitigation**: the old key is deleted from `LuaBundle.properties` in the same commit as its call
  site (`implementation-plan.md` Phase 3), so a surviving reference is a compile-or-`MissingResourceException`
  failure rather than a silent gap. Phase 4 rewrites the two tests that assert the old message
  rather than deleting them, keeping both their assertions.

### Risk 1.3 — The predicate runs on the EDT [Low]

- **Impact**: a rename-action invocation stalls the UI.
- **Mitigation**: `substituteElementToRename` is the only EDT caller, and its work is two
  `PsiTreeUtil.findChildrenOfType` passes over **one file** plus one
  `LuaTypesSnapshot.forFile`, which is `CachedValuesManager`-cached
  ([LuaTypes.kt:280-286](../../../../src/main/kotlin/net/internetisalie/lunar/lang/psi/types/LuaTypes.kt))
  and, on the rename path, already warm from the daemon. No index read, no VFS, no project-wide
  scan. It runs once per <kbd>Shift+F6</kbd>, not per keystroke: `isAvailableOnDataContext` — the
  hook that *does* run per action update — is `LuaInplaceRenameHandler`'s, and it declines a
  `METHOD_FUNCTION` before reaching any of this (`design.md` §1, prior-art table, last row).

## Design Gaps

### Gap 2.1 — Renaming a table leaves its `function t:m()` receiver segment behind [pre-existing, not caused here]

Measured during `REFACT-09-00-DR-02`, on the **unmodified** rename path and confirmed against the
dotted control:

```
P06 caret on the receiver of a colon call      → RENAMED
    | local renamedTable = {}
    | function t:m() end          ← left behind
    | renamedTable:m()
P19 CONTROL: the receiver of a DOTTED declaration → RENAMED
    | local renamedTable = {}
    | function M.run() end        ← left behind
    | renamedTable.run()
```

The mechanism: `LuaNameReference.isReferenceTo` resolves the `t` of `function t:m()` through
`LuaScopeProcessor`'s `is LuaFuncDecl` recursion branch
([LuaScopeProcessor.kt:79-84](../../../../src/main/kotlin/net/internetisalie/lunar/lang/LuaScopeProcessor.kt)),
which returns **that leaf itself**, so the identity test against the `local t` leaf is false and the
segment is never collected.

- **Not this feature's**: it is the same in both spellings, it predates [[TYPE-13]], and
  `REFACT-09-05` (caret on the receiver renames the receiver, not the method) still holds — `m` is
  untouched. `requirements.md` case 19 pins the measurement so a later reader does not rediscover it.
- **Action — done**: filed as **[[BUG-476]]**
  (`docs/features/bug-fixes/476-renaming-a-table-leaves-its-method-declarations-receiver-behind/bug-report.md`),
  with a roadmap row in Wave 20. It belongs to `REFACT-01`'s declaration-collection rules, not here;
  fixing it inside this feature would widen a colon-method rename into a receiver rename.
- **Re-measured for the bug report on `128ba091`, with no plugin change applied**, and it is wider
  than the two fixtures above: a **declaration** caret (`local <caret>t = {}`) reproduces it as well
  as a usage caret, so it is not caret-position specific either.

  ```
  BUG476[colon]     1| local renamedTable = {}   2| function t:m() end   3| renamedTable:m()
  BUG476[declCaret] 1| local renamedTable = {}   2| function t:m() end   3| renamedTable:m()
  BUG476[dotted]    1| local renamedTable = {}   2| function M.run() end 3| renamedTable.run()
  ```

### Gap 2.2 — `refactoring.rename.colonMethod` becomes unreachable

`requirements.md`'s acceptance criteria require this to be answered either way. **Answer: deliberately
replaced.** After `design.md` §2.2, the `METHOD_FUNCTION` arm calls `colonMethodSubstitution`, whose
own refusals are the narrower messages `design.md` §7.2 lists, each naming the clause that
declined — including `notADeclaration`, which every `planFor` step that cannot reach the
declaration or its `funcName` renders and which the grammar makes unreachable from that function's
stated input (§7.2). §7.2 is the list; this gap does not restate its length. The old key and its call site are removed together in
Phase 3.

### Gap 2.3 — Measured reach on real code is ZERO [blocking a disposition decision]

The predicate accepts a **file-local table whose value never leaves the file**, including its
`self:` calls and its `---@class`-annotated form. On the pinned corpus that set is **empty**.

**Executed** on the gce builder against a transcription of `design.md` §3.2-§3.6 onto real PSI
(`test --rerun --no-build-cache`, throwaway probe, reverted). The corpus is the same one
[[TYPE-13]] measures — all four pinned checkouts, whole trees, not the sweep's narrower `roots`:

```
REACH files=734 colonMethodDeclarations=941
REACH accepted=0
```

The declaration count differs from the **809** `REFACT-01-00-DR-03` reports over the same four
checkouts (`REFACT-01` risks-and-gaps, "4. Cost" table) because the two count different things:
that figure is a text measure of `function X:m()`, this one is every IDENTIFIER leaf whose
`LuaDeclarationSite.kindOf` is `METHOD_FUNCTION` — which is exactly `planFor`'s admissible input,
and therefore the right denominator here. Neither supersedes the other.

**The transcription is control-validated in both directions on the design's own fixtures** — it
accepts every accepting fixture and refuses every refusing one with the clause `design.md` predicts:

```
CONTROL[c01] ACCEPTED             CONTROL[c07] R3_escape_bare_notACallHead   (requirements case 7, `return M`)
CONTROL[c04] ACCEPTED             CONTROL[c06] R1_receiverNotUniqueFileLocal (case 6, global receiver)
CONTROL[c05] ACCEPTED             CONTROL[c11] R3_dottedAccess               (case 11, `print(t.m)`)
CONTROL[c26] ACCEPTED             CONTROL[c25] R3_escape_memberRead          (case 25, `t.a(other)`)
                                  CONTROL[c27] R3_escape_bracketIndexStep    (case 27, `t["m"]`)
```

**Which clause declines first**, in `design.md` §3.2's specified R1→R5 order:

| declarations | first clause to decline |
| --: | :-- |
| 335 | R1 — the receiver is not a unique file-local binding |
| 197 | R3 escape — a bare occurrence that is not a colon-call head (`return t`, `f(t)`, `local u = t`) |
| 159 | R3 escape — a bare occurrence that is a **dotted** call head (`t.f()`) |
| 111 | R3 escape — a member read (`local c = t.count`) |
| 59 | R3 escape — the occurrence's parent is not a `LuaVar` |
| 35 | R2 — an occurrence precedes the binding |
| 26 | R3 escape — a bare occurrence whose parent is not a `LuaVarOrExp` |
| 11 | R3 escape — a bracket index step |
| 5 | R3 escape — a member read through `self` |
| 3 | R3 — dotted access to the member being renamed |

**No single clause is the cause, and that is the finding.** Re-run with the short-circuits disabled,
so every clause that would decline is counted: only **12** of the 941 declarations are declined by
exactly one clause (7 by "the receiver is never declared in this file", 3 by a `self` member read,
2 by a bare non-call-head occurrence). Every other declaration is declined **independently by two or
more clauses**, so relaxing any one of them buys almost nothing:

```
REACH.relax accepted=0    relaxing=<nothing: the specified predicate>
REACH.relax accepted=4    relaxing=[the member-read escape, direct and through self]
REACH.relax accepted=3    relaxing=[every escape on a self occurrence]
REACH.relax accepted=0    relaxing=[the bracket-index-step escape]
REACH.relax accepted=0    relaxing=[R1's uniqueness test]
REACH.relax accepted=394  relaxing=[EVERY Verdict.Escape]
REACH.relax accepted=614  relaxing=[EVERY Verdict.Escape, and R1's uniqueness test]
```

- **What a wider predicate would have to give up, stated exactly.** The only relaxation that moves
  the number is deleting the escape set entirely — which is `design.md` §3.1 layer (a), the
  containment argument, and it is the *whole* of the evidence that the rewrite set is complete.
  `REFACT-09-00-DR-02` Finding 1 measured that the platform supplies no usage set to fall back on
  (`ReferencesSearch` returns 0 at every scope), so a predicate that accepts those 394 renames with
  no evidence of completeness at all. That is `REFACT-01-00-DR-03`'s half-rename, which is the
  failure this feature exists to prevent. **This trade is a product decision, not a design one, and
  it is not taken here.**
- **The widest clause is the one Gap 2.7 predicted was cheap.** The member-read escape reached
  through a `self` occurrence declines **761** of the 941 declarations on its own — more than any
  other clause — because `function C:m() … self.field … end` is what ordinary Lua OO looks like.
- **Where real width would come from**: widening `upSet` reach to the factory / `self` /
  nested-constructor shapes ([[TYPE-13]] Gap 2.7) and giving a `require`d module's type its members
  ([[TYPE-13]] Gap 2.11) are both engine changes with the member-map blast radius [[TYPE-13]]
  Risk 1.1 describes. Those change what is *decidable*, which is the only direction that raises
  reach without giving up containment.
- **Action**: the disposition of [[REFACT-09]] is **open**. The measurement above is the input;
  choosing between shipping a rename that refuses every file in the corpus, deferring behind the
  two [[TYPE-13]] engine gaps, and cancelling is not a planning decision. `status:` stays `todo`.

### Gap 2.4 — Making colon call sites resolve would remove the need for a private collector

`design.md` §9 Alternative B. If `LuaNameReference.multiResolve` gained a colon-method branch,
`ReferencesSearch` would find call sites, `findReferences` would need no arm, and Go to Declaration
and Find Usages would start working on `obj:m()` as a side effect.

- **Why not here**: measured, that route reports `declarationOf == null` for aliases, parameter
  receivers, `self`, required modules and cross-file globals. A resolution that is null in those
  cases is a navigation feature with its own requirements, and folding it into a rename would put a
  rename's safety on a hook every inspection also reads.
- **Action**: recorded as future work; it is the natural predecessor of a wider `REFACT-09`.

### Gap 2.5 — The conflict arm changes which rules run for `METHOD_FUNCTION`, and that kind reaches them for the first time

Before this feature no `METHOD_FUNCTION` ever reached `findCollisions` — `substituteElementToRename`
refused first. `design.md` §5 states each inherited rule's premise and why it is false here; the C4
case was **measured** producing a conflict that does not exist (`requirements.md` case 22).

- **Risk of the arm**: `captures` (C1) no longer runs for this kind. C1's premise is lexical capture
  of the *renamed name*, which a table member does not participate in.
- **Action**: `implementation-plan.md` Phase 5 pins both directions — case 21 (a real member
  collision is reported) and case 22 (an unrelated same-named receiver in another file is not).

### Gap 2.6 — A bracket step refuses more than it must, deliberately [accepted]

`design.md` §3.3 escapes on **any** bracket index step on the receiver, without reading the
expression inside it. That refuses a rename that is in fact safe whenever the declaring file also
writes the table array-style. Measured:

```
WPROBE[arrayWrite] REFUSED  Cannot perform refactoring. | The receiver's value escapes at 't' (bracket index step), so not every call site of this method can be found.
```

on `local t = {}` / `t[1] = 0` / `function t:<caret>m() end` / `t:m()`.

- **Why the cost is accepted**: reading the literal instead needs a reader correct for `"m"`, `'m'`
  and every long-bracket level — the defect [[BUG-467]] is the record of — and it is *still*
  undecidable for `t[k]`, so the escape would remain for the general case. One rule that refuses is
  smaller and cannot be subtly wrong.
- **Action**: none. `requirements.md` case 29 pins the over-refusal as a visible property rather
  than leaving it to be rediscovered, and Out of Scope names it.

### Gap 2.7 — A member *read* refuses more than the `self` route strictly needs [accepted]

The member-read escape exists to keep `design.md` §3.4's `self` clause sound, and it fires whether
or not the file contains a `self` occurrence at all. A narrower rule — escape on a member read only
when the plan actually uses the `self` route — would keep `local c = t.count` acceptable in a file
with no `self` in it.

- **Why the wider rule**: the narrower one makes acceptance depend on state gathered in a different
  clause, so the classifier stops being a function of the occurrence and the target. `Risk 1.1`'s
  whole argument is that each clause is *accept only if*, decidable locally; a conditional escape
  weakens exactly that.
- **What it costs on a fixture**: `local t = {}` / `function t:m() end` / `local c = t.count` /
  `t:m()` refuses — `PROBE[READ-other] REFUSED … escapes at 't' (member read '.count')` — while the
  write form `t.count = 0` still renames (`PROBE[WRITE-other] RENAMED`).
- **What it costs on real code, measured (Gap 2.3)**: this is the **widest** clause in the whole
  predicate. Counted with the short-circuits disabled over the corpus's 941 colon-method
  declarations, a member read reached through a `self` occurrence declines **761** of them and a
  direct member read declines **491**. Field-*writing* OO is indeed unaffected, and that is beside
  the point: a method body that *reads* `self.field` is what ordinary Lua OO looks like, so the
  read form is not a corner but the majority shape.
- **The narrower rule does not rescue the feature either.** Dropping the member-read escape
  altogether — both spellings — moves the corpus accept count from **0 to 4** (Gap 2.3's relaxation
  table), because those 761 declarations are declined independently by other clauses too. The
  narrower rule is therefore not "the first thing to reach for": it buys four declarations and
  costs the argument that makes `design.md` §3.4's `self` rewrites sound.
- **Action**: folded into Gap 2.3's disposition question. Nothing to do at this clause alone.

### Gap 2.8 — The member-name conflict sees declarations, not assigned members [accepted]

`design.md` §3.9's `memberDeclarationsNamed` enumerates `LuaFuncName` nodes, so it reports
`function t:n()` and `function t.n()` and does **not** report a member introduced by `t.n = f` or by
the table constructor `local t = { n = f }`.

- **Reachable, and in one direction only.** `t.n = 1` beside `function t:m()` is a sole-step
  assignment target, which `design.md` §3.3 classifies as `Unrelated`, so the plan accepts and then
  reports no conflict for `m` → `n`. The rename still rewrites exactly its own sites and the file
  stays internally consistent; the cost is two members named `n` where the user expected one
  member renamed.
- **Why it is accepted**: `REFACT-09-07` is a `Should`, and the alternative — treating an
  assignment target as a member declaration — would give `memberDeclarationsNamed` a second notion
  of "declaration" that `LuaDeclarationSite.functionNameLeafOf` does not share, on the one path
  where a false positive blocks a rename the user asked for.
- **Action**: none now. If it is picked up, the extension is a `LuaVar` arm in the enumeration, not
  a change to the predicate.

### Gap 2.9 — `---@field` names the member in a spelling the rename neither rewrites nor refuses [accepted]

`design.md` §3.4's occurrence scan walks `LuaNameRef` only. `LuaCatsFieldTag extends PsiElement`
([LuaCatsFieldTag.java:8](../../../../src/main/gen/net/internetisalie/lunar/luacats/lang/psi/LuaCatsFieldTag.java))
and is not a `LuaNameRef`, so `---@field m fun()` on the receiver's `---@class` is invisible to
every clause: it is not an occurrence, so it neither joins the rename set nor triggers an escape.
Renaming `m` therefore leaves a stale annotation on precisely the annotated receiver shape this
feature accepts.

Measured, fixture `---@class T` / `---@field m fun()` / `local t = {}` / `function t:m() end` /
`t:m()`:

```
N5 fieldTags=1 texts=[@field m fun()]
N5 fieldTagIsALuaNameRef=false
N5 verdict=ACCEPTED
```

- **This is the same premise as the bracket hole, not a new one.** Both come from deciding
  occurrences over one PSI type: `LuaNameRef` for the scan, `LuaIndexExpr.nameRef` for an index
  step. The dotted spelling shares it in the other direction — `t.m` *is* a `LuaNameRef`, which is
  why it gets a `DottedMember` refusal rather than silence.
- **Why not refuse instead**: refusing on any `LuaCatsFieldTag` in the file would refuse the
  `---@class`-annotated receiver outright, which is one of the few shapes the predicate accepts at
  all (`Gap 2.3`). Refusing only when a field tag *names this member* is the coherent rule and is a
  scan over a second PSI language, with its own decidability question for `---@field` on an
  inherited class.
- **Not measurable on the pinned corpus.** 0 of its 734 files carry a `---@` tag at all
  (`build.gradle.kts:322-326`, BUG-473 DR-6), so corpus frequency cannot size this; the fixture
  above is the measurement.
- **Action**: named in `requirements.md` Out of Scope, with the fixture above as its record.

## Technical Debt & Future Work

- **TBD: dotted access to a colon-declared member.** `t.m`, `function t.m()` and `t.m = f` name the
  same member as `t:m` and are currently a refusal (design §3.3 `DottedMember`). Rewriting them
  alongside is a coherent extension; it needs its own decidability rule for the dotted call sites,
  which resolve through `getQualifiedName`/`LuaGlobalDeclarationIndex` rather than through the type
  engine — a different mechanism, not a wider version of this one.
- **TBD: `setmetatable` OO.** The instance call `o:b()` was measured resolving to its declaration;
  what refuses the shape is that `Class` is passed as an argument and therefore escapes. Admitting
  it needs a value-flow rule, not a shape exception (design §9 Alternative D).

## Pre-Implementation De-risking Tasks

| ID | Action | Resolves | Status |
| :-- | :-- | :-- | :-- |
| `REFACT-09-00-DR-02` | Define a **complete** usage set for a colon method operationally, and measure whether it is computable without a whole-project scan. | Risk 1.1; adopted from `TYPE-13-00-DR-02`, which named [[REFACT-09]] as its owner | **done — result below** |

### DR-02 result — executed on the gce builder at `0bccadae`

Two probes, both reverted (`git status --porcelain` empty in the working tree and on the builder):
`Refact09Dr02CompletenessProbe` (reach, no production change) and `Refact09PrototypeProbe` driving a
throwaway prototype of `design.md` §2-§4 end to end through `myFixture.renameElementAtCaret`.

**Finding 1 — the platform's usage set for a colon method is empty at every scope.**
`ReferencesSearch.search(<declaration leaf>, GlobalSearchScope.allScope(project))` returned
`references=0` for the plain local table, the global table, `setmetatable` OO, the factory, the
`require`d module and the `---@class`-annotated receiver. `LuaNameReferenceSearcher` *does* scan
colon call sites, but gates on `isReferenceTo`, which resolves, and a colon call site resolves to
nothing. So completeness cannot be delegated to the platform; the feature must supply the set or refuse.

**Finding 2 — `declarationOf` answers "where is this member declared", not "is this set complete",
and is null wherever completeness is at risk.** The full reach table is in `requirements.md`
Overview. The shapes reporting `declarationOf == null` include an alias (`local u = t; u:m()`), a
parameter receiver, every `self:` call, a `require`d module, and a **global receiver called from
another file** — while the same global's in-file call resolves. A completeness rule built on
`declarationOf` alone would therefore accept the global shape and miss every cross-file call site.

**Finding 3 — completeness is decidable syntactically, and the answer is a file-local containment
argument plus a per-site decidability test.** `design.md` §3.1 states the rule; §3.2-§3.5 specify it. The measured outcome
of the resulting predicate, driven end to end:

| fixture | outcome |
| :-- | :-- |
| plain local table, declaration caret | RENAMED — all three sites |
| plain local table, call-site caret | RENAMED |
| `self:` call-site caret | RENAMED |
| plain local table + a `self:` call | RENAMED — declaration, `self:` call and direct call |
| two receivers sharing a method name | RENAMED — the other receiver untouched |
| `---@class` annotated local | RENAMED |
| identical shape in a second file | RENAMED, sibling file untouched, no conflict |
| the new name already a member of the receiver | CONFLICT reported |
| global receiver (with a cross-file call) | REFUSED — `receiver 'Obj' is not a file-local table` |
| module `return M` | REFUSED — `escapes at 'M' (bare, not a call head)` |
| alias `local u = t` | REFUSED — same |
| `setmetatable({}, Class)` | REFUSED — same |
| `t().x = 1` in the receiver's own suffix | REFUSED — `escapes at 't' (call step in a suffix)` |
| `t:m()` written above the `local t` | REFUSED — `receiver 't' is not a file-local table` |
| receiver name declared twice in the file | REFUSED — same |
| the method declared twice on one receiver | REFUSED — `'m' is declared 2 times on this receiver` |
| `print(t.m)` beside `function t:m()` | REFUSED — `also accessed as '.m'` |
| `self.m = 1` beside `function C:m()` | REFUSED — same |
| `t:m():m()` | REFUSED — `The call 'm' on line 3 cannot be bound to a declaration` |
| `local function f(x) x:m() end` | REFUSED — `The call 'm' on line 4 …` |
| `require`d module receiver, call-site caret | REFUSED — `Cannot determine which declaration this name refers to` |
| caret on `self` | REFUSED — `'self' is not the method name` |
| caret on the receiver of `t:m()` | RENAMED (the receiver; `m` untouched) — see Gap 2.1 |
| control: dotted `function M.run()` | RENAMED, unchanged by this feature |

Every refusal above left its file byte-identical.

**Finding 3a — outcomes added when the predicate was re-measured against the review's
counterexamples**, on the same harness at `128ba091`. Every one of these was executed; the fixtures
are `requirements.md` cases 25-29 and the `design.md` §3.3 table.

| fixture | outcome |
| :-- | :-- |
| `print(t["m"])` beside `function t:m()` | REFUSED — `escapes at 't' (bracket index step)` |
| `t[1] = 0` beside `function t:m()` | REFUSED — same (the deliberate over-refusal, Gap 2.6) |
| `t.a(other)` beside `function t:a() self:m() end` | REFUSED — `escapes at 't' (member read '.a')` |
| `local f = t.a` ; `f(other)`, same shape | REFUSED — same |
| `local c = t.count` beside `function t:m()` | REFUSED — `escapes at 't' (member read '.count')` |
| `t.count = 0` beside `function t:m()` | RENAMED — a sole-step write obtains nothing |
| `function C:m() self.count = 1 end` | RENAMED — same |
| `t.a.b = 1` beside `function t:m()` | REFUSED — the first step reads `.a` before the write |

**Finding 4 — the regression surface is the two `LuaRenameTest` methods that assert the replaced
message, and nothing else.** Full suite with the prototype applied:
**2 failures, 0 errors**, and they are exactly `LuaRenameTest.testColonMethodDeclarationIsRefused`
and `testSelfInsideAMethodIsRefusedAsTheMethod` — each failing only on the refusal *message*, each
still refusing, and each on a **global** receiver, so clause R1 declines both by construction:

```
testColonMethodDeclarationIsRefused -> the refusal must name its own reason, not merely abort:
  Cannot perform refactoring. The receiver 'Obj' is not a file-local table, so call sites in other files cannot be found.
testSelfInsideAMethodIsRefusedAsTheMethod -> (the same refusal text)
```

`implementation-plan.md` Phase 4 rewrites them. Executed against the predicate of `design.md`
§3.2-§3.6 applied to `128ba091` (`test --rerun --no-build-cache`). The total-test count is
deliberately not recorded: it counts the prototype's own throwaway methods and cannot be reproduced
from a clean tree.

**Finding 5 — resolution cannot be used to find the receiver binding.** The first prototype used
`declaration.funcName.nameRef.reference?.resolve()` and **refused every fixture**, because
`LuaScopeProcessor`'s recursion branch returns the funcName's own leaf. `design.md` §3.3.1 records
the mechanism and the text-plus-uniqueness lookup that replaces it. Recorded because it reads
correct and is not.

## Test Case Gaps

- **The dialog and its refusal balloons are not exercised headlessly.**
  `CommonRefactoringUtil.showErrorHint` throws instead of painting under `BasePlatformTestCase`, so
  every refusal is asserted through an exception message and nobody has seen the balloon.
  `human-verification-checklists.md` covers it.
- **The corpus reach measurement has been run — see Gap 2.3.** It is **0 accepted of 941
  colon-method declarations across 734 files**, with a per-clause breakdown and a relaxation table.
  It was run against a transcription of the specified predicate onto real PSI, not against the
  shipped code, which does not exist yet; re-running it against the implementation is a Phase 2
  verification task if the feature proceeds. What it does **not** measure is whether
  `structuralDeclarationOf` ever returns a *wrong* declaration rather than none — see Risk 1.1's
  residual, which remains unmeasured.
- **In-place rename is not covered, because it is unreachable.** Both in-place gates require
  `kindOf(...)?.isFileLocal == true` and `METHOD_FUNCTION.isFileLocal` is `false`. If a later change
  makes a method kind file-local, that assumption dies silently — `design.md` §1's prior-art table is
  where it is written down.

## See Also

- Requirements: [requirements.md](requirements.md)
- Design: [design.md](design.md)
- Plan: [implementation-plan.md](implementation-plan.md)
- [[TYPE-13]] risks and gaps — Gaps 2.7, 2.11 and 2.12 are the engine limits this feature refuses around.
