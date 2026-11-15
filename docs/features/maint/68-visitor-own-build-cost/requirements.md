---
id: "MAINT-68"
title: "68: Visitor Cost of Slow Files"
type: "feature"
status: "todo"
priority: "medium"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-68: Visitor Cost of Slow Files

## Overview
Some slow files spend their own build in the visitor (`file.accept` in
`LuaTypesVisitor.buildSnapshot`), not in the checker, and often on the first build only (MAINT-61
risks DR-01, DR-05). That points at first-touch work rather than at the visit itself.

These are the MAINT-68 rows of MAINT-61's
[files-over-target.md](../61-own-build-cost/files-over-target.md), which lists the hubs and the rule.
The verdict files them under MAINT-61 (MAINT-61 risks Gap 2.2).

## Requirements
To be written with the `plan-feature` skill. The first task is to profile the first build of these
hubs by visitor function, as MAINT-61 DR-01 did for the checker.
