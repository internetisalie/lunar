---
id: SYNTAX-15-DESIGN
title: Lexer Optimization Design
type: design
parent_id: SYNTAX-15
status: planned
---

# Technical Design: Lexer Optimization

## 1. Architecture Overview
- **Component**: `LuaLexer.flex`

## 2. Core Algorithms
1. Convert `LONG_STRING` and `LONG_COMMENT` lexer rules from recursive patterns to state-based scanning (`%state LONG_STRING_STATE`).
