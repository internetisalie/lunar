---
id: COMP-06-DESIGN
title: Postfix Templates Design
type: design
parent_id: COMP-06
status: planned
---

# Technical Design: Postfix Templates

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider`
- **Implements**: `com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider`

## 2. Core Algorithms
1. Implement `LuaIfPostfixTemplate` extending `StringBasedPostfixTemplate`.
2. Map `if` to generate `if $expr$ then $END$ end`.
3. Provide these templates via `LuaPostfixTemplateProvider.getTemplates()`.

## 3. Integration Points
```xml
<codeInsight.template.postfixTemplateProvider language="Lua" implementationClass="net.internetisalie.lunar.lang.completion.postfix.LuaPostfixTemplateProvider"/>
```
