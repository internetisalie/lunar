---
id: INSP-06-DESIGN
title: Local Shadowing Design
type: design
parent_id: INSP-06
status: done
---

# Technical Design: Local Shadowing

## 1. Core Components
- `LuaShadowingVariableInspection` extending `LocalInspectionTool`.

## 2. Algorithms
1. Visit `LuaLocalDef` elements.
2. Get the element's name.
3. Crawl up from the *parent* scope using `LuaResolveUtil.scopeCrawlUp` with a `ResolveProcessor`.
4. If it resolves to a declaration in an outer scope, it's shadowing.

## 3. Integration Points
```xml
<localInspection language="Lua" shortName="LuaShadowingVariable" displayName="Shadowing variable" groupName="Lua" enabledByDefault="true" level="WEAK_WARNING" implementationClass="net.internetisalie.lunar.analysis.inspections.LuaShadowingVariableInspection"/>
```
