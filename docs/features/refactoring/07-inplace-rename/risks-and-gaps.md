---
id: "REFACT-07-RISKS"
title: "Risks & Gaps"
type: "risk"
parent_id: "REFACT-07"
priority: "high"
folders:
  - "[[features/refactoring/07-inplace-rename/requirements|requirements]]"
---

# REFACT-07: Risks & Gaps

## How to read this document

`REFACT-07-00-*` are **de-risking tasks**: executable probes that run **before** the phase that
depends on them. Each states its decision rule *before* the run, and each outcome moves something —
a de-risking task whose every branch leads to "proceed as planned" is not a probe, it is a
formality.

`Risk N.N` are hazards the design accepts or mitigates. `RD-N` are **recorded disagreements** with
inputs this feature consumed but may not edit.

## De-risking tasks

**Run order is DR-05, DR-01, DR-02, DR-04, DR-03** — IDs are identities, not sequence. DR-05 runs
first and on the **unchanged** tree, because every other task's setup assumes an answer it has not
established, and because the answer decides whether design §3.5 is part of the delivery.

### REFACT-07-00-05 (DR-05) — What element does the platform actually hand a rename handler, at each caret position?

**Status: EXECUTED 2026-08-26** on `f3a270fb`, and its decision rule is applied to `design.md`,
`requirements.md` and `implementation-plan.md`. The record is below.

**This is the premise the route decision and `REFACT-07-11` both rest on, and nothing has ever
executed it.** Design §1's data-context subsection traces it and design §3.5 designs against the
traced answer; both are labelled Read, not run. The shipped `LuaInplaceRenameTest` cannot settle it
because every case injects `CommonDataKeys.PSI_ELEMENT` by hand (`LuaInplaceRenameTest.kt:106-116`),
which is the same blind spot in test form.

**Setup.** On the **unchanged** tree — no §3.1 edit, no §3.2 edit — in a `BasePlatformTestCase`
fixture, for each caret position below: configure the file, then read the element the platform
computes from a **real** editor data context, never an injected one:

```
val context = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)
val supplied = PsiElementRenameHandler.getElement(context)
record: supplied?.javaClass, supplied?.node?.elementType, supplied?.textRange, supplied?.text,
        supplied?.parent?.javaClass, supplied is PsiNameIdentifierOwner, supplied is LuaNameRef
```

`DataManager…getDataContext(editor.getComponent())` is what `CodeInsightTestUtil.tryInlineRename`
itself uses (`CodeInsightTestUtil.java:244`), so this measures the path the document-layer cases
will take.

| # | Caret position | Fixture |
| :--- | :--- | :--- |
| a | declaration | `local coun<caret>ter = 0` / `print(counter)` / `counter = counter + 1` |
| b | usage (read) | `local counter = 0` / `print(coun<caret>ter)` |
| c | usage (write target) | `local counter = 0` / `coun<caret>ter = counter + 1` |
| d | parameter declaration | `---@param a number` / `local function f(<caret>a) return a end` |
| e | global declaration | `con<caret>fig = {}` / `print(config)` |
| f | numeric-`for` variable | `for i<caret> = 1, 10 do` / `  print(i)` / `end` |
| g | label declaration | `::ret<caret>ry::` / `goto retry` |

Also record, for (a) and (b), the return of
`MemberInplaceRenameHandler().isAvailableOnDataContext(context)` — the real gate, with no injected
element — and of `RenameHandlerRegistry.getInstance().getRenameHandlers(context)`.

**Decision rule, stated before the run:**

- **(a) is the declaring `LuaNameRef` and (b) is the declaration IDENTIFIER leaf** → design §1's
  table and §3.5 are confirmed as written. Proceed; §3.5 ships in Phase 3.
- **(b) is the declaring `LuaNameRef`** (i.e. something normalises the resolve result before the
  data context sees it) → **§3.5 is unnecessary and must be deleted, not shipped inert.** A
  handler that is never available is dead code that a later reader will mistake for a mechanism.
  Delete §2.3, §2.4's added registration and TC-04's mutation, re-point `REFACT-07-11` at §3.2's
  `kindOf` clause, and record the deletion in the DR record. Phase 3 loses task 3.4.
- **(a) is the declaration IDENTIFIER leaf too** → the platform handler is never available for a
  Lua variable and §3.5's handler carries **every** caret position. Design §3.2's
  `isMemberInplaceRenameAvailable` clause (2) then has no reachable input and must be reconsidered
  before Phase 3; every declaration-caret document-layer case drives `LuaInplaceRenameHandler()`
  rather than `MemberInplaceRenameHandler()`, **and therefore switches driver** from
  `CodeInsightTestUtil.tryInlineRename` to design §6's `renameInPlaceViaHandler`, whose parameter
  type is `RenameHandler`; and TC-02's expected handler class changes to `LuaInplaceRenameHandler`,
  with its `instanceof` assertion changing to match. The pairwise availability invariant is unaffected — it is what makes
  this branch safe — but the extension-list premise DR-02 tests is unaffected too, and still has to
  hold.
- **(e) is the global's declaration leaf rather than its `LuaNameRef`** → TC-10's mutation is the
  `LuaInplaceRenameHandler` one, not the `isMemberInplaceRenameAvailable` one; `requirements.md`
  TC-10 states both and this probe picks.
- **(g) is not a `LuaLabelName`** → REFACT-04's shipped label rename does not work the way
  `REFACT-07-13` assumes, which is a finding about REFACT-04, not about this feature. File it here
  and do not change §3.2's clause (1) on the strength of it.
- **(f) is anything other than null or a bare leaf whose parent is a `LuaNumericForStatement`** →
  §3.5's third step is not the exclusion `REFACT-07-14` needs. Re-derive it before Phase 3.
- **any position yields null** → `Shift+F6` there reaches no handler at all today; record it, and
  check it against the shipped behaviour before treating it as a regression.

**Blocks**: DR-01, DR-02, and Phases 1-5. **Estimate**: 1-2 h.

#### Result — EXECUTED 2026-08-26, on the unchanged tree at `f3a270fb`

Driven through `PsiElementRenameHandler.getElement(DataManager.getInstance().getDataContext(…))`
in a `BasePlatformTestCase` fixture. **No element was injected into any context.** Both
`myFixture.editor.contentComponent` (this spec's snippet) and `myFixture.editor.component`
(`CodeInsightTestUtil.java:244`) were measured on every caret and **agree on every row**, so the
two spellings are interchangeable here and no case turns on which was used.

GoLand 2026.1.3 (`platformType = GO`, `platformVersion = 2026.1.3`), builder VM `lunar-builder`,
`test --tests *Dr05DataContextProbeTest* --rerun --no-build-cache` → `BUILD SUCCESSFUL in 31s`,
JUnit XML `tests="1" skipped="0" failures="0" errors="0"`, timestamp `2026-08-26T10:01:46.642Z`.
Raw rows in `dr-05-evidence/measured-rows.txt`; the harness in
`dr-05-evidence/probe-harness.kt.txt`, **removed from `src/test` after the run** — it prints and
asserts nothing, and a non-failing case left in the suite is the defect Risk 1.2 is about.

Rows `a2`, `d2`, `d3`, `f2`, `f3` and `g2` are **additions to the spec's table**, added because
without them three of the answers below would have been unattributable: `a2` separates "a `local`
declaration" from "a `local` declaration that shadows a global", which design §1 names as the most
likely falsifier; `d2`/`d3` separate the parameter answer from the `---@param` tag and from
caret-at-token-boundary; `f2`/`f3` separate `f`'s null from the caret having landed on whitespace.

**Table 1 — what the data context supplies.** `Lua*Impl` are
`net.internetisalie.lunar.lang.psi.impl.*`; `LeafPsiElement` is
`com.intellij.psi.impl.source.tree.LeafPsiElement`. "leaf at caret" is
`findElementAt(caretOffset)`, recorded for contrast only — where it differs from the supplied
element, the platform has walked away from the caret.

| # | Caret | `supplied.javaClass` | `supplied.node.elementType` | `supplied.textRange` | `supplied.text` | `supplied.parent.javaClass` | `is PsiNameIdentifierOwner` | `is LuaNameRef` | leaf at caret |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| a | `local coun\|ter = 0` | `LuaNameRefImpl` | `NAME_REF` | `(6,13)` | `counter` | `LuaAttNameImpl` | **false** | **true** | `IDENTIFIER 'counter' (6,13)` |
| a2 | `config = 1` ⏎ `local con\|fig = 2` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | **`(0,6)`** | `config` | `LuaNameRefImpl` | false | false | `IDENTIFIER 'config' (17,23)` |
| b | usage read `print(coun\|ter)` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | **`(6,13)`** | `counter` | `LuaNameRefImpl` | **false** | **false** | `IDENTIFIER 'counter' (24,31)` |
| c | usage write `coun\|ter = counter + 1` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | **`(6,13)`** | `counter` | `LuaNameRefImpl` | false | false | `IDENTIFIER 'counter' (18,25)` |
| d | parameter, with `---@param` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | `(36,37)` | `a` | `LuaNameRefImpl` | **false** | **false** | `IDENTIFIER 'a' (36,37)` |
| d2 | parameter, no `---@param` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | `(17,18)` | `a` | `LuaNameRefImpl` | false | false | `IDENTIFIER 'a' (17,18)` |
| d3 | parameter, caret inside `f(cou\|nt)` | `LeafPsiElement` | `LuaTokenType.IDENTIFIER` | `(17,22)` | `count` | `LuaNameRefImpl` | false | false | `IDENTIFIER 'count' (17,22)` |
| e | global decl `con\|fig = {}` | `LuaNameRefImpl` | `NAME_REF` | `(0,6)` | `config` | `LuaVarImpl` | **false** | **true** | `IDENTIFIER 'config' (0,6)` |
| f | `for i\| = 1, 10 do` | **null** | null | null | null | null | false | false | `WHITE_SPACE ' ' (5,6)` |
| f2 | `for \|i = 1, 10 do` | **null** | null | null | null | null | false | false | `IDENTIFIER 'i' (4,5)` |
| f3 | `for in\|dex = 1, 10 do` | **null** | null | null | null | null | false | false | `IDENTIFIER 'index' (4,9)` |
| g | label decl `::ret\|ry::` | `LuaLabelNameImpl` | `LABEL_NAME` | `(2,7)` | `retry` | `LuaLabelImpl` | **true** | false | `IDENTIFIER 'retry' (2,7)` |
| g2 | `goto ret\|ry` | `LuaLabelNameImpl` | `LABEL_NAME` | **`(2,7)`** | `retry` | `LuaLabelImpl` | **true** | false | `IDENTIFIER 'retry' (15,20)` |

**Table 2 — the gates, with nothing injected.** The spec asks for `a` and `b`; every caret was
recorded, which costs nothing and settles TC-10 and TC-12 in the same run. `RenameHandler.EP_NAME`
held **10** extensions in every row.

| # | `MemberInplaceRenameHandler().isAvailableOnDataContext` | `RenameHandlerRegistry.getRenameHandlers` | EP handlers whose `isRenaming` is true |
| :--- | :--- | :--- | :--- |
| a | **false** | `VariableInplaceRenameHandler` | `VariableInplaceRenameHandler` |
| a2 | false | `PsiElementRenameHandler` | *(none)* |
| b | **false** | `PsiElementRenameHandler` | *(none)* |
| c | false | `PsiElementRenameHandler` | *(none)* |
| d / d2 / d3 | false | `PsiElementRenameHandler` | *(none)* |
| e | false | `PsiElementRenameHandler` | *(none)* |
| f / f2 / f3 | false | **empty list** | *(none)* |
| g / g2 | **true** | `MemberInplaceRenameHandler` | `MemberInplaceRenameHandler` |

`PsiElementRenameHandler` in column 2 is **not an extension hit**. It is
`myDefaultElementRenameHandler`, returned at `RenameHandlerRegistry.java:121-123` only when the
EP-available set is empty — which column 3 shows independently, by measurement rather than by
reading the source. Those rows therefore mean *no registered handler is available at that caret*,
and Shift+F6 opens the dialog.

#### Per-caret verdict against design §1's prediction

| # | Design §1 predicted | Measured | Verdict |
| :--- | :--- | :--- | :--- |
| a | the declaring `LuaNameRef` (`resolve()` finds nothing, so `getNamedElement` answers) | `LuaNameRefImpl` | **HOLDS** |
| a2 | not enumerated in the decision rule; added to the probe table so that a `local` shadowing an earlier same-file global could be separated from a plain `local` | the **global's** IDENTIFIER leaf at `(0,6)`, 11 characters away from the caret | **a declaration caret does not universally supply the declaring `LuaNameRef`.** `resolve()` is non-null when the declared name is already in scope, and the reference branch wins |
| b | the declaration IDENTIFIER **leaf**, neither `PsiNameIdentifierOwner` nor `LuaNameRef` | exactly that — leaf `(6,13)` while the caret is at `(24,31)` | **HOLDS, in every field** |
| c | same as b | same as b | **HOLDS** |
| d | not enumerated separately by the decision rule, which asks about (a)'s declaration shape only | the IDENTIFIER **leaf** — and `d2`/`d3` show it is neither the `---@param` tag nor caret placement | **a declaration caret does not universally supply the declaring `LuaNameRef`.** A parameter declaration caret behaves like a usage caret |
| e | not enumerated; the decision rule asks whether it is the leaf | the declaring `LuaNameRef` | rule branch (e) **does not fire** |
| f | rule expects null or a bare leaf whose parent is the `for` statement | **null**, at all three caret placements | rule branch (f) **does not fire**; and the null is not a whitespace artifact |
| g | rule asks whether it is not a `LuaLabelName` | `LuaLabelNameImpl`, `PsiNameIdentifierOwner` **true** | rule branch (g) **does not fire**; REFACT-04 works as §3.2 clause (1) assumes |
| g2 | not enumerated | the label **declaration** composite, from a `goto` usage caret | labels already have a working in-place route from **both** carets today |

**The applied branch is the first one: (a) is the declaring `LuaNameRef` and (b) is the declaration
IDENTIFIER leaf → design §1's table and §3.5 are confirmed, §3.5 ships in Phase 3.** The deletion
branch — "(b) is the declaring `LuaNameRef` → §3.5 is unnecessary" — **does not fire**, so §2.3,
§2.4's registration, §3.5 and TC-04's mutation all stay, and plan tasks 3.3 and 3.4 stand.

#### What the measurement settles, and where each answer now lives

1. **§3.5 carries the parameter declaration caret as well as the usage caret.** At `d`/`d2`/`d3` the
   context supplies the parameter's IDENTIFIER leaf, and `declaringNameRefOf` accepts it —
   `IDENTIFIER` ✓, `kindOf` = `PARAMETER` (`LuaDeclarationSite.kt:243`), `isFileLocal` ✓ (`:20`),
   `parent is LuaNameRef` ✓ (measured). The platform's `MemberInplaceRenameHandler` refuses that
   leaf at `MemberInplaceRenameHandler.java:56` and falls through to `performDialogRename` at
   `:87`. So Lunar's handler is the available one there, `REFACT-07-09` is served through it, and
   TC-09 drives it through design §6's `renameInPlaceViaHandler`. Carried by design §3.5's scope
   statement, §3.2 ("Which declaration carets clause (2) actually serves"), §5 Example 2, §6's
   driver rule, §8's `REFACT-07-01` and `REFACT-07-09` rows, `requirements.md`'s TC-09 row and its
   `REFACT-07-09` acceptance criterion.
2. **TC-10's mutation is `isMemberInplaceRenameAvailable`'s `isFileLocal` clause.** Probe `e`
   supplies the declaring `LuaNameRef`, so §3.2's predicate is on that caret's path; the
   corresponding `isFileLocal` test in `declaringNameRefOf` (§3.5 step 2) is **inert** there.
   Carried by `requirements.md`'s TC-10 row.
3. **TC-12 is a guard, and §3.5 step 3 has no reachable input.** Probe `f` is null at all three
   placements, so no Lunar predicate is on that caret's path. `REFACT-07-14` is delivered by the
   platform supplying nothing; the safe cast stays as the classifier-invariant assertion §3.5 calls
   it, and no requirement rests on it. Carried by design §3.5's step table, §3.4's numeric-`for`
   row, §8's `REFACT-07-14` row, Risk 1.7, `requirements.md`'s TC-12 row and its `REFACT-07-14`
   acceptance criterion.
4. **§3.2 clause (2) keeps reachable input, and the availability invariant's structural premise
   holds.** Carets `a` and `e` supply a `LuaNameRef`, which §3.1 will make a
   `PsiNameIdentifierOwner`; carets `b`, `c`, `d` supply a leaf, which §3.1 cannot touch. No caret
   supplies an element that is both. That is the premise; the consequence for handler *availability*
   with §3.1 and §2.3 present is still DR-02's, because this ran on the unchanged tree.
5. **TC-02's named mutation is reachable.** With the shipped predicate in place,
   `VariableInplaceRenameHandler` is the *only* extension renaming at caret `a` (Table 2), which is
   exactly the second handler TC-02's mutation relies on to trigger
   `RenameHandlerRegistry.java:114-119`.
6. **DR-02 is NOT discharged by this.** `RenameHandler.EP_NAME` held 10 extensions in the unit-test
   application; a full GoLand loads a different and larger set. The rows above are evidence *for*
   the §3.2 premise, not a substitute for enumerating it in a running IDE.
7. **Two `REFACT-07-01` kinds are outside this probe's table** — generic-`for` and
   `local function`. DR-01 probe (b) measured both: generic-`for` supplies the declaring
   `LuaNameRef` and is served by §3.2's predicate, `local function` supplies the leaf and is served
   by §3.5's handler. Design §3.2's split table is where that answer lives. Probe (b) also measured
   Observation 1's unmeasured half, which is now **Gap 2.21**.

#### Two findings about shipped behaviour, neither caused by REFACT-07

**Observation 1 — a declaration caret on a `local` that shadows an earlier same-file global
targets the global** (`a2`). With `config = 1` ⏎ `local con|fig = 2`, the data context supplies the
global's declaration leaf at `(0,6)` — a different declaration, on a different line, from the one
under the caret. The declaring statement is excluded from its own scope (`LuaBlockExt.kt:32-36`), so
`scopeCrawlUp` continues to file scope and resolves to the global, and
`TargetElementUtilBase.java:235-239` prefers that resolve over `getNamedElement`.

*Evidence*: measured **at the data context only**, on the unchanged tree at `f3a270fb`; the raw row
is `a2` in Table 1 and its handler row is `a2` in Table 2. What the dialog path finally renames was
**not driven**, so the user-visible consequence is unmeasured.

*Is REFACT-07 expected to change it?* For the measured shape, **no**: the supplied element is a
*global's* leaf, which `declaringNameRefOf` refuses at its `isFileLocal` step, and which the
platform's handler refuses as a non-`PsiNameIdentifierOwner`, so the caret keeps the dialog exactly
as Table 2 records today. For the shape where the shadowed declaration is a **file-local** rather
than a global — `local config = 1` ⏎ `local con|fig = 2` — **DR-01 probe `b6` measured it, and the
answer is yes**: that leaf passes every step of `declaringNameRefOf`, Lunar's handler accepts it,
and the registry hands the caret to Lunar, so an in-place template would anchor on the *earlier*
declaration. That is filed as **Gap 2.21**, which also records why `BUG-470`'s fix for the global
half does not reach it. What the template finally renames end to end is still not driven. The design
is **not** widened to handle it here.

**Observation 2 — Shift+F6 on a numeric-`for` variable reaches no handler at all** (`f`/`f2`/`f3`):
the registry returns an **empty** list, not even the default dialog handler, because the supplied
element is null.

*Evidence*: measured at all three caret placements — before the identifier, inside it, and on the
following whitespace — so it is not a caret-placement or whitespace artifact. Rows `f`/`f2`/`f3` of
Tables 1 and 2. This is the decision rule's "any position yields null → record it, and check it
against the shipped behaviour before treating it as a regression".

*Is REFACT-07 expected to change it?* **No.** Neither Lunar predicate is on that caret's path, for
the reason Risk 1.7 states, and this feature adds nothing that runs when the data context supplies
no element. `REFACT-07-14` is the requirement that pins the outcome, and TC-12 is its guard.

Neither observation is caused by this feature and neither is filed as a bug here; both are recorded
for the supervisor.

#### Design §1 evidence-table rows this settles (plan task 5.6)

Design §1's evidence-table row for *which element `CommonDataKeys.PSI_ELEMENT` holds at each caret
position* is now labelled **Executed**, and §1's caret table carries the per-caret answer with the
probe that measured it. §1's further claim — that no platform in-place handler is available from a
caret whose context supplies the leaf — is confirmed directly:
`MemberInplaceRenameHandler().isAvailableOnDataContext` is `false` at `b`, `c` and `d`.

The row for the *pairwise availability invariant* is **split** rather than settled: its structural
premise is measured here, its handler-availability consequence is DR-02's, because this ran on the
unchanged tree where §3.1's interface and §2.3's handler do not exist. Every other row of that
table still carries a "Read, not run" label and is task 5.6's to settle when its DR task runs.

### REFACT-07-00-01 (DR-01) — Does `PsiNameIdentifierOwner` on `LuaNameRef` actually unblock Route B, end to end?

**Status: EXECUTED 2026-08-26** on scratch branch `spike/refact-07-dr-01` off `bbed46c3`. The
record is below, after the decision rule. **Probe (f) came back negative**; no phase moves and no
design document is amended on the strength of it without a supervisor decision.

**This is the feature's central open question, and it runs before any implementation phase.** Every
prior attempt at `REFACT-01-12` flagged the interface as the root cause and none spiked it; the
whole of design §3.3 is a **read** trace through frames that have never been executed with the
interface present.

**Precondition**: DR-05 has run and its decision rule is applied to the plan.

**Setup.** On a scratch branch off `f6148451`, apply exactly the edits design §3.1, §3.2, §2.3 and
§2.4 specify — `PsiNameIdentifierOwner` + `getNameIdentifier()` on `LuaNameRefBaseImpl`, the two
predicate bodies on `LuaRefactoringSupportProvider`, `LuaInplaceRenameHandler`, and its
`renameHandler` registration — and nothing else. Add one throwaway test:

```
fixture: "local coun<caret>ter = 0\nprint(counter)\ncounter = counter + 1\n"
action:  CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), "total",
                                             myFixture.editor, myFixture.file.findElementAt(myFixture.caretOffset))
assert:  myFixture.checkResult("local total = 0\nprint(total)\ntotal = total + 1\n")
```

The fourth argument is the caret **leaf**, deliberately: `tryInlineRename` prefers
`PsiElementRenameHandler.getElement(context)` and uses the argument only as a fallback
(`CodeInsightTestUtil.java:246`), so passing the leaf makes the probe fail loudly if the data
context ever yields nothing, instead of quietly measuring a hand-picked element.

Use the `temporary-edits` skill to snapshot before editing; restore with the skill, never with
`git checkout -- <path>`.

**Measurements to record, each as raw output pasted into this task's evidence directory
(`dr-01-evidence/`), not as prose:**

| # | Probe | Why it moves something |
| :--- | :--- | :--- |
| a | `(nameRef as PsiNameIdentifierOwner).nameIdentifier` — is it the IDENTIFIER leaf? | If null, §3.1's body is wrong and everything downstream is void. |
| b | `PsiElementRenameHandler.getElement(ctx)`, then `MemberInplaceRenameHandler().isAvailableOnDataContext(ctx)` and `LuaInplaceRenameHandler().isAvailableOnDataContext(ctx)`, with `ctx = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)` — for a `local` declaration caret, a **usage** caret, a **parameter** declaration caret, a **generic-`for`** variable caret (`for k, v in pairs(t) do`), a **`local function`** name caret,
and a `local` that shadows an earlier same-file `local` (`local config = 1` / `local con<caret>fig = 2`). No `CommonDataKeys.PSI_ELEMENT` is added to the context. | The gates design §3.2 and §3.5 claim, driven through the path the IDE uses. Injecting the element here would re-measure the plan's assumption; DR-05 exists because that is what the shipped test does. **The generic-`for` and `local function` kinds are `REFACT-07-01`'s and DR-05 did not probe them**, so which of the two mechanisms serves them is still Read, not run — and with it, which driver and which mutation a future case on those kinds would need (design §3.2, "Which declaration carets clause (2) actually serves"). The shadowing shape is DR-05 Observation 1's unmeasured half: probe `a2` shadowed a *global*, whose leaf `declaringNameRefOf` refuses; a shadowed *file-local* would pass every step, and this probe establishes whether Lunar's handler then anchors a template on the earlier declaration. |
| b2 | for the usage caret, the document after `renameInPlaceViaHandler(LuaInplaceRenameHandler(), "total")` — design §6's helper, verbatim. `CodeInsightTestUtil.tryInlineRename` cannot be used: its parameter type is `VariableInplaceRenameHandler` (`CodeInsightTestUtil.java:236`) and this handler implements `RenameHandler` directly (§2.3) | `REFACT-07-11`'s acceptance, through §3.5's handler and through `invoke`, the entry point `BaseRefactoringAction.performRefactoringAction` calls (`:172`). |
| c | `MemberInplaceRenamer(nameRef, leaf, editor).performInplaceRename(null)` return value | `false` means a frame after `getNameIdentifier` still blocks — the outcome [[REFACT-01]] Gap 2.20 leaves open, since it establishes the interface as the root cause without establishing that it is the only one. |
| d | `TemplateManagerImpl.getTemplateState(editor)?.getCurrentVariableRange()` immediately after the template starts | Non-null is the difference between a usable template and the corrupting one the prior attempts measured. |
| e | the document text after `tryInlineRename` commits | The only measurement that is acceptance. |
| f | design §5 Example 2's fixture (`---@param a number` / `local function f(<caret>a) return a end`), driven with `renameInPlaceViaHandler(LuaInplaceRenameHandler(), "count")` — **not** `CodeInsightTestUtil.tryInlineRename`, because the data context supplies the parameter's IDENTIFIER leaf here (DR-05 probe `d`) and `MemberInplaceRenameHandler` refuses it at `:56`. Assert the tag moved, and additionally record which template segment was `PRIMARY_VARIABLE_NAME` | Proves the commit reached `LuaRenameProcessor.renameElement` through §3.5's handler, which is Ground 1 of the route decision and `REFACT-07-09`'s route. The segment record settles §3.5's "What the template looks like" for a parameter caret, which is Read, not run. |
| g | `MemberInplaceRenameHandler().doRename(nameRef, editor, ctx)` — does the substitution callback fire at all? | `PsiElementRenameHandler.canRename` gates it (`RenamePsiElementProcessorBase.java:244`), and a refusal there is **silent**: `doRename` returns and no template starts. Design §3.3 step 3 argues it cannot fire; this is the probe. |
| h | the full unit suite, `test --rerun --no-build-cache` | The regression-relative baseline. |

**Decision rule, stated before the run:**

- **(a) null** → §3.1 is wrong. Re-derive the accessor from `LuaNameRefImpl.getIdentifier()` and
  re-run. If it is still null, the mixin is not on the instantiated class and Alternative D
  (`.bnf` `implements=` + regeneration) becomes the delivery route; design §9 Alternative D is
  promoted and Phase 1 grows a regeneration step.
- **(b) false** → the predicate is wrong, not the PSI. Fix §3.2 and re-run; no phase moves.
- **(c) false** → **a frame after `getNameIdentifier` blocks too.** Capture the frame from a
  breakpoint or by bisecting `performInplaceRefactoring`'s early returns
  (`InplaceRefactoring.java:206-240`), and file it as a new gap here. If the blocking frame is
  `checkLocalScope`, the `MemberInplaceRenamer` override claim (design §1 Ground 2) is false
  against the compiled build and the `getUseScope` override returns to the plan as a second
  required edit — with `REFACT-07-15`'s document-level test landing **first**, per Risk 1.2 and the ordering principle in [[features/refactoring/07-inplace-rename/implementation-plan|implementation-plan]].
- **(d) null with (c) true** → the template starts and cannot be typed into: the corrupting state,
  reproduced on Route B. **Stop.** Do not proceed to Phase 2; the route decision is refuted and
  `REFACT-07` returns to planning.
- **(e) not the expected text** → record the actual text and treat it as (d): a rename that starts
  and produces something else is worse than no in-place rename.
- **(f) tag did not move** → **the measured mechanism, replacing the one this branch predicted.**
  The branch as first written said "Route B's commit is not reaching `LuaRenameProcessor`", and
  prescribed a re-plan onto Route A with a `renameSynthetic` override. The run refutes the premise:
  the commit **does** reach `LuaRenameProcessor.renameElement`, and Ground 1 survives. It arrives
  with the wrong *element* — `MemberInplaceRenamer.getSubstituted()` re-derives the target with
  `findElementOfClassAtRange(…, PsiNameIdentifierOwner.class)` (`MemberInplaceRenamer.java:367-372`)
  and §3.1 is what makes the Lua composite answer, so `LuaDeclarationSite.kindOf` is null and the
  `---@param` clause is skipped. The Route A prescription therefore does **not** apply and was not
  taken; the applied response is design §2.5/§3.6's normalisation in `renameElement`, which is a
  latent `REFACT-01` defect this feature is the first caller to expose. Any future re-run of this
  probe that sees the tag not move should check §3.6's normalisation is still present before
  concluding anything about the route.
- **(g) the callback did not fire** → `PsiElementRenameHandler.canRename` refused the substituted
  leaf, which design §3.3 step 3 argues it cannot. Capture the message
  `getRenameErrorMessage` produced (`PsiElementRenameHandler.java:150-176`) and treat it as (c):
  a new gap, filed here, before any phase moves.
- **all of (a)-(h) as designed** → **also record the use-scope question [[REFACT-01]] Gap 2.20 leaves open**: with the
  interface present, is a `getUseScope()` override needed? Design §1 Ground 2 predicts **no** for
  Route B and **yes** for Route A, on the reading that `MemberInplaceRenamer.checkLocalScope()`
  (`:105-111`) never consults `PsiSearchHelper`. (c) returning `true` **without** any
  `getUseScope` override is the executed proof. Record it explicitly; it is the question the prior
  attempts have left open.
- **Additionally, whichever branch fires**: record the delivered change's surface by naming every
  production file and every `plugin.xml` element it touches — the design's own list is
  `LuaBaseElements.kt`, `LuaRefactoringSupportProvider.kt`, `LuaInplaceRenameHandler.kt` and one
  `renameHandler` element — and whether DR-03 found any
  consumer outside `com.intellij.refactoring.rename` whose behaviour moved. If both answers are
  "no additional surface", **recommend collapsing REFACT-07 into a REFACT-01 phase** and say so in
  the DR record. That is a legitimate outcome (`requirements.md`, "Why this is a feature").

**Blocks**: Phases 1-4. **Estimate**: 3-4 h.

#### Result — EXECUTED 2026-08-26, on scratch branch `spike/refact-07-dr-01` (`abb55194`, `fb02fa8e`) off `bbed46c3`

**Status: EXECUTED. Design §3.3 survives to step 8 and FAILS at step 8.** Probes (a)-(e), (g) and
(h) came back as designed; probe (f) came back **negative**, and the frame it fails in is named
below. The measured mechanism is not the one decision-rule branch (f) predicts — branch (f) says
"Route B's commit is not reaching `LuaRenameProcessor`", and the commit **does** reach
`LuaRenameProcessor.renameElement`; it arrives there with the wrong element. Whether the design is
amended on the strength of that is the supervisor's call and is not made here.

GoLand 2026.1.3 (`platformType = GO`, `platformVersion = 2026.1.3`), builder VM `lunar-builder`.
Raw rows in `dr-01-evidence/measured-rows.txt`; the three probe harnesses in the same directory as
`*.kt.txt`, **removed from `src/test` after the run** — most cases print rather than assert, so
leaving them in the suite would be tests that cannot fail (Risk 1.2). `instrumentation-note.txt`
records the one throwaway production edit (a `println` in `LuaRenameProcessor.renameElement`),
reverted with `git show <ref>:<path> > <path>`.

**Delivered surface, as the decision rule requires it recorded**: exactly the four the design lists
and nothing else — `LuaBaseElements.kt` (`LuaNameRefBaseImpl` gains `PsiNameIdentifierOwner` +
`getNameIdentifier()`), `LuaRefactoringSupportProvider.kt` (both predicate bodies),
`LuaInplaceRenameHandler.kt` (new), and one `renameHandler` element in `plugin.xml`. **DR-03 has
since run (2026-08-26) and answers the other input: three consumers outside
`com.intellij.refactoring.rename` DID move** — `NamedElementDuplicateHandler` (the caret after
<kbd>Ctrl+D</kbd>), `RelatedItemLineMarkerProvider` (an extra Related-Symbol item from a composite
context) and `BookmarkManager.findElementBookmark` (changed but callerless). The
collapse-into-REFACT-01 recommendation the rule asks for is therefore **not made**, now because
*both* inputs are negative rather than because one is missing.

#### Per-frame result against design §3.3

| §3.3 step | What the design reads | What ran | Verdict |
| :--- | :--- | :--- | :--- |
| 0 | the data context supplies the declaring `LuaNameRef` at a declaration caret | `LuaNameRefImpl` `NAME_REF` `(6,13)` at a `local` caret; `LuaNameRefImpl` at both generic-`for` carets | **HOLDS** for `local` and generic-`for`; **does not hold** for `local function` (leaf — new, see below) |
| 2 | `MemberInplaceRenameHandler.isAvailable` passes: element is a `PsiNameIdentifierOwner` after §3.1 and `isMemberInplaceRenameAvailable` is true | `isAvailableOnDataContext` = **true** at the `local` caret and at both generic-`for` carets, **false** at usage / parameter / `local function` / shadowing carets | **HOLDS** |
| 3 | `LuaRenameProcessor` is the processor, `isInplaceRenameSupported()` is true, and the substitution callback fires with the declaration IDENTIFIER **leaf**; `PsiElementRenameHandler.canRename` does not block it | `forElement` → `LuaRenameProcessor`; `isInplaceRenameSupported` = `true`; 2-arg override → `LeafPsiElement IDENTIFIER (6,13)`; 3-arg call returned normally and the callback **FIRED** with `LeafPsiElement IDENTIFIER (6,13)` | **HOLDS.** Probe (g) positive: the gate design §3.3 step 3 argues cannot fire did not fire |
| 4 | `MemberInplaceRenamer(elementToRename = LuaNameRef, substituted = leaf, editor)` | implied by steps 5-6 succeeding; constructed directly in probe (c) with the same arguments | **HOLDS** |
| 5 | `performInplaceRefactoring` clears `checkLocalScope()` without a `getUseScope()` override | `performInplaceRename(null)` = **`true`**, with **no `getUseScope` override anywhere in the tree** | **HOLDS** |
| 6 | `getNameIdentifier()` returns the IDENTIFIER leaf, `getSelectedInEditorElement` takes the `nameIdentifier` branch, `addVariable` creates `PRIMARY_VARIABLE_NAME`, a template with a current variable exists | `nameIdentifier` = `LeafPsiElement` `LuaTokenType.IDENTIFIER` `(6,13)`, **identical object** to the leaf at the caret; `currentVariableRange` = `(6,13)`; variables `PrimaryVariable (6,13)`, three `OtherVariable (24,31)`; no `LOG.error` (`TestLoggerAssertionError` would have failed the run) | **HOLDS** |
| 7 | `findProblem()` in a non-blocking read action; no problem for a valid identifier | commit proceeded; document changed | **HOLDS** (not exercised adversarially — DR-04's) |
| 8 | `tryRollback()` restores the pre-template text, `getVariable()`/`getSubstituted()` re-derive the target, `performRenameInner(substituted, newName)` runs a full `RenameProcessor` — **"and therefore `LuaCatsParamRenamer`"** | `tryRollback()` **did** restore (document read `local function f(a)` at `refactoringStarted`); a real `RenameProcessor` **did** run (`refactoring.inplace.rename` is `MemberInplaceRenamer.MyRenameProcessor.getRefactoringId()`, `MemberInplaceRenamer.java:409-411`, not a different mechanism); `LuaRenameProcessor.renameElement` **was** reached — **but with `element = LuaNameRefImpl(NAME_REF)`, not the leaf** | **FAILS at its last clause.** See the divergence below |
| 9 | Esc reverts | not driven | **Read, not run, and it stays so — no de-risking task covers it.** `REFACT-07-06` is delivered and verified by **TC-06**, which is in Phase 2's fail-first list and carries a named mutation. See DR-04's record for the disposition and why the deferral is safe |

#### The divergence, with the platform line it is on

`MemberInplaceRenamer.getSubstituted()` (`MemberInplaceRenamer.java:356-372`) does not return the
leaf the step-3 callback produced. By the time `performRefactoringRename` calls it (`:264`) the
original leaf has been invalidated by the template edit plus `tryRollback`, so control reaches the
`mySubstitutedRange` branch at `:367-372`:

```java
return PsiTreeUtil.findElementOfClassAtRange(psiFile, mySubstitutedRange.getStartOffset(),
                                             mySubstitutedRange.getEndOffset(),
                                             PsiNameIdentifierOwner.class);
```

**It re-derives the target by asking for a `PsiNameIdentifierOwner` at that range — and §3.1 is
exactly what makes the Lua composite answer.** Measured on the parameter fixture at range `(36,37)`:

| Probe | Query | Result |
| :--- | :--- | :--- |
| h | `findElementOfClassAtRange(file, 36, 37, PsiNameIdentifierOwner::class)` | `LuaNameRefImpl(NAME_REF) 'a'` |
| h | `LuaDeclarationSite.kindOf(thatElement)` | **null** |
| h | `file.findElementAt(36)` | `LeafPsiElement(LuaTokenType.IDENTIFIER) 'a'` |
| h | `LuaDeclarationSite.kindOf(leaf)` | `PARAMETER` |

`LuaRenameProcessor.renameElement` gates the `---@param` rewrite on
`declarationKind == LuaDeclarationKind.PARAMETER` (`LuaRenameProcessor.kt:229`, `:236` — pre-§3.6 line numbers, from DR-01's measurement on `bbed46c3`; the site is `:255-256` today and now normalises), and
`LuaDeclarationSite.kindOf` returns null for anything whose element type is not `IDENTIFIER`
(`LuaDeclarationSite.kt:44-45`). So on Route B the tag rewrite is never built. Instrumented rows,
one line per `renameElement` call:

```
DR01|probe|renameElement|element=LeafPsiElement(LuaTokenType.IDENTIFIER) text='a' range=(36,37) valid=true kind=PARAMETER newName=count usages=1   <- dialog control (f4)
DR01|probe|renameElement|element=LuaNameRefImpl(NAME_REF)             text='a' range=(36,37) valid=true kind=null      newName=count usages=1   <- Route B (f/f2/g3)
DR01|probe|renameElement|element=LuaNameRefImpl(NAME_REF)             text='counter' range=(6,13) valid=true kind=null newName=total usages=3   <- Route B, local decl (e)
```

**This is not "Route B does not reach `LuaRenameProcessor`".** It reaches it and renames correctly
for everything that is not keyed on `kindOf`. Two Lunar overrides *are* keyed on `kindOf` and are
therefore inert on Route B: `renameElement`'s `---@param` clause (`REFACT-07-09`, the observable
failure) and `findReferences`' file-local scope narrowing (`LuaRenameProcessor.kt:129-133`), which
falls through to `super.findReferences` with the renamer's own `LocalSearchScope` and so changes
cost rather than results. No other `kindOf`-keyed override is on the commit path.

**Ground 1 as design §1 states it — "Route B's commit reaches `LuaRenameProcessor.renameElement`" —
survives. Its consequence for `REFACT-07-09` does not.**

#### Per-probe verdict

| # | Verdict | Measured |
| :--- | :--- | :--- |
| a | **PASS** | `nameIdentifier` is the IDENTIFIER leaf `(6,13)` and is the **same object** as `findElementAt(caretOffset)`. §3.1's body is right |
| b | **PASS**, with two new facts — table below | pairwise disjointness held on all **eight** caret shapes measured; exactly one handler in the registry list in every row |
| b2 | **PASS** | `renameInPlaceViaHandler(LuaInplaceRenameHandler(), "total")` returned `true` from a usage caret; document `local total = 0\nprint(total)\ntotal = total + 1\n`. `REFACT-07-11`'s acceptance is met |
| c | **PASS** | `performInplaceRename(null)` = `true`, **with no `getUseScope()` override**. This is the executed proof design §1 Ground 2 predicted and [[REFACT-01]] Gap 2.20 left open: for Route B, `MemberInplaceRenamer.checkLocalScope()` (`:105-111`) is sufficient and **no `getUseScope` override is needed** |
| d | **PASS** | `currentVariableRange` = `(6,13)`, non-null. Not the corrupting state the prior attempts measured |
| e | **PASS** | `tryInlineRename` returned `true`; `checkResult("local total = 0\nprint(total)\ntotal = total + 1\n")` passed |
| f | **FAIL** | template correct (`PrimaryVariable (36,37)` = the parameter's own identifier, `OtherVariable (46,47)`; the `nameIdentifier` branch at `InplaceRefactoring.java:851-854` answered as §3.5 predicts), code renamed — **tag did not move**. Final document `---@param a number\nlocal function f(count) return count end\n`. Control on the identical fixture through the dialog route (`myFixture.renameElementAtCaret("count")`, which is shipped `LuaCatsParamRenameTest.testParamTagFollowsParameter`) gives `---@param count number\n…`, so the fixture is not at fault |
| g | **PASS** | `RenamePsiElementProcessor.forElement` → `LuaRenameProcessor`; `isInplaceRenameSupported` = `true`; the 3-arg substitution callback **FIRED** with `LeafPsiElement IDENTIFIER (6,13)`; `doRename` then produced a template with `currentVariableRange (6,13)`. `PsiElementRenameHandler.canRename` did not refuse, as §3.3 step 3 argues |
| h | **1 failure, predicted** | `2851 tests completed, 1 failed, 1 skipped`, `BUILD FAILED in 7m 37s`. The one failure is `LuaInplaceRenameTest > testInplaceRenameIsOfferedForAFileLocalDeclaration` (`LuaInplaceRenameTest.kt:54`) — it asserts `isInplaceRenameAvailable` is `true`, which §3.2 sets to `false`. That is the file design §6 **replaces**, so the failure is the design's own predicted consequence, not an unforeseen frame. The other cases in it stay green because each asserts something is withheld — design §6's "guard, not gate" point, confirmed by measurement. `ktlintCheck` **BUILD SUCCESSFUL**, 0 violations, after one `ktlintFormat`-on-the-VM + `rsync`-back pass |

#### Probe (b) — the eight caret shapes

`Lua*Impl` are `net.internetisalie.lunar.lang.psi.impl.*`. `RenameHandler.EP_NAME` held **11**
extensions in every row: DR-05's 10 plus Lunar's new one. In every row the registry returned
**exactly one** handler, and it was the one whose gate is `true`.

| # | Caret | supplied | `MemberInplaceRenameHandler` gate | `LuaInplaceRenameHandler` gate | registry returns |
| :--- | :--- | :--- | :--- | :--- | :--- |
| b1 | `local coun\|ter = 0` | `LuaNameRefImpl` `NAME_REF` `(6,13)` | **true** | false | `MemberInplaceRenameHandler` |
| b2 | usage `print(coun\|ter)` | `LeafPsiElement` `IDENTIFIER` `(6,13)` | false | **true** | `LuaInplaceRenameHandler` |
| b3 | parameter `f(\|a)` | `LeafPsiElement` `IDENTIFIER` `(17,18)` | false | **true** | `LuaInplaceRenameHandler` |
| b4 | generic-`for` `for k\|, v in pairs(t)` | `LuaNameRefImpl` `NAME_REF` `(4,5)` | **true** | false | `MemberInplaceRenameHandler` |
| b4b | generic-`for` `for ke\|y, value` | `LuaNameRefImpl` `NAME_REF` `(4,7)` | **true** | false | *(gate only)* |
| b4c | generic-`for` 2nd var `for key, val\|ue` | `LuaNameRefImpl` `NAME_REF` `(9,14)` | **true** | false | *(gate only)* |
| b5 | `local function hel\|per()` | `LeafPsiElement` `IDENTIFIER` `(15,21)` | false | **true** | `LuaInplaceRenameHandler` |
| b6 | `local config = 1` ⏎ `local con\|fig = 2` | `LeafPsiElement` `IDENTIFIER` **`(6,12)`** | false | **true** | `LuaInplaceRenameHandler` |

Three things this settles that were Read, not run:

1. **The generic-`for` kind is served by §3.2's predicate, not by §3.5's handler.** All three
   placements — after the first variable, inside a multi-character first variable, and inside the
   second variable — supply the declaring `LuaNameRef`. `b4b`/`b4c` were added because `b4`'s caret
   landed on the `,` leaf (`for k|,`), so without a multi-character fixture the row could not be
   distinguished from a `findElementAt`-off-by-one artifact.
2. **The `local function` kind is served by §3.5's handler, not by §3.2's predicate** — its context
   supplies the IDENTIFIER **leaf**, exactly as a parameter's does. Design §3.5's scope statement
   currently names "a **usage** caret and a **parameter declaration** caret"; the measured scope is
   those two **plus a `local function` name caret**. Design §3.2's "Which declaration carets clause
   (2) actually serves" is likewise incomplete: measured, clause (2) serves `local`, global and
   generic-`for`; §3.5 serves parameter and `local function`.
3. **DR-05 Observation 1's unmeasured half is confirmed, and it is a live hazard.** From the second
   of two same-file `local config` declarations the context supplies the **first** declaration's
   leaf at `(6,12)` while the caret is at `(23,29)`, Lunar's handler **accepts** it, and the
   registry hands the rename to Lunar. Renaming from the shadowing declaration's caret would
   therefore anchor a template on the **earlier** declaration. Not caused by REFACT-07 — the same
   element is supplied on the unchanged tree — but REFACT-07 is what makes an in-place template
   start there. Filed as Gap 2.21 below.

#### What this does NOT settle

- **DR-02 is not discharged.** `RenameHandler.EP_NAME` held 11 extensions in the unit-test
  application; a full GoLand loads more. The "exactly one handler" column above is evidence *for*
  §3.5's availability invariant in the unit-test app, not the live enumeration DR-02 owns.
- **DR-03 has since run (2026-08-26)** and §4's consumer audit is executed; the decision rule's
  "collapse REFACT-07 into a REFACT-01 phase" recommendation is still not made, because DR-03 found
  three consumers outside `com.intellij.refactoring.rename` whose behaviour moved.
- **Probe (f)'s residual is now decided, and the decision is recorded in the design rather than
  here.** The response is **not** to make the template reach `renameElement` with the leaf — the
  element `MemberInplaceRenamer.getSubstituted()` produces is the platform's to choose — but to
  normalise inside `renameElement`, which is where the repo's own `identifierLeafOf` contract says
  the leaf comes from. Design §2.5 and §3.6 carry it, `LuaRenameProcessor` moves from REUSED
  UNCHANGED to EXTENDED in design §1's prior-art table, and plan task 3.6 delivers it. The framing
  that binds a later reader: **this is a latent `REFACT-01` defect**, not an in-place workaround —
  every caller that hands `renameElement` a composite loses the `---@param` clause, and REFACT-07 is
  only the first that does.
- **The scope of that fix was established by enumeration, not by assumption.**
  `grep -rn "kindOf(" src/main/kotlin/ | grep -v "fun kindOf"` returns every
  `LuaDeclarationSite.kindOf` caller; design §3.6's audit table gives a per-site verdict for all of
  them, and that table is the list — do not restate its membership here. The sites it marks
  **AFFECTED** are `renameElement` (`LuaRenameProcessor.kt:229` — pre-§3.6 line numbers, from DR-01's measurement on `bbed46c3`; the site is `:255-256` today and now normalises, the observable failure) and
  `findReferences` (`:130`, cost only — DR-01 measured identical results, because
  `MemberInplaceRenamer` supplies its own `LocalSearchScope`). Every other site either normalises at
  the call, admits the composite explicitly, or is not reached with a different element than before
  §3.1. `findReferences` is fixed with the same normalisation and **carries no test case and no
  mutant**, because no measured path shows an observable difference and inventing an assertion to
  fill that row is what the planning bar forbids.
- **DR-01's success does not discharge DR-02, DR-03 or step 9**, and none of the amendments above
  may be read as doing so. The eleven `RenameHandler.EP_NAME` extensions are a unit-test-application
  number; §4's consumer audit is unrun; and Esc-restore has **no spike** — it is TC-06's, per the
  disposition recorded under DR-04.

### REFACT-07-00-02 (DR-02) — Does the registry select Route B, and only Route B, and is Lunar's handler the only handler it has to choose between?

**Status: EXECUTED 2026-08-26** on scratch branch `spike/refact-07-dr-02` off `9d54df30`, and its
decision rule is applied. **Both halves ran** — the mechanism in the unit-test application, and
probe (1) in a running GoLand, which registers **34** `renameHandler` extensions where the
unit-test application registers 11. The record is below, after the decision rule.

Design §1 Ground 3 is the reason `isInplaceRenameAvailable` must return `false`, and it is a
**read** of `RenameHandlerRegistry.java:104-119` against a checkout that is not the build Lunar
runs (design §1, Provenance).

This task carries a second question the plan cannot answer by reading at all. `REFACT-07-02` and
design §3.5 both need the premise *"no registered `renameHandler` other than the platform's two
in-place handlers is available for a Lua editor caret"*, because `doGetRenameHandlers`'s early
return is over the whole extension list and not over the two handlers §3.5 compares
(`RenameHandlerRegistry.java:106-113`). Design §3.5 grounds that premise for `platform/` only. The
list a running GoLand registers is not in that checkout, and it is what the registry actually reads.

**Setup.** With DR-01's edits in place, and separately with `isInplaceRenameAvailable` restored
to the shipped predicate, evaluate for a `local` **declaration** caret
(`local coun<caret>ter = 0\nprint(counter)\n`), for a **usage** caret
(`local counter = 0\nprint(coun<caret>ter)\n`), and for a **parameter declaration** caret
(`---@param a number\nlocal function f(<caret>a) return a end\n`). The third is not a duplicate of
the first: DR-05 measured its data context supplying the IDENTIFIER **leaf** (probe `d`), so it is
the declaration caret at which *Lunar's* handler is the available one and at which a second
registered handler would collide with Lunar's rather than with the platform's:

```
val context = DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)

// (1) the enumeration the premise is about — EVERY registered handler, and which claim this caret
RenameHandler.EP_NAME.extensionList.map { it to it.isRenaming(context) }
    → record every handler's class name, and its verdict, for both carets

// (2) what the registry does with them
RenameHandlerRegistry.getInstance().getRenameHandlers(context)  → size and element classes
RenameHandlerRegistry.getInstance().getRenameHandler(context)   → class

// (3) whether Lunar's handler is a removal-loop target — it must not be
LuaInplaceRenameHandler() is MemberInplaceRenameHandler
```

Probe (1) is the one this task exists for and it is not optional. Design §3.5 grounds the refusal of
each handler registered under `platform/` by reading its gate; **it cannot enumerate what a GoLand
build registers**, and `REFACT-07-02` rests on that enumeration. `RenameHandler.EP_NAME` is declared
at `RenameHandler.java:13`, and `doGetRenameHandlers` reads exactly this list at
`RenameHandlerRegistry.java:106`, so probe (1) measures the registry's own input.

**The context is built from the editor component, never with an injected
`CommonDataKeys.PSI_ELEMENT`.** That is the whole point of the probe: a registry answer computed
from an element the plan chose is not evidence about what the registry does when the user presses
<kbd>Shift+F6</kbd>. This executes both halves of design §3.5 — the pairwise availability invariant,
and the extension-list premise the invariant is not sufficient for. The usage and parameter carets
are the positions where a second handler would collide with *Lunar's* rather than the platform's.

**Decision rule:**

- **probe (1) shows only the platform's two in-place handlers claiming either caret** → the premise
  holds against this build; record the full list as the evidence `REFACT-07-02`'s acceptance
  criterion requires.
- **probe (3) is `true`** → the plan was not implemented as designed. §2.3 requires
  `LuaInplaceRenameHandler` to implement `RenameHandler` directly, precisely so that the removal
  loop at `RenameHandlerRegistry.java:114-119` has no Lunar entry to delete. Fix the class before
  reading anything else from this task; every other branch below assumes it is `false`.
- **one handler at the declaration caret, a `MemberInplaceRenameHandler`, with the predicate at
  `false`** → Ground 3 holds; TC-02 is written as specified.
- **one handler at the usage caret, and it is a `LuaInplaceRenameHandler`** → §3.5's availability
  invariant holds and TC-04 is written as specified.
- **one handler at the parameter caret, and it is a `LuaInplaceRenameHandler`** → §3.5's scope
  covers the parameter kind as design §3.2 ("Which declaration carets clause (2) actually serves")
  states, and TC-09 is written as specified. A `MemberInplaceRenameHandler` there instead would mean
  DR-05's probe `d` does not reproduce with the §3.1 edit present, which is a blocker: TC-09's
  driver and design §5 Example 2 both follow from it. Record and stop.
- **two handlers at a caret, one of them Lunar's and one of them the platform's
  `MemberInplaceRenameHandler`** → the *pairwise* invariant is false: some element is both a
  `PsiNameIdentifierOwner` and a Lua IDENTIFIER leaf, which §3.1 and §3.5 say cannot happen. Record
  both classes and stop; §3.5's gate is wrong and needs re-deriving before Phase 3. This is the only
  branch a narrower Lua gate fixes.
- **two handlers at a caret, and the second is neither of those** → the *premise* is false, not the
  invariant, and no Lua-side gate can fix it: the second handler is another plugin's and Lunar does
  not control its availability. Record its class and its `isRenaming` reason. Then:
  - Confirm from probe (3) that Lunar's handler survived the removal loop. If it did, the delivered
    behaviour is `RenameElementAction`'s chooser popup (`RenameElementAction.java:111-122`) with
    Lua's rename among the options — degraded but reachable. Record it, weaken `REFACT-07-02`'s
    Status to the measured behaviour rather than asserting it, add the observation to
    `human-verification-checklists.md`, and proceed: this is Risk 1.9's accepted residual.
  - If Lunar's handler did **not** survive, that contradicts probe (3) and is a blocker; stop.
- **two handlers with the predicate restored** → confirms the hazard is real and TC-02's mutation
  is reachable. Record the raw list.
- **`getRenameHandler` returns a `VariableInplaceRenameHandler` with the predicate at `false`** →
  something other than Lunar's providers is making it available; find it before Phase 2, because
  Route A would then be silently in play.
- **a chooser dialog appears in the unit fixture** → `isUnitTestMode` is not taking the
  `myRenameHandlerSelectorInTests` branch (`RenameHandlerRegistry.java:79-81`); the test must drive
  `getRenameHandlers` rather than `getRenameHandler`, and TC-02 is rewritten accordingly.

**Blocks**: Phase 2's TC-02, and Phase 3's task 3.3. **Estimate**: 1-2 h.

#### Result — EXECUTED 2026-08-26, both halves, on scratch branch `spike/refact-07-dr-02` (`ae75d450`, `8bbb7032`) off `9d54df30`

**Status: EXECUTED, not partially executed.** The GoLand enumeration probe (1) exists in this
task because a unit-test application cannot answer it, and it was obtained: a running GoLand
2026.1.3 registers **34** `renameHandler` extensions where the unit-test application registers
**11**. Both applications, both predicate states, all three carets. **The premise holds, and
Ground 3 is confirmed by the running registry.**

DR-01's edits were **replayed**, not re-derived: `bbed46c3..9d54df30` is docs-only, so the four
files (`LuaBaseElements.kt`, `LuaRefactoringSupportProvider.kt`, `LuaInplaceRenameHandler.kt`, the
one `renameHandler` element) were written verbatim out of `spike/refact-07-dr-01` onto a branch cut
from `9d54df30`. A rebase was rejected because `main` already carries `dr-01-evidence/` from
`ee692f1a`, so replaying `abb55194`/`fb02fa8e` would have conflicted on those doc files for no gain;
`git diff spike/refact-07-dr-01 -- src/` is empty on the DR-02 branch, which is the check that makes
the replay equivalent to the rebase.

GoLand 2026.1.3 (`platformType = GO`, `platformVersion = 2026.1.3`), builder VM `lunar-builder`.
Raw rows — every handler, every verdict, per caret, per application, per predicate state — in
`dr-02-evidence/measured-rows.txt`; the two harnesses in the same directory as `*.kt.txt`,
**removed from `src` after the run** (they print and assert nothing, which is the defect Risk 1.2 is
about). Unit runs: `test --tests *Dr02RegistryProbeTest* --rerun --no-build-cache` →
`BUILD SUCCESSFUL in 29s` / `in 32s`, JUnit XML `tests="3" skipped="0" failures="0" errors="0"` at
`2026-08-26T11:22:35.169Z` and `2026-08-26T11:23:33.260Z`. GoLand runs: `./gradlew runIde` on
Xvfb `:99`, plugin load confirmed fresh in the sandbox log at `11:25:29,676` and `11:31:46,145`
(`Loaded custom plugins: lunar`), carets driven with `xdotool` `ctrl+g <line:column>` and the probe
fired with a keyboard shortcut so that the editor never lost focus.

**Every data context was built from the editor component**
(`DataManager.getInstance().getDataContext(editor.contentComponent)`, and in the IDE action from
`CommonDataKeys.EDITOR`'s `contentComponent`). **Nothing injected `CommonDataKeys.PSI_ELEMENT`
anywhere**, which is the whole point of the task and the defect that makes `LuaInplaceRenameTest`
(`:106-116`) unable to settle any of this.

#### Table 1 — probe (1), the enumeration the task exists for

The three carets are the spec's: a `local` **declaration** caret (`local coun|ter = 0`), a **usage**
caret (`print(coun|ter)`), and a **parameter declaration** caret
(`---@param a number` / `local function f(|a) return a end`). In the IDE the fixture is one file and
the carets are offsets 9, 27 and 69.

**Every handler not named in the table below returned `isRenaming` = `false` at all three carets, in
both applications, in both predicate states.** The table lists only the rows that were ever `true`.

| Application | `EP_NAME` size | Caret | `isInplaceRenameAvailable` | Handlers whose `isRenaming` is **true** |
| :--- | :--- | :--- | :--- | :--- |
| unit-test app | 11 | declaration | `false` (§3.2) | `MemberInplaceRenameHandler` |
| unit-test app | 11 | usage | `false` (§3.2) | `LuaInplaceRenameHandler` |
| unit-test app | 11 | parameter | `false` (§3.2) | `LuaInplaceRenameHandler` |
| unit-test app | 11 | declaration | shipped predicate | `VariableInplaceRenameHandler`, `MemberInplaceRenameHandler` |
| unit-test app | 11 | usage | shipped predicate | `LuaInplaceRenameHandler` |
| unit-test app | 11 | parameter | shipped predicate | `LuaInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | declaration | `false` (§3.2) | `MemberInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | usage | `false` (§3.2) | `LuaInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | parameter | `false` (§3.2) | `LuaInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | declaration | shipped predicate | `VariableInplaceRenameHandler`, `MemberInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | usage | shipped predicate | `LuaInplaceRenameHandler` |
| **GoLand 2026.1.3** | **34** | parameter | shipped predicate | `LuaInplaceRenameHandler` |

**The 34 GoLand registrations, in extension order.** The unit-test application's 11 are rows 1-6,
8-10, 16 and 34 — the platform core plus Lunar's own; DR-05 measured **10** on the unchanged tree,
and the eleventh is §2.4's registration, which is the consistency check that the probe is reading the
list it thinks it is. The **23 a bundled GoLand adds** are every other row, and they are the reason
design §3.5's `platform/`-only grounding could not close this question by reading.

| # | Handler | # | Handler |
| :--- | :--- | :--- | :--- |
| 1 | `com.intellij.refactoring.rename.PlainDirectoryRenameHandler` | 18 | `com.intellij.sql.refactoring.rename.inplace.SqlInplaceRenameHandler` |
| 2 | `com.intellij.refactoring.rename.FileDumbRenameHandler` | 19 | `com.intellij.sql.refactoring.rename.SqlRenameHandler` |
| 3 | `com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler` | 20 | `com.intellij.lang.javascript.refactoring.rename.JSRenameWrongRefHandler` |
| 4 | `com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler` | 21 | `com.intellij.lang.javascript.refactoring.rename.JSInplaceRenameHandler` |
| 5 | `com.intellij.platform.renameProject.RenameProjectHandler` | 22 | `com.intellij.lang.javascript.refactoring.rename.JSShorthandPropertyRenameHandler` |
| 6 | `com.intellij.platform.renameProject.ProjectFolderRenameHandler` | 23 | `com.intellij.lang.typescript.refactoring.TypeScriptMemberInplaceRenameHandler` |
| 7 | `org.intellij.plugins.markdown.ui.projectTree.MarkdownFileRenameHandler` | 24 | `com.intellij.lang.typescript.refactoring.TypeScriptProxyImplicitMemberRenameHandler` |
| 8 | `com.intellij.xml.refactoring.SchemaPrefixRenameHandler` | 25 | `com.intellij.psi.css.actions.rename.CssClassOrIdRenameHandler` |
| 9 | `com.intellij.xml.refactoring.XmlTagRenameHandler` | 26 | `com.intellij.psi.css.actions.rename.CssCustomPropertyRenameHandler` |
| 10 | `com.intellij.polySymbols.refactoring.PsiSourcedPolySymbolRenameHandler` | 27 | `com.intellij.psi.css.actions.rename.CssColorValueRenameHandler` |
| 11 | `com.intellij.sh.backend.rename.ShRenameHandler` | 28 | `com.intellij.kubernetes.helm.ChartsRenameHandler` |
| 12 | `org.editorconfig.language.codeinsight.refactoring.EditorConfigRenameHandler` | 29 | `com.intellij.kubernetes.references.KubernetesLabelValueRenameHandler` |
| 13 | `com.intellij.swagger.core.refactoring.rename.SwUrlPathRenameHandler` | 30 | `com.intellij.kubernetes.references.KubernetesLabelKeyRenameHandler` |
| 14 | `com.intellij.swagger.core.refactoring.rename.SwYamlPathVariableRenameHandler` | 31 | `com.intellij.kubernetes.references.KubernetesResourceRenameHandler` |
| 15 | `com.intellij.database.psi.DbRenameHandler` | 32 | `com.intellij.kubernetes.references.KubernetesMapResourceEntryRenameHandler` |
| 16 | **`net.internetisalie.lunar.refactoring.rename.LuaInplaceRenameHandler`** | 33 | `com.intellij.kubernetes.helm.gotpl.TemplateRenameHandler` |
| 17 | `com.goide.refactor.rename.GoRenameHandler` | 34 | `com.intellij.platform.lsp.impl.rename.LspRenameHandler` |

#### Table 2 — probes (2) and (3), what the registry does with them

`supplied` is `PsiElementRenameHandler.getElement(context)`, recorded to confirm each caret is the
shape DR-05 measured and therefore that the probe drove the position it names.

| Caret | Predicate | `supplied` | `getRenameHandlers` size / classes | `getRenameHandler` |
| :--- | :--- | :--- | :--- | :--- |
| declaration | `false` | `LuaNameRefImpl` `NAME_REF` `(6,13)` | **1** — `MemberInplaceRenameHandler` | `MemberInplaceRenameHandler` |
| usage | `false` | `LeafPsiElement` `IDENTIFIER` `(6,13)` | **1** — `LuaInplaceRenameHandler` | `LuaInplaceRenameHandler` |
| parameter | `false` | `LeafPsiElement` `IDENTIFIER` (`'a'`) | **1** — `LuaInplaceRenameHandler` | `LuaInplaceRenameHandler` |
| declaration | shipped | `LuaNameRefImpl` `NAME_REF` `(6,13)` | **1** — **`VariableInplaceRenameHandler`** | **`VariableInplaceRenameHandler`** |
| usage | shipped | `LeafPsiElement` `IDENTIFIER` `(6,13)` | **1** — `LuaInplaceRenameHandler` | `LuaInplaceRenameHandler` |
| parameter | shipped | `LeafPsiElement` `IDENTIFIER` (`'a'`) | **1** — `LuaInplaceRenameHandler` | `LuaInplaceRenameHandler` |

Identical in both applications, row for row.

**Probe (3): `LuaInplaceRenameHandler() is MemberInplaceRenameHandler` is `false`**, in both
applications and every run. §2.3's base-type choice holds: the removal loop at
`RenameHandlerRegistry.java:114-119` has no Lunar entry to delete, so the decision rule's
"fix the class before reading anything else" branch does not fire and every branch below is read
against a `false` probe (3), as the rule requires.

#### The row where probe (1) and probe (2) disagree, and why that matters

At the declaration caret with the shipped predicate, **probe (1) shows two claiming handlers and
probe (2) returns one**. That is the removal loop at `RenameHandlerRegistry.java:114-119` deleting
the `MemberInplaceRenameHandler` entry and `break`ing — measured, not read. Two consequences:

1. **The decision rule's "two handlers at a caret" branches are phrased against `getRenameHandlers`,
   where the two never coexist.** Only probe (1) sees both. A future re-run that reads `size` alone
   will see `1` in every row of this task and conclude, wrongly, that nothing was ever contended.
2. **The chooser-popup branch is unreachable here**, and its `isUnitTestMode` sub-branch
   (`RenameHandlerRegistry.java:79-81`) was never exercised: `getRenameHandler` returned a single
   class, with no exception and no selector, in all twelve rows.

#### Which decision-rule branches fired

| Branch | Fired? | Measured |
| :--- | :--- | :--- |
| probe (1) shows only the platform's two in-place handlers claiming either caret → **the premise holds against this build** | **YES** | No third-party handler claims any of the three carets. In GoLand, 31 of the 34 registrations answered `false` everywhere; the three that ever answered `true` are `VariableInplaceRenameHandler`, `MemberInplaceRenameHandler` and Lunar's own — the two the premise names, plus the one §3.5 introduces |
| probe (3) is `true` → the plan was not implemented as designed | no | probe (3) is `false` |
| one handler at the declaration caret, a `MemberInplaceRenameHandler`, with the predicate at `false` → **Ground 3 holds; TC-02 is written as specified** | **YES** | both applications |
| one handler at the usage caret, and it is a `LuaInplaceRenameHandler` → **§3.5's availability invariant holds; TC-04 as specified** | **YES** | both applications |
| one handler at the parameter caret, and it is a `LuaInplaceRenameHandler` → **§3.5's scope covers the parameter kind; TC-09 as specified** | **YES** | both applications. The blocker sub-branch — a `MemberInplaceRenameHandler` there, meaning DR-05 probe `d` does not reproduce with §3.1 present — **did not fire** |
| two handlers at a caret, one Lunar's and one the platform's `MemberInplaceRenameHandler` → the *pairwise* invariant is false | no | never observed, in either application, in either predicate state |
| two handlers at a caret, the second neither of those → the *premise* is false | no | never observed |
| **two handlers with the predicate restored → the hazard is real and TC-02's mutation is reachable** | **YES** | `VariableInplaceRenameHandler` **and** `MemberInplaceRenameHandler`, at the declaration caret, in both applications. Raw list in `measured-rows.txt` |
| `getRenameHandler` returns a `VariableInplaceRenameHandler` **with the predicate at `false`** → something other than Lunar's providers is making it available | no | with the predicate at `false` it returns `MemberInplaceRenameHandler`. It returns `VariableInplaceRenameHandler` only with the predicate *restored*, which is the branch above, not this one |
| a chooser dialog appears in the unit fixture | no | `getRenameHandler` returned one class in every row; the removal loop had already collapsed the contended row to one |

#### What the measurement settles

1. **`REFACT-07-02`'s premise HOLDS against this build.** No registered `renameHandler` other than
   the platform's two in-place handlers — and Lunar's own, which §3.5 introduces — is available at a
   Lua declaration, usage or parameter caret. This is the enumerated evidence
   `REFACT-07-02`'s acceptance criterion asks for, obtained from the list
   `doGetRenameHandlers` itself reads (`RenameHandlerRegistry.java:106`). `requirements.md`'s
   Premises row and design §1's evidence row are updated from "not established" to this result.
   **Its scope is this build**: GoLand 2026.1.3 with the plugin set a `runIde` sandbox loads. A
   different IDE, or a user's third-party plugin, registers a different list, and Risk 1.9 remains
   the accepted residual for that — DR-02 measures the premise, it does not make it invariant.
2. **Design §1 Ground 3 is CONFIRMED by the running registry**, in a real GoLand and not only by
   reading `RenameHandlerRegistry.java:104-119`. With `isInplaceRenameAvailable` at the shipped
   predicate, the declaration caret is claimed by **two** handlers and the registry hands it to
   `VariableInplaceRenameHandler` — Route A — after deleting the `MemberInplaceRenameHandler` entry
   Route B needs. With the predicate at `false`, `VariableInplaceRenameHandler` stops claiming, one
   handler remains, and it is the `MemberInplaceRenameHandler`. **`isInplaceRenameAvailable` must be
   `false` for Route B to be reachable at all**, exactly as §3.2 sets it.
3. **§3.5's availability invariant is executed, not read.** DR-05 measured its structural premise on
   the unchanged tree; this measures the handler-availability consequence with §3.1's interface and
   §2.3's handler present. At no caret, in either application, in either predicate state, were
   Lunar's handler and `MemberInplaceRenameHandler` both available. Design §1's evidence-table row
   for that invariant is no longer **Split**.
4. **§2.3's base-type choice is vindicated by measurement.** Probe (3) is `false`, so Lunar's
   handler is not a removal-loop target — and the removal loop was observed *firing* on the same
   caret in the same run, which is what makes the `false` load-bearing rather than incidental.
5. **The unit-test application is not a substitute for the IDE, and now there is a number for it.**
   11 registrations against 34. Every conclusion above is identical across both, which is a
   corroboration and not a licence: the 23 extra handlers were checked, individually, and each
   returned `false`.

#### Nothing here contradicts the design

Every branch that fired is a "proceed as planned" branch. TC-02, TC-04 and TC-09 are written as
their rows specify; §3.5 ships; §2.3 stands. **No design document is amended on the strength of this
task beyond flipping the evidence labels the design itself nominated DR-02 to settle** (design §1's
three rows, `requirements.md`'s Premises row).

#### Two notes on the specification as written

- **Probe (3) does not compile as written.** `LuaInplaceRenameHandler() is MemberInplaceRenameHandler`
  compares unrelated types and Kotlin rejects it; the probe was run as
  `LuaInplaceRenameHandler() as Any is MemberInplaceRenameHandler`. Same question, same answer — but
  a future re-run should expect to insert the cast. The class hierarchy was recorded alongside it, so
  the answer does not rest on the `is` check alone.
- **The setup names three carets and the decision rule says "either caret" in two places.** All
  three were measured everywhere; the rule's two-caret phrasing is a leftover from before DR-05
  established that the parameter caret is a third, distinct case.

### REFACT-07-00-03 (DR-03) — The `PsiNameIdentifierOwner` consumer audit

**Status: PARTIALLY EXECUTED 2026-08-26** on `spike/refact-07-dr-02`'s tree (`8bbb7032`) against
`f6148451`, and its decision rule is applied. **The third and fifth branches fire; the fourth — the
one that reopens design §3.1 Alternative B — does not, and must not be treated as if it had.** The
record is below, after the decision rule.

**The residual, named, so the per-consumer verdict table below cannot be read as covering it.**
DR-03 is **partially** executed. Four rows were carved out here; **two of them are now closed and
two remain open**, and the table records which is which rather than leaving all four looking
equally unsettled:

- **Closed 2026-08-27** by `implementation-plan.md` task 5.1a — `RecentPlacesFeatures.findDeclaration`
  and `VcsFeatureProvider`. Both branches do flip, and neither moves completion item order.
  **Measured-inert, not assumed-inert.**
- **Still open, and not closable on this platform** — `MinimapStructureMarkerCollector` and
  `MinimapHoverHitCheck`. The classes do not exist in the build under test, so these are
  unexecutable rather than unattempted. They are what keeps `REFACT-07-12` at `Partial`.

| Row | Status, and why | Where it goes |
| :--- | :--- | :--- |
| `MinimapStructureMarkerCollector` | **Blocked by provenance.** The class is absent from GoLand 2026.1.3: the provenance checkout (ic master `5ba8ab1cfe37`) carries a minimap rewrite with `model/` and `hover/` packages; the shipped build is branch 261 and has neither. No fixture and no VNC session can observe it — this is **Risk 1.3** in its strongest form, a design row describing code that does not exist in the build under test | re-opens only if Lunar moves to a platform that ships the rewrite. Design §4 marks both rows NOT APPLICABLE rather than leaving them looking audited |
| `MinimapHoverHitCheck` | same, paired with the row above | same |
| `RecentPlacesFeatures.findDeclaration` | **MEASURED 2026-08-27 — branch flips, effect inert.** The branch is genuinely newly taken: a probe transcribing `findDeclaration` verbatim answers `null` on base and `LuaNameRefImpl` on treatment, over four caret kinds. The **effect is nil**, and for two independently executed reasons. (1) *The stored value is identical on both arms.* `findDeclaration`'s result is consumed at exactly one place — `declaration?.getChildrenNames() ?: emptyList()` (`RecentPlacesFeatures.kt:72-73`) — and a `LuaNameRef`'s only child is the IDENTIFIER **leaf**, which `ASTDelegatePsiElement.getChildren()` omits, so `childCount=0` and `getChildrenNames()` is `[]` on treatment, exactly the `?: emptyList()` base takes. `putChildren(emptyList())` is a no-op both ways. (2) *No feature value could reorder Lua anyway.* Reordering needs `shouldReRank()`, i.e. a `com.intellij.completion.ml.model` provider matching the language; enumerating every such registration in the GoLand 2026.1.3 build under test yields **SQL, Go, JavaScript, TypeScript, Shell and nothing else** — the EP is a plain `ExtensionPointName`, not a `LanguageExtension`, so there is no `language=""` catch-all. `MLCompletionWeigher`, which does run, returns `DummyComparable` whose `compareTo` is a constant `0`. The **live A/B agrees**: two bytecode-discriminated sandboxes (`javap` shows the supertype absent then present; jar md5 `d1244987…` vs `0429cf13…`, both reporting `0.18.0`) driven by one unedited script (md5 `c6002f0a…`) over an equalised history produced not merely the same order but **pixel-identical frames** | **SETTLED — no longer outstanding.** Evidence: `phase-5-live-evidence/task-5-1a-ranking-measurement.txt`, `33-ranking-ab-baseline-completion-order.png`, `34-ranking-ab-treatment-completion-order.png`, `31-`/`32-ranking-ab-*-recent-locations.png` |
| `VcsFeatureProvider` | **MEASURED 2026-08-27 — same EP, same ranking consequence, same inert verdict**, settled by reason (2) above: with no ranking model matching Lua in the build under test, no value this provider emits can reorder a Lua lookup. **Stated precisely, because it is weaker than the row above:** its `psi is PsiNameIdentifierOwner` branch (`VcsFeatureProvider.kt:40`) was *not* exercised as its own live arm — the fixture was not a VCS-modified tracked file, so `ChangeListManager.getChange(file)` returned no change and the guarded block was not entered on either arm. It is settled by the ranking gate, not by its own fixture | **SETTLED — no longer outstanding**, with the scope limit above recorded rather than glossed. Evidence: `phase-5-live-evidence/task-5-1a-ranking-measurement.txt` (Strand 5 and the SCOPE section) |

Nothing else is outstanding: every other row in design §4 carries a verdict below.

`REFACT-07-12` asks whether the interface change moves behaviour outside rename. Design §4 gives a
per-consumer argument, and **every row of it was Read, not run** before this task. The interface is
consulted by far more than rename, and the newly-taken branches are the ones a green suite would not
notice.

**Setup.** With DR-01's edits in place, for each consumer design §4 lists:

1. Confirm the citation still resolves in the compiled platform (by symbol, per design §1
   Provenance).
2. **Read the rows out of design §4's table, do not copy them here.** For every row whose Effect
   column begins **"Newly taken"**, exercise the feature it gates against a Lua fixture and compare
   the observed behaviour with the same fixture on `f6148451`. Record a verdict under the row's own
   consumer name, so the evidence and the table are joined by name rather than by position. A row
   this task skips is a row `REFACT-07-12` was signed off without.
3. For every row marked **"Inert"** or **"Newly eligible, unreachable"**, re-confirm the stated
   ground for unreachability — the absent registration, the absent subclass — with the grep the row
   names. These need no fixture; they need their premise checked, because the premise is what the
   row's verdict rests on.
4. Run the full unit suite on both commits and diff the results, not just the pass count.

For the rows that are cheapest live rather than in a fixture — the minimap, the completion
commands, <kbd>Ctrl+D</kbd> — use `.agents/skills/verify-in-ide` and attach screenshots to
`dr-03-evidence/`.

**Decision rule:**

- **every "Newly taken" row observably unchanged, and every unreachability ground re-confirmed** →
  `REFACT-07-12` is satisfied; record the evidence per consumer, by consumer name, and proceed.
- **an unreachability ground no longer holds** — Lunar has since registered the thing the row said
  it does not — → that row is promoted to "Newly taken" and exercised before the verdict.
- **a row changed and the change is desirable** (`AbstractRenameActionCommandProvider` newly
  offering Rename at a Lua name is the anticipated case) → record it as a deliberate outcome and
  add it to `human-verification-checklists.md`. It is not a defect.
- **a row changed and the change is not desirable** → this is the outcome that reopens design §3.1
  Alternative B (gate `getNameIdentifier()` on `LuaDeclarationSite.kindOf`). Weigh the specific
  consumer against §3.1's argument for a structural accessor; do not apply Alternative B
  reflexively, and record the reasoning either way.
- **a citation no longer resolves** → design §4's enumeration was taken from a different build.
  Re-enumerate against the compiled platform before trusting any row.

**Blocks**: Phase 3. **Estimate**: 4-6 h, the largest of the de-risking tasks.

#### Result — EXECUTED 2026-08-26, on `spike/refact-07-dr-02`'s tree (`8bbb7032`) against `f6148451`

**Status: EXECUTED, with two rows recorded NOT RUN and one row's premise found false.**
**`REFACT-07-12` does NOT hold as written.** Three consumers change observable behaviour, and one
of them — <kbd>Ctrl+D</kbd>'s resting caret — is user-visible in the editor on every Lua line whose
first token is a name. The decision rule's **third and fifth branches both fire**; the fourth does
not, because none of the three changes is undesirable.

GoLand 2026.1.3 (`platformType = GO`, `platformVersion = 2026.1.3`), builder VM `lunar-builder`.
Raw rows in `dr-03-evidence/` — `probe-observations-{base,treatment}.txt` and the
`probe-observations.diff` the verdicts rest on. Harnesses `Dr03ConsumerAuditProbeTest.kt.txt` and
`Dr03RegistrationProbeTest.kt.txt`, **removed from `src/test` after the run**; they print and assert
nothing, which is the defect Risk 1.2 is about.

**DR-01's edits were reused, not re-derived**, from `spike/refact-07-dr-02` (`8bbb7032`), for DR-04's
reasons; `git diff spike/refact-07-dr-01 spike/refact-07-dr-02 -- src/` was re-run here and is empty
both before and after. Both trees were checked out into throwaway `git worktree`s so the primary
worktree never left `main`; both were removed afterwards, no branch was created, and `git diff --
src/` on `main` is empty. **No `CommonDataKeys.PSI_ELEMENT` was injected anywhere** — every
observation is a direct call on PSI from `myFixture`, a platform entry point given only a
`PsiFile`/`Editor`, or a bytecode/registration fact about the shipped build.

##### Step 1 first, because it changes what the rest of the table means

Design §4's enumeration was taken from `intellij-community` `master` at `5ba8ab1cfe37` (2026-05-04).
**GoLand 2026.1.3 is branch 261, cut before that**, and the difference is not cosmetic:

- **`MinimapStructureMarkerCollector` and `MinimapHoverHitCheck` DO NOT EXIST in the shipped build.**
  ic master carries a wholesale minimap rewrite — `model/`, `hover/`, `layers/`, `geometry/`,
  `scene/`, 120-odd files. GoLand 2026.1.3 ships the *old* minimap: 36 classes under
  `com/intellij/ide/minimap`, with **no `model/` and no `hover/` package at all**
  (`intellij.platform.ide.impl.jar`). Both rows cite code that is not in the platform Lunar
  compiles and ships against.
- **`AbstractActionCompletionCommand` and `AbstractCopyFQNCompletionCommand` are FILE names, not
  class names.** The classes those files declare are `ActionCommandProvider` (`:59`) and
  `ActionCompletionCommand` (`:205`), and `AbstractCopyFQNCompletionCommandProvider`. All three are
  present in `intellij.platform.lang.impl.jar`. The citations resolve once read as file paths.
- **`ViewStructureCompletionCommandProvider` has moved package** — the shipped class is
  `com.intellij.codeInsight.completion.command.commands.ViewStructureCompletionCommandProvider`,
  not `com.intellij.platform.structureView.backend.completion.commands.…`.
- **`PolySymbolUsageSearcher` no longer carries the reference.** The class exists, but in the
  shipped build the `PsiNameIdentifierOwner` constant lives in `PolySymbolUsageQueries`.
- **`CompletionPolicy` is absent from the IDE distribution** — it is `platform/testFramework`, a
  separate artifact, which is consistent with the row's own "test framework only".

Every other citation resolves by symbol. **So the fifth branch of the decision rule fires**, and the
audit was re-enumerated against the compiled platform rather than trusted: every class in every jar
of GoLand 2026.1.3 was scanned for the constant-pool entry `com/intellij/psi/PsiNameIdentifierOwner`
— **144 classes, 125 of them top-level** (`dr-03-evidence/compiled-platform-consumers.txt`).

That re-enumeration surfaces **five consumers design §4 does not carry**, because §4's grep was
scoped to `platform/` in a source checkout and therefore excluded every bundled plugin, and because
two of them changed name. Three are irrelevant to Lua (`PsiViewerDialog`, an IDE-Features-Trainer
lesson, `mcpserver`'s `Psi_utilKt`); **two are not**, and they are recorded as new rows below.

##### Per-consumer verdict, joined to design §4 by consumer NAME

`Δ` is the observed difference between `f6148451` and `8bbb7032` on the same fixture.

| §4 consumer (row's own name) | §4's Effect | DR-03 verdict | Evidence |
| :--- | :--- | :--- | :--- |
| `IdentifierUtil.getNameIdentifier` | Same answer, cheaper path | **CONFIRMED, Δ none.** Called on all 12 `LuaNameRef`s of the fixture: identical `LeafPsiElement` at an identical range on both trees, every one. | `probe-observations.diff` — 12 unchanged lines |
| `SafeDeleteProcessor.isInside` | Newly taken; same verdict | **CONFIRMED, Δ none.** Premise measured, not read: `PsiTreeUtil.isAncestor(nameRef, nameIdentifier, /*strict=*/true)` is `true` for all 12, so the `if` body at `:118-120` cannot run. End-to-end Safe Delete run three ways — unused local via the IDENTIFIER leaf (statement removed), via the `LuaNameRef` composite (no handler, document unchanged), and a *used* local (`ConflictsInTestsException`, document unchanged). All three byte-identical on both trees. | `probe-observations.diff` |
| `PsiElement2Declaration.getIdentifyingElement` | Newly taken; narrows the declaration range | **CONFIRMED, Δ none.** Exercised through the public `targetSymbols(file, offset)`, which reaches `createFromDeclaredPsiElement` and then picks by *minimal* range (`chooseByRange`) — the exact place a narrowed range could change the answer. Four carets (`counter`, `alpha`, `helper`, `unused`): same symbol count and same extracted element on both trees. The row's "identical ranges" ground is measured directly: `rangesEqual=true` for all 12 `LuaNameRef`s. | `probe-observations.diff` |
| `NamedElementDuplicateHandler` | Newly taken | **CHANGED. Δ = the caret's resting position after <kbd>Ctrl+D</kbd>.** Document text identical in all three cases; the caret is not. Line `counter = helper(1, 2)`: base `151`, treatment `129`. Line `print(counter)`: base `158`, treatment `144`. Line `local counter = 0`: `35` on both. Detail below. | `probe-observations.diff` |
| `RelatedItemLineMarkerProvider` | **Inert** — "Lunar registers no `RelatedItemLineMarkerProvider`" | **GROUND FALSE — row PROMOTED and exercised. CHANGED.** Lunar registers `LuaOverrideLineMarkerProvider : RelatedItemLineMarkerProvider()` (NAV-05) at `plugin.xml:730-732`, confirmed live in the runtime EP enumeration. Detail below. | `runtime-registration-enumeration.txt`, `probe-observations.diff` |
| `BookmarkManager.getNameIdentifier` path | Newly taken | **CHANGED, but with no consumer anywhere in the shipped build.** `findElementBookmark` on a `LuaNameRef` returns `null` on base and the bookmark on treatment. Its first line is `if (!(element instanceof PsiNameIdentifierOwner)) return null`, so the change is exactly the grant. **It is unreachable**: a bytecode scan of every class in GoLand 2026.1.3 finds `findElementBookmark` referenced only by `BookmarkManager` itself, and `BookmarkManager` is `@Deprecated`. No user-visible effect. | `probe-observations.diff`; bytecode scan |
| `NonAsciiCharactersInspection` | Newly taken | **CONFIRMED, Δ none, and the row's ground measured rather than argued.** `local café = 1` lexes as `IDENTIFIER['caf']` + `BAD_CHARACTER['é']`; `naïve` as `IDENTIFIER['na']` + `BAD_CHARACTER['ï']` + `IDENTIFIER['ve']`. A non-ASCII character can never be *inside* a Lua `IDENTIFIER` leaf, so the fixture the branch needs is not producible from any Lua text. **The inspection itself was not run** — the lexing result makes it moot. | `probe-observations.diff` |
| `RedundantSuppressInspectionBase` | Inert | **GROUND RE-CONFIRMED.** `grep -rl SuppressionUtil src/main/kotlin` → 0 files; no `lang.inspectionSuppressor` in `plugin.xml` (the one `suppress` hit is the word in an EDITOR-01 comment). | grep |
| `ChangeSignatureAction` | Newly taken at the availability check only | **CONFIRMED, Δ none.** `myFixture.testAction(ChangeSignatureAction())` at a `local function` name and at a local: `enabled=false visible=false` on both trees. Ground re-confirmed at the source: `isAvailableOnElementInEditorAndFile` returns `false` unless `getChangeSignatureHandler(...)` is non-null, which is `RefactoringSupportProvider.getChangeSignatureHandler()`; `LuaRefactoringSupportProvider().changeSignatureHandler` is `null`, measured at runtime. | `probe-observations.diff` |
| `MemberInplaceRenamer` | Intended | **Out of scope here** — Route B's own frames, executed by DR-01 and DR-04. |  |
| `MemberInplaceRenameHandler` | Intended | **Out of scope here** — ditto. |  |
| `InplaceRefactoring.getNameIdentifier` | Intended | **Out of scope here** — ditto. |  |
| `VariableInplaceRenamer` | Unreached on Route B | **Out of scope here** — DR-04 settled the conflict channel this row is about. |  |
| `ModCommandExecutorImpl` | Newly taken *for any Lunar quick fix implemented as a `ModCommand`* | **UNREACHABLE — ground re-confirmed.** `grep -rl ModCommand src/main/kotlin` → 0 files. The row is conditional on a `ModCommand` existing; none does. Not exercised against a fixture, because there is nothing to exercise. | grep |
| `MinimapStructureMarkerCollector` | Newly taken | **NOT RUN — the consumer does not exist in the shipped platform.** See step 1. | class index |
| `ViewStructureCompletionCommandProvider` | Newly taken | **UNREACHABLE — ground established, not merely grepped.** `com.intellij.codeInsight.completion.command.provider` is a **`LanguageExtensionPoint`** (`CompletionExtensionPoints.xml:78-81`), and the shipped provider is registered per language — `language="JAVA"` and `language="kotlin"` only. Lunar registers zero `codeInsight.completion.command.provider`. | `runtime-registration-enumeration.txt` |
| `AbstractActionCompletionCommand`, `AbstractCopyFQNCompletionCommand`, `AbstractRenameActionCommandProvider` | Newly taken — and "`AbstractRenameActionCommandProvider` newly offering Rename at a Lua name is a **desired** consequence" | **UNREACHABLE. The anticipated desirable outcome DOES NOT MATERIALISE.** All three are `abstract`; every concrete subclass in the tree is language-specific (`JavaRenameActionCommandProvider`, `KotlinRenameActionCommandProvider`, `PropertiesRenameActionCommandProvider`) and reaches the editor only through the same language-keyed EP above. Lunar subclasses none and registers none, so **"Rename" will not appear in the `.` command menu at a Lua name** as a consequence of this feature. This is a correction to design §4, not a defect in the code. | `runtime-registration-enumeration.txt`, ic subclass grep |
| `CompletionPolicy` | Test framework only | **GROUND RE-CONFIRMED.** Absent from the IDE distribution; `grep -rl "propertyBased\|CompletionPolicy\|MadTestingUtil" src/test src/integrationTest` → 0 files. | grep |
| `VcsCodeVisionLanguageContext` | Newly taken *if* a code-vision provider ever runs over Lua; unreachable today | **UNREACHABLE — ground re-confirmed, and by a more precise EP than the row names.** The row's grep (`grep -c codeVision plugin.xml`) is right by luck: the *provider* (`VcsCodeVisionProvider`) **is** registered globally and does load. What gates the branch is `vcs.codeVisionLanguageContext`, a **`LanguageExtensionPoint`** with entries for JAVA, kotlin and Python only. `VcsCodeVisionProvider` returns `READY_EMPTY` when neither `forLanguage(fileLanguage)` nor an `isCustomFileAccepted` context matches, so `computeEffectiveRange` — the frame with the cast — is never reached for a Lua file. | `runtime-registration-enumeration.txt`, `VcsExtensionPoints.xml:244` |
| `MinimapHoverHitCheck` | Newly taken | **NOT RUN — the consumer does not exist in the shipped platform.** See step 1. | class index |
| `ModPsiUpdater.rename` / `PsiUpdateImpl` | Newly eligible, unreachable | **GROUND RE-CONFIRMED.** `grep -rl ModCommand src/main/kotlin` → 0 files. (In the shipped build the reference sits on the inner `PsiUpdateImpl$ModPsiUpdaterImpl`; the citation resolves.) | grep |
| `NamingConvention` family | Inert | **GROUND RE-CONFIRMED.** `grep -rl NamingConvention src/main/kotlin` → 0 files. | grep |
| `AbstractInplaceIntroducer` | Inert | **GROUND RE-CONFIRMED.** `LuaIntroduceVariableHandler : RefactoringActionHandler` (`LuaIntroduceVariableHandler.kt:36`); zero references to `AbstractInplaceIntroducer` or `InplaceVariableIntroducer` anywhere in `src/main/kotlin`. | grep |
| `RenameChangeInfo` | Inert | **GROUND RE-CONFIRMED.** Same ground as `ChangeSignatureAction`: no `LanguageChangeSignature` handler; `LuaRefactoringSupportProvider().changeSignatureHandler` is `null` at runtime. | runtime |
| `InlineOptionsDialog` | Inert | **GROUND RE-CONFIRMED.** `grep -c inlineActionHandler plugin.xml` → 0. | grep |
| `PolySymbolUsageSearcher` | Inert | **GROUND RE-CONFIRMED.** `grep -rl PolySymbol src/main/kotlin` → 0 files; no `polySymbol` registration. (Citation drifted — see step 1.) | grep |
| `CreateFromTemplateAction.moveCaretAfterNameIdentifier` | Inert | **GROUND RE-CONFIRMED.** `grep -rl CreateFromTemplateAction src/main/kotlin` → 0 files; no `createFromTemplate` registration. | grep |

**Rows found by re-enumerating the compiled platform, which design §4 does not carry:**

| Consumer (not in §4) | Where | DR-03 verdict |
| :--- | :--- | :--- |
| `SpellcheckingStrategy.getTokenizer` / `PsiIdentifierOwnerTokenizer` | `intellij.spellchecker.jar` | **NOT TAKEN, Δ none — but only because Lunar overrides.** `SpellcheckingStrategy:91` is `if (element instanceof PsiNameIdentifierOwner) return PsiIdentifierOwnerTokenizer.INSTANCE;`, and Lunar **does** register a strategy (`EDITOR-02`, `plugin.xml:750-752`). `LuaSpellcheckingStrategy.getTokenizer` overrides without calling `super`, so line 91 is never reached: all 12 `LuaNameRef`s route to `LuaIdentifierTokenizer` on **both** trees. Had EDITOR-02 not existed, every Lua name would newly have been tokenised as an identifier and spellchecked. |
| `RenameTo` (spellchecker quick fix) | `intellij.spellchecker.jar` | **NEWLY TAKEN, Δ none.** Reachable because `LuaIdentifierTokenizer` passes `useRename=true`. The shipped `RenameTo.getNameRelativeRange(PsiNamedElement)` branches on the interface at bytecode offset 16-20 — verified by `javap -c` on the shipped class, because the shipped signature differs from ic master's. Measured on both trees: the range is `(0,6)` from a `LuaNameRef` and `(0,6)` from the IDENTIFIER leaf. Identical, for the reason the `PsiElement2Declaration` row gives. |
| `RecentPlacesFeatures` (`findDeclaration`) and `VcsFeatureProvider` | `completionMlRanking.jar` | **NEWLY TAKEN, effect NOT RUN.** Both are `<completion.ml.elementFeatures language="">` — **empty language, i.e. every language**, so unlike the completion-*command* rows there is no per-language gate to stop them. `RecentPlacesFeatures.findDeclaration` walks up to the first `PsiNameIdentifierOwner`: on base it walks to the `PsiFile` and returns `null` for every Lua element; on treatment it stops at the enclosing `LuaNameRef`. `VcsFeatureProvider` additionally needs a VCS-modified file. The branch change is determinate from the measured premise; **the downstream effect — completion-ranking feature values, hence completion item ORDER — was not measured**, because the completion-ML-ranking plugin is not loaded in the unit-test container (`completion.ml.elementFeatures` enumerates `total=0` there) and its ordering is model- and experiment-dependent. This is the one row where DR-03 is genuinely partial. |
| `PsiViewerDialog`, `mcpserver.util.Psi_utilKt`, `SearchEverywhereLesson` | dev tool / MCP plugin / IFT | **Not user-facing for Lua.** Recorded so the re-enumeration is complete. |

##### The three changes, in detail

**1. `NamedElementDuplicateHandler` — <kbd>Ctrl+D</kbd> leaves the caret somewhere else.** The
handler's branch runs only with **no selection**, over the caret's whole **line**
(`NamedElementDuplicateHandler:44-56`), so a sub-name selection can never reach it — the first
version of this probe used one and measured nothing, which is why the harness now drives three
whole-line carets. When the line's first non-blank element has a `LuaNameRef` ancestor contained in
the line, `findNameIdentifier` now returns that name's identifier, and the handler moves the caret
to it *before* duplicating (`if (name != null && !name.getTextRange().containsOffset(caretOffset))`).

The **document is byte-identical on both trees** in all three cases. What differs is where the caret
comes to rest: with the caret at the end of `counter = helper(1, 2)` it lands at offset `151` (end of
the duplicated line) on base and `129` (on the duplicated line's `counter`) on treatment; for
`print(counter)`, `158` versus `144`. `local counter = 0` is unchanged at `35`, because the line
starts with the `local` keyword, whose ancestors contain no `LuaNameRef`.

This is the platform's designed behaviour for named elements — land on the name so it can be renamed
— and it is **desirable**, so the decision rule's third branch fires, not the fourth. But it is a
real, user-visible change **outside rename**, on a keystroke unrelated to rename, and
`REFACT-07-12`'s "changes no observable behaviour" does not survive it as written.

**2. `RelatedItemLineMarkerProvider` — the row's premise is false, and the promoted row changes.**
`RelatedItemLineMarkerGotoAdapter` (registered platform-wide as `<gotoRelatedProvider>`) is the only
caller of `collectNavigationMarkers(elements, result, /*forNavigation=*/true)`, the overload
carrying the interface branch. With the grant, a `LuaNameRef` in the ancestor chain contributes its
identifier as an extra element to collect over, and `LuaOverrideLineMarkerProvider` answers for it.

Measured on a `---@class Impl : Base` fixture with two `run` methods: given the `LuaNameRef`
composite, **base returns 0 related items and treatment returns 1** (the `function Base:run() end`
override target). Given the IDENTIFIER **leaf**, both return 1.

**The editor-driven action is unaffected**, and that was measured rather than assumed:
`GotoRelatedSymbolAction.getContextElement` is `psiFile.findElementAt(caretOffset)`, always a leaf,
and `GotoRelatedSymbolAction.getItems(file, editor)` returns `n=1` on both trees. The change is
reachable only when something supplies a composite `PSI_ELEMENT` without an editor. Also
**desirable** where it does fire — Navigate → Related Symbol finding the override is the feature's
own point — so again the third branch, not the fourth.

**3. `BookmarkManager.findElementBookmark` — changed and dead.** Covered in the table: real Δ, zero
callers in the entire shipped distribution, `@Deprecated` class. No user-visible effect.

##### Step 4 — the suites, compared by RESULT

| Commit | Result line | JUnit XML aggregate |
| :--- | :--- | :--- |
| base `f6148451` | `BUILD SUCCESSFUL in 7m 20s` | `tests=2851 skipped=1 failures=0 errors=0` |
| treatment `8bbb7032` | `BUILD FAILED in 7m 36s` | `tests=2851 skipped=1 failures=1 errors=0` |

Both runs used `test --rerun --no-build-cache`. The single failure is the **predicted** one,
`LuaInplaceRenameTest > testInplaceRenameIsOfferedForAFileLocalDeclaration`, which asserts the
opposite of what design §3.2 sets; the single skip is `LuaCompletionTest` on both.

**The test-NAME sets were compared, not the counts.** Every `<testcase name=… classname=…>` from
every `TEST-*.xml` was extracted and `LC_ALL=C` sorted: 2851 names on each side and `diff` **empty**.
The two runs executed the identical set of tests, so the matching totals are not masking a different
selection. Checksums and per-class counts in `dr-03-evidence/suite-results-comparison.txt`.

**The extraction command, so a later baseline comparison is a one-liner rather than a re-derivation.**
`suite-results-comparison.txt` recorded the sha256 but not the recipe that produced it, which made
the hash unusable for anything but a copy of the original run. Run this in the run's own
`build/test-results/test`:

```sh
grep -h "<testcase " TEST-*.xml \
  | sed -n 's/.*name="\([^"]*\)" classname="\([^ ]*\).*/\2#\1/p' \
  | LC_ALL=C sort | sha256sum
```

It reproduces `1fe20dcfd6ec0edec82d13c1d7b55d0a01eeaf5af13ea2c837d8cdeb0b6b47c2` for the
`f6148451` baseline — **verified 2026-08-26 during Phase 2**, byte-for-byte identical output to the
script DR-03 actually ran, over a 1515-name corpus of the same Gradle JUnit XML. Two details are
load-bearing and neither is obvious from the hash alone: the classname capture is `[^ ]*`, which
stops at the space before Gradle's `time="…"` attribute and therefore **keeps the closing quote** —
so the hashed lines read `net.internetisalie.lunar.FooTest"#testBar`, and a recipe that strips it
produces a different hash over the same tests. And the trailing `.*` is what tolerates that
attribute at all; a pattern anchored to `"` at the end matches nothing.

**And that is the finding this step exists for.** All three behaviour changes above are invisible to
those 2851 tests — the caret after <kbd>Ctrl+D</kbd>, a Related-Symbol item, a bookmark anchor.
Nothing in the suite asserts on any of them, on either commit. The Premises row "A green full suite
means the PSI change is safe" is re-confirmed as **NOT true**, now with three named instances.

##### What `REFACT-07-12` should be read as, having been measured

`REFACT-07-12` as written — "changes no observable behaviour of Find Usages, Safe Delete, identifier
highlighting, Go to Declaration, the Structure View or the inspections that branch on the interface"
— **is satisfied for every behaviour it names.** All six were exercised and all six are byte-identical
across the two commits (Find Usages: 3 usages at identical ranges; Safe Delete: three drives;
identifier highlighting via `IdentifierUtil`: 12 calls; Go to Declaration: same resolved leaf;
Structure View: identical tree; the interface-branching inspections: `NonAsciiCharactersInspection`
unreachable, `RedundantSuppressInspectionBase` and the `NamingConvention` family unregistered).

The claim it makes in its **title** — "The PSI change is inert outside rename" — **does not hold.**
Three consumers change, none of them in the enumerated list, two of them desirably and one of them
inertly. Whether that reopens design §3.1 Alternative B is **the supervisor's call, not DR-03's**;
DR-03 records that the fourth branch of the decision rule (an undesirable change) did **not** fire,
which is the input that call needs. What DR-03 does assert is that §4's table cannot be signed off
as-is: two rows cite code absent from the shipped platform, one row's unreachability ground is false,
four rows' "Newly taken" is unreachable for want of a Lunar registration, one row's most quotable
consequence does not materialise, and five consumers are missing from it.

##### Coverage — exactly what was not run

- **`MinimapStructureMarkerCollector`, `MinimapHoverHitCheck` — NOT RUN.** Unexecutable as
  specified: the classes are not in GoLand 2026.1.3. No fixture and no live VNC session can
  observe them. This is why DR-03 is **partially executed with respect to step 2**.
- **`RecentPlacesFeatures` / `VcsFeatureProvider` — branch established, effect NOT RUN.** Reason in
  the table.
- **`NonAsciiCharactersInspection` — the inspection was not executed**, only the lexing that makes
  its fixture unconstructible.
- The rows the spike's specification leaves outside both step 2 and step 3 — `IdentifierUtil`
  ("Same answer, cheaper path"), the three "Intended" rows, `VariableInplaceRenamer` ("Unreached on
  Route B") and `CompletionPolicy` ("Test framework only") — were handled anyway: `IdentifierUtil`
  and `CompletionPolicy` are in the table above; the four rename-route rows belong to DR-01/DR-04.
- **The completion popup, the minimap and code vision were settled in-process rather than over VNC.**
  For the minimap there is nothing to drive. For the other two the gating fact is a
  `LanguageExtensionPoint` with no Lua entry, and a runtime enumeration of that EP inside the
  fixture is *stronger* evidence than a screenshot, which could not distinguish "not registered"
  from "wrong trigger typed". Recorded as a deliberate substitution.

### REFACT-07-00-04 (DR-04) — How does a conflict surface on Route B, and can a test see it?

**Status: EXECUTED 2026-08-26** on `spike/refact-07-dr-02`'s tree (`8bbb7032`), and its decision
rule is applied. **The first branch fires.** The record is below, after the decision rule.

Design §3.3 step 8 states that conflicts reach the user through `RenameProcessor`'s conflicts
dialog rather than `VariableInplaceRenamer`'s inline popup, because
`MemberInplaceRenamer.findCollision()` returns null (`:113-117`). That is **read**. TC-08 must
assert on whatever channel actually fires.

**Setup.** With DR-01's edits, drive TC-08's fixture
(`local coun<caret>ter = 0\nlocal total = 1\nprint(counter + total)\n`) to commit with `"total"`
and record: which exception (if any) is thrown, its type and message; whether the document changed;
and whether `LuaRenameConflictDetector` ran at all (a temporary log line, removed afterwards).

**Decision rule:**

- **`BaseRefactoringProcessor.ConflictsInTestsException` with Lunar's capture message, document
  unchanged** → TC-08 asserts that exception type and the message key
  `refactoring.rename.conflict.capture` (`LuaBundle.properties:154`).
- **an exception, but the document already changed** → the template's edits were not rolled back
  before the conflict fired. That is a `REFACT-07-06`/`REFACT-07-08` violation and a blocker;
  investigate `MemberInplaceRenamer.tryRollback` (`:253`) ordering before Phase 2.
- **no exception and the rename applied** → `findCollisions` is not being reached from this path.
  Compare against the dialog path on the same fixture; if the dialog refuses and in-place does not,
  `REFACT-07-08` is unmet and the route needs a `findCollision()` override that delegates to
  `LuaRenameProcessor.findCollisions`.
- **an exception whose message is not Lunar's** → a platform conflict fired first; record which,
  because it changes what the user reads.

**Blocks**: Phase 2's TC-08. **Estimate**: 1-2 h.

#### Result — EXECUTED 2026-08-26, on `spike/refact-07-dr-02`'s tree (`8bbb7032`)

**Status: EXECUTED. The decision rule's FIRST branch fires** —
`BaseRefactoringProcessor.ConflictsInTestsException` carrying Lunar's capture message, with the
document unchanged. **The second branch — an exception with the document already changed — does
NOT fire**, and that is measured directly rather than inferred from the final text: probe (d)
records the template's edit landing, `tryRollback` removing it, and only then the conflict
surfacing.

GoLand 2026.1.3 (`platformType = GO`, `platformVersion = 2026.1.3`), builder VM `lunar-builder`.
Raw rows for all six runs in `dr-04-evidence/measured-rows.txt`; the harness as
`Dr04ConflictChannelProbeTest.kt.txt`, **removed from `src/test` after the run** — it prints and
asserts nothing, which is the defect Risk 1.2 is about. The two sanctioned log lines and the
mutation are in `instrumentation.diff` and `instrumentation-note.txt`, reverted with
`git show HEAD:<path> > <path>`.

**DR-01's edits were reused, not re-derived.** `spike/refact-07-dr-02` was chosen over
`spike/refact-07-dr-01` because it is one docs commit behind `main` where DR-01's branch is off
`bbed46c3`, and because DR-02 already proved the two trees equal;
`git diff spike/refact-07-dr-01 spike/refact-07-dr-02 -- src/` was re-run here and is empty, both
before the run and again after the revert. The tree was checked out into a throwaway `git worktree`
so that the primary worktree never left `main`; that worktree and its branch were removed
afterwards, so the tree DR-04 ran on is exactly `spike/refact-07-dr-02`'s and no new branch exists.

**No `CommonDataKeys.PSI_ELEMENT` was injected anywhere.** Every data context is
`DataManager.getInstance().getDataContext(myFixture.editor.contentComponent)`, and probe (a) drives
`CodeInsightTestUtil.tryInlineRename`, which builds its own context from the editor
(`CodeInsightTestUtil.java:244`).

**Runs** — `tooling/gce-builder/gce-builder.sh run "test --tests *Dr04ConflictChannelProbeTest* --rerun --no-build-cache"`:

| Run | What it measured | Result line | JUnit XML |
| :--- | :--- | :--- | :--- |
| 1 | probes a / b / c, unmutated | `BUILD SUCCESSFUL in 33s` | `tests="3" skipped="0" failures="0" errors="0"` |
| 2 | **MUTANT M1** — TC-08's named mutation | `BUILD SUCCESSFUL in 32s` | `tests="3" skipped="0" failures="0" errors="0"` |
| 3 | probe d, **no drain after `gotoEnd`** — inconclusive, retained as a negative about the probe | `BUILD SUCCESSFUL in 34s` | `tests="4" skipped="0" failures="0" errors="0"` |
| 4 | probe d with the drain | `BUILD SUCCESSFUL in 33s` | `tests="4" skipped="0" failures="0" errors="0"` |
| 5 | adds `ConflictsInTestsException.getMessages()` | `BUILD SUCCESSFUL in 29s` | `tests="4" skipped="0" failures="0" errors="0"` |
| 6 | final — adds the throwing frame's stack trace, and is taken with the instrumentation **not** applied | `BUILD SUCCESSFUL in 32s` | `tests="4" skipped="0" failures="0" errors="0"` |

Run 6 doubles as a control on the instrumentation: production code is untouched there, and the
exception, its message and the unchanged document all reproduce. Runs 1-5 carry the two log lines.

The suite counts are the probe's own, not a regression gate: **DR-04 changed no shipping code**, and
after the revert the tree is byte-identical to the one DR-01 ran the full suite on. The full suite
and `ktlintCheck` were therefore **not re-run** — DR-01's record carries them for this exact tree
(`2851 tests completed, 1 failed, 1 skipped`, the predicted `LuaInplaceRenameTest` failure;
`ktlintCheck` `BUILD SUCCESSFUL`, 0 violations). That is a reuse of a prior measurement, stated so a
reader does not mistake it for one taken here.

#### Table 1 — what the spike asks for, on TC-08's fixture

Fixture `local coun<caret>ter = 0` ⏎ `local total = 1` ⏎ `print(counter + total)`, committed with
`"total"`. Probe (b) is the dialog control on the identical fixture; probe (c) is the same driver
and fixture with a **non-colliding** new name, which is what makes (a)'s refusal attributable to the
conflict rather than to a driver that never started a template.

| # | Driver | Exception type | Exception message | Document after | `LuaRenameConflictDetector` ran? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| a | `CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), "total", …)` — **Route B** | `com.intellij.refactoring.BaseRefactoringProcessor$ConflictsInTestsException` | capture message ⏎ shadow message | **unchanged** — `local counter = 0\nlocal total = 1\nprint(counter + total)\n` | **YES** — `kind=LOCAL_VARIABLE newName=total identifier='counter' usages=1`, produced **2** collisions |
| b | `myFixture.renameElementAtCaret("total")` — dialog control | same class | same two messages, same order | **unchanged** | YES — same two collisions |
| c | Route B, `"tally"` (no collision) | **NONE** | — | `local tally = 0\nlocal total = 1\nprint(tally + total)\n` | YES — produced **0** collisions |

`tryInlineRename` did not return in (a): it **threw**, so its `true` return was never reached. In
(c) it returned `true`.

The exception's messages, measured both ways:

- `getMessage()` = `Renaming to 'total' would bind a usage of 'counter' to a different declaration that is already visible here.\nThe renamed declaration would shadow this existing reference, changing which value it reads.`
- `getMessages()` = a 2-element collection, `[0]` the capture message and `[1]` the shadow message,
  and `getMessages().contains(LuaBundle.message("refactoring.rename.conflict.capture", "total", "counter"))` is **`true`** — the
  bundle-formatted string matches character for character.

**The element `findCollisions` receives on Route B is the composite, and it does not matter here.**
`LuaRenameProcessor.findCollisions` was entered with `LuaNameRefImpl(NAME_REF)` on Route B and with
`LeafPsiElement(LuaTokenType.IDENTIFIER)` on the dialog path — the divergence DR-01 measured for
`renameElement`. Neither early return fired on either path: `findCollisions` opens with
`LuaDeclarationSite.identifierLeafOf(element)` (`LuaRenameProcessor.kt:162`), whose `LuaNameRef`
branch (`LuaDeclarationSite.kt:61`) normalises the composite to its identifier before `kindOf` is
asked. So the `kindOf`-on-the-composite defect that makes `renameElement`'s `---@param` clause inert
(DR-01, design §3.6) **does not reach conflict detection**, and `REFACT-07-08` needs nothing from
§3.6. That is measured — the instrumentation logs a distinct row on each early return, precisely so
that "the detector did not run" and "`findCollisions` was never called" could not be confused.

#### Table 2 — probe (d): the edits ARE rolled back, and when the exception arrives

Probe (d) drives the template by hand — `MemberInplaceRenameHandler().doRename(supplied, …)`, write
the new name into `currentVariableRange`, `gotoEnd(false)` — so that the document can be read at
each step instead of only at the end. `supplied` was `LuaNameRefImpl(NAME_REF)` and
`currentVariableRange` was `(6,13)`, which confirms `requirements.md` TC-08's row that this caret
drives `MemberInplaceRenameHandler` on DR-05 probe `a`'s shape.

| Step | Document | Exception |
| :--- | :--- | :--- |
| after the template edit | `local total = 0\nlocal total = 1\nprint(total + total)\n` — **two bindings spelled `total`** | — |
| after `gotoEnd(false)` returns | *still* the template text | **none** |
| `findCollisions` / detector run | — | — (2 collisions produced) |
| after `NonBlockingReadActionImpl.waitForAsyncTaskCompletion()` | `local counter = 0\nlocal total = 1\nprint(counter + total)\n` — **the original** | **`ConflictsInTestsException`**, both messages |
| after a full drain, and after `Disposer.dispose` | the original | none further |

**Two things follow, and the second is a trap for TC-08.**

1. **`tryRollback` works and branch 2 does not fire.** The corrupt two-`total` text exists only
   inside the template and is gone before the caller sees the conflict. `REFACT-07-06` and
   `REFACT-07-08` are not violated on this path, and there is no `BUG-468`-class finding here.
2. **Observation — the conflict surfaces on the DRAIN, not on `gotoEnd`, and this is not a
   defect.** The rollback happens; the corrupt text never reaches the user. Between `gotoEnd(false)`
   returning and the non-blocking read action completing, however, the document transiently holds
   the corrupt text and no exception has been thrown. *Evidence*: run 3 measured exactly that state
   — retained as a negative — and run 4 measured it resolving after the drain; both are rows in
   `dr-04-evidence/measured-rows.txt`. *Is REFACT-07 expected to change it?* **No** — it is the
   platform's asynchronous commit, not anything this feature adds. *Why it is recorded anyway*: *any
   future test that inspects the document between `gotoEnd` and the drain will see an invalid
   intermediate state, and could easily be written against it by mistake* — it would then assert the
   opposite of `REFACT-07-06`/`REFACT-07-08` and pass. Every driver in this plan drains;
   `CodeInsightTestUtil.tryInlineRename` hides the window because it drains at
   `CodeInsightTestUtil.java:265` inside its own `try`, which is why probe (a) sees the exception
   escape the driver. **A TC-08 that drives the template by hand and reads the document before
   draining would assert on the corrupt intermediate and see no exception** — run 3 in
   `measured-rows.txt` is exactly that mistake, retained as evidence of it.

#### Which decision-rule branch fired

| Branch | Fired? | Measured |
| :--- | :--- | :--- |
| **`ConflictsInTestsException` with Lunar's capture message, document unchanged → TC-08 asserts that exception type and the message key `refactoring.rename.conflict.capture`** | **YES** | probe (a). The bundle-formatted capture message is `getMessages()[0]` and a substring of `getMessage()` |
| an exception, but the document already changed → template edits not rolled back; a `REFACT-07-06`/`REFACT-07-08` violation and a blocker | **no** | probe (d) records the rollback happening before the exception reaches the caller |
| no exception and the rename applied → `findCollisions` is not being reached | **no** | `findCollisions` was entered, the detector ran, and both a capture and a shadow collision were produced |
| an exception whose message is not Lunar's → a platform conflict fired first | **no** | both messages are Lunar's, from `LuaBundle.properties:154` and `:155` |

#### What TC-08 must assert, in the terms the measurement produced

1. **Drive** `CodeInsightTestUtil.tryInlineRename(MemberInplaceRenameHandler(), "total", editor, leaf)`
   — or, if driven by hand, **drain with `NonBlockingReadActionImpl.waitForAsyncTaskCompletion()`
   before reading anything**. Without the drain the case measures the corrupt intermediate.
2. **Assert the driver throws `BaseRefactoringProcessor.ConflictsInTestsException`.** It escapes the
   driver; the call cannot be made bare.
3. **Assert the message CONTAINS the capture message** — `LuaBundle.message("refactoring.rename.conflict.capture", "total", "counter")`,
   as a member of `getMessages()` or a substring of `getMessage()`. **Equality against the whole
   message is wrong**: this fixture also trips the shadow rule, so the exception carries two
   messages, and asserting the exact string would couple TC-08 to the shadow rule's wording.
4. **Assert the document is the unchanged fixture** — `myFixture.checkResult(TC08_FIXTURE)`.
5. It does **not** need to assert that a template started. `implementation-plan.md` Phase 2's rule
   that every document-layer case whose expected text is the unchanged file must also assert a
   template started exists because `tryInlineRename` returns `false` silently when none does
   (Risk 1.2, second residual). TC-08 is exempt **by measurement, not by argument**: with no
   template there is no commit, no `RenameProcessor`, and therefore no
   `ConflictsInTestsException` — assertion 2 cannot pass with the feature absent. Probe (c) is the
   positive control that the template does start on this fixture.

#### A claim in `requirements.md`'s TC-08 row is REFUTED by measurement

TC-08's row names its mutation as "delete the capture rule from `LuaRenameConflictDetector.collisions`
(`LuaRenameConflictDetector.kt:120-131`)" and states the consequence: *"no collision is reported,
the rename proceeds, and the document gains two bindings spelled `total`."* **That was run (MUTANT
M1, run 2) and every clause of the consequence is false.**

| Under MUTANT M1 | Stated | Measured |
| :--- | :--- | :--- |
| collisions reported | none | **1** — the shadow rule still fires on this fixture |
| the rename | proceeds | **refused**, still by `ConflictsInTestsException` |
| the document | gains two bindings spelled `total` | **unchanged** |
| the message | — | the shadow message **only**; the capture message is gone |

The fixture `local coun<caret>ter = 0` ⏎ `local total = 1` ⏎ `print(counter + total)` matches **two**
of the four rules, not one: C1 capture (the usage of `counter` would bind to the visible `total`)
and C2 shadow (`target.kind.isFileLocal` is true for `LOCAL_VARIABLE`, so `shadows(target)` runs).

**Consequence, which is the point of running the mutation rather than reading it:** a TC-08 that
asserted only the exception type and the unchanged document would be **green under its own named
mutation** — a test that cannot fail, which is Risk 1.2's shape exactly. Assertion 3 above is what
makes the mutation reachable: with the capture rule deleted the message is the shadow message alone
and the containment assertion reddens.

`requirements.md`'s TC-08 row is therefore inconsistent with measurement in its Mutation column.
**It is not amended here** — rewriting a mutation-proof obligation is a planning decision and the
supervisor's call. The measured replacement is stated above.

#### Does design §3.3 step 8 survive?

**Yes, and the frame is measured rather than read.** The exception's stack trace, captured in the
compiled GoLand 2026.1.3 build, is step 8's own call chain:

```
com.intellij.refactoring.rename.RenameProcessor.preprocessUsages(RenameProcessor.java:180)
com.intellij.refactoring.BaseRefactoringProcessor.doRun(BaseRefactoringProcessor.java:354)
com.intellij.refactoring.rename.RenameProcessor.doRun(RenameProcessor.java:149)
com.intellij.refactoring.rename.inplace.MemberInplaceRenamer$MyRenameProcessor.doRun(MemberInplaceRenamer.java:417)
com.intellij.refactoring.BaseRefactoringProcessor.run(BaseRefactoringProcessor.java:739)
com.intellij.refactoring.rename.inplace.MemberInplaceRenamer.performRenameInner(MemberInplaceRenamer.java:316)
com.intellij.refactoring.rename.inplace.MemberInplaceRenamer.lambda$performRefactoringRename$1(MemberInplaceRenamer.java:283)
com.intellij.openapi.command.impl.CoreCommandProcessor.executeCommand(…)
```

Read bottom-up, that is design §3.3 step 8 verbatim: `performRefactoringRename` at `:283` calling
`performRenameInner`, defined at `:309-317` and running the processor at `:316`, and the processor
being a real `RenameProcessor` (through `MyRenameProcessor`, the inner class DR-01 identified by its
`getRefactoringId`). Step 8's "runs a full `RenameProcessor` … which reaches
`LuaRenameProcessor.findCollisions` (`REFACT-07-08`)" is therefore **executed**: `findCollisions` was
entered from that processor, the detector ran, both rules were evaluated, and the collisions reached
the caller — `RenameProcessor`'s channel, not `VariableInplaceRenamer`'s inline popup. Design §6's
statement at "Conflicts raise `BaseRefactoringProcessor.ConflictsInTestsException` in unit-test mode
rather than showing a dialog — for Route B, from `RenameProcessor`'s conflict handling" is confirmed
as written.

**The throwing frame is `RenameProcessor.preprocessUsages`, not either `BaseRefactoringProcessor`
conflict helper.** Reading the reference checkout offers three plausible throw sites —
`BaseRefactoringProcessor.processConflicts` (`:540`), `BaseRefactoringProcessor.showConflicts`
(`:797`) and `RenameProcessor.preprocessUsages` (`:180`) — and only the last one is on the path.
`RenameUtil.addConflictDescriptions` converts the `UnresolvableCollisionUsageInfo`s
`LuaRenameConflictDetector` produced into the conflict map immediately above it
(`RenameProcessor.java:170`). Anything asserting on Lunar's conflicts through a `processConflicts`
or `showConflicts` seam would be asserting on a frame that does not run.

**Also a data point against Risk 1.3.** Every platform line in that trace matches the line the same
symbol occupies in the `5ba8ab1cfe37` reference checkout design §1 Provenance flags as a different
build — `MemberInplaceRenamer.java:283` and `:316` are design §3.3 step 8's own citations, and
`RenameProcessor.java:180` is the `throw` read there. For these frames the offsets did not drift.

Step 7's `findCollision()`-returns-null clause (`MemberInplaceRenamer.java:113-117`) is confirmed
**consequentially**: no inline-popup channel fired at any point, and the only conflict signal
observed came from step 8. The override itself was not read back off the compiled class, so
"`findCollision()` returns null" remains **Read**; "no conflict reaches the user through step 7"
is now **run**. Step 7's other half — `findProblem()` on an *invalid* identifier — is still not
exercised adversarially; that is TC-07's, not this task's.

Step 8's `renameElement` clause is unchanged by this task and stands as DR-01 left it: reached, with
the composite, which design §3.6's normalisation answers.

#### What this does NOT settle

- **§3.3 step 9 — Esc reverts — is Read, not run, and it stays that way. No de-risking task covers
  it, and that is the decision.** DR-04's specification contains no Esc probe — neither its Setup
  nor any branch of its decision rule mentions cancellation — and none was invented, because
  inventing the probe means inventing its decision rule. **No DR task is added either.**
  `REFACT-07-06` is a `Must`, **TC-06 already tests it** — start the template, assert a template
  started, type, `gotoEnd(true)`, assert the original document — it is in Phase 2's fail-first list,
  and it carries a named mutation. A spike whose only job would be to predict what a required test
  will assert duplicates that test. **If the platform does not in fact revert on <kbd>Esc</kbd>,
  TC-06 is where that surfaces**, at Phase 2's fail-first run or Phase 4's mutation, which is the
  cheap place for a wrong expectation to fail — and that is why the deferral is safe rather than an
  open gap. Design §3.3 step 9 carries the same disposition, so the DR-01/DR-04 deferral chain ends
  here rather than pointing on.
- **The conflict *dialog* was not driven.** Unit-test mode takes the `isUnitTestMode` branch at
  `BaseRefactoringProcessor.java:538-541` and throws instead of constructing
  `ConflictsDialogBase`. What the user actually reads, and whether the dialog's Continue button
  proceeds with a rename whose template has already been rolled back, is a live-IDE question for
  `human-verification-checklists.md`.
- **Only the capture and shadow rules were exercised.** The two global rules
  (`refactoring.rename.conflict.globalExists`, `…ambiguousGlobal`) are unreachable from a
  `LOCAL_VARIABLE` target and were not measured on Route B.
- **This is one fixture.** It establishes the channel, not that every conflicting Lua rename reaches
  it.
- **DR-03 has since run (2026-08-26) and this caution was warranted.** DR-04's success narrowed
  nothing: the audit found the Safe Delete verdict unchanged, the bookmark anchor changed but
  callerless, the minimap absent from the shipped platform altogether, and a fourth consumer this
  bullet did not think to name — the <kbd>Ctrl+D</kbd> caret — changed.

## Risks

### Risk 1.1 — The interface change is a PSI-model change, and PSI-model changes have a wide blast radius

**Severity: high. Likelihood: medium.** `PsiNameIdentifierOwner` is consulted across highlighting,
Safe Delete, the declaration/target API, bookmarks, the minimap and the completion-command menu
(design §4). A behaviour change in any of them is invisible to Lunar's suite, which asserts on
rename, Find Usages and Safe Delete outcomes and on nothing else in that list.

**Mitigation**: DR-03, which is scheduled before the phase that ships the change, and
`REFACT-07-12` as a `Must` with per-consumer evidence rather than a single suite run.

**Residual**: consumers outside the platform tree — other installed plugins that branch on the
interface over a `LuaNameRef` — are not enumerable from this repo. Accepted: Lunar's PSI is
reachable by another plugin only through the language extension points it registers, and a
third-party plugin branching on Lua PSI is not a supported configuration.

### Risk 1.2 — A green suite has already proved it cannot see this feature break

**Severity: high. Likelihood: certain — it has happened.** The full suite passed with an override
that blanked usages in a live editor, because nothing drove a rename template to commit. The same
hole would swallow a Route B regression.

**Mitigation**: `REFACT-07-15` and design §6's test layers. Specifically: the document layer is
mandatory, and the predicate layer alone is explicitly declared insufficient. Every case's mutation
is executed in Phase 4 rather than asserted in the plan.

**Residual**: `CodeInsightTestUtil.tryInlineRename` drives the handler directly and never consults
either availability predicate (`CodeInsightTestUtil.java:246`), so the document layer *alone* is
also insufficient — a suite of document-layer cases stays green with the feature switched off at
the predicate. This is why the registry layer exists and why TC-02 is a `Must`.

**Second residual, of the same shape**: when no template starts, `tryInlineRename` asserts nothing
and returns `false` (`CodeInsightTestUtil.java:250-256`), and design §6's `renameInPlaceViaHandler`
returns `false` in the same circumstance. So **every** document-layer case whose expected text is the
**unchanged** file — read them off `requirements.md`'s Then column — passes with the feature entirely
absent. `implementation-plan.md` Phase 2 handles it with a rule that admits no exemptions: every
document-layer case asserts that a template started, not only what the document says. That rule is
also what makes TC-06 a gate rather than a guard; its `requirements.md` row says so.

### Risk 1.3 — The platform citations come from a different build than the one Lunar runs

**Severity: medium. Likelihood: high for offsets, low for semantics.** Design §1 Provenance records
that citations resolve against `intellij-community` `master` at `5ba8ab1cfe37` (2026-05-04) while
Lunar compiles against GoLand `2026.1.3` (`gradle.properties:18-19`).

**Mitigation**: every behavioural claim is routed to a DR task that executes against the compiled
platform. DR-01 step (c) and DR-03 step 1 both re-verify by symbol.

**A data point for some of these frames, and it does not discharge the risk.** DR-04 captured the
conflict refusal's stack trace from the **compiled** GoLand 2026.1.3 build, and every platform line
in it sits at the offset the same symbol occupies in the `5ba8ab1cfe37` reference checkout:
`MemberInplaceRenamer.java:283` and `:316` — design §3.3 step 8's own citations — and
`RenameProcessor.java:180`, the `throw` read there. **Scoped to those frames only.** It says nothing
about the rest of the design's citations, and a run that happened to agree on three offsets is not a
general guarantee that the builds are aligned; the cite-and-verify-by-symbol rule stands.

**The first measured instance of this risk biting, recorded in Phase 3.** Phase 2's Run B threw the
`getSelectedInEditorElement` `LOG.error` out of a fixture running on the compiled platform and the
stack frame read **`InplaceRefactoring.java:859`**. The same `LOG.error(nameIdentifier …)` statement
is at **`:860`** in the provenance checkout. Both numbers are correct, each for its own build:

| Build identity | Line of the `getSelectedInEditorElement` `LOG.error` | How established |
| :--- | :--- | :--- |
| GoLand **2026.1.3** (build 261) — what Lunar compiles and tests against (`gradle.properties:18-19`) | **`:859`** | executed — the frame in Phase 2 Run B's `TestLoggerFactory$TestLoggerAssertionError` |
| `intellij-community` `master` **`5ba8ab1cfe37`** (2026-05-04) — design §1 Provenance's checkout | **`:860`** | read — `grep -n "LOG.error(nameIdentifier"` |

**The consequence is not the one-line offset; it is that RD-2 misdiagnosed it.** RD-2 recorded
REFACT-01's `:859` as "off by one" against the provenance checkout and concluded `:859` was the
preceding blank line. Against the *shipped* build it is the statement, so REFACT-01's citation was
right about the build REFACT-01 was measured on and RD-2 compared it to a different one. That is
this risk in its exact shape: **a bare line number is ambiguous across builds, and a disagreement
between two of them is not evidence that either is wrong.** RD-2's second finding — the
`LuaBaseElements.kt` line — is unaffected, being a citation into this repo, where there is only one
build.

**Standing rule this instance establishes**: a platform line number cited anywhere in REFACT-07's
artifacts names the build it was read from. Where the two builds disagree, both are given. The
citations for this `LOG.error` now carry both; no other citation in this feature has been measured
to diverge, and the ones DR-04 checked (`MemberInplaceRenamer.java:283`/`:316`,
`RenameProcessor.java:180`) agree.

**Residual**: a *semantic* difference — `MemberInplaceRenamer.checkLocalScope()` not overriding in
the shipped build, say — would invalidate design §1 Ground 2 and reinstate the `getUseScope`
override. DR-01's (c)-false branch is written to catch exactly that.

### Risk 1.4 — Route B's `RenameProcessor` re-run makes the rename two document transactions

**Severity: medium. Likelihood: high — it is how the route works.**
`MemberInplaceRenamer.performRefactoringRename` calls `tryRollback()` (`:253`) and then runs a full
`RenameProcessor` (`:283`, `:309-317`). The file is therefore written by the template, restored,
and written again by `LuaRenameProcessor.renameElement`.

**The rollback half is no longer an assumption.** DR-04 probe (d) measured the sequence directly
on TC-08's fixture: the template's edit lands, `tryRollback` removes it, and only then does the
conflict reach the caller. So "the file is written, restored, and written again" is **Executed**,
and the ordering failure — a conflict arriving while the corrupt text is still in the document — was
looked for and does **not** occur on this path.

**What remains to check, and it is not the rollback**: whether the user sees one undoable command or
two, and whether <kbd>Ctrl+Z</kbd> after an in-place rename restores the original file in one step.
REFACT-01 measured that a rename is recorded as a single named command
(`Undo Renaming local variable counter`); the rollback-then-rename shape may split it. DR-04 did not
drive undo.

**Mitigation**: `human-verification-checklists.md` carries an explicit undo check, and DR-01's step
(e) records the document after commit. If undo needs two steps, that is a defect to file, not a
reason to change route — Route A's single transaction costs `REFACT-07-09`.

**Residual**: accepted for this feature; filed as a follow-up bug if observed.

### Risk 1.5 — `MemberInplaceRenamer` was designed for members, and a Lua local is not one

**Severity: medium. Likelihood: medium.** The class's own affordances assume a symbol with
cross-file usages: `collectRefs` additionally searches `getSubstituted()` (`:173-183`),
`appendAdditionalElement` can show a rename chooser (`:207-236`), and `getVariable()` re-derives the
target from a range marker after the template edits it (`InplaceRefactoring.java:646-653`).

For a Lua file-local declaration those paths should be inert or benign, because
`getReferencesSearchScope` is the editor's own file (`:200-204`). "Should be" is a **read**.

**Mitigation**: DR-01 exercises the whole chain on a real fixture with three usages, which is the
input that would expose a double-collected reference (a usage renamed twice, or a segment appearing
twice in the template).

**Residual**: the rename-chooser popup (`enable.rename.options.inplace` registry key, `MemberInplaceRenamer.java:208`) could
surface an unexpected dialog in the live IDE that no unit fixture shows. Covered by the
`verify-in-ide` checklist item, not by a unit test.

### Risk 1.6 — Narrowing `isInplaceRenameAvailable` to `false` removes a shipped predicate

**Severity: low. Likelihood: certain — it is the design.** `REFACT-01-12`'s Phase 7 deliverable is
a predicate that must now answer `false`. Anyone reading only REFACT-01's artifacts will see a
requirement's shipped behaviour reversed.

**Mitigation**: RD-1 proposes the REFACT-01 row's replacement wording, and design §1 "Prior art"
marks the method **REPLACED** with the expression preserved. The user-visible behaviour does not
regress at any point: before, `false` sent the user to the dialog through
`VariableInplaceRenameHandler`'s fallback; after, `false` sends the user to
`MemberInplaceRenameHandler`, which starts the template.

**Residual**: none, provided the §3.1, §3.2, §2.3 and §2.4 edits ship in the same commit. Shipping
§3.2's `isInplaceRenameAvailable = false` without the `isMemberInplaceRenameAvailable` clause would
leave Lua with the dialog and no template — a silent removal of nothing, but a wasted release.
Shipping §2.3's handler without §2.4's registration is the same failure, quieter: the class
compiles, the suite that constructs it directly stays green, and no user ever reaches it.

### Risk 1.7 — `REFACT-07-14`'s test has no mutation this repo can apply

**Severity: low. Likelihood: certain — measured.** TC-12 asserts no in-place handler is available
for a numeric-`for` variable, and no Lunar code is on that input's path:

- `isMemberInplaceRenameAvailable` cannot be mutated into reach. The reason is grammatical —
  `numericForStatement ::= FOR IDENTIFIER '='` (`lua.bnf:152`) produces no `LuaNameRef` — so
  widening its first clause finds nothing to anchor on. Its caller's gate is not reached at all in
  any case: `MemberInplaceRenameHandler.isAvailable` short-circuits on a null element at `:46`.
- Design §3.5's step 3 (`leaf.parent as? LuaNameRef`) is not on the path either. DR-05 probes
  `f`/`f2`/`f3` measured the data context supplying **null** at all three caret placements, so
  `declaringNameRefOf` returns at its first line and step 3 is never evaluated. Design §8 therefore
  attributes `REFACT-07-14` to the platform supplying nothing, and the safe cast is kept as the
  classifier-invariant assertion it is, with no requirement resting on it.

**Mitigation**: `requirements.md` TC-12 is labelled a **guard**, with that argument stated on its
row, and its KDoc must say so in its first line. No weaker assertion is substituted to make the row
read as coverage.

**Residual, accepted and recorded**: a green run of TC-12 is evidence that nothing regressed, not
evidence the feature works. The only mutation that would redden it — adding `nameRef` to the
grammar rule and regenerating — is outside the routine sweep.

### Risk 1.8 — A registered `renameHandler` is consulted for every rename in every language

**Severity: low. Likelihood: certain — it is how the extension point works.**
`RenameHandlerRegistry.doGetRenameHandlers` iterates
`RenameHandler.EP_NAME.getExtensionList()` (`RenameHandler.java:13`) and
calls `isRenaming(dataContext)` on each (`RenameHandlerRegistry.java:106-110`), so
`LuaInplaceRenameHandler.isAvailableOnDataContext` runs on the EDT for a Java rename, a JSON rename
and every other. Two hazards follow: a slow predicate becomes everyone's slow predicate, and a
too-permissive one hijacks another language's rename.

**Mitigation**: §3.5's first step is an `IElementType` identity comparison against
`LuaElementTypes.IDENTIFIER`, a static singleton, so a non-Lua element is rejected before
`LuaDeclarationSite.kindOf` is reached, and §2.3's `isAvailableOnDataContext` returns `false` before
that whenever `CommonDataKeys.EDITOR` is absent. Note that the null-editor and null-file guard
`VariableInplaceRenameHandler.isAvailableOnDataContext` performs (`:33-43`) is **not** inherited —
§2.3 implements `RenameHandler` directly and restates the editor half itself; the file half is not
read by this handler at all.

**Residual**: a Lua **injected** fragment inside another language's file would reach the Lua
branch, which is correct but unmeasured. DR-02 does not cover it and no requirement asserts it;
recorded rather than designed for, because Lunar registers no language injector for Lua
(`grep -c languageInjector src/main/resources/META-INF/plugin.xml` is `0`) and REFACT-07-16 already
puts cross-file and injected in-place rename out of reach.

### Risk 1.9 — `REFACT-07-02` rests on a premise about every other registered rename handler, and the premise is not Lunar's to enforce

**Severity: medium. Likelihood: unknown until DR-02 runs.**
`RenameHandlerRegistry.doGetRenameHandlers` returns without further work only when the map built
from **every** registered handler holds exactly one entry (`RenameHandlerRegistry.java:106-113`).
The pairwise fact that Lunar's handler and `MemberInplaceRenameHandler` are never both available
(design §3.5) does not supply that. If any other plugin's handler is renaming at a Lua caret, the
map holds two entries and the removal loop at `:114-119` runs.

**Mitigation, and it is a design choice rather than a hope**: §2.3 implements `RenameHandler`
**directly** instead of subclassing `MemberInplaceRenameHandler`, so no map entry the removal loop
can match belongs to Lunar. Had it subclassed, then at a caret whose data context supplies an IDENTIFIER leaf — a usage caret
or a parameter declaration caret, where the platform's own
member-inplace handler is not available — Lunar's would have been the map's only member-inplace
entry and therefore the one deleted, and `REFACT-07-11` and `REFACT-07-09` would have been silently
unreachable.
Design Alternative I records the rejection; §2.3 itemises what implementing the interface directly
costs. DR-02 probe (1) enumerates the live extension list, and probe (3) asserts Lunar's handler is
not a removal-loop target.

**Residual, accepted**: with two entries in the list, `RenameElementAction` shows its `Renamer`
chooser popup (`RenameElementAction.java:111-122`) rather than starting the template directly, so
`REFACT-07-02` is unmet in that configuration. It is unmet **visibly**, and Lua's rename is one of
the offered options rather than deleted. Lunar cannot prevent another plugin from claiming the
caret, and narrowing Lua's own gate does not help, because the second entry is not Lunar's. DR-02's
branch for this outcome records the second handler's class, weakens `REFACT-07-02`'s Status to what
was measured, and adds the observation to `human-verification-checklists.md`.

**The symmetric case is the platform's, not this feature's**: where the data context supplies the
declaring `LuaNameRef` — a `local` or global declaration caret — the map's
member-inplace entry is the platform's own `MemberInplaceRenameHandler`, and a second handler
deletes it. That is the behaviour every language gets — it is design §1 Ground 3 seen from the other
side — and REFACT-07 neither causes it nor can prevent it.

### Risk 1.10 — REFACT-07's inertness on spellchecking depends on `EDITOR-02`, a feature shipped for an unrelated reason

**Severity: medium. Likelihood: low, but the failure is silent and the trigger looks like a
cleanup.** `SpellcheckingStrategy.getTokenizer` returns `PsiIdentifierOwnerTokenizer` for **any**
`PsiNameIdentifierOwner` — `SpellcheckingStrategy.java:91`, a single unguarded `instanceof`. §3.1
makes every Lua `LuaNameRef` one. So on the face of it this feature newly submits every Lua
identifier to the spellchecker.

**It does not, and the only thing stopping it is Lunar's own strategy.**
`LuaSpellcheckingStrategy.getTokenizer` (`LuaSpellcheckingStrategy.kt:37`, registered
`plugin.xml:750-752`) is a **total override with no `super.` call anywhere in the class** — verified,
`grep -n "super\."` on that file returns nothing — so the platform's line 91 is unreachable for a
Lua element. DR-03 measured the consequence: every `LuaNameRef` routes to `LuaIdentifierTokenizer`
on both commits, Δ none.

**The coupling, stated plainly: REFACT-07 is inert here because of `EDITOR-02`.** Nothing in
REFACT-07 establishes it, no REFACT-07 requirement names it, and no REFACT-07 test would fail if it
changed. **Anyone who later makes `LuaSpellcheckingStrategy` delegate to `super` — a natural-looking
tidy-up — re-opens this**, and the symptom is spellchecker squiggles under ordinary Lua identifiers,
far from anything that would be suspected of causing it.

**Mitigation, and it is not this document.** A coupling recorded only in a feature's risk register is
invisible at the place it can be broken. `implementation-plan.md` Phase 1 carries a task to put a
one-line comment at `LuaSpellcheckingStrategy.kt:37` stating that the total override is load-bearing
for REFACT-07 and why. That is the mitigation; this entry is the explanation it points back to.

**Residual**: accepted. The alternative — gating §3.1's `getNameIdentifier()` on something
spellcheck-aware — is design §3.1's Alternative B, rejected there for making a structural accessor
answer a semantic question.

### Risk 1.11 — TC-13's named mutation looks unreachable through the driver its row specifies

**Severity: low. Likelihood: high — it is a Phase 4 blocker in waiting, not a defect in the
feature.** Raised during Phase 2 while writing the case; **read, not run**, and recorded as such so
Phase 4 executes it rather than inheriting the conclusion.

**SETTLED BY A RUN, 2026-08-26 — the risk was right.** Phase 4 applied the `:57` deletion and the
whole 64-test sweep stayed green (`BUILD SUCCESSFUL`): **TC-13's named mutation SURVIVED**. The
green run is itself the proof of the mechanism, not merely consistent with it — had the `:58`
`kindOf` gate returned early as the row claimed, TC-13 would have reddened. It did not, so
`kindOf(requested)` was non-null, so `requested` was already the IDENTIFIER leaf. Everything below
that this entry predicted from reading is now measured.

**The case was NOT deleted and the requirement is NOT untestable**, which is the other branch
`implementation-plan.md` task 4.2 offers. A reachable mutant exists and was measured in the same
sweep: `reference.isReferenceTo(target)` → `false` at `LuaNameReferenceSearcher.kt:76` reddens
TC-13's gating half with `expected:<3> but was:<0>`. `requirements.md`'s TC-13 row now names `:76`,
and records what that costs — at `:76` the mutant is shared with TC-03 and with every other case that needs the searcher to find a usage — a set that grows with each new consumer, so TC-13
pins the *searcher* rather than the Find Usages path specifically. No assertion was invented to fill
the row.

TC-13's row names, as the mutation that reddens it, deleting the `identifierLeafOf` normalisation
from `LuaNameReferenceSearcher.processQuery` and searching `requested` directly
(`LuaNameReferenceSearcher.kt:57`), on the ground that "the `kindOf` gate at `:58` then returns
early". That gate returns early only if `kindOf(requested)` is null, i.e. only if `requested` is the
`LuaNameRef` **composite**. But the row's driver is Find Usages, and Find Usages passes the
IDENTIFIER **leaf**: `CodeInsightTestFixtureImpl.findUsages` calls
`handler.processElementUsages(psiElement, …)` on the element it is handed
(`CodeInsightTestFixtureImpl.java:1249-1263`), and `kindOf` of a declaration leaf is
`LOCAL_VARIABLE`, not null. Under the mutation `target` would simply be the leaf and the search
would proceed normally — TC-13 GREEN.

Nor can the case be re-pointed at the composite to reach the mutation: `LuaFindUsagesProvider`
`canFindUsagesFor(element) = LuaDeclarationSite.kindOf(element) != null`
(`LuaFindUsagesProvider.kt:35`), which is `false` for a `LuaNameRef`, so the fixture cannot obtain a
Find Usages handler for one.

**What this does not put in doubt.** TC-13 is written and green, and it is a real
`REFACT-07-12` regression guard: DR-03 established that no test in the 2851-name baseline asserted
Find Usages' result at all, and this one does. The open question is only whether it has an
*absence-detecting* mutant, which is `implementation-plan.md` task 4.2's existing procedure — find a
reachable mutation, or delete the case and record why the requirement is untestable. Do **not**
invent an assertion to fill the row.

## Recorded disagreements

These concern artifacts this feature consumed and may not edit. They are recorded here for the
supervisor, per the scope boundary.

### RD-1 — `REFACT-01-12` should be delegated to REFACT-07

REFACT-01's `requirements.md` is frozen and its `REFACT-01-12` row is audited and conforming. The
house pattern for delegated work is `REFACT-01-11` → [[REFACT-05]] and `REFACT-01-17` →
[[REFACT-04]]: the row keeps its ID and priority and its Description becomes a pointer.

**Proposed replacement Description for the `REFACT-01-12` row** (for the supervisor to apply after
review; the Priority column stays **S** and the Status column stays **Partial** until REFACT-07
ships):

> Delegated to [[REFACT-07]], not duplicated. What REFACT-01 ships and keeps: the availability
> predicate's expression — `element is LuaNameRef && LuaDeclarationSite.kindOf(element.identifier)?.isFileLocal == true`
> (design §2.6) — and `LuaInplaceRenameTest`'s discriminating cases. REFACT-07 moves that expression
> from `isInplaceRenameAvailable` to `isMemberInplaceRenameAvailable`, supplies the primitive both
> in-place routes require — a `PsiNameIdentifierOwner` on the declaring `LuaNameRef` — and adds the
> `renameHandler` that serves every caret whose data-context element is a declaration
> IDENTIFIER leaf — a usage caret and a parameter declaration caret — and which therefore reaches no
> platform in-place handler. This row becomes `Full` when
> REFACT-07 does.

The epic `docs/features/refactoring/requirements.md` needs a matching `REFACT-07` row; this
feature adds it.

### RD-2 — Gap 2.20's `LOG.error` citation is off by one, and its `LuaBaseElements.kt` citation names the wrong line

Recorded rather than corrected in place, because REFACT-01's artifacts are this feature's frozen
input.

- Gap 2.20 and `LuaRefactoringSupportProvider`'s KDoc cite the `getSelectedInEditorElement`
  `LOG.error` at `InplaceRefactoring.java:859`. In the checkout design §1 Provenance names, it is
  at `:860`; `:859` is the preceding blank line. Verified by
  `grep -n "LOG.error(nameIdentifier"`.
  **Superseded by measurement — this row was wrong, and Risk 1.3 above carries the correction.**
  Phase 2 Run B threw that `LOG.error` from the **shipped GoLand 2026.1.3** and the frame read
  `:859`. REFACT-01's citation is right for the build Lunar runs; `:860` is right for the
  provenance checkout. Neither is "off by one" — this row compared two builds and read the
  difference as an error.
- Gap 2.20 cites `LuaNameRefElement : PsiNamedElement` at `LuaBaseElements.kt:79`. The declaration
  is at `:75`; `:79` is `) : LuaBaseElement(node),` inside `LuaNameRefElementImpl`. The KDoc on
  `LuaRefactoringSupportProvider.kt:32` cites `:75` and is right.

Neither changes any conclusion Gap 2.20 draws. Both are recorded because the next reader will try
to follow them.

### RD-3 — The "founding premise" Gap 2.20 attributes to design §2.2 is not there

Gap 2.20 states that *"design §2.2's premise — 'Lunar models no declaration PSI: apart from
`::labels::` every declared name is a `LuaNameRef`' — is what has to move for REFACT-01-12 to be
deliverable."*

**The quoted sentence is not in design §2.2.** §2.2 is `LuaRenameProcessor`'s component
specification — threading, collaborators, and the `PotemkinProgress` measurement.

**Where it actually is, and why the nearest instance explains the misattribution.** The sentence
appears **verbatim** as the KDoc of `LuaRenameProcessor` itself
(`src/main/kotlin/net/internetisalie/lunar/refactoring/rename/LuaRenameProcessor.kt:39`):

```kotlin
 * Lunar models no declaration PSI: apart from `::labels::` every declared name is a [LuaNameRef]
 * in a particular parent container, so the whole processor is keyed on the declaration IDENTIFIER
 * **leaf** that [LuaDeclarationSite] normalises to — the same key reference search, Find Usages
 * and Safe Delete use.
```

`LuaRenameProcessor` is the class design §2.2 specifies, so the quotation is the *class's* KDoc
attributed to the *section that specifies the class*. That is the likeliest explanation of the
misattribution RD-3 exists to correct, and it is the instance the supervisor most needs, because it
is the one a reader following the citation will land next to. Enumerate the rest with
`grep -rn "models no declaration PSI" src/ docs/`; it also reaches `LuaDeclarationSite.kt:34`, in
that object's KDoc, and REFACT-01's own `design.md` §1 and `requirements.md:61` state the premise in
their own words.

**Assessed honestly, the premise is true and its stated cost is not.** Design §1's *"Premises
examined"* table prices it as: *"**Fixed for REFACT-01.** Changing it means editing `lua.bnf` and
regenerating the parser, which moves every consumer of `LuaNameRef`."*

- **True**: there is no declaration PSI, and `nameRef ::= IDENTIFIER` (`lua.bnf:169`) is every
  declared name but a label.
- **Not true**: that giving it a `PsiNameIdentifierOwner` requires a `.bnf` edit or a regeneration.
  `nameRef` declares `mixin="net.internetisalie.lunar.lang.psi.LuaNameRefBaseImpl"`
  (`lua.bnf:170`), a hand-written Kotlin class, and the platform tests the interface with
  `instanceof`. Design §3.1 delivers it in one file with no generated-code change. The table's own
  scoping — *"Fixed **for REFACT-01**"* — is the honest part, and it does not assert what the cost
  sentence asserts.
- **Also not true as stated**: that the change "moves every consumer of `LuaNameRef`". It moves
  consumers of `PsiNameIdentifierOwner`, which is a different and enumerable set (design §4), and
  the enumeration is what DR-03 executes.

The correction is what makes REFACT-07 a change to two hand-written Kotlin files plus one new
handler, rather than a grammar change.

### RD-4 — `AGENTS.md`'s LuaCATS-tag note points at the same primitive, and this feature does not deliver it

`.agents/AGENTS.md` records, in the LuaCATS-tag lesson, that stubbing doc tags as
`PsiNameIdentifierOwner` "would also unlock Find Usages/Rename on types à la EmmyLua's
`PsiNameIdentifierOwner` doc tags", and calls it the *"correct but heavy"* fix.

**It is the same interface and a different element, and one change does not serve both.** The
heaviness in that note is not the interface — it is making `LuaCatsClassTag` / `LuaCatsAliasTag`
**stubbed**, which needs a lazy-parseable element type and runs into the `IElementType` registry
size limit. That work is on the LuaCATS comment PSI (`LuaCatsLazyCommentImpl` and the tag elements
under `luacats/`), not on `LuaNameRefBaseImpl`, and REFACT-07 touches none of it.

What REFACT-07 *does* supply is precedent: the enumeration in design §4 is the audit any future
`PsiNameIdentifierOwner` grant in this plugin has to repeat, and DR-03's evidence is reusable as
its baseline. Recorded so that a later reader does not mistake this feature for having delivered
type rename.

### RD-5 — `REFACT-01` Gap 2.20's open use-scope question is answered, and the answer is that no override is needed

Gap 2.20 established the missing `PsiNameIdentifierOwner` as the root cause of `REFACT-01-12`'s
failure and left open whether a `getUseScope()` override is *also* required. That spike proposed
one. REFACT-01's artifacts are frozen, so the answer is recorded here for the supervisor to carry
back rather than written into them.

**Answer, executed: no override is needed on Route B.** DR-01 probe (c) constructed
`MemberInplaceRenamer(nameRef, leaf, editor)` and called `performInplaceRename(null)`; it returned
**`true`** with **no `getUseScope()` override anywhere in the tree**. That is the executed form of
design §1 Ground 2's prediction: `MemberInplaceRenamer.checkLocalScope()` (`:105-111`) returns the
editor's own PSI file and never consults `PsiSearchHelper`, so the
`InplaceRefactoring.java:283-290` gate Route A trips on is not reached.

Two things this does **not** say. It is not a statement about Route A, where
`InplaceRefactoring.checkLocalScope()` does read the use scope and the override would still be
required — the question is route-dependent, which is why the route decision had to come first. And
it is not a claim that `LuaNameRef.getUseScope()` is *correct*; it is a claim that Route B does not
depend on it. The memory note that Lunar's locals have no `getUseScope` override and therefore
search module-wide stands untouched by this feature.

## Gaps

### Gap 2.1 — Nothing in the suite drives an inline rename template to commit

**Status: this feature's reason for existing.** Stated as a gap rather than a risk because it is a
present, measured absence rather than a hazard: the full suite is green while a live editor blanks
usages. `REFACT-07-15` closes it and design §6 layer 3 specifies how.

### Gap 2.2 — There is no cross-route parity test

`REFACT-07-05` requires the in-place path to produce the dialog path's exact result, and TC-05
asserts it for one fixture. Nothing asserts it for the other declaration kinds, and nothing would
notice if the two paths diverged for parameters or `local function` names specifically.

**No longer accepted on that reasoning — DR-01 measured the premise and it is false.** The two
paths do both end in `LuaRenameProcessor.renameElement`, but **not with the same element**: the
dialog path passes the declaration IDENTIFIER leaf and Route B passes the `LuaNameRef` composite,
because `MemberInplaceRenamer.getSubstituted()` re-derives the target as a `PsiNameIdentifierOwner`
(`MemberInplaceRenamer.java:367-372`) and §3.1 makes the composite one. Parity is therefore
**not** structural: it holds for every rewrite that is not keyed on `LuaDeclarationSite.kindOf` and
fails for every rewrite that is. See DR-01's record. What follows from that is a design decision,
not a measurement, and is the supervisor's.

### Gap 2.3 — Undo after a rename commit is measured, and it fails — `BUG-471`

**Measured in Phase 5's live session, and the result is negative.** After a rename commit
*Edit ▸ Undo* is present and **enabled**, and no number of undos restores the document — tried by
keystroke, by the menu item and by Find Action. It is filed as [[BUG-471]].

**It is not this feature's**, and the control is what says so: the same failure reproduces on the
modal dialog path REFACT-07 does not touch, while a typing-undo in the same session works. So undo
is neither broken in the sandbox generally nor broken by the in-place route specifically; what fails
is undo of a *Lunar rename*, on both routes. `BUG-471` also records the one thing that was **not**
separated — the headless container — and names it as the first hypothesis to rule out.

Still no automated case asserts it, for the reason this gap always gave: a single-command assertion
needs the command stack rather than the document. See Risk 1.4;
`human-verification-checklists.md` § "Undo after a commit" carries the raw observations and the
screenshots.


### Gap 2.21 — A `local` that shadows an earlier same-file `local`: the in-place path fails loudly, the dialog path rewrites the wrong declaration

**Measured, DR-01 probe `b6`.** For `local config = 1` ⏎ `local con|fig = 2`, the data context
supplies the **first** declaration's IDENTIFIER leaf at `(6,12)` while the caret sits at `(23,29)`.
`declaringNameRefOf` passes every step for it — `IDENTIFIER` ✓, `kindOf` = `LOCAL_VARIABLE` ✓
`isFileLocal` ✓, `parent is LuaNameRef` ✓ — so `LuaInplaceRenameHandler.isAvailableOnDataContext`
is `true` and the registry hands the caret to Lunar's handler. The template would then be anchored
on the declaration on the *previous line*.

**Not caused by REFACT-07.** The same element is supplied on the unchanged tree, for the reason
DR-05 Observation 1 gives: the declaring statement is excluded from its own scope
(`LuaBlockExt.kt:32-36`), so `scopeCrawlUp` resolves to the earlier declaration and
`TargetElementUtilBase.java:235-239` prefers that resolve. What REFACT-07 changes is the
consequence: today no handler accepts that leaf and the caret gets the dialog (DR-05 Table 2, row
`a2` — the global variant); with §2.3 present an in-place template starts on the wrong line.

**Not designed around here.** Whether §3.5's `declaringNameRefOf` should additionally require the
supplied leaf to be at the caret is a design decision and is the supervisor's.

**The end-to-end behaviour has now been driven, and the prediction above is wrong.** Measured
2026-08-26 on the shipped tree at `8913cf4b`, no production edit, every data context taken from the
editor with nothing injected — raw rows and the probe source are in `gap-2-21-evidence/`. **No
template starts on the previous line. No template starts at all, and the document is not changed.**

| | in-place (this gap's caret) | dialog, same fixture (control) |
| :--- | :--- | :--- |
| supplied | the earlier `local`'s leaf, `(6,12)`, line 0 | same |
| handler selected | `LuaInplaceRenameHandler` | `LuaInplaceRenameHandler` — **the registry selects Lunar's handler for this fixture in both columns.** The dialog column is a **forced** control, driven by invoking the dialog path directly, not a route the registry offers here. `PsiElementRenameHandler` is what the registry returns for the *global* variant (probe `a2`), not this one. |
| template anchored on | **nothing — none starts** | n/a |
| outcome | IDE error logged, then an unguarded `NullPointerException` | **completes silently** |
| document after | **unchanged** | `local renamed = 1`⏎`local config = 2`⏎`print(renamed)` |

The in-place mechanism, measured rather than argued: `MemberInplaceRenamer` collects the refs of the
element it was handed — the line-0 declaration — and `InplaceRefactoring.buildTemplateAndStart` then
asks `getSelectedInEditorElement` which of them holds the caret. The caret is at `(23,29)` and none
of them does, so every branch misses and control reaches `LOG.error` and `return null`
(`InplaceRefactoring.java:860-861` in the `intellij-community` checkout design §1 Provenance names,
`:859-860` in the shipped GoLand — Risk 1.3 records why both are cited). Under the test logger that
throws, which is what the first run observed. **Re-run with `LoggedErrorProcessor` swallowing the
error, so that execution continues the way it does in a production IDE where `Logger.error` does not
throw**, the very next statement dereferences that null:
`java.lang.NullPointerException: Cannot invoke "com.intellij.psi.PsiElement.getTextRange()" because
"selectedElement" is null`, at `InplaceRefactoring.java:363` (`:362` shipped). The platform has no
guard there.

**So REFACT-07 changes the consequence, but not in the direction this gap predicted.** The dialog
path — shipped, and untouched by this feature — renames the **wrong declaration** and rewrites
`print(config)`, a usage that belongs to the *other* `local`, silently changing what the program
prints from `2` to `1`. That is `BUG-457`'s class of outcome. What the in-place path does instead is
fail loudly and touch nothing. On this fixture the in-place route's blast radius is **smaller** than
the dialog's, not larger.

**The global variant, for comparison, and it is the benign half.** For `config = 1` ⏎
`local con|fig = 2`, `declaringNameRefOf` refuses at the `isFileLocal` step exactly as designed, the
registry returns `PsiElementRenameHandler`, and the dialog path then **refuses** the rename outright
(`CommonRefactoringUtil$RefactoringErrorHintException: Cannot perform refactoring.`) leaving the
document unchanged. `BUG-470` covers that half; it is a refusal, where the `local` half is a wrong
rewrite.

**Control**: the same drive on a plain `local con|fig = 2` with nothing shadowed anchors the template
at `(6,12)`, the caret's own declaration, and commits `local renamed = 2` ⏎ `print(renamed)`. The
harness measures what it claims to.

**One fact bearing on the design decision, stated because it is measured and not because it argues
for an answer.** "The supplied leaf must contain the caret" cannot be applied as a universal guard —
a usage caret legitimately supplies a declaration leaf that is not at the caret, which is `TC-04`, a
core case. What distinguishes `TC-04` from this fixture is not the supplied element but whether the
caret falls inside the element or one of its collected references, and that is precisely the
predicate `getSelectedInEditorElement` already computes. Today it answers by logging an error and
returning null into an unguarded dereference. **Whether to act on that, and where, remains the
supervisor's decision; nothing here has been guarded, and `declaringNameRefOf` is unchanged.**

**Relationship to `BUG-470`.** `BUG-470` was filed for the shadowing-a-**global** case DR-05
measured (Observation 1, probe `a2`). This gap is the same root cause — the declaring statement is
excluded from its own scope, so the reference branch resolves to the earlier declaration — but it is
**the more serious half of the family**, and `BUG-470`'s report currently describes only the global
half and so understates it:

| | shadowed declaration is a **global** (`BUG-470`, DR-05 `a2`) | shadowed declaration is a **`local`** (this gap, DR-01 `b6`) |
| :--- | :--- | :--- |
| supplied element | the global's declaration leaf | the earlier `local`'s declaration leaf |
| `declaringNameRefOf` | **refused** at the `isFileLocal` step — `GLOBAL_VARIABLE.isFileLocal` is `false` (`LuaDeclarationSite.kt:24`) | **accepted** — every step passes |
| with REFACT-07 present | the caret keeps the dialog; no in-place template | an in-place template starts, anchored on the previous line |

So the fix `BUG-470` needs is not a fix for this one: refusing globals is already what stops the
global case, and it is exactly what fails to stop this one. Whoever picks up `BUG-470` must widen
its report to the family before choosing a fix, or the `local` half will be closed as covered when
it is not.
