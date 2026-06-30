---
id: BUG-135-PLAN
title: Stdlib Inlay Hints Plan
type: plan
parent_id: BUG-135
---

# Implementation Plan

## Phase 1: Filter Logic [Must]
- **Tasks**: Check virtual file system for SDK path in `LuaInlayParameterHintsProvider`.
- **Verification**: `LuaInlayHintsTest` validating `print()` shows no hints.
