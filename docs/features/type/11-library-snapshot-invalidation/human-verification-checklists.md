---
id: "TYPE-11-CHECKLIST"
title: "Verification Checklists"
type: "qa"
parent_id: "TYPE-11"
folders:
  - "[[features/type/11-library-snapshot-invalidation/requirements|requirements]]"
---

# Verification Checklists: TYPE-11 — Library Snapshot Invalidation

This feature changes **how long a cache lives**. Both of its failure modes are invisible to a green
suite — one is a latency the test JVM does not feel the way a user does, the other is a stale type
that `design.md` §1.1 measured 2543 pre-existing tests failing to notice **and that TYPE-11-DR-09
then measured all four corpus baselines failing to notice as well** (`risks-and-gaps.md` → "DR-09
measured": `2571 tests completed, 2 failed`, both of them TYPE-11's own fixtures). Outside
`TypeElevenDr01ResidualTest`, the scenarios below are the only thing standing between this feature
and a silent stale type. Everything below must be run in a real IDE against a real definition
library.

**Common setup** (steps 1–5 of Scenario 1.1) is referenced by later scenarios; do it once per session.

## 1. The measured symptom

### Scenario 1.1: Typing in a project file no longer costs a library rebuild

- **Setup**:
  1. Open a Lua project in the sandbox IDE (`run` / `runIde`).
  2. Settings → Languages & Frameworks → Lua → Definition Libraries: enable **love2d** (97 KB
     archive, the largest in the bundled catalog) and wait for the fetch to finish.
  3. Confirm the tree landed: the Project view shows a "Lua Definition Libraries" node containing a
     `love2d-…` directory.
  4. Create `consumer.lua` containing exactly `local pad = 1` on line 1.
  5. Wait for indexing to finish (no progress bar, "Indexing" gone from the status bar).
- **Steps**:
  1. On line 2 type `love.` and wait for the completion popup. Note roughly how long the **first**
     popup takes — this is the cold build and TYPE-11 does not change it.
  2. Press Escape. Type a single character on line 1 (e.g. change `1` to `12`).
  3. Type `graphics.` after the `love.` on line 2 and wait for the popup again.
- **Expected**: the popup in step 3 appears **without a perceptible pause** — comparable to the second
  popup in a project with no definition library enabled, not to the cold popup in step 1. Before this
  change, step 3 pays the full library graph rebuild again.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 1.2: The cold path is unchanged

- **Setup**: as 1.1, then File → Invalidate Caches → *Invalidate and Restart*.
- **Steps**:
  1. After restart and indexing, open `consumer.lua` and trigger completion on `love.` once.
- **Expected**: the first completion is **still slow** (hundreds of milliseconds or more). TYPE-11 is
  explicitly not a fix for the cold path; if this got fast, something other than this feature changed
  and the result should be investigated rather than celebrated.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. Correctness — the residual paths, by hand

These are the two shapes `design.md` §1.1 measured breaking under the rejected design. They must
behave identically before and after the change.

### Scenario 2.1: A project file shadowing a library global still wins, and still updates

- **Setup**: common setup from 1.1.
- **Steps**:
  1. Create `shadow.lua` containing `love = { myOwnField = 1 }`.
  2. In `consumer.lua`, type `love.` and read the popup.
  3. Edit `shadow.lua` to read `love = { renamedField = 1 }`. Save is not required.
  4. Return to `consumer.lua`, retype `love.` and read the popup.
- **Expected**: step 2 offers `myOwnField`; step 4 offers `renamedField` and **does not** offer
  `myOwnField`. A popup still showing `myOwnField` after step 4 is the exact stale-type defect this
  feature must not introduce — stop and report it.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: A project file extending a library class still updates

- **Setup**: common setup from 1.1.
- **Steps**:
  1. Create `libclass.lua` containing:
     ```lua
     ---@class MyHost
     local MyHost = {}
     HostHandle = MyHost
     ```
  2. Create `ext.lua` containing `function MyHost:beforeEdit() end`.
  3. In `consumer.lua`, type `HostHandle:` and confirm `beforeEdit` is offered.
  4. Edit `ext.lua` to read `function MyHost:afterEdit() end`.
  5. Retype `HostHandle:` in `consumer.lua`.
- **Expected**: step 5 offers `afterEdit` and **does not** offer `beforeEdit`. (Note: steps 1–2 put
  the class in a *project* file, so this scenario passes trivially; it is here because the same shape
  with `libclass.lua` inside the library tree is what broke, and this is the closest a user can get
  without editing a library.)
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.3: A library file is genuinely read-only to this feature

- **Setup**: common setup from 1.1.
- **Steps**:
  1. Open any `.lua` file inside the `love2d-…` definition tree from the Project view.
  2. Trigger completion inside it (e.g. on a `love.` receiver).
  3. Attempt to type a character.
- **Expected**: completion works inside the library file, and the IDE refuses the edit (read-only
  banner). A library file that accepts an edit means it is not registered as a library root, and every
  premise of this feature is off for that tree.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.4: The two shapes this checklist cannot arrange (TYPE-11-06)

`design.md` §1.8 measured two further stale-type shapes: a library file whose free global **nothing
declares yet** (so the recorder records an empty set and the file is pinned), and a library file
whose inner library snapshot was served **warm** while it was being built (so it records an
incomplete set). Both are covered by `TypeElevenDr11LateDeclarationTest` and
`TypeElevenDr12WarmInnerSnapshotTest`.

**Neither has a reliable manual arrangement with the bundled catalog**, and saying so is the point:
both need a library file that references a global the library does not define, and the measured
bundled set contains none — all 10 stdlib stubs and the 123 KiB probe library record zero global
absences. Do **not** invent a scenario that looks like it exercises them; a manual check that cannot
fail is worse than none.

What a tester *can* do is watch for the symptom class while running everything above: **a completion
popup whose member set stops changing after a project edit that should have changed it.** If that is
ever seen, record the exact file that stopped updating and what was edited — that is the report this
feature needs, and the two fixtures above are the shapes to compare it against.

- **Result**: ⬜ Not applicable (no manual arrangement) / ⬜ Symptom observed — record details

## 3. Generation signals (TYPE-11-02)

### Scenario 3.1: Disabling a definition library takes its types away immediately

- **Setup**: common setup from 1.1, with `love.` completing.
- **Steps**:
  1. Settings → … → Definition Libraries: **disable** love2d. Apply.
  2. Without restarting or editing anything, return to `consumer.lua` and type `love.`.
- **Expected**: the popup no longer offers love2d members.
- ⚠ **A pass here is weak evidence, and the checker should know why.** Disabling the library removes
  its root from scope entirely, so the members would disappear even if pinned snapshots were never
  invalidated — the resolver simply finds no candidate file. This scenario catches a *catastrophic*
  failure (stale members served from a snapshot that outlived its root) and nothing subtler. The
  automated equivalent could not go red at all and was restated for this reason (`design.md` §8.1
  TC-2). **Scenario 3.4 below is the one that actually exercises the pin.**
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.4: A second library's arrival re-judges the first (the real TYPE-11-02 check)

- **Setup**: common setup from 1.1, with `love.` completing, and a **second** definition library ready
  to enable (any addon; it need not be related to love2d).
- **Steps**:
  1. Complete `love.` once so love2d's snapshots are built and pinned.
  2. Settings → … → Definition Libraries: **enable the second library**, leaving love2d enabled. Apply.
  3. Without editing anything, complete `love.` again, then complete against the new library.
- **Expected**: both libraries' members are offered. The first library stayed enabled throughout, so
  nothing left scope — the only way its snapshot can reflect the new generation is if the roots tick
  discarded and rebuilt it. This is the scenario that fails if `generationTracker()` is wrong.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.2: Re-enabling brings them back without a restart

- **Setup**: continue from 3.1.
- **Steps**:
  1. Re-enable love2d. Apply. Wait for indexing.
  2. Type `love.` in `consumer.lua`.
- **Expected**: members are offered again, with no IDE restart and no cache invalidation.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 3.3: Switching the target discards library types

- **Setup**: common setup from 1.1.
- **Steps**:
  1. In `consumer.lua` confirm `string.` completes with stdlib members (`format`, `gsub`, …).
  2. Settings → … → Lua: switch the platform/version target (e.g. Standard 5.4 → Redis).
     Apply.
  3. Without editing anything, retype `string.` and then `redis.`.
- **Expected**: the offered set follows the new target. A stale stdlib member set after step 2 means
  `targetModificationTracker` is not reaching pinned snapshots (REDIS-04 §3.1a behaviour).
- ⚠ **This is a regression check on existing behaviour, not a test of this feature.**
  `targetModificationTracker` is already an unconditional dependency of `forFile` today
  (`LuaTypes.kt:216`), and §3.3 step 9 keeps it in **both** branches — so it passes on `main` and
  cannot fail because of anything TYPE-11 does, unless the implementer drops it from the *pinnable*
  branch specifically. Worth running for exactly that reason; not worth reading as evidence the pin
  works (`design.md` §8.1 TC-3).
- **Result**: ⬜ Pass / ⬜ Fail

## 4. Out-of-scope behaviour must be unchanged

### Scenario 4.1: A LuaRocks tree behaves exactly as before

- **Setup**: a project with a `lua_modules/` tree populated by `luarocks install` (e.g. `penlight`).
- **Steps**:
  1. Complete a member off a rock module (`pl.` or similar) and note the result.
  2. Edit an unrelated project file, then repeat the completion.
- **Expected**: identical behaviour to the pre-change build, including the latency. v1 leaves rocks on
  the project-wide tracker deliberately; a *speed-up* here would mean the provenance set is wider than
  designed and should be reported.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 4.2: Indexing does not leave a stuck type

- **Setup**: common setup from 1.1.
- **Steps**:
  1. File → Invalidate Caches → *Invalidate and Restart*.
  2. **While the "Indexing" progress is still running**, open `consumer.lua` and trigger completion
     on `love.` two or three times.
  3. Wait for indexing to finish completely.
  4. Trigger completion on `love.` again.
- **Expected**: step 4 offers the full love2d member set. Members missing in step 4 that were also
  missing in step 2 is the dumb-mode staleness of TYPE-11-05 — `design.md` §1.6 could not reproduce
  it automatically, so **this scenario is currently the only check that covers it**. Record the
  outcome either way; it feeds TYPE-11-DR-06.
- **Result**: ⬜ Pass / ⬜ Fail

## 5. Regression sweep

### Scenario 5.1: Nothing else moved

- **Setup**: any non-trivial Lua project.
- **Steps**:
  1. Go to Declaration on a stdlib symbol (`table.insert`).
  2. Quick Documentation on the same symbol.
  3. Hover a local to read its inferred type.
  4. Trigger the Type Hierarchy on a `---@class`.
- **Expected**: all four behave as before. They all read through `LuaTypesSnapshot` or
  `LuaTypeManager`, so a mistake in the recorder or the pinnable condition surfaces here first.
- **Result**: ⬜ Pass / ⬜ Fail
