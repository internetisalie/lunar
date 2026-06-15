---
id: INTENT-01-DESIGN
title: String Conversion Design
type: design
parent_id: INTENT-01
status: planned
---

# Technical Design: String Conversion

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.intentions.LuaStringConversionIntention`

## 2. Core Algorithms
1. Detect caret on `LuaLiteralExpr` containing a string.
2. Determine current quote style (`'`, `"`, `[[`).
3. Replace the text node with the next style in the cycle, appropriately escaping or unescaping internal quotes.

## 3. Integration Points
```xml
<intentionAction>
  <className>net.internetisalie.lunar.lang.intentions.LuaStringConversionIntention</className>
  <category>Lua</category>
</intentionAction>
```
