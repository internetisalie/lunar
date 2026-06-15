---
id: INTENT-02-DESIGN
title: Invert If Design
type: design
parent_id: INTENT-02
status: planned
---

# Technical Design: Invert If

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.intentions.LuaInvertIfIntention`
- **Implements**: `com.intellij.codeInsight.intention.PsiElementBaseIntentionAction`

## 2. Core Algorithms
1. Ensure the element is within a `LuaIfStat` that has both a `then` block and an `else` block (no `elseif`).
2. Use a helper `LuaConditionInverter` to negate the binary condition expression (e.g. swap `==` and `~=`, `<` and `>=`).
3. Replace the `LuaIfStat` with a newly created AST node where the condition is negated and the blocks are swapped.

## 3. Integration Points
```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.intentions.LuaInvertIfIntention</className>
  <category>Lua</category>
</intentionAction>
```
