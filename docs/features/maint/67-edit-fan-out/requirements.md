---
id: "MAINT-67"
title: "67: Edit Fan-Out Through Pending Frames"
type: "feature"
status: "todo"
priority: "high"
parent_id: "MAINT"
folders:
  - "[[features/maint/requirements|maint]]"
---

# MAINT-67: Edit Fan-Out Through Pending Frames

## Overview
In factoryplanner, an edit rebuilds most of the member, and no single build dominates the read.
Most of those rebuilds do not follow from a recorded source: they come from the pending frames
that `LuaSourceSetTracker` walks (MAINT-63). The evidence is MAINT-61 risks DR-02 and DR-03.

MAINT-61's change sweeps file these passes under MAINT-61 (MAINT-61 risks Gap 2.2). They are
the MAINT-67 rows of MAINT-61's [files-over-target.md](../61-own-build-cost/files-over-target.md).
Related: [[MAINT-66]], which covers cycle answers read from half-built results.

## Requirements
To be written with the `plan-feature` skill. MAINT-61 DR-02 and DR-03 are the starting evidence.
