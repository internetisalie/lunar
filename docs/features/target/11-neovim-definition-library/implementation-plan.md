---
id: "TARGET-11-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "TARGET-11"
folders:
  - "[[features/target/11-neovim-definition-library/requirements|requirements]]"
---

# TARGET-11: Implementation Plan

**Precondition:** [[BUG-517]] is merged before Phase 2's cost check (TC 9) and before this feature
merges. Phase 1 does not wait for it. Every other fix this feature rests on is already merged
(requirements, Dependencies).

## Phases

### Phase 1: Entry, invariant, tests [Must]
- **Goal**: the catalog carries `neovim`, and the routine suite proves it registers `runtime/lua`.
- **Tasks**:
  - [x] Append design §2.1's JSON block to `src/main/resources/definitions/lunar-definitions-catalog.json`.
  - [x] In `LuaDefinitionCatalogLoaderTest`, replace the `/library` assertion and add
        `neovimEntryIsPinnedToNeovim0126` (design §2.2).
  - [x] Add the KDoc sentence of design §2.3.
  - [x] Create `src/test/kotlin/net/internetisalie/lunar/definitions/NeovimDefinitionLibraryTest.kt`
        (design §2.4).
  - [x] Mutation proof (`mutation-proof` skill): apply each mutation of requirements TC 1–6 in turn,
        run `NeovimDefinitionLibraryTest` and `LuaDefinitionCatalogLoaderTest`, confirm the named case
        fails **at its assertion** (not in `setUp` or provisioning), and restore with the
        `temporary-edits` skill.
  - [x] **If a mutation leaves its case green, or fails it before the assertion, stop.** (Not
        triggered: every mutation turned its case red at the assertion.) Do not edit
        the expectation, the fixture or the mutation to force red. Report the mutation, the case and
        the run log to the supervisor as a planning defect.
- **Exit criteria**: `tooling/gce-builder/gce-builder.sh run test` green (full suite, `--rerun`);
  `run ktlintCheck` green; TC 1–6 pass; each listed mutation observed red at its assertion once.

### Phase 2: Real archive, live check, release note [Must]
- **Goal**: the real pin fetches and works in GoLand.
- **Tasks**:
  - [x] Run design §4.1's command (TC 7); paste its output into the PR description.
  - [x] Run [human-verification-checklists.md](human-verification-checklists.md) Scenario 1.1 (TC 8)
        per the `verify-in-ide` skill; record result, date and evidence in the checklist.
  - [x] Add the `CHANGELOG.md` entry per the `changelog` skill: "Neovim (`vim.*`) definition
        library: enable it under Settings ▸ Languages & Frameworks ▸ Lua ▸ Lua Project ▸ Definition
        Libraries to declare `vim` and its direct members." (TARGET-11-07).
  - [x] With BUG-517 merged into the branch, run design §4.2 (TC 9) and restore its temporary edits.
        Paste the four `elapsedMs` values and the ratio into the PR description.
  - [x] **If the ratio is above 1.5, stop.** (Not triggered: the ratio was 1.29.) Do not raise
        the bound or change the measurement. Report the four values and the run logs to the
        supervisor.
  - [x] **If §4.2 cannot be carried out, stop.** (Not triggered.) That covers a non-zero exit
        from `fetch-corpus.py` or `fetch-luac.py`, a failed build or test, and a run with no
        `elapsedMs=` line. Report the step and its output to the supervisor. Never skip TC 9, and
        never substitute another measurement.
- **Exit criteria**: TC 7 prints design §4.1's expected line; Scenario 1.1 passes; the TC 9 ratio is
  ≤ 1.5.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
| :-- | :-- | :-- |
| TARGET-11-01 | M | Phase 1 |
| TARGET-11-02 | M | Phase 1 |
| TARGET-11-03 | M | Phase 1 |
| TARGET-11-04 | M | Phase 1 |
| TARGET-11-05 | M | Phase 2 |
| TARGET-11-06 | M | Phase 2 |
| TARGET-11-09 | M | Phase 2 |
| TARGET-11-07 | S | Phase 2 |
| TARGET-11-08 | W | — (BUG-505, BUG-507) |

## Verification Tasks
- [x] TC 1, 2 — `LuaDefinitionCatalogLoaderTest`.
- [x] TC 3–6 — `NeovimDefinitionLibraryTest`.
- [x] TC 7 — design §4.1 command.
- [x] TC 8 — human-verification-checklists.md.
- [x] TC 9 — design §4.2 probe.

## Task Summary

| Phase | Status | Priority |
| :-- | :-- | :-- |
| Phase 1: Entry, invariant, tests | done | Must |
| Phase 2: Real archive, live check, release note | done | Must |
