---
id: MAINT-03-DESIGN
title: Deprecation Cleanup Design
type: design
parent_id: MAINT-03
---

# Technical Design: Deprecation Cleanup

## 1. Architecture Overview
- **Component**: Multiple actions

## 2. Core Algorithms
1. Replace `dataContext.getData(DataConstants.PROJECT)` with `CommonDataKeys.PROJECT.getData(dataContext)`.

## 3. Platform & Tooling Modernization
1. Update `org.jetbrains.intellij.platform` plugin in `gradle/libs.versions.toml` from `2.5.0` to `2.17.0`.
2. Ensure gradle wrapper settings are aligned and check for any deprecated plugin API warnings during build.
