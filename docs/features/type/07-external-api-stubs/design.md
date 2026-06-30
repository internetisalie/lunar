---
id: TYPE-07-DESIGN
title: External API Stubs Design
type: design
parent_id: TYPE-07
---

# Technical Design: External API Stubs

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.library.LuaLibraryProvider`
- **Implements**: `com.intellij.navigation.ItemPresentation`, `com.intellij.openapi.roots.AdditionalLibraryRootsProvider`

## 2. Core Algorithms
1. Implement `AdditionalLibraryRootsProvider.getAdditionalProjectLibraries()`.
2. Return a `SyntheticLibrary` pointing to a bundled resource path containing standard `LuaCATS` `.lua` stub files.

## 3. Integration Points
```xml
<additionalLibraryRootsProvider implementation="net.internetisalie.lunar.lang.library.LuaLibraryProvider"/>
```
