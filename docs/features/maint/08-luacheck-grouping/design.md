---
id: MAINT-08-DESIGN
title: Luacheck Grouping Design
type: design
parent_id: MAINT-08
---

# Technical Design: Luacheck Grouping

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.analysis.luacheck.LuacheckInspection`

## 2. Core Algorithms
1. Override `getGroupPath()` in `LuacheckInspection` to provide a hierarchy like `["Lua", "Luacheck"]`.
