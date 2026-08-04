---
id: "MAINT-34-HVC"
title: "Human Verification Checklists"
type: "qa"
parent_id: "MAINT-34"
folders:
  - "[[features/maint/34-luacats-extraction-unification/requirements|requirements]]"
---

# MAINT-34: Human Verification Checklists

MAINT-34 is a refactor with two user-visible consequences: inheritance that currently breaks
depending on whether a file is open (BUG-402), and the `sourceElement` provenance that override
navigation depends on. Both need eyes in a running IDE — the unit suite cannot see gutter icons or
"file happens to be open" behaviour.

Run in the containerized/VM GoLand per the **verify-in-ide** skill. Record pass/fail and the date.

## Scenario 1 — BUG-402: inheritance no longer depends on which file is open

Fixture, in a project file `defs.lua` **that you do not open**:

```lua
---@class Container<K, V>
---@field size integer
local Container = {}

---@class StringMap : Container<string, number>
---@field label string
local StringMap = {}

return StringMap
```

In a separate `main.lua`:

```lua
---@type StringMap
local m
m.
```

| # | Step | Expected |
| :-- | :-- | :-- |
| 1.1 | With `defs.lua` **closed**, complete after `m.` | `label` **and** `size` offered (before the fix: `size` missing) |
| 1.2 | Open `defs.lua` in a tab, return to `main.lua`, complete again | Identical list to 1.1 — **the point of the fix is that these agree** |
| 1.3 | Close `defs.lua`, invalidate caches and restart, complete again | Identical list |

> 1.1 vs 1.2 is the whole feature. If they differ, the stub and AST paths still disagree.
> Note DR-01: if `Container<string, number>` cannot resolve at all, `size` may be absent in
> **both** — that is a *different* defect, and 1.1 ≡ 1.2 still passes this scenario.

## Scenario 2 — Override navigation still works (R4 / `sourceElement`)

```lua
---@class Base
---@field describe fun(): string
local Base = {}

---@class Derived : Base
local Derived = {}

function Derived.describe() return "x" end
```

| # | Step | Expected |
| :-- | :-- | :-- |
| 2.1 | Look at the gutter beside `function Derived.describe()` | The override marker is present |
| 2.2 | Click it | Navigates to the `---@field describe` **tag line** in `Base`, not merely to `local Base = {}` |
| 2.3 | Repeat with `Base` declared in a **closed** file | Marker present; target is the host declaration (stub path has no tag PSI — this asymmetry is intended) |

## Scenario 3 — Optional and keyed fields still behave (regression guard)

| # | Step | Expected |
| :-- | :-- | :-- |
| 3.1 | `---@field beta? number`, complete the member | Offered as `beta`, never `beta?` |
| 3.2 | Hover / quick-doc that field | Rendered as `beta?` — the display form deliberately keeps the marker |
| 3.3 | `---@field [string] number`, quick-doc the class | The keyed field renders with its key, not the literal word "Unknown" (only if DR-02 confirmed the gap and Phase 5 shipped) |

## Scenario 4 — Stub version bump takes effect

| # | Step | Expected |
| :-- | :-- | :-- |
| 4.1 | Install the new build over a sandbox that ran the previous one | Indexing runs on first open; no stale-stub exceptions in `idea.log` |
| 4.2 | `grep -i 'stub' idea.log` after indexing | No `SerializerNotFoundException` / stub-mismatch stack traces through `net.internetisalie.*` |

## Sign-off

| Scenario | Result | Date | Notes |
| :-- | :-- | :-- | :-- |
| 1 — inheritance parity | | | |
| 2 — override navigation | | | |
| 3 — optional / keyed fields | | | |
| 4 — stub version bump | | | |
