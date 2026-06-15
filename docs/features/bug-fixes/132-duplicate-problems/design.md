---
id: BUG-132-DESIGN
title: Duplicate Problems Design
type: design
parent_id: BUG-132
status: planned
---

# Technical Design: Duplicate Problems

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.analysis.luacheck.LuacheckExternalAnnotator`

## 2. Core Algorithms
1. In `doAnnotate`, after parsing the Luacheck output into `Problem` objects, group them by `(line, message)`.
2. Filter the collection to only retain distinct problems before applying them to the `AnnotationHolder`.
