---
id: SYNTAX-11
title: "11: Numeric Literal Validation"
type: feature
parent_id: SYNTAX
status: "done"
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# Specification: SYNTAX-11 Numeric Literal Validation

This document defines the requirements for verifying and validating Lua 5.4 numeric literals in the Lunar editor.

## 1. Scope

This specification covers the parsing, validation, and semantic annotation of integer and floating-point literals, including decimal and hexadecimal representations as defined in the Lua 5.4 Reference Manual (Section 3.1).

## 2. Syntax Rules

Lua 5.4 supports several formats for numeric constants.

### 2.1 Decimal Numerals
- **Integers**: `123`, `0`
- **Floats**: `123.0`, `12.3e-2`, `.05`, `3.14E+10`
- An optional fractional part and an optional decimal exponent are permitted.

### 2.2 Hexadecimal Numerals
- **Integers**: `0x1A`, `0Xfff`
- **Floats**: `0x1.921FB54442D18p+1`, `0X.5p-3`
- Must begin with `0x` or `0X`.
- Can have an optional fractional part and an optional binary exponent (marked by `p` or `P`).

## 3. Validation Constraints

### 3.1 Malformed Numerals
- A numeral with an exponent indicator (`e`, `E`, `p`, `P`) must have a valid exponent value following it.
  - `12e` (Invalid)
  - `0xp` (Invalid)
- A numeral cannot have multiple decimal points.
  - `1.2.3` (Invalid)
- A numeral cannot contain invalid characters for its base.
  - `0x12G` (Invalid, `G` is not a hex digit)
  - `12A` (Invalid, `A` is not a decimal digit or valid exponent marker)

## 4. Annotator Behavior

1. **Error Highlighting**: The annotator must flag malformed numerals with a clear syntax error describing the issue.
2. **Semantic Differences**: (Optional but recommended) Provide subtle highlighting differences between Integer and Float types, as well as indicating exponent parts.

## 5. Implementation Status

| Requirement | Status | Notes |
|---|---|---|
| Lexer: decimal number patterns (int, float, leading/trailing dot) | Implemented | `lua.flex` |
| Lexer: hex integer patterns | Implemented | `lua.flex` |
| Lexer: hex float patterns with binary exponent (`p`/`P`) | Implemented | Added in SYNTAX-11 |
| Lexer: capture malformed exponents (`12e`, `0xp`) as single tokens | Implemented | Added in SYNTAX-11 |
| Annotator: error on decimal exponent with no digits | Implemented | `LuaNumeralAnnotator` |
| Annotator: error on hex exponent with no digits | Implemented | `LuaNumeralAnnotator` |
| Annotator: error on `0xp` (no hex digits before exponent) | Implemented | `LuaNumeralAnnotator` |
| Semantic highlighting: INTEGER vs FLOAT distinction | Implemented | `LuaHighlight.NUMBER_INT/FLOAT` |
| Multiple decimal points `1.2.3` | Partial | Two separate tokens; parser reports syntax error |
| Invalid base chars `0x12G`, `12A` | Partial | Two separate tokens; parser reports syntax error |
