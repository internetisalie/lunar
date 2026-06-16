---
id: SYNTAX-05-DESIGN
title: Method Separators Design
type: design
parent_id: SYNTAX-05
status: done
---

# Technical Design: Method Separators

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.editor.LuaDaemonCodeAnalyzer`

## 2. Core Algorithms
1. Register `DaemonCodeAnalyzer.LineMarkerProvider`.
2. Provide a `LineMarkerInfo` with a `SEPARATOR` type above `LuaFunctionDecl`.
