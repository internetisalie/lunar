---
id: BUG-135-DESIGN
title: Stdlib Inlay Hints Design
type: design
parent_id: BUG-135
status: planned
---

# Technical Design: Stdlib Inlay Hints

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.hints.LuaInlayParameterHintsProvider`

## 2. Core Algorithms
1. In `getParameterHints()`, check if the resolved `LuaFunctionDecl` is contained within a VirtualFile that belongs to the SDK/Stdlib path.
2. If true, return empty hints.
