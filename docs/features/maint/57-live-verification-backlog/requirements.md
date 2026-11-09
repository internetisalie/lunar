---
id: "MAINT-57"
title: "57: Live Verification Backlog for Shipped Features"
type: "feature"
parent_id: "MAINT"
status: "todo"
priority: "high"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-57: Live Verification Backlog for Shipped Features

Features below are marked done, but the human-verification checklist that is their live-IDE gate
has no recorded run. Unit tests prove the logic; these checklists are the only check that the
surface registers and renders in GoLand. Each item is run with the `verify-in-ide` skill against
the feature's own `human-verification-checklists.md`. A run that finds a defect files a BUG report;
a passing run records its date and results in that checklist and ticks the plan's box.

The inventory was taken on 2026-11-08 from the docs and git history only: a blank result line or an
unticked box with no commit recording a run. Nothing was re-run to build it, so an item here may
have been checked informally without a record.

Paths are under `docs/features/`.

## 1. No recorded run

### Editor surfaces

- [x] **ROCKS-16** package browser (`rocks/16-package-browser-redesign`). Run 2026-11-09: all 8
  scenarios pass, after BUG-64–BUG-69, which the run found.
- [ ] **COMP-09** member enumeration (`completion/09-member-enumeration`). 16 of 18 results blank;
  only 1.1 and 2.3 were run.
- [ ] **TYPE-08** flow-sensitive narrowing (`type/08-flow-sensitive`). 9 results blank.
- [ ] **TYPE-10** lambda parameter inference (`type/10-lambda-parameter-inference`). 4 blank.
- [ ] **TYPE-11** library snapshot invalidation (`type/11-library-snapshot-invalidation`). 12 blank.
- [ ] **TYPE-12** member-demand clique (`type/12-member-demand-clique`). 3 blank.
- [ ] **INSP-03** type mismatch (`inspections/03-type-mismatch`). 7 blank.
- [ ] **FORMAT-07** StyLua compatibility (`formatting/07-stylua-compatibility`). 11 blank.
- [ ] **NAV-13** colon-call resolution (`navigation/13-colon-call-resolution`). Partly run: HV-5
  self and cross-file-global rows, HV-7 lag half, HV-9 steps 2, 4 and 6.
- [ ] **MAINT-26** Luacheck pipeline (`maint/26-luacheck-pipeline`). 6 blank.
- [ ] **MAINT-27** LuaCATS doc correctness (`maint/27-luacats-doc-correctness`). 6 blank.
- [ ] **MAINT-28** completion correctness (`maint/28-completion-correctness`). Plan box unticked; no
  checklist file found.
- [ ] **MAINT-32** process execution discipline (`maint/32-process-execution-discipline`). Rockspec
  typing latency and mid-build cancel; 5 blank.

### Run, debug and test

- [ ] **MAINT-24** debugger hardening (`maint/24-debugger-hardening`). HV-04, 06, 07, 08: non-ASCII
  variable, Run to Cursor, non-default port, busted live output.
- [ ] **RUN-02** run configurations (`debug/run-02-run-configurations`). Settings editor and live
  launch/debug "Not yet performed"; 12 boxes.
- [ ] **RUN-04** run-config validation (`debug/run-04-run-configuration-validation`). Checklist never
  written; flow is a Debug launch without `debug.lua` showing the run-error dialog.
- [ ] **RUN-05** test runner (`debug/run-05-test-runner`). Test Results tree, coverage viewer, output
  conversion; 16 blank.
- [ ] **REFACT-09** colon-method rename (`refactoring/09-colon-method-rename`). Conflicts dialog and
  refusal balloons; 38 boxes.

### Redis

- [ ] **REDIS-01** connections and run config (`redis/01-connections-run-config`). 15 blank.
- [ ] **REDIS-02** LDB debugger (`redis/02-ldb-debugger`). 19 blank.
- [ ] **REDIS-03** Valkey target (`redis/03-valkey-target`). 10 blank.
- [ ] **REDIS-04** language integration (`redis/04-language-integration`). Completion popup, doc popup,
  live ERROR gutter; 10 blank.
- [ ] **REDIS-05** functions workflow (`redis/05-functions-workflow`). 15 blank.
- [ ] **REDIS-06** sandbox and quickdoc (`redis/06-sandbox-quickdoc-refinements`). 5 blank.
- [ ] **BUG-381** Redis ephemeral provisioning (`bug-fixes/381-redis-ephemeral-provisioning-no-ui`).
  Live pass recorded "one step not reached"; VNC-1 (Server combo) and VNC-2 (container lifecycle)
  unticked.

### Settings and toolchain

- [ ] **TOOLING-08** settings restructure (`tooling/08-settings-restructure`). 2.2 and 4.1 not
  verified, 3.1 partial, and 1.3 not re-run after its failure was fixed as BUG-404.
- [ ] **TOOLING-03-12** terminal PATH injection (`tooling/03-execution-and-injection`). Scenario 03.1
  in `tooling/human-verification-checklists.md`.
- [ ] **TARGET-07** Lua 5.5 stubs (`target/07-lua-5.5-stdlib-stubs`). 5 blank.
- [ ] **TARGET-08** on-demand definition libraries (`target/08-on-demand-definition-libraries`).
  Offline reuse, offline balloon, browser link, untick drops resolution.

### Rocks

- [ ] **ROCKS-01, 02, 03**: plans' manual verification unticked; ROCKS-01 DR-01 (generator in the
  sandbox).
- [ ] **ROCKS-05** module resolution (`rocks/05-module-resolution`). 5 blank.
- [ ] **ROCKS-06** scenarios 4.1–4.3, which need a live rock server.
- [ ] **ROCKS-10** workspace build (`rocks/10-workspace-build`). 7 blank.
- [ ] **ROCKS-11** makefile tasks (`rocks/11-makefile-tasks`). 5 blank.
- [ ] **ROCKS-12** project view roots (`rocks/12-project-view-roots`). 6 blank.

### Checklists with no record since 2026-06-30

- [ ] **COMP-02, COMP-03, COMP-04** and the COMP epic checklist.
- [ ] **INSP-01** undeclared variable and **INSP-09** language level.
- [ ] **SYNTAX-09** Lua 5.5 and **SYNTAX-17** inferred type highlighting.
- [ ] **TYPE-09** union distribution, including plan item 3.3 (error ranges in the IDE).
- [ ] **SCHEMA-01** engine: squiggles, completion, Quick Doc.
- [ ] **BUG-134** return comma parsing.
- [ ] The **TOOL** and **ROCKS** epic checklists.
- [ ] **EDITOR-07** move statement and **DOC-01** quick documentation: single unticked live items.

## 2. Run recorded, checklist not updated

A commit records a live run, but the checklist or plan boxes were left blank. These need the
recorded results copied into the checklist, not a new run.

| Feature | Evidence |
|---|---|
| MAINT-03 | fe90a894: CL1–CL5 verified live |
| MAINT-22 | 9cb518bf: live debug verified; 3 prerequisite boxes unticked |
| MAINT-21 | plan says TC-15 and TC-16 pass; `requirements.md` box unticked |
| ROCKS-14, ROCKS-15 | 4f701461, fe284125 (both superseded) |
| SCHEMA-02 | cdb6b492; plan line 40 still "outstanding follow-up" |
| TOOLING-04, 06, 07 | 70954207, 8c65a53e, e8f4df01, a8cf99f9 |
| EDITOR-01 to 06 | d69cce1d, 0a81dc75 |
| REFACT-01 | `phase-8-live-evidence/`; plan boxes not matched to frames |
| REFACT-08 | verified live 2026-09-03; 6 results blank |

## 3. To resolve before running

- Which BUG-381 step was not reached.
- Whether TOOLING-06's live sign-off covered TOOLING-05's handed-over gate and terminal PATH.
- MAINT-34 and MAINT-35 result lines, and the inlay-hint settings items in
  `syntax/07-inlay-hints/07-09-per-category-settings` and `07-11-large-file-threshold`.

## Out of scope

Features not yet built (TARGET-09, TARGET-10, REDIS-07, DEBUG-05, AI-01, AI-03) carry their live
gate in their own plan.
