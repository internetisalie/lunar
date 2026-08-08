---
id: "COMP-09-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "COMP-09"
folders:
  - "[[features/completion/09-member-enumeration/requirements|requirements]]"
---

# Verification Checklists: COMP-09 — Member Enumeration

> Run in a real sandbox IDE (`gce-builder.sh run runIde`), not a fixture. `LibraryRootTestCase`
> exists because a projectScope-vs-allScope defect is structurally invisible to a light fixture —
> that blind spot let BUG-395 and BUG-398 both ship green while the running IDE completed nothing.
> These scenarios are the counterpart: they check what a *user* sees, including the two things no
> automated test here covers — perceived latency, and the deliberate loss of type text (§4.5).

## 1. Latency, as perceived

### Scenario 1.1: First `wx.` in a session

- **Setup**: sandbox IDE; a project with the wxLua definition library enabled (or any library with a
  ≥200 KiB declaring file). Restart the IDE so nothing is cached.
- **Steps**:
  1. Open a Lua file. Type `wx.` and **do not type further**.
  2. Start a stopwatch at the `.` keypress; stop when the first suggestion appears.
- **Expected**: first suggestion **under 100 ms** — no perceptible pause. Measured before this
  feature: **746 ms** to first element (design §1.9). *(An earlier revision said ~12.9 s; that is
  time-to-**exhaustive**, which is not what this scenario times, and any improvement would have
  "passed" against it.)* The complete list settles ~31 ms later — §1.9 measured the gap.
- **Result**: ⬜ Pass / ⬜ Fail — time observed: ______

### Scenario 1.2: The keystroke that used to cost the most

- **Setup**: as 1.1, after the popup has appeared once.
- **Steps**:
  1. Press Escape. Type a comment line in the *consumer* file — an edit unrelated to the library.
  2. Type `wx.` again and time to first suggestion.
- **Expected**: still under 100 ms. This is the invalidation path (design §1.2): before this feature an
  unrelated edit invalidated the library snapshot and repaid the full cost.
- **Result**: ⬜ Pass / ⬜ Fail — time observed: ______

### Scenario 1.3: A large project, unrelated content

- **Setup**: as 1.1, plus a second large library enabled that the file never references.
- **Steps**:
  1. Time `wx.` to first suggestion.
- **Expected**: unchanged from 1.1. Enumeration work must not grow with unrelated indexed content —
  the user-visible form of COMP-09-09, whose counted form is design §4.10b.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.4: A small receiver and a huge one, both cold

- **Setup**: as 1.1, with a 3-member table declared in one library file and a 3 600-member namespace
  in another. Restart between the two measurements so each is cold.
- **Steps**:
  1. Time the small receiver's `.` to first suggestion, then the large one's.
- **Expected**: **comparable**. DR-02a measured 41 ms vs 1 641 ms on today's code — a 40x spread that
  `non-functional.md` forbids outright, since time-to-first must be independent of candidate count.
  This is the clause most likely to still be violated after the fix, because it is the one nothing
  before this feature ever checked.
- **Result**: ⬜ Pass / ⬜ Fail — small: ______  large: ______

### Scenario 1.5: The slow tier — a receiver bound through `require`

- **Setup**: as 1.1, plus a library file containing `Helper = require("some.module")` and a project
  file that uses `Helper.`.
- **Steps**:
  1. Restart so nothing is cached. Time `Helper.` to first suggestion.
- **Expected**: **unchanged from before this feature** — this receiver is deliberately *not* covered
  by the 100 ms budget (design §4.12, `non-functional.md`'s two-tier statement). The index cannot see
  through the binding, so it resolves through the type graph exactly as it does today.
- ⚠ **This is the judgement no test makes.** If tier 2 is noticeably slow in ordinary use, the
  two-tier contract is the wrong answer and the fix is to widen what the index can see through, not
  to loosen the budget. Record how it feels.
- **Result**: ⬜ Acceptable / ⬜ Noticeably slow — time observed: ______

## 2. Correctness of what is offered

### Scenario 2.1: Colon-declared methods still appear

- **Setup**: a library declaring `---@class C` whose members are **all** colon-declared
  (`function C:alpha()`, `function C:beta()`), with no dot member anywhere.
- **Steps**:
  1. `local c = ...` typed as `C`; then `c:` and invoke completion.
- **Expected**: `alpha` and `beta` both offered. This receiver has no key in the old stub index
  (design §1.3) and is the single most likely regression in the feature.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Nested qualifiers stop being members — a deliberate CHANGE, not a preservation

- **Setup**: a library declaring `Foo = {}`, `Foo.bar = {}`, `Foo.bar.baz = 1`, `Foo.direct = 2`.
- **Steps**:
  1. Type `Foo.` and invoke completion.
  2. Type `Foo.bar.` and invoke completion.
- **Measured before the change** (DR-12): `Foo.` offers `[bar, baz, direct]` and `Foo.bar.` offers
  `[]`. That is **BUG-430** — `baz` offered where it does not exist and withheld where it does.
- **Expected after**: `Foo.` offers `[bar, direct]` — `baz` **gone**. `Foo.bar.` is expected to stay
  empty; fixing *that* half is BUG-430's job, not this feature's.
- ⚠ An earlier revision of this scenario expected `baz` to be absent already, citing `memberNameOf`.
  It would have failed on today's code for a reason that is not this feature's fault. The removal of
  `baz` from `Foo.` is a **user-visible behaviour change** COMP-09 makes on purpose (design §4.4a).
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: ⚠ Type text is absent on the cross-file path — expected, not a bug

- **Setup**: as 1.1.
- **Steps**:
  1. Complete `wx.` and read the suggestion rows.
  2. In the same file, define `local t = { field = 1 }` and complete `t.` for contrast.
- **Expected**: cross-file library members show **name and icon but no type text**; the in-file `t.`
  members still show type text. This is the deliberate trade in design §4.5 — the index carries no
  type. **Record how it feels.** If it reads as a regression to a user, that is the signal to
  reconsider carrying a type string in the index value, and it is the one judgement no automated test
  can make.
- **Result**: ⬜ Acceptable / ⬜ Reads as a regression — note: ______

### Scenario 2.4: Go-to-declaration and gutter markers still work

- **Setup**: a library `---@class Base` with a method overridden by `---@class Derived`.
- **Steps**:
  1. Ctrl+B on a member completed from the library.
  2. Look for the override gutter marker on the derived method and click it.
- **Expected**: navigation lands in the library file; the gutter marker appears and navigates.
  `sourceElement` feeds `LuaOverrideLineMarkerProvider` (design §4.1), and `materializeClass:256-262`
  warns the parity harness compares names and types only — so this is checkable *only* by hand.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.5: A `@field` that did not complete before

- **Setup**: a library with `---@class Derived : Base`, `---@field ownField number` and
  `Derived = {}` at top level.
- **Steps**:
  1. Type `Derived.` and invoke completion.
- **Measured before the change** (DR-14): `[ownFn]` — the `@field` is **absent**, because the global
  door builds the type from the assignment and never reads the `@class` comment.
- **Expected after**: `[ownField, ownFn]`. A **deliberate new member** on this path (design §4.5a),
  declared rather than discovered. Inherited members from `Base` are still absent, and that is also
  unchanged — they were never on this door.
- **Result**: ⬜ Pass / ⬜ Fail — offered: ______

## 3. Operators (COMP-09-05)

### Scenario 3.1: A `@class`-declared `__add`

- **Setup**: a library with `---@class Vec` declaring a `__add` field.
- **Steps**:
  1. In a project file, `local a, b = ...` typed as `Vec`; write `local c = a + b`.
  2. Then type `a.` and invoke completion.
- **Expected**: no diagnostic on `a + b` (closes COMP-04-DR-01 / BUG-426).
- **SETTLED by measurement (DR-12), no longer contested.** `v.` offers `[__add, len, x]` — a
  `@class`-declared `__add` **is** already in the completion list today, so design §4.7 was right and
  this checklist and `implementation-plan.md` were both wrong. `v:` offers `[len]` only, so the colon
  filter already excludes it.
- **Expected**: the offered sets are **unchanged** by COMP-09-05 — `v.` still shows `__add`, `v:`
  still does not. Only the operator check gains it.
- **Worth noting for a future reader**: `LuaGraphType.kt:50-52` says metamethods are held separately
  because putting them in `localMembers` "would make `t.__add` complete on the instance, which is not
  what Lua exposes". For a `@class`-declared metamethod that has never been true. The comment
  describes an intent the code does not implement on this path; COMP-09-05 preserves the behaviour
  rather than the intent, which is the conservative call, but the gap is real.
- **Result**: ⬜ Pass / ⬜ Fail

## 3b. While the IDE is indexing (DR-10)

### Scenario 3b.1: Completion during a re-index offers nothing, and says nothing

- **Setup**: sandbox IDE with a definition library. Trigger a full re-index (**File > Invalidate
  Caches and Restart**, or edit a library file so its tree re-indexes).
- **Steps**:
  1. While the indexing progress bar is running, type `wx.` and invoke completion.
  2. Watch the event log and the notification area for the remainder of indexing.
- **Expected**: the popup offers **nothing** (or only keywords) and **no error notification appears**.
  DR-10 measured today's behaviour as `[]` with no throw; COMP-09 must not turn that into an
  exception, which the prototype's `membersOf` does (design §4.9).
- ⚠ **If a red "IDE internal error" appears, it may not be this feature**: `resolveType` has no
  dumb-mode guard and reports one for any indexing-time call (**BUG-432**). Note whether the stack
  names `LuaTypeManagerImpl.resolveType` before attributing it here.
- **Result**: ⬜ Pass / ⬜ Fail — notification seen: ______

### Scenario 3b.2: The first completion after indexing finishes

- **Setup**: as 3b.1.
- **Steps**:
  1. Wait for indexing to finish. Type `wx.` again.
- **Expected**: full member list, under the latency budget. The dumb-mode path must not have poisoned
  a cache with the empty result — a plausible failure that no unit test in this plan covers.
- **Result**: ⬜ Pass / ⬜ Fail

## 4. No new diagnostics

### Scenario 4.1: The checker's view is unchanged

- **Setup**: open the pinned ZeroBrane corpus checkout with a definition library enabled.
- **Steps**:
  1. Note the Problems view counts for `LuaTypeAssignability` and `LuaReturnTypeMismatch`.
  2. Compare with `src/test/resources/corpus/zerobrane.baseline`.
- **Expected**: **identical**. Any movement means enumeration became a type source — BUG-395's
  reverted experiment (BUG-397). Stop and revert; do not triage forward.
- **Result**: ⬜ Pass / ⬜ Fail — observed: ______ / ______
