---
id: TYPE-08-DESIGN
title: Flow Sensitive Typing Design
type: design
parent_id: TYPE-08
status: planned
---

# Technical Design: Flow Sensitive Typing

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.type.LuaTypeManager`

## 2. Core Algorithms
1. When resolving a variable type, crawl up the AST looking for `LuaIfStat`.
2. Extract the condition. If it's a type guard (`type(x) == '...'`), calculate the refined type.
3. Apply the refined type if the reference is within the `then` block (for `==`) or `else` block (for `~=`).
