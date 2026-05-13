# TARGET-02: Implicit Language Level

**Requirement**: Selecting a Target must automatically derive the `LuaLanguageLevel` (e.g., Redis implies Lua 5.1). The language level is not independently user-selectable when a platform is active.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md §1.1 (Target)](../design.md)

---

## Overview

`LuaLanguageLevel` controls which parser grammar and built-in symbol definitions apply to a project. Before TARGET, language level was a free user choice. After TARGET, it is **derived** from the `Target` — the user selects a platform and version, and the language level follows deterministically. The user cannot set it independently.

---

## Derivation Rules

`Target.getImplicitLanguageLevel()` maps every `(platform, version.label)` pair to a `LuaLanguageLevel`:

```kotlin
fun getImplicitLanguageLevel(): LuaLanguageLevel {
    return when {
        platform == STANDARD && version.label == "5.1" -> LuaLanguageLevel.LUA51
        platform == STANDARD && version.label == "5.2" -> LuaLanguageLevel.LUA52
        platform == STANDARD && version.label == "5.3" -> LuaLanguageLevel.LUA53
        platform == STANDARD && version.label == "5.4" -> LuaLanguageLevel.LUA54
        platform == STANDARD && version.label == "5.5" -> LuaLanguageLevel.LUA54  // ¹
        platform == LUAJIT    -> LuaLanguageLevel.LUA51  // ²
        platform == REDIS     -> LuaLanguageLevel.LUA51  // ³
        platform == TARANTOOL -> LuaLanguageLevel.LUA51
        platform == NGX       -> LuaLanguageLevel.LUA51  // ⁴
        else                  -> LuaLanguageLevel.LUA54  // default fallback
    }
}
```

**Notes**:

¹ **Lua 5.5** — Language level support is future work. Until `LUA55` is added to `LuaLanguageLevel`, the parser falls back to `LUA54`. This must be revisited when 5.5 parsing support is implemented.

² **LuaJIT** — LuaJIT implements Lua 5.1 semantics (with extensions). All LuaJIT versions (2.0, 2.1) map to `LUA51`.

³ **Redis** — All Redis versions embed Lua 5.1 (confirmed across Redis 5, 6, 7, and 8). All Redis version entries map to `LUA51`.

⁴ **OpenResty (NGX)** — Built on LuaJIT; maps to `LUA51`.

---

## Platform-to-Language-Level Summary

| Platform | Version | Implicit Language Level |
|:---------|:--------|:------------------------|
| STANDARD | 5.1     | `LUA51`                 |
| STANDARD | 5.2     | `LUA52`                 |
| STANDARD | 5.3     | `LUA53`                 |
| STANDARD | 5.4     | `LUA54`                 |
| STANDARD | 5.5     | `LUA54` (temporary)     |
| LUAJIT   | 2.0     | `LUA51`                 |
| LUAJIT   | 2.1     | `LUA51`                 |
| REDIS    | 5       | `LUA51`                 |
| REDIS    | 6       | `LUA51`                 |
| REDIS    | 7+      | `LUA51`                 |
| TARANTOOL| 2.10    | `LUA51`                 |
| NGX      | latest  | `LUA51`                 |
| PANDOC   | latest  | `LUA54` (fallback)      |

---

## Impact on Settings State

`LuaProjectSettings` keeps `languageLevel` in sync with `target` during the migration period:

```kotlin
fun setTarget(target: Target) {
    state.target = target
    state.languageLevel = target.getImplicitLanguageLevel()
}
```

This ensures that code still reading `state.languageLevel` directly sees the correct derived value until it is updated to use `getTarget()`.

---

## Impact on UI

The language level **must not** be exposed as an editable field in the settings panel when a Target is active. It is displayed as a read-only label:

```
Platform:  [Redis      ▼]
Version:   [7+         ▼]
           Language Level: Lua 5.1
```

See TARGET-03 for full UI specification.

---

## Future Work

- When `LuaLanguageLevel.LUA55` is added to the enum and the parser/lexer supports Lua 5.5 syntax, update the derivation rule for `STANDARD / "5.5"` from `LUA54` to `LUA55`.

---

## Acceptance Criteria

- [ ] `Target.getImplicitLanguageLevel()` returns the correct level for every entry in `PlatformVersionRegistry`
- [ ] `LuaLanguageLevel` is not user-selectable when a Target is active
- [ ] `state.languageLevel` is always updated on `setTarget()`
- [ ] `STANDARD / "5.5"` returns `LUA54` until `LUA55` is implemented
- [ ] Unit tests cover every row in the platform-to-language-level table above
