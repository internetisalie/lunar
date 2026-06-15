---
id: REFACT-05-DESIGN
title: Name Validator Design
type: design
parent_id: REFACT-05
status: planned
---

# Technical Design: Name Validator

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.refactoring.rename.LuaNamesValidator`
- **Implements**: `com.intellij.lang.refactoring.NamesValidator`

## 2. Core Algorithms
1. `isKeyword(String name, Project project)`: Compare the input string against the set of `LuaTokenTypes.KEYWORDS`.
2. `isIdentifier(String name, Project project)`: Evaluate the string using a Regex or the Lexer to confirm it matches `[a-zA-Z_][a-zA-Z0-9_]*` and is not a keyword.

## 3. Integration Points
```xml
<lang.namesValidator language="Lua" implementationClass="net.internetisalie.lunar.lang.refactoring.rename.LuaNamesValidator"/>
```
