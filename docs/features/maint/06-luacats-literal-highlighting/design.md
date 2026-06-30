---
id: MAINT-06-DESIGN
title: LuaCATS Literal Highlighting Design
type: design
parent_id: MAINT-06
---

# Technical Design: LuaCATS Literal Highlighting

## 1. Architecture Overview
- **Component**: `net.internetisalie.lunar.lang.syntax.LuaSyntaxHighlighter`

## 2. Core Algorithms
1. Map `LuaCatsTypes.NUMBER_TYPE`, `STRING_TYPE`, `BOOLEAN_TYPE` to `DefaultLanguageHighlighterColors.KEYWORD`.
