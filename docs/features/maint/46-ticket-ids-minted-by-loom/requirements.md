---
id: "MAINT-46"
title: "46: Ticket IDs Minted by loom"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "medium"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-46: Ticket IDs Minted by loom

## The gap

A new `BUG-n` is chosen by hand: the next number after the highest, or, since 2026-10-29, the
lowest number below 400 that nothing refers to. Both read a branch snapshot, so two sessions
filing at once can pick the same number, and nothing records that a number was used if its draft
was abandoned.

## Scope

- **Mint with `loom-cli id mint BUG`.** It claims the lowest unclaimed number in the prefix's range
  with a conditional create in the artifact store, so concurrent callers never get the same ID
  (loom L-073, `docs/features/tooling/id-allocation/design.md` §3.1). It fills the unused low
  numbers by itself.
- **A project card.** Lunar has no `.loom.yml`. It needs one declaring the project name and the
  `BUG` prefix's range; mint pads to the width of the range maximum. Other prefixes (`MAINT`,
  `TYPE`, …) are out of scope unless the card makes them free.
- **Backfill every number already used**, before the first mint: every
  `docs/features/bug-fixes/<n>-*` directory plus every `BUG-<n>` in `git log --all -p`, the current
  tree, `.agents/` and the agent memory (matching `BUG-0*<n>([^0-9]|$)`). The manifest is generated
  by a script from that census, not assembled by hand. Mint scans upward from the range minimum,
  so a partial backfill hands out a used number.
- **Store access.** The `lunar-ci` MinIO user covers only item and commit keys; minting writes
  `claims/ticket/v1/lunar/BUG/<digits>`. Decide whose credentials mint, and get that prefix granted.
- **Skills.** `report-bug` and `plan-bug` mint instead of choosing a number.

## Not established

- Whether existing unpadded IDs (`BUG-8`) and mint's padded output (`BUG-008`) can coexist, or the
  card's range must be chosen so no padding occurs.
- Whether the backfill's `filed` / `unfiled` / `burned` kinds need distinguishing for Lunar.
