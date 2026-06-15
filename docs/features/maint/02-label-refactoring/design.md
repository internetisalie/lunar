---
id: MAINT-02-DESIGN
title: Label Refactoring Design
type: design
parent_id: MAINT-02
status: planned
---

# Technical Design: Label Refactoring

## 1. Architecture Overview
- **Component**: `LuaLabelStat` (PSI)

## 2. Core Algorithms
1. Ensure `LuaLabelStat` implements `PsiNameIdentifierOwner`.
2. Implement `setName(String name)` method which creates a new label PSI element and replaces the old one.
3. The platform's standard `RenameProcessor` will handle updating `goto` references via `PsiReference.handleElementRename`.
