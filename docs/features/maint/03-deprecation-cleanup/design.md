---
id: MAINT-03-DESIGN
title: Deprecation Cleanup Design
type: design
parent_id: MAINT-03
status: planned
---

# Technical Design: Deprecation Cleanup

## 1. Architecture Overview
- **Component**: Multiple actions

## 2. Core Algorithms
1. Replace `dataContext.getData(DataConstants.PROJECT)` with `CommonDataKeys.PROJECT.getData(dataContext)`.
