---
id: REFACT-06-DESIGN
title: Create from Usage Design
type: design
parent_id: REFACT-06
status: planned
---

# Technical Design: Create from Usage

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.intentions.LuaCreateLocalVariableIntention`, `LuaCreateFunctionIntention`
- **Implements**: `com.intellij.codeInsight.intention.IntentionAction`

## 2. Core Algorithms
1. For `LuaCreateLocalVariableIntention`, check if the caret is on an undeclared `LuaNameReference`. If so, make available. Upon execution, insert `local ` before the reference.
2. For `LuaCreateFunctionIntention`, check if the caret is on a `LuaCallExpr` target. Upon execution, generate a function stub taking the correct number of arguments based on the call signature, and insert it above the current scope.

## 3. Integration Points
```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.intentions.LuaCreateLocalVariableIntention</className>
  <category>Lua</category>
</intentionAction>
```
