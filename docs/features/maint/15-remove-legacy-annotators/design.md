---
id: MAINT-15-DESIGN
title: Remove Legacy Annotators Design
type: design
parent_id: MAINT-15
status: planned
---

# Technical Design: Remove Legacy Annotators

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.annotator.*`

## 2. Core Algorithms
1. Unregister legacy annotators from `plugin.xml`.
2. Delete the corresponding `.kt` or `.java` files.
