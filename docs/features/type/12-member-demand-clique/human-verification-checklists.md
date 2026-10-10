---
id: "TYPE-12-CHECKLIST"
title: "12: Verification checklists"
type: "qa"
parent_id: "TYPE-12"
folders:
  - "[[features/type/12-member-demand-clique/requirements|requirements]]"
---

# Verification Checklists: TYPE-12 — The member-demand clique

Run in GoLand on the libvirt builder VM per the `verify-in-ide` skill. Every scenario is run twice:
first with the plugin jar built from the implementation base (the **pre-change build**), then with the
jar from the branch under test. Confirm `Loaded custom plugins: lunar` in `idea.log` after each
hot-swap, before anything is read.

Fixtures are the `paramN(n)` and `annotated(n)` texts in design Appendix C, saved as
`paramN160.lua`, `paramN320.lua` and `annotated20.lua` in a fresh project. In `paramN(n)`, line 5 is
`function Builder:setName(n) return self end`, line 6 is `local b = Builder`, and call `i` is on line
`7 + i`.

## 1. Latency

### Scenario 1.1: A 320-call annotated file highlights promptly
- **Setup**: open `paramN320.lua`.
- **Steps**:
  1. Take a screenshot at +5 s, +15 s and +45 s after the editor opens, on each build.
- **Expected**: on the branch build, by +15 s, line 5 carries the same error markers the pre-change
  build shows once its highlighting completes. Record the time each build took. For scale, BUG-473
  DR-7 measured 20–45 s on its own 320-call fixture before this feature.
- **Result**: ⬜ Pass / ⬜ Fail

## 2. What the engine says is unchanged

### Scenario 2.1: The `---@param` violation is reported on the declaration, as before
- **Setup**: `paramN160.lua`.
- **Steps**:
  1. Wait for highlighting to complete; open the Problems tool window (current file).
  2. Record every entry whose text is one of the messages below, with its line.
- **Expected**, on both builds and identical between them — exactly these entries, all on line 5:
  - `number is not assignable to string` on the parameter `n` (column 26) — *Type assignability*;
  - `number is not assignable to string` on the declaration — *Return type mismatch*;
  - `string | number is not assignable to string` on the declaration — *Return type mismatch*;
  - `{ ... } | Builder is not assignable to Builder` on the declaration — *Return type mismatch*.

  No entry with any of these messages on any call line (7–166).
- **Why this is the expectation**: these are the ERROR-tier entries today's engine and the chosen
  design both report for `paramN(160)` (risks-and-gaps DR-02). An entry anchored on the declaration
  is surfaced by `LuaReturnTypeMismatchInspection`, because `isReturnRelated` holds for a
  `LuaFuncDecl` (`TypeErrorClassification.kt:23-27`); the one on `n` by
  `LuaTypeAssignabilityInspection`. Per-argument conflicts at call lines are HYPOTHESIS, which both
  inspections skip (`LuaTypeAssignabilityInspection.kt:32`, `LuaReturnTypeMismatchInspection.kt:30`).
  `addError` keeps one entry per `(element, message)` (`LuaTypeGraph.kt:272`), so the declaration
  carries each message once however many calls there are.
- **Result**: ⬜ Pass / ⬜ Fail

### Scenario 2.2: The annotate-it intention is offered at a later call site, as before
- **Setup**: `annotated20.lua` (no `---@param`).
- **Steps**:
  1. Put the caret on `:setName` of the **last** call line; press Alt+Enter. Record the list.
  2. Do the same on the first call line.
- **Expected**: the lists are identical between the two builds, on both lines. This is a sanity check
  only: several HYPOTHESIS entries anchor on each `:setName`, so it cannot isolate the replicated
  one — requirements cases 2, 4 and 9 do that.
- **Result**: ⬜ Pass / ⬜ Fail
