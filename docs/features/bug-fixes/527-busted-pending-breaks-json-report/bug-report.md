---
id: "BUG-527"
title: "A busted spec that calls `pending(...)` produces no test tree: busted 2.3.0's JSON handler fails to encode the report"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "low"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-527: a spec with `pending(...)` produces no test tree

## 1. Reproduction

Seen on the builder with busted 2.3.0 (`/usr/local/bin/busted`) while capturing fixtures for BUG-526.

1. Create `spec/pend_spec.lua`:
   ```lua
   describe("outer group", function()
     it("passes", function()
       assert.are.equal(1, 1)
     end)
     pending("is not done yet")
   end)
   ```
2. Run `busted --output=json spec/pend_spec.lua`, or run the spec from Lunar with
   *Run 'Lua Tests: pend_spec.lua'*.

## 2. Expected vs Actual
- **Expected**: the test tree shows `passes` as passed and `is not done yet` as ignored (pending).
- **Actual**: busted prints `Failed to encode test results to json: type 'function' is not supported
  by JSON.` instead of a report, and exits 0. Without a JSON report, Lunar builds no test tree; the
  console output is all the user sees.

Reproduced with exactly this file on 2026-10-26. In the larger BUG-526 fixture spec,
`busted --output=json --filter="is not done yet"` also reproduced it on its own, so the `pending`
entry is the cause. A spec with an `it("name")` that has no function body produced a report with no entry for
that test.

## 3. Notes

- This is likely an upstream busted defect. `outputHandlers/base.lua`'s `copyElement` drops
  function-valued fields only at the top level of the element. A nested function value in a pending
  entry would then reach `dkjson`, which rejects it. This has not been confirmed.
- A Lunar-side fix depends on the route taken. RUN-09 (a Lunar-shipped busted output handler) would
  not use busted's JSON encoder. Short of that, Lunar could detect the encoder failure message and
  tell the user why the tree is empty.
- Root cause and fix strategy are not established. Run `plan-bug` before fixing.
