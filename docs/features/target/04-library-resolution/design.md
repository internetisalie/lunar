---
id: TARGET-04-DESIGN
parent_id: TARGET-04
type: design
folders:
  - "[[features/target/04-library-resolution/requirements|requirements]]"
title: "Technical Design"
status: not_implemented
---

# Technical Design: Library Root Resolution

## Resource Directory Structure

```
src/main/resources/runtime/
├── standard/
│   ├── lua-5.1/
│   ├── lua-5.2/
│   ├── lua-5.3/
│   ├── lua-5.4/
│   └── lua-5.5/         (future — no files yet)
├── luajit/
│   ├── luajit-2.0/      (future — no files yet)
│   └── luajit-2.1/      (future — no files yet)
├── redis/
│   ├── redis-5/
│   ├── redis-6/
│   └── redis-7/
├── tarantool/
│   └── tarantool-2.10/  (future — no files yet)
├── ngx/
│   └── ngx-latest/      (future — no files yet)
└── pandoc/
    └── pandoc-latest/   (future — no files yet)
```

Directories marked **future** have no bundled library files at this stage. `RuntimeLibraryProvider` handles a missing directory by returning `null` — this is expected, not an error.

---

## File Migration Table

Existing resources are relocated:

| Old Path                         | New Path                        |
|:---------------------------------|:--------------------------------|
| `platform/Lua51/`                | `runtime/standard/lua-5.1/`     |
| `platform/Lua52/`                | `runtime/standard/lua-5.2/`     |
| `platform/Lua53/`                | `runtime/standard/lua-5.3/`     |
| `platform/Lua54/`                | `runtime/standard/lua-5.4/`     |
| `sdk/redis-5/`                   | `runtime/redis/redis-5/`        |
| `sdk/redis-6/`                   | `runtime/redis/redis-6/`        |
| `sdk/redis-8/` ¹                 | `runtime/redis/redis-7/`        |

¹ The directory was named `redis-8` but its contents correspond to the Redis 7 Lua API. It is moved and renamed.

---

## `RuntimeLibraryProvider` (new)

**File**: `src/main/kotlin/net/internetisalie/lunar/platform/target/RuntimeLibraryProvider.kt`

```kotlin
class RuntimeLibraryProvider(private val project: Project) {

    fun getLibraryRoot(target: Target): VirtualFile? {
        val path = target.getLibraryRootPath()   // e.g. "runtime/redis/redis-7"
        return RuntimeLibraryProvider::class.java
            .classLoader
            .getResource(path)
            ?.let { VfsUtil.findFileByURL(it) }
    }

    fun getLibraryFiles(target: Target): List<VirtualFile> =
        getLibraryRoot(target)
            ?.children
            ?.filter { it.extension == "lua" }
            ?: emptyList()
}
```

`getLibraryRoot()` returns `null` when no resources are bundled for the target. Callers must handle null (treat as "no library files").

---

## Integration Points

The following components currently consume library files and must be updated:

| Component | Current Behaviour | Updated Behaviour |
|:----------|:------------------|:------------------|
| `PlatformLibraryProvider.getPlatformLibrary()` | Hardcodes `platform/LuaXX` path | Delegates to `RuntimeLibraryProvider.getLibraryFiles(target)` |
| `PlatformLibraryIndex` | Calls `PlatformLibraryProvider` | Unchanged; receives resolved files from updated provider |
| Completion contributors | Index-driven | Unchanged; benefits automatically from updated index |
| Inspections / Luacheck | Separate path — see TARGET-05 | Unchanged here |

---

## Library Update Flow

When the project Target changes (via `settings.setTarget()`), the library set must be refreshed:

1. `setTarget()` persists the new Target
2. `LuaProjectSettings` fires a `LuaSettingsChangedEvent`  
3. `PlatformLibraryProvider` listens and refreshes its library list
4. IntelliJ's `PsiManager.dropResolveCaches()` is called to invalidate stale references

This event-driven flow ensures that changing the target in the settings dialog takes effect without requiring an IDE restart.

---

## Null-Library Platforms

Platforms without bundled library files today (LUAJIT, NGX, PANDOC, TARANTOOL) will resolve to `null` from `getLibraryRoot()`. This is handled gracefully:

- `getLibraryFiles()` returns an empty list
- `PlatformLibraryProvider` treats an empty file list as "no library contribution"
- No error is logged; the absence is expected for these platforms
- When library files are later added to the resource tree, no code change is required
