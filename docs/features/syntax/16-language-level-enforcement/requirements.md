---
id: SYNTAX-16
title: "16: Language Level Enforcement"
type: feature
parent_id: SYNTAX
status: "done"
vf_icon: ✅
priority: "medium"
folders:
  - "[[features/syntax/requirements|requirements]]"
---
# SYNTAX-16: Enforce Language Level Restrictions
**Task ID**: 111

## Overview

Create a semantic annotator that validates Lua code against the configured language level, flagging version-specific syntax used in projects configured for earlier Lua versions.

## Problem

Lunar supports a **unified parser** that accepts all Lua 5.1-5.4 syntax. However, the language level is a **runtime setting** (configured in `LuaProjectSettings`), not enforced at parse time.

**Current issue**: Users can write code using 5.3-only features in a project configured for Lua 5.1, and Lunar won't warn them until runtime.

**Example**:
```lua
-- Project configured for Lua 5.1
local x = 1 // 2        -- ❌ Should warn: // not available in 5.1
goto exit               -- ❌ Should warn: goto not available in 5.1
x = x & 3               -- ❌ Should warn: bitwise ops not available in 5.1
::exit::
```

## Solution

Implement a **semantic annotator** (`LuaLanguageLevelAnnotator`) that:

1. Reads the project's configured language level from `LuaProjectSettings`
2. Scans PSI elements for version-specific syntax
3. Flags violations at severity ERROR (non-configurable for correctness)
4. Does NOT modify the parser (leaves it language-agnostic)

## Version-Specific Syntax

| Feature | Introduced | Lua Versions | Syntax |
|---------|-----------|--------------|--------|
| `goto` and `label` | 5.2 | 5.2+ | `goto name` / `::name::` |
| Integer division | 5.3 | 5.3+ | `x // y` |
| Bitwise AND | 5.3 | 5.3+ | `x & y` |
| Bitwise OR | 5.3 | 5.3+ | `x \| y` |
| Bitwise NOT | 5.3 | 5.3+ | `~x` |
| Left shift | 5.3 | 5.3+ | `x << n` |
| Right shift | 5.3 | 5.3+ | `x >> n` |
| XOR | 5.3 | 5.3+ | `x ^ y` |
| Attributes | 5.4 | 5.4 | `local x <const> = v` |

## Implementation Plan

### Phase 1: Infrastructure
- [ ] Create `LuaLanguageLevelAnnotator` extending `PsiElementAnnotator`
- [ ] Register in `plugin.xml` under `<annotator>`
- [ ] Add utility methods to detect version-specific syntax

### Phase 2: Version-Specific Checks
- [ ] **Lua 5.2+**: Detect `LuaGotoStatement`, `LuaLabel`
- [ ] **Lua 5.3+**: Detect `LuaBinaryOp` with bitwise operators (`&`, `|`, `~`, `<<`, `>>`, `^`)
- [ ] **Lua 5.3+**: Detect `LuaBinaryOp` with `//` operator
- [ ] **Lua 5.4+**: Detect `LuaLocalVarDecl` with attributes

### Phase 3: Annotation
- [ ] For each violation, create error annotation
- [ ] Message format: `"<Feature> is a Lua X.Y+ feature (project configured for Lua A.B)"`
- [ ] Examples:
  - `"Goto statements are a Lua 5.2+ feature (project configured for Lua 5.1)"`
  - `"Integer division (//) is a Lua 5.3+ feature (project configured for Lua 5.2)"`
  - `"Attributes are a Lua 5.4 feature (project configured for Lua 5.3)"`

### Phase 4: Testing
- [ ] Write tests for each version transition
- [ ] Test multi-version projects (verify no false positives)
- [ ] Test with actual `LuaProjectSettings.languageLevel` values

## Success Criteria

✅ Annotator correctly identifies version-specific syntax  
✅ Flags errors only when language level is insufficient  
✅ No parser changes required  
✅ All tests passing  
✅ Works with all runtime platforms (Standard, Redis, LuaJIT, OpenResty, Pandoc, Tarantool)  

## Files to Modify/Create

- **Create**: `src/main/kotlin/net/internetisalie/lunar/lang/annotator/LuaLanguageLevelAnnotator.kt`
- **Update**: `src/main/resources/META-INF/plugin.xml` (register annotator)
- **Create**: `src/test/kotlin/net/internetisalie/lunar/lang/annotator/LuaLanguageLevelAnnotatorTest.kt`

## Related

- **SYNTAX-01**: Lua 5.4 Support (parser support for attributes)
- **SYNTAX-12**: Label & Goto Scope Resolution (scope validation for goto)
- **TARGET**: Runtime Environment Configuration (language level detection)

## Implementation Notes

### Accessing Language Level
```kotlin
val settings = LuaProjectSettings.getInstance(element.project)
val level = settings.languageLevel  // Returns LanguageLevel enum
```

### Checking Version
```kotlin
if (level < LanguageLevel.LUA_52) {
    holder.newAnnotation(HighlightSeverity.ERROR, 
        "Goto statements are a Lua 5.2+ feature (project configured for Lua ${level.displayName})").create()
}
```

### Binary Operator Detection
```kotlin
when (binaryOp.operationName) {
    "//" -> if (level < LanguageLevel.LUA_53) error(...)
    "&", "|", "~", "<<", ">>", "^" -> if (level < LanguageLevel.LUA_53) error(...)
}
```

---

**Created**: 2026-05-13  
**Task ID**: SYNTAX-16  
**Epic**: SYNTAX: Syntax & Editor
