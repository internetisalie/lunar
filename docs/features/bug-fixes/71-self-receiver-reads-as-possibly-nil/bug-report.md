---
id: "BUG-71"
title: "A method's self receiver reads as possibly nil"
type: "bug"
parent_id: "BUG"
status: "todo"
priority: "medium"
folders:
  - "[[features/bug-fixes|bug-fixes]]"
---

# BUG-71: A method's self receiver reads as possibly nil

Found by MAINT-55 DR-07 (2026-11-09), which switched Lunar's possibly-nil check on `.f`, `:m` and calls to LuaLS's `need-check-nil` rule and swept the corpora. Lunar ships with that check off (MAINT-55 deviation `uncertain-nil`); this defect is one reason it cannot be turned on, and fixing it is a precondition for re-measuring.

## 1. Reproduction
Corpus site: `zerobrane/src/editor/findreplace.lua:526`, `self.toolbar` inside a `function T:m()` method. 281 of the sweep's 1180 reports had `self` as the receiver.

## 2. Expected vs Actual Behavior
- **Expected**: `self` in a colon-declared method is the receiver and never nil.
- **Actual**: A nil reaches `self`, so a member read through it is reported as possibly nil.
