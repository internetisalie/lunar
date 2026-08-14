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
  feature: **746 ms** to first element (design §1.9), and **491 ms** median of five cold receivers on
  the re-plan's fixture (§1.10.2). *(An earlier revision said ~12.9 s; that is time-to-**exhaustive**,
  which is not what this scenario times, and any improvement would have "passed" against it.)* The
  complete list settles ~31 ms later — §1.9 measured the gap.
- **Fixture measurement for comparison**: the armed prototype gave **7.4 ms** median of five cold
  wide receivers (§1.10.2). A stopwatch cannot resolve that; what this scenario checks is that the
  *pause is gone*, not the microseconds.
- **Result**: **RUN 2026-08-14, live GoLand 2026.1.3 on the builder VM.** ✅ **Pass on intent,
  ❌ fail on the stated number.** Timed by cropped `scrot` frames at 25–50 ms with ms stamps either
  side, so each figure is a bracketing window, not a point.

  | Measurement | Observed |
  | :-- | --: |
  | `ngx.` — **first completion of the session** | **≈1.1 s** (absent 1069–1114 ms, present 1115–1147 ms) |
  | `love.` — a second, never-completed wide receiver, same session | **≈130 ms** |
  | `ngx.` — warm repeat | **≈150 ms** |

  **The 1.1 s is session warmup, not enumeration**, and `love.` is the control that proves it: a
  genuinely cold, never-touched wide receiver in the same session pops in ~130 ms. Per-receiver
  enumeration cost would have charged `love.` too. Time-to-first == time-to-complete-list — the
  first popup frame is byte-identical to every later one.

  Against this scenario **as literally written** (restart the IDE, time the first receiver, expect
  <100 ms) it **fails at 1.1 s**. Against what the feature owns, the pause is gone: ~130 ms, and
  indistinguishable cold vs warm — the popup reads as "already there", and typing straight through
  to filter never outran it. **The 7.4 ms / 12 ms harness medians are not confirmable by stopwatch
  and are not confirmed here.** The once-per-session warmup is called out separately rather than
  folded into a pass; it is class-loading/JIT of the completion path and belongs to no phase of this
  feature. Real material only — `ngx.` (135 declared members) and `love.` (40), both sha256-verified
  against the shipped catalog. **The ≥200 KiB / large-receiver clause could not be discharged**: no
  wxLua library exists (TARGET-10 is at Phase 0), and inventing one would have made this a fixture
  measurement again.

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
- **Expected**: **comparable**. DR-02a measured 41 ms for the small receiver and 1 641 ms for the
  large one on today's code — one inside the 100 ms budget, one far outside it, which
  `non-functional.md` forbids outright since time-to-first must be independent of candidate count.
  *(An earlier revision called that "a 40x spread". The ratio is **retired** as a quotable quantity —
  DR-08's standing consequence — and survives only as a pre-Phase-0 record; the two absolute figures
  and which side of the budget each falls on are what this scenario is checking.)* This is the clause
  most likely to still be violated after the fix, because it is the one nothing before this feature
  ever checked.
- ⚠ **Do not read a verdict off the stopwatch here.** The automated form of this assertion was
  re-derived as a **count** (design §4.10a-bis) precisely because its timing form flipped verdict
  across three runs of identical code (1x / 3x / 4x against the same derived 2x floor). Armed, the
  prototype measured 6 214 µs narrow and 7 414 µs wide — a difference smaller than a human can time.
  Record both numbers; the binding assertion is `LuaReceiverMemberWork.entries`, not this.
- **Result**: ⬜ Pass / ⬜ Fail — small: ______  large: ______

### Scenario 1.5: The `require`-bound receiver — REWRITTEN 2026-08-12, the "slow tier" is withdrawn

- **Setup**: as 1.1, plus a library file containing `Helper = require("some.module")` and a project
  file that uses `Helper.` **alongside at least one ordinary receiver such as `wx.`**. The second
  receiver is the point of the scenario, not incidental.
- **Steps**:
  1. Restart so nothing is cached. Time `Helper.` to first suggestion.
- **Expected**: **under 100 ms, like everything else.** An earlier revision exempted this receiver as
  "tier 2" and asked only whether it *felt* acceptable. That exemption is **withdrawn** (design
  §4.12): measured at the new change site it lands at 25–61 ms armed against 121–130 ms unarmed, with
  no work specific to it. It improves because the *other* receivers in the file stop forcing the
  consumer file's whole type-graph build — which is why the setup needs one.
- ⚠ **If this is over 100 ms, that is a defect to file, not a contract to widen.** The one case not
  covered is a file whose *only* receivers are opaque, which has nothing to ride on — that is
  **DR-24**, and if this scenario is slow, check whether the file has any non-opaque receiver before
  concluding anything.
- **Result**: ⬜ Pass / ⬜ Fail — time observed: ______

### Scenario 1.6: A big file that shadows a library name (Rule S's residual)

- **Setup**: a **large** project file (2 000+ lines) which declares `local wx = { … }` at the top,
  in a project that also has the wxLua library enabled.
- **Steps**:
  1. Restart. Type `wx.` inside that file and time to first suggestion.
  2. Confirm the offered members are the **local's**, not the library's.
- **Expected**: the local's members, and no perceptible pause. This is the one arm where Rule S's
  O(file) walk runs (design §4.14) — measured at 8–16 ms on a 4 002-line file, on top of a path that
  costs 124 ms without it. If it feels slow, that is **DR-23**.
- **Result**: ⬜ Pass / ⬜ Fail — time: ______  members offered: ______

### Scenario 1.7: Shadowing, by every binding form (COMP-09-10)

- **Setup**: a project file declaring `Shadow = {}` and `function Shadow.fromLibrary() end`; a
  separate consumer file.
- **Steps**: in the consumer, one at a time, complete `Shadow.` under each of —
  1. `local Shadow = { fromLocal = 1 }`
  2. `local function Shadow() end`
  3. inside `local function f(Shadow) … end`
  4. inside `for Shadow in pairs({}) do … end` and `for Shadow = 1, 2 do … end`
  5. after the consumer's own `Shadow = { fromThisFile = 1 }`
  6. with the consumer binding nothing at all
  7. inside `function C:m() … end`, completing `self.` (Rule S's `name == "self"` clause)
  8. inside `if type(Shadow) == "table" then … end`, after `local Shadow = { fromLocal = 1 }`
- **Expected**: 1–5, 7 and 8 offer **only what that file's own binding provides** and never
  `fromLibrary`; 6 offers `fromLibrary`. This is the rule the aborted Phase 2 was forbidden from
  inventing implicitly; TC 10a–10j automate it, and this scenario is the eyes-on confirmation that
  the popup a user actually sees agrees. Cases 7 and 8 were added 2026-08-12 — they are the two
  clauses the `LuaParList` mutation proof cannot reach (TC 10i, TC 10j).
- **Result**: ⬜ Pass / ⬜ Fail — any case that leaked `fromLibrary`: ______

### Scenario 1.8: A Redis target — the ambient globals and the `@class` stub (TC 10h, TC 7f)

- **Setup**: **Settings → Languages & Frameworks → Lua**, set the target to **Redis 7+**; a script
  file in the project. Nothing else — `KEYS`, `ARGV` and `redis` all come from the bundled stubs.
- **Steps**:
  1. Complete `KEYS.` and `ARGV.` (and both at `:`).
  2. Complete `redis.` and `redis:`.
- **Expected**: (1) offers **nothing**, exactly as today — this is the one `LuaScope.declare` site
  Rule S deliberately excludes (`seedAmbientGlobals`), and it is live only on Redis/Valkey targets,
  so it is invisible on the default STANDARD target every other scenario here uses. (2) `redis.`
  offers the thirteen functions **plus** the ten `@field` constants (`LOG_DEBUG`, `REPL_ALL`,
  `REDIS_VERSION`, …) — a **declared change**, design §4.5a's third instance; `redis:` offers
  the thirteen functions only, unchanged. If `redis:` gains the constants, the syntactic `isColon`
  filter is wrong, not the index.
- ⚠ **"Thirteen" is a Redis 7+ number.** The bundled `redis.lua` declares **10** functions on Redis 5,
  **11** on Redis 6, **13** on Redis 7+ and **12** on both Valkey targets (design §1.10.8a). Count the
  *delta* — ten constants appear at `.` and none at `:` — rather than matching an absolute total.
- **Result**: ⬜ Pass / ⬜ Fail — `KEYS.` offered: ______  `redis.` count: ______  `redis:` count: ______

### Scenario 1.9: A Valkey target — `server.` must NOT move (TC 7f-bis, the control for Scenario 1.8)

- **Setup**: **Settings → Languages & Frameworks → Lua**, set the target to **Valkey 8**; a script file.
- **Steps**:
  1. Complete `server.` and `server:`. Then complete `redis.` in the same file.
- **Expected**: **`server.` and `server:` are unchanged** — `server.` offers its ten constants and its
  eleven functions exactly as today, `server:` the eleven functions. `redis.` *does* gain its ten
  constants. The two receivers carry the **same ten `---@field` declarations**; the only difference is
  that `server.lua` also writes `server.LOG_DEBUG = 0`-style assignments, so the constants were
  already visible to today's global door and to design §4.3's source 2 (measured, §1.10.8a).
- ⚠ **If `server.` moves, the diagnosis is not "the `@field` source is wrong"** — it is that source 2
  or the first-declaring-file selection stopped seeing the assignments. Check `server.LOG_DEBUG`
  specifically before concluding anything about `@field`.
- **Result**: ⬜ Pass / ⬜ Fail — `server.` count: ______  `server:` count: ______  `redis.` count: ______

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
- **Result**: **RUN 2026-08-14.** ✅ **Acceptable as shipped — with one caveat that is not about
  the type text.**

  Three things only visible live:
  1. **The loss is not library-specific.** A cross-file member of a plain *project* global renders
     with no type text too. It is the whole index arm, exactly as design §4.5 says.
  2. **The icon carries more than expected.** Rows split visibly into field (`ⓕ`) and method (`ⓜ`).
     On love2d — where nearly every member is a function — that alone answers what the type text
     would have, so the row loses almost nothing.
  3. **The fallback decides it, and it is uneven.** `Ctrl+Q` on a love2d member gives the whole
     signature (`function love.getVersion() : number, number, number, string`, with per-return
     docs). `Ctrl+Q` on **every** openresty member tried returns **"No documentation found."** —
     including `ngx.status` (a real `---@field`) and `ngx.say` (a real `function ngx.say`). The
     inferred type after insertion is degraded too: `local b = ngx.status` shows `nil | string`,
     `local z = ngx.say` shows `fun(...)`.

  **Verdict from use, not from the design:** dropping the type string off the row did not read as a
  regression — name plus field/method icon is enough to pick a member, and one keystroke restores
  the signature. What should **not** be signed off is the assumption underneath: the trade is only
  cheap *because Quick Doc is there*, and on an openresty-shaped library it is not. A row reading
  `status` with a blank right column and nothing behind `Ctrl+Q` leaves the user with a name and
  nothing else. **That argues for fixing the doc path, not for putting a type back in the index
  value.**

### Scenario 2.4: Go-to-declaration and gutter markers still work

- **Setup**: a library `---@class Base` with a method overridden by `---@class Derived`.
- **Steps**:
  1. Ctrl+B on a member completed from the library.
  2. Look for the override gutter marker on the derived method and click it.
- **Expected**: navigation lands in the library file; the gutter marker appears and navigates.
  `sourceElement` feeds `LuaOverrideLineMarkerProvider` (design §4.1), and
  `LuaTypeManagerImpl.materializeClass` (`LuaTypeManagerImpl.kt:341`, comment at `:357-361`)
  warns the parity harness compares names and types only — so this is checkable *only* by hand.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.5: A `@field` that did not complete before

- **Setup**: a library with `---@class Derived : Base`, `---@field ownField number` and
  `Derived = {}` at top level.
- **Steps**:
  1. Type `Derived.` and invoke completion.
- **Measured before the change** (DR-14): `[ownFn]` — the `@field` is **absent**, because the global
  door builds the type from the assignment and never reads the `@class` comment.
- **Expected after**: `[Show, ownField, ownFn]`. A **deliberate new member** on this path (design
  §4.5a), declared rather than discovered. Inherited members from `Base` are still absent, and that is
  also unchanged — they were never on this door.
- **And check `Base` too — EXTENDED 2026-08-12.** With `---@class Base` carrying
  `---@field inheritedField string` and `---@field onClose fun(): nil`, `Base.` moves from
  `[Show, inheritedFn]` to `[Show, inheritedField, inheritedFn, onClose]`, and **`Base:` moves from
  `[Show, inheritedFn]` to `[Show, inheritedFn, onClose]`** — a `@field` whose type text starts
  `fun(` is indexed as a function and survives the colon filter. Both are declared (TC 7d/7e). This
  is the one place the index arm's *syntactic* colon filter visibly differs from the graph arm's
  *semantic* one; if `onClose` looks wrong to a human at `Base:`, say so here — the automated tests
  will happily assert whichever we choose.
- **Result**: ⬜ Pass / ⬜ Fail — `Derived.`: ______  `Base.`: ______  `Base:`: ______

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
- **RE-MEASURED at Phase 4 (2026-08-12), after Phase 2's hoist, and it held.** DR-12's figures were
  taken before global receivers moved onto the index arm, so they were re-run rather than carried
  forward: `v.` = `[__add, len, x]` and `v:` = `[len]` for the class declared on a local **and** on a
  global. The receiver-name carets on the global form are `Vec.` = `[__add, len, x]` and
  `Vec:` = `[__add, len]` — `Vec:` keeping `__add` is scenario 2.x's syntactic-colon-filter
  divergence (the field's type text starts `fun(`), not a metamethod-visibility change. If `__add`
  at `Vec:` looks wrong to a human, say so here; it is the same judgement call as `onClose`.
- **Also worth a human's eye**: the step above writes `local a, b = ...` typed as `Vec`. Do **not**
  type them by calling the class (`local a, b = Vec(), Vec()`) — that infers `Undefined`, which
  absorbs every diagnostic, so the scenario would "pass" against a build with no implementation at
  all. Annotate the locals with `---@type Vec`.
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
