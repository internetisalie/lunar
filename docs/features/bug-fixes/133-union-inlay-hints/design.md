---
id: BUG-133-DESIGN
title: Union Inlay Hints Design
type: design
parent_id: BUG-133
status: done
---

# Technical Design: Union Inlay Hints

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.hints.LuaInlayParameterHintsProvider`

## 2. Core Algorithms
1. If the resolved function is a `LuaUnionType`, iterate through variants.
2. Find the first variant that matches `LuaFunctionType` and use its signature.
