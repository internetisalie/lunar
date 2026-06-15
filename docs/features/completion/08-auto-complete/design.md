---
id: COMP-08-DESIGN
title: Auto Complete Design
type: design
parent_id: COMP-08
status: done
---

# Technical Design: Auto Complete

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.completion.LuaEnterHandler`
- **Implements**: `com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter`

## 2. Core Algorithms
1. Intercept Enter key.
2. If previous token is `THEN`, `DO`, `FUNCTION` (and no existing `end` matches it), insert `\nend` and adjust caret position.

## 3. Integration Points
```xml
<enterHandlerDelegate implementation="net.internetisalie.lunar.lang.completion.LuaEnterHandler"/>
```
