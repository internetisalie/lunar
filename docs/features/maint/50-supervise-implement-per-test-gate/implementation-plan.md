---
id: "MAINT-50-PLAN"
title: "Implementation Plan"
type: "plan"
parent_id: "MAINT-50"
folders:
  - "[[features/maint/50-supervise-implement-per-test-gate/requirements|requirements]]"
---

# MAINT-50: Implementation Plan

Phase 1 is a normal PR of tracked tooling. Phase 2 edits the gitignored skill. Neither touches product
code, so `build-plugin.yml` on the PR is Phase 1's gate. Any probe edit uses `temporary-edits`.

## Phases

### Phase 1: Script, tests and CI step [Must]
- **Goal**: `unit_gate.sh` on `main`, so the skill can freeze it.
- **Tasks**:
  - [x] From the proof branch (design §2), bring `tooling/ctrf/unit_gate.sh` (mode 755) and
    `test_unit_gate.py` onto a feature branch unchanged:
    `git checkout proof/maint-50-gate -- tooling/ctrf/unit_gate.sh tooling/ctrf/test_unit_gate.py`.
    Leave `mutants_unit_gate.py` on the proof branch.
  - [x] Add design §5's step to both workflows.
  - [x] Add to `tooling/ctrf/README.md` a `# Supervise-implement gate (MAINT-50)` section naming the
    subcommands and `Tests: cd tooling/ctrf && python3 -m unittest test_unit_gate`.
  - [x] Run the tests, then from a proof-branch worktree `python3 tooling/ctrf/mutants_unit_gate.py`.
  - [x] Open the PR with message `feat(maint-50): the supervise-implement gate script`.
- **Exit criteria**: requirements TCs 3–18, 22 and 23 pass; mutants print `bad 0 of` with no `SURVIVED`;
  the PR's CI is green and it merges.

### Phase 2: Apply the skill edit and close [Must]
- **Goal**: the skill carries design §4.
- **Tasks**:
  - [ ] Run TC 20 with credentials loaded in a subshell.
  - [ ] Snapshot `/home/mini/Documents/src/lua/lunar/.agents/skills/supervise-implement/SKILL.md` to
    `~/.cache/claude-scratch/lunar/maint-50-tc/SKILL.before.md`.
  - [ ] Edit that path per design §4.1–§4.5. Make the edit in that path: `.agents` in a worktree is a
    symlink to it.
  - [ ] Run TC 21 and TC 24, then walk TC 19 on a throwaway branch.
  - [ ] In `docs/features/maint/requirements.md`, set the MAINT-50 row to **Full**; in
    `docs/roadmap.md`, delete the MAINT-50 row; set this feature's `requirements.md` `status: done`
    and its Status column to Full.
  - [ ] Commit the docs: `docs(maint-50): the supervise-implement gate is per test`.
- **Exit criteria**: TCs 19–21 and 24 pass; `python3 scripts/lint_docs.py docs` reports 0 errors; the commit's
  tree holds every doc named above.

## Requirement → Phase Coverage

| Requirement | Priority | Delivered in |
|-------------|----------|--------------|
| MAINT-50-02, -03, -04, -05, -06, -08, -09 | M | Phase 1 |
| MAINT-50-01, -07 | M | Phase 2 |
