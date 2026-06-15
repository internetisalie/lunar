---
id: COMP-07-DESIGN
title: Live Templates Design
type: design
parent_id: COMP-07
status: done
---

# Technical Design: Live Templates

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType`
- **Data**: `resources/liveTemplates/lua.xml`

## 2. Core Algorithms
1. Implement `LuaTemplateContextType` extending `TemplateContextType`.
2. Return `true` if the context is a `LuaFile`.
3. Provide the templates natively in `resources/liveTemplates/lua.xml`.

## 3. Integration Points
```xml
<defaultLiveTemplates file="liveTemplates/lua.xml"/>
<liveTemplateContext implementation="net.internetisalie.lunar.lang.completion.templates.LuaTemplateContextType"/>
```
