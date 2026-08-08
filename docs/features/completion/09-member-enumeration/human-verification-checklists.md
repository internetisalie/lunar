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
- **Expected**: first suggestion **under 100 ms** — no perceptible pause. Before this feature it was
  ~12.9 s. The complete list may still take longer to settle; that is by design (`non-functional.md`).
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
  the user-visible form of COMP-09-09.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Correctness of what is offered

### Scenario 2.1: Colon-declared methods still appear

- **Setup**: a library declaring `---@class C` whose members are **all** colon-declared
  (`function C:alpha()`, `function C:beta()`), with no dot member anywhere.
- **Steps**:
  1. `local c = ...` typed as `C`; then `c:` and invoke completion.
- **Expected**: `alpha` and `beta` both offered. This receiver has no key in the old stub index
  (design §1.3) and is the single most likely regression in the feature.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: Nested qualifiers are still not members

- **Setup**: a library declaring `Foo.bar.baz = 1`.
- **Steps**:
  1. Type `Foo.` and invoke completion.
- **Expected**: `bar` is **not** offered as a member with the name `bar.baz`, and `baz` is not offered
  on `Foo` at all — matching `memberNameOf`'s rejection of nested qualifiers (design §4.4).
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

## 3. Operators (COMP-09-05)

### Scenario 3.1: A `@class`-declared `__add`

- **Setup**: a library with `---@class Vec` declaring a `__add` field.
- **Steps**:
  1. In a project file, `local a, b = ...` typed as `Vec`; write `local c = a + b`.
  2. Then type `a.` and invoke completion.
- **Expected**: no diagnostic on `a + b` (closes COMP-04-DR-01 / BUG-426); and `__add` does **not**
  appear in the completion list — metamethods are held separately for exactly that reason
  (`LuaGraphType.kt:50-52`).
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
