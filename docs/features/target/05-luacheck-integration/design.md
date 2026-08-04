---
id: TARGET-05-DESIGN
parent_id: TARGET-05
type: design
folders:
  - "[[features/target/05-luacheck-integration/requirements|requirements]]"
title: "Technical Design"
---

# Technical Design: Luacheck Integration

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
| STANDARD | 5.5     | `"lua54"`     | `--std lua54`    |
| LUAJIT   | 2.0     | `"luajit"`    | `--std luajit`   |
| LUAJIT   | 2.1     | `"luajit"`    | `--std luajit`   |
| REDIS    | 5       | `"redis5"`    | `--std redis5`   |
| REDIS    | 6       | `"redis6"`    | `--std redis6`   |
| REDIS    | 7+      | `"redis7"`    | `--std redis7`   |
| TARANTOOL| 2.10    | `null`        | (omitted)        |
| NGX      | latest  | `null`        | (omitted)        |
| PANDOC   | latest  | `null`        | (omitted)        |

**Note on `lua55`** (corrected 2026-08-04): no `lua55` std exists in upstream luacheck 1.2.0 or in the `glimmer/luacheck` fork (both probed). The code emits `lua54` for Lua 5.5 — `PlatformVersionRegistry.kt:21` — which is what this table now records. The "fall back on an unrecognised std" mitigation described here was **never implemented**: `LuaCheckCommandLine.kt:78-82` appends `--std` unconditionally, and an unknown std is a luacheck *Critical error* that aborts the run. Tracked as **BUG-403**.

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

> **Correction (2026-08-04): the first bullet is false for NGX.** `ngx_lua` is a builtin std in
> upstream luacheck 1.2.0 *and* in the `glimmer/luacheck` fork (`builtin_standards/init.lua:270`,
> `ngx_lua = luajit + ngx`). OpenResty projects therefore get no `--std` when a correct one is
> available in every luacheck build. Tracked as **BUG-405**. Tarantool and Pandoc genuinely have no
> builtin std, so the bullet holds for those two.

For these platforms, luacheck runs without `--std`. Users who want stricter analysis can configure a custom luacheck config file (`.luacheckrc`) in their project root — the plugin does not interfere with that file.

---

## Change Detection

When the project Target changes, any luacheck results already cached for open files must be invalidated so that the next analysis pass uses the new `--std`. This is achieved via the same `LuaSettingsChangedEvent` that triggers library refresh (see TARGET-04).
