---
id: "MAINT-59"
title: "59: Cancel Superseded CI Runs"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-59: Cancel Superseded CI Runs

## Overview
Gitea 1.26 removed automatic cancellation of a superseded run (upstream PR #32751), and the forge now
runs 28.3.0. Every push to a branch, and every sync of a PR, queues a full run while the earlier
runs keep their slots. lunar's `build-plugin.yml` holds a `lunar-ci` VM for about 12 minutes per run,
and that pool runs two jobs at once, so a few quick pushes queue work nobody will read. The fix is a
`concurrency:` block with `cancel-in-progress: true` (loom.manager notice, 2026-11-10). Parent:
[[features/maint/requirements|MAINT]].

## Scope

### In Scope
- `concurrency:` in `build-plugin.yml` and `docs-lint.yml`, under both `.gitea/workflows/` and
  `.github/workflows/`, kept equivalent in effect (AGENTS.md "Edit both").

### Out of Scope
- Changing which events trigger the workflows.
- Tag builds: a `v*` tag run is never cancelled.

## Functional Requirements

| ID | Requirement | Priority | Description |
|----|-------------|----------|-------------|
| MAINT-59-01 | **Supersede on a branch** | M | A new push to a non-default branch cancels that workflow's earlier queued or running push run on the same branch. |
| MAINT-59-02 | **Supersede on a PR** | M | A new sync of a PR cancels that workflow's earlier pull_request run for the same PR. |
| MAINT-59-03 | **Events kept apart** | M | A push run never cancels a pull_request run, or the reverse: the group includes the event name. |
| MAINT-59-04 | **Never cancel main or tags** | M | Runs on `main` and on `v*` tags are never cancelled. |
| MAINT-59-05 | **Both platforms** | M | The `.gitea` and `.github` workflows get equivalent blocks. |

## Test Cases

| # | Requirement | Given (input) | When (action) | Then (expected) |
|---|-------------|---------------|---------------|-----------------|
| 1 | MAINT-59-01 | a branch push whose build is running | push again to the branch | the first run ends `cancelled`; the second runs |
| 2 | MAINT-59-02, -03 | an open PR from that branch | push once | one push run and one pull_request run; neither cancels the other |
| 3 | MAINT-59-02 | a PR whose pull_request run is queued | push again | the queued run ends `cancelled` |
| 4 | MAINT-59-04 | two merges to `main` in quick succession | both push runs | both run to completion |
| 5 | MAINT-59-04 | a `v*` tag pushed | another push to main | the tag run is not cancelled |

## Acceptance Criteria
- [ ] Test cases 1–5 observed on gitea.internetisalie.net, with run ids recorded.
- [ ] The GitHub copies carry the same group and cancel rule.

## Dependencies
- **loom LGR-112** (blocked_by). loom is measuring, on this forge, whether an expression evaluates in
  `cancel-in-progress` and what `github.ref` holds for a pull_request run, and will send the tested
  form. Use that form rather than an unproven one.

## Risks
- `${{ }}` is not evaluated by the `lunar-ci` executor (AGENTS.md). `concurrency:` is applied by the
  Gitea server, not the executor, so this should not matter, but test case 1 confirms it on that lane.
