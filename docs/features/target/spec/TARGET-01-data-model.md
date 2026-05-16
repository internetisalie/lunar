---
folders:
  - "[[features/target/requirements|requirements]]"
title: "01: Target Data Model"
---

# TARGET-01: Target Data Model

**Requirement**: Define a `Target` configuration combining a `LuaPlatform` and a platform-specific `VersionEntry`.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md §1](../design.md)

---

## Overview

A `Target` is the single source of truth for a project's runtime environment. It encodes a platform (e.g., Redis) and version (e.g., "7+") as an immutable value and provides deterministic derivation of the implicit language level and library root path. No component may independently track language level or library path without going through `Target`.

---

## Types

### `LuaPlatform` (modified)

**File**: `src/main/kotlin/net/internetisalie/lunar/platform/LuaPlatform.kt`

Add a `pathSegment` property to the existing enum. `pathSegment` is the stable resource directory name — it is never derived from `label` or `name`.

```kotlin
enum class LuaPlatform(val label: String, val pathSegment: String) {
    STANDARD  ("Standard",   "standard"),
    LUAJIT    ("LuaJIT",     "luajit"),
    PANDOC    ("Pandoc",     "pandoc"),
    REDIS     ("Redis",      "redis"),
    TARANTOOL ("Tarantool",  "tarantool"),
    NGX       ("OpenResty",  "ngx");

    override fun toString(): String = label
}
```

**`LUAJIT` and `NGX` must be added** — they do not exist in the current enum and are prerequisites for this work.

---

### `VersionEntry` (new)

**File**: `src/main/kotlin/net/internetisalie/lunar/platform/target/VersionEntry.kt`

```kotlin
data class VersionEntry(
    val label: String,                   // Displayed in UI (e.g., "7+", "2.1", "latest")
    val pathSegment: String,             // Used in resource paths (e.g., "redis-7", "luajit-2.1")
    val luacheckStd: String? = null      // luacheck --std value, null if unsupported
)
```

**Invariants**:
- `label` is the serialized key written to `.idea/lunar.xml`
- `pathSegment` must be a valid directory name (no `+`, spaces, or special characters)
- `luacheckStd` matches a valid luacheck `--std` identifier, or is null

---

### `Target` (new)

**File**: `src/main/kotlin/net/internetisalie/lunar/platform/target/Target.kt`

```kotlin
data class Target(
    val platform: LuaPlatform,
    val version: VersionEntry
) {
    fun getImplicitLanguageLevel(): LuaLanguageLevel { ... }   // see TARGET-02
    fun getLibraryRootPath(): String = "runtime/${platform.pathSegment}/${version.pathSegment}"
    fun getLuacheckStd(): String? = version.luacheckStd        // see TARGET-05

    companion object {
        fun default(): Target = Target(LuaPlatform.STANDARD, PlatformVersionRegistry.defaultVersion(LuaPlatform.STANDARD))
    }
}
```

---

### `PlatformVersionRegistry` (new)

**File**: `src/main/kotlin/net/internetisalie/lunar/platform/target/PlatformVersionRegistry.kt`

Provides the complete, statically-declared set of supported targets. All `VersionEntry` values live here — no other code constructs them.

```kotlin
object PlatformVersionRegistry {
    private val registry = mapOf(
        LuaPlatform.STANDARD  to listOf(
            VersionEntry("5.1", "lua-5.1",       luacheckStd = "lua51"),
            VersionEntry("5.2", "lua-5.2",       luacheckStd = "lua52"),
            VersionEntry("5.3", "lua-5.3",       luacheckStd = "lua53"),
            VersionEntry("5.4", "lua-5.4",       luacheckStd = "lua54"),
            VersionEntry("5.5", "lua-5.5",       luacheckStd = "lua55"),
        ),
        LuaPlatform.LUAJIT    to listOf(
            VersionEntry("2.0", "luajit-2.0",    luacheckStd = "luajit"),
            VersionEntry("2.1", "luajit-2.1",    luacheckStd = "luajit"),
        ),
        LuaPlatform.REDIS     to listOf(
            VersionEntry("5",   "redis-5",       luacheckStd = "redis5"),
            VersionEntry("6",   "redis-6",       luacheckStd = "redis6"),
            VersionEntry("7+",  "redis-7",       luacheckStd = "redis7"),
        ),
        LuaPlatform.TARANTOOL to listOf(VersionEntry("2.10", "tarantool-2.10")),
        LuaPlatform.NGX       to listOf(VersionEntry("latest", "ngx-latest")),
        LuaPlatform.PANDOC    to listOf(VersionEntry("latest", "pandoc-latest")),
    )

    fun getVersions(platform: LuaPlatform): List<VersionEntry> = registry[platform] ?: emptyList()
    fun defaultVersion(platform: LuaPlatform): VersionEntry = getVersions(platform).last()
    fun findVersion(platform: LuaPlatform, label: String): VersionEntry? =
        getVersions(platform).find { it.label == label }
}
```

---

### `LuaProjectSettings.State` (modified)

**File**: `src/main/kotlin/net/internetisalie/lunar/settings/LuaProjectSettings.kt`

Add a `target` field. The existing `platform` field is removed (superseded). The `languageLevel` field is deprecated but retained for migration.

```kotlin
class State {
    var target: Target? = null

    @Deprecated("Use target instead")
    var languageLevel: LuaLanguageLevel = LuaLanguageLevel.LUA54
    
    // `platform` field removed — now encoded in target.platform
    var interpreter: LuaInterpreter? = null
    var sourcePath: String = PathConfiguration.DEFAULT_SOURCE_PATH
}
```

Accessor methods on `LuaProjectSettings`:
```kotlin
fun getTarget(): Target = state.target ?: migrateFromLegacySettings()
fun setTarget(target: Target) {
    state.target = target
    state.languageLevel = target.getImplicitLanguageLevel()  // keep in sync during transition
}
```

---

## Serialization Format

`Target` is serialized by storing `platform.name` (the enum constant name) and `version.label` (the user-visible version key) in `lunar.xml`.

```xml
<component name="LuaProjectSettings">
  <option name="target">
    <map>
      <entry key="platform" value="REDIS" />
      <entry key="version"  value="7+" />
    </map>
  </option>
  <!-- legacy field maintained for backward compat -->
  <option name="languageLevel" value="LUA51" />
</component>
```

On deserialization, `version.label` is resolved back to a `VersionEntry` via `PlatformVersionRegistry.findVersion()`. If the label is unrecognised (e.g., from a future version of the plugin), `defaultVersion()` is used as a safe fallback.

---

## Acceptance Criteria

- [ ] `LuaPlatform` enum has `pathSegment` property; `LUAJIT` and `NGX` entries exist
- [ ] `VersionEntry` data class exists with `label`, `pathSegment`, `luacheckStd`
- [ ] `Target` data class exists with `getImplicitLanguageLevel()`, `getLibraryRootPath()`, `getLuacheckStd()`
- [ ] `PlatformVersionRegistry` is the sole place `VersionEntry` instances are constructed
- [ ] `LuaProjectSettings.State` has `target: Target?` field; `platform` field removed
- [ ] `getTarget()` / `setTarget()` accessor methods exist on `LuaProjectSettings`
- [ ] Serialization round-trips correctly for all registered platforms and versions
- [ ] Unknown version label on deserialization falls back to `defaultVersion()` without exception
