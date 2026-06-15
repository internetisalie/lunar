---
id: BUG-134-DESIGN
title: Return Comma Parsing Design
type: design
parent_id: BUG-134
status: planned
---

# Technical Design: Return Comma Parsing

## 1. Architecture Overview
- **Component**: `LuaParser.bnf`

## 2. Core Algorithms
1. Update `return_stat` rule in grammar from `return expr_list? ';'?` to handle optional trailing commas: `return expr_list? ','? ';'?`.
