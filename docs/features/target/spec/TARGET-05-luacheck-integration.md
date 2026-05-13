# TARGET-05: Luacheck Integration

**Requirement**: The Luacheck static analyser must use the correct `--std` value for the active project Target. The std value is declared on the `VersionEntry` and requires no string transformation.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md §4.4](../design.md)

---

## Overview

Luacheck's `--std` flag controls which global symbols and built-in functions are recognised for a given Lua environment. If `--std` is wrong for the project's platform, luacheck emits false-positive "undefined global" errors (e.g., `redis.call` flagged in a Redis project) or misses real errors (e.g., `bit` used in a non-LuaJIT project). After TARGET, `--std` is always taken directly from `target.getLuacheckStd()` — there is no mapping logic at the call site.

---

## Data Flow

```
LuaProjectSettings.getTarget()
    └─> Target.getLuacheckStd()
            └─> VersionEntry.luacheckStd   // declared in PlatformVersionRegistry
                    └─> passed as --std to luacheck invocation
```

`Target.getLuacheckStd()` is a single-line delegation:

```kotlin
fun getLuacheckStd(): String? = version.luacheckStd
```

---

## `--std` Mapping Table

| Platform | Version | `luacheckStd` | luacheck `--std` |
|:---------|:--------|:--------------|:-----------------|
| STANDARD | 5.1     | `"lua51"`     | `--std lua51`    |
| STANDARD | 5.2     | `"lua52"`     | `--std lua52`    |
| STANDARD | 5.3     | `"lua53"`     | `--std lua53`    |
| STANDARD | 5.4     | `"lua54"`     | `--std lua54`    |
| STANDARD | 5.5     | `"lua55"`     | `--std lua55`    |
| LUAJIT   | 2.0     | `"luajit"`    | `--std luajit`   |
| LUAJIT   | 2.1     | `"luajit"`    | `--std luajit`   |
| REDIS    | 5       | `"redis5"`    | `--std redis5`   |
| REDIS    | 6       | `"redis6"`    | `--std redis6`   |
| REDIS    | 7+      | `"redis7"`    | `--std redis7`   |
| TARANTOOL| 2.10    | `null`        | (omitted)        |
| NGX      | latest  | `null`        | (omitted)        |
| PANDOC   | latest  | `null`        | (omitted)        |

**Note on `lua55`**: luacheck may not yet have a `lua55` standard. If the luacheck invocation fails with an unrecognised std, fall back to `"lua54"`. This must be re-evaluated when luacheck adds official Lua 5.5 support.

---

## Luacheck Invocation

**File**: `src/main/kotlin/net/internetisalie/lunar/analysis/luacheck/LuacheckRunner.kt` (or equivalent)

```kotlin
fun buildArguments(target: Target, filePath: String): List<String> {
    val std = target.getLuacheckStd()
    return buildList {
        add("luacheck")
        if (std != null) {
            add("--std")
            add(std)
        }
        add("--formatter")
        add("plain")
        add(filePath)
    }
}
```

When `getLuacheckStd()` returns `null`, the `--std` argument is **omitted entirely**. Luacheck will fall back to its own default behaviour for unrecognised platforms. This is preferred over passing an invalid value.

---

## Null-Std Platforms

Platforms with `luacheckStd = null` (TARANTOOL, NGX, PANDOC) are platforms where:
- Luacheck has no bundled standard library definition
- Passing an incorrect `--std` would produce worse results than omitting it

For these platforms, luacheck runs without `--std`. Users who want stricter analysis can configure a custom luacheck config file (`.luacheckrc`) in their project root — the plugin does not interfere with that file.

---

## Change Detection

When the project Target changes, any luacheck results already cached for open files must be invalidated so that the next analysis pass uses the new `--std`. This is achieved via the same `LuaSettingsChangedEvent` that triggers library refresh (see TARGET-04).

---

## Acceptance Criteria

- [ ] `Target.getLuacheckStd()` returns the correct value for every row in the mapping table above
- [ ] Luacheck invocations pass `--std <value>` when `getLuacheckStd()` is non-null
- [ ] Luacheck invocations omit `--std` entirely when `getLuacheckStd()` is null
- [ ] Changing the Target clears cached luacheck results for open files
- [ ] No string transformation is performed between `VersionEntry.luacheckStd` and the `--std` argument
- [ ] Unit tests verify `getLuacheckStd()` output for all registered targets
