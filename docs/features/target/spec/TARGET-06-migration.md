# TARGET-06: Settings Migration

**Requirement**: Existing project settings must migrate to the new Target data model without user action or data loss.  
**Priority**: Must  
**Status**: Not Implemented  
**Design reference**: [design.md §5](../design.md)

---

## Overview

Before TARGET, `LuaProjectSettings.State` stores `languageLevel: LuaLanguageLevel` and `platform: LuaPlatform` as separate fields. After TARGET, `target: Target?` is the single source of truth. Opening an existing project must silently convert the old fields to a `Target` and persist the result.

---

## Legacy State Structure (before migration)

```xml
<!-- lunar.xml — before TARGET -->
<component name="LuaProjectSettings">
  <option name="platform"      value="STANDARD" />
  <option name="languageLevel" value="LUA51"    />
  <option name="sourcePath"    value="src"      />
</component>
```

```xml
<!-- lunar.xml — non-STANDARD platform example -->
<component name="LuaProjectSettings">
  <option name="platform"      value="REDIS"    />
  <option name="languageLevel" value="LUA51"    />
</component>
```

---

## New State Structure (after migration)

```xml
<!-- lunar.xml — after TARGET -->
<component name="LuaProjectSettings">
  <option name="target">
    <map>
      <entry key="platform" value="STANDARD" />
      <entry key="version"  value="5.1"      />
    </map>
  </option>
  <!-- languageLevel retained for tooling compatibility during transition -->
  <option name="languageLevel" value="LUA51" />
  <option name="sourcePath"    value="src"   />
</component>
```

---

## Migration Algorithm

`LuaProjectSettings.migrateFromLegacySettings()` is called from `getTarget()` when `state.target` is `null`:

```kotlin
private fun migrateFromLegacySettings(): Target {
    val legacyPlatform = state.legacyPlatform   // nullable; null = was never set = STANDARD
    val legacyLevel    = state.languageLevel

    val target = when {
        legacyPlatform != null && legacyPlatform != LuaPlatform.STANDARD -> {
            // Non-STANDARD platform: version is not derivable from languageLevel,
            // so use the platform's default (most recent) version.
            Target(legacyPlatform, PlatformVersionRegistry.defaultVersion(legacyPlatform))
        }
        else -> {
            // STANDARD platform: map LuaLanguageLevel to its version label
            val label   = legacyLevel.toVersionLabel()
            val version = PlatformVersionRegistry.findVersion(LuaPlatform.STANDARD, label)
                          ?: PlatformVersionRegistry.defaultVersion(LuaPlatform.STANDARD)
            Target(LuaPlatform.STANDARD, version)
        }
    }

    setTarget(target)   // persists and keeps languageLevel in sync
    return target
}
```

`LuaLanguageLevel.toVersionLabel()` maps enum constants to STANDARD version labels:

```kotlin
fun LuaLanguageLevel.toVersionLabel(): String = when (this) {
    LuaLanguageLevel.LUA50 -> "5.1"   // LUA50 maps to 5.1 (closest supported)
    LuaLanguageLevel.LUA51 -> "5.1"
    LuaLanguageLevel.LUA52 -> "5.2"
    LuaLanguageLevel.LUA53 -> "5.3"
    LuaLanguageLevel.LUA54 -> "5.4"
}
```

**`LUA50` handling**: Lua 5.0 is not a registered STANDARD version. It maps to `"5.1"` (the closest supported version). `findVersion()` returns null for `"5.0"`, so the `?: defaultVersion()` fallback handles it gracefully even without the explicit mapping.

---

## Migration Scenarios

| Legacy `platform`   | Legacy `languageLevel` | Migrated Target             |
|:--------------------|:-----------------------|:----------------------------|
| `STANDARD` (or null)| `LUA51`                | `STANDARD / "5.1"`          |
| `STANDARD` (or null)| `LUA52`                | `STANDARD / "5.2"`          |
| `STANDARD` (or null)| `LUA53`                | `STANDARD / "5.3"`          |
| `STANDARD` (or null)| `LUA54`                | `STANDARD / "5.4"`          |
| `STANDARD` (or null)| `LUA50`                | `STANDARD / "5.1"`          |
| `REDIS`             | `LUA51`                | `REDIS / "7+"` (default)    |
| `TARANTOOL`         | `LUA51`                | `TARANTOOL / "2.10"` (def.) |
| `PANDOC`            | `LUA54`                | `PANDOC / "latest"` (def.)  |

---

## Serialization Dual-Field Strategy

To ensure backward compatibility with older plugin versions that can read the file, `languageLevel` is written alongside `target` for the duration of the migration period:

```kotlin
fun setTarget(target: Target) {
    state.target = target
    state.languageLevel = target.getImplicitLanguageLevel()  // kept for backward compat
}
```

The `platform` field is **removed** from State and no longer written. Older plugin versions that read `platform` will fall back to their own defaults — this is acceptable because `platform` was a recent addition.

---

## Deserialization Robustness

On read, `LuaProjectSettings` first attempts to populate `state.target`:

1. If `target` element is present in XML → deserialize `platform` + `version` → call `PlatformVersionRegistry.findVersion()`
2. If the version label is not in the registry (future plugin version wrote an unknown label) → use `defaultVersion()` as fallback
3. If `target` element is absent → `state.target` remains null → `getTarget()` triggers migration

No exception may propagate from deserialization; all unrecognised values must degrade gracefully.

---

## Acceptance Criteria

- [ ] `migrateFromLegacySettings()` is called when `state.target` is null
- [ ] Migration correctly handles all `LuaLanguageLevel` values including `LUA50`
- [ ] Non-STANDARD legacy platforms migrate to the platform's default version
- [ ] STANDARD legacy platform migrates using `languageLevel` to version label mapping
- [ ] After migration, `state.target` is populated and `state.languageLevel` is in sync
- [ ] XML serialization writes both `target` and `languageLevel` fields
- [ ] XML deserialization reads `target`; falls back to migration if absent
- [ ] Unknown version label on deserialization uses `defaultVersion()`, not an exception
- [ ] Unit tests cover all rows in the Migration Scenarios table above
