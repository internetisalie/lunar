---
id: INTENT-03-DESIGN
title: Name Suggestion Design
type: design
parent_id: INTENT-03
status: planned
---

# Technical Design: Name Suggestion

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.refactoring.rename.LuaNameSuggestionProvider`
- **Implements**: `com.intellij.refactoring.rename.NameSuggestionProvider`

## 2. Core Algorithms
1. Implement `getSuggestedNames(PsiElement element, PsiElement nameSuggestionContext, Set<String> result)`.
2. Analyze the `element` to determine its type and text.
3. If `element` is `LuaCallExpr`, extract the function name, strip common prefixes (`get`, `create`, `build`), lowercase the first letter, and add to `result`.

## 3. Integration Points
```xml
<nameSuggestionProvider implementation="net.internetisalie.lunar.lang.refactoring.rename.LuaNameSuggestionProvider"/>
```
