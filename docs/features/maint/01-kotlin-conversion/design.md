---
id: MAINT-01-DESIGN
title: Kotlin Conversion Design
type: design
parent_id: MAINT-01
---

# Technical Design: Kotlin Conversion

## 1. Architecture Overview
- **Component**: Legacy Java files in `net.internetisalie.lunar.*`

## 2. Core Algorithms
1. Run IntelliJ J2K (Java to Kotlin) converter on all `.java` files.
2. Fix nullability warnings (remove `!!` and substitute with safe calls `?.` or `?:`).
